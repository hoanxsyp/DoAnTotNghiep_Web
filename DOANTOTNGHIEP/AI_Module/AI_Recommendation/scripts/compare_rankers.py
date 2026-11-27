"""Compare FAISS and trained rankers on shared candidates and held-out users."""

import argparse
import csv
import json
import sys
from collections.abc import Callable
from functools import partial
from pathlib import Path
from statistics import fmean
from tempfile import TemporaryDirectory
from time import perf_counter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import faiss
import numpy as np
import pandas as pd

from app.recommender.faiss.engine import retrieve_candidates
from app.recommender.faiss.indexer import IndexBundle, build_index
from app.recommender.interactions import aggregate_interactions, interaction_count
from app.recommender.profile import build_user_profile
from app.reranker.dataset import temporal_split
from app.reranker.features import compute_features, compute_user_stats, get_feature_names
from app.reranker.ranker import CANDIDATE_FETCH, DEFAULT_K, MIN_VIEWS_FOR_LGBM
from scripts.evaluate import ndcg_at_k, recall_at_k, relevance_map
from scripts.train_ranker_experiment import DATA_DIR, EXPERIMENT_DIR, _user_split

BASELINE = "FAISS"
CPU_THREADS = 4
MODEL_DIRS = ("lightgbm", "xgboost", "catboost")
Predictors = dict[str, Callable[[pd.DataFrame], np.ndarray]]


def load_predictors() -> Predictors:
    from catboost import CatBoostRanker
    from lightgbm import Booster
    from xgboost import XGBRanker

    lightgbm_path = EXPERIMENT_DIR / "lightgbm" / "model.txt"
    lightgbm = Booster(model_str=lightgbm_path.read_text(encoding="utf-8"))
    xgboost = XGBRanker(n_jobs=CPU_THREADS)
    xgboost.load_model(EXPERIMENT_DIR / "xgboost" / "model.ubj")
    catboost = CatBoostRanker().load_model(str(EXPERIMENT_DIR / "catboost" / "model.cbm"))

    predictors = {
        "FAISS + LightGBM": partial(lightgbm.predict, num_threads=CPU_THREADS),
        "FAISS + XGBoost": xgboost.predict,
        "FAISS + CatBoost": partial(catboost.predict, thread_count=CPU_THREADS),
    }
    feature_names = get_feature_names()
    model_features = (
        lightgbm.feature_name(), xgboost.get_booster().feature_names, catboost.feature_names_
    )
    warmup = pd.DataFrame(np.zeros((1, len(feature_names)), dtype=np.float32), columns=feature_names)
    for (name, predict), features in zip(predictors.items(), model_features):
        if features != feature_names:
            raise ValueError(f"{name}: feature schema differs from the current schema.")
        predict(warmup)
    return predictors


def evaluate_split(
    users: list[dict],
    events_by_user: dict[str, list[dict]],
    bundle: IndexBundle,
    predictors: Predictors,
) -> dict:
    samples = {name: [] for name in (BASELINE, *predictors)}
    candidate_recalls = []
    candidate_counts = []
    reranked_users = 0
    feature_names = get_feature_names()

    for user in users:
        history, targets = temporal_split(events_by_user.get(user["id"], []))
        relevance = relevance_map(targets)
        relevant_ids = {room_id for room_id, label in relevance.items() if label > 0}
        if not relevant_ids:
            continue

        started = perf_counter()
        interactions = aggregate_interactions(history)
        profile = build_user_profile(user, history, bundle.room_cache)
        candidates = list(retrieve_candidates(profile, bundle, set(interactions), CANDIDATE_FETCH))
        candidate_ids = [room["id"] for room, _ in candidates]
        faiss_top = candidate_ids[:DEFAULT_K]
        retrieval_ms = (perf_counter() - started) * 1000

        candidate_counts.append(len(candidate_ids))
        candidate_recalls.append(recall_at_k(candidate_ids, relevant_ids, CANDIDATE_FETCH))
        # Use the same FAISS order for all methods when the serving path skips reranking.
        rankings = {name: (faiss_top, retrieval_ms) for name in samples}
        if predictors and candidates and interaction_count(interactions) >= MIN_VIEWS_FOR_LGBM:
            reranked_users += 1
            started = perf_counter()
            user_stats = compute_user_stats(history, bundle.room_cache)
            features = np.stack([
                compute_features(user_stats, room, score, user.get("district"))
                for room, score in candidates
            ]).astype(np.float32)
            feature_matrix = pd.DataFrame(features, columns=feature_names)
            base_ms = retrieval_ms + (perf_counter() - started) * 1000

            for name, predict in predictors.items():
                started = perf_counter()
                scores = np.asarray(predict(feature_matrix))
                if scores.shape != (len(candidates),) or not np.isfinite(scores).all():
                    raise ValueError(f"{name}: invalid candidate scores.")
                order = np.argsort(-scores, kind="stable")[:DEFAULT_K]
                ranking = [candidate_ids[position] for position in order]
                latency_ms = base_ms + (perf_counter() - started) * 1000
                rankings[name] = ranking, latency_ms

        for name, (ranking, latency_ms) in rankings.items():
            samples[name].append((
                ndcg_at_k(ranking, relevance, DEFAULT_K),
                recall_at_k(ranking, relevant_ids, DEFAULT_K),
                latency_ms,
            ))

    evaluated_users = len(candidate_recalls)
    if not evaluated_users:
        raise ValueError("No users with target interactions to evaluate.")

    methods = {}
    for name, rows in samples.items():
        ndcg, recall, latency = (fmean(column) for column in zip(*rows))
        methods[name] = {"ndcg_at_10": ndcg, "recall_at_10": recall, "mean_latency_ms": latency}
    return {
        "assigned_users": len(users),
        "evaluated_users": evaluated_users,
        "reranked_users": reranked_users,
        "users_with_candidate_hit": sum(recall > 0 for recall in candidate_recalls),
        "mean_candidates": fmean(candidate_counts),
        "candidate_recall_at_80": fmean(candidate_recalls),
        "methods": methods,
    }


def write_results(output_dir: Path, comparison: dict) -> None:
    fields = ["split", "method", "evaluated_users", "ndcg_at_10", "recall_at_10", "mean_latency_ms"]
    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(dir=output_dir.parent) as temporary:
        directory = Path(temporary)
        (directory / "comparison.json").write_text(
            json.dumps(comparison, ensure_ascii=False, indent=2, allow_nan=False) + "\n",
            encoding="utf-8",
        )
        with (directory / "comparison.csv").open("w", encoding="utf-8-sig", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=fields)
            writer.writeheader()
            for name in ("validation", "test"):
                split = comparison[name]
                for method, metrics in split["methods"].items():
                    writer.writerow({
                        "split": name,
                        "method": method,
                        "evaluated_users": split["evaluated_users"],
                        **{key: f"{value:.6f}" for key, value in metrics.items()},
                    })
        directory.rename(output_dir)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=EXPERIMENT_DIR / "comparison")
    output_dir = parser.parse_args().output_dir.resolve()
    if output_dir.exists():
        parser.error(f"Output directory already exists: {output_dir}")

    users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    split_path = EXPERIMENT_DIR / "xgboost" / "training_meta.json"
    split = _user_split(users, split_path)
    for name in MODEL_DIRS:
        metadata = json.loads((EXPERIMENT_DIR / name / "training_meta.json").read_text(encoding="utf-8"))
        if metadata["user_split"] != split or metadata["feature_names"] != get_feature_names():
            parser.error(f"{name}: training split or feature schema differs from the comparison setup.")

    events = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    events_by_user = {}
    for event in events:
        events_by_user.setdefault(event["user_id"], []).append(event)
    users_by_id = {user["id"]: user for user in users}

    faiss.omp_set_num_threads(CPU_THREADS)
    rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))
    bundle = build_index(rooms)
    predictors = load_predictors()
    validation_users = [users_by_id[user_id] for user_id in split["validation"]]
    validation = evaluate_split(validation_users, events_by_user, bundle, predictors)
    selected = max(validation["methods"], key=lambda name: validation["methods"][name]["ndcg_at_10"])
    print(f"Selected on validation NDCG@10: {selected}")

    test_users = [users_by_id[user_id] for user_id in split["test"]]
    test_predictors = {} if selected == BASELINE else {selected: predictors[selected]}
    test = evaluate_split(test_users, events_by_user, bundle, test_predictors)
    comparison = {
        "selected_method": selected,
        "selection_metric": "validation NDCG@10",
        "top_k": DEFAULT_K,
        "faiss_fetch_k": CANDIDATE_FETCH,
        "faiss_index": bundle.meta,
        "min_signals_for_reranking": MIN_VIEWS_FOR_LGBM,
        "cpu_threads": CPU_THREADS,
        "latency_scope": "Profile, FAISS retrieval/filter, and feature/scoring/sorting when applicable.",
        "user_split": split,
        "validation": validation,
        "test": test,
    }
    write_results(output_dir, comparison)
    print(f"Saved comparison to {output_dir}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    main()
