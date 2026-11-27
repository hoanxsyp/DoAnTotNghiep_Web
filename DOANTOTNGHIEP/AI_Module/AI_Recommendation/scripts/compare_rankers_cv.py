"""Supplement the holdout experiment with user-level LightGBM and CatBoost folds."""

import argparse
import json
import sys
from functools import partial
from pathlib import Path
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

import faiss
from sklearn.model_selection import KFold, train_test_split

from app.recommender.faiss.indexer import build_index
from app.reranker.catboost.trainer import train_model as train_catboost
from app.reranker.dataset import generate_training_data
from app.reranker.lightgbm.experiment import train_model as train_lightgbm
from scripts.compare_rankers import CPU_THREADS, evaluate_split
from scripts.train_ranker_experiment import DATA_DIR, EXPERIMENT_DIR, SPLIT_SEED

FOLDS = 5


def compare() -> dict:
    users = sorted(
        json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8")),
        key=lambda user: user["id"],
    )
    events_by_user = {}
    for event in json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8")):
        events_by_user.setdefault(event["user_id"], []).append(event)

    faiss.omp_set_num_threads(CPU_THREADS)
    bundle = build_index(json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8")))
    folds = []
    outer_split = KFold(n_splits=FOLDS, shuffle=True, random_state=SPLIT_SEED)
    for fold_number, (development_indices, test_indices) in enumerate(outer_split.split(users), 1):
        print(f"Fold {fold_number}/{FOLDS}")
        train_indices, validation_indices = train_test_split(
            development_indices, test_size=0.20, random_state=SPLIT_SEED
        )
        train_users = [users[index] for index in train_indices]
        validation_users = [users[index] for index in validation_indices]
        test_users = [users[index] for index in test_indices]
        training_data = generate_training_data(train_users, events_by_user, bundle)
        validation_data = generate_training_data(validation_users, events_by_user, bundle)
        lightgbm, lightgbm_meta = train_lightgbm(*training_data, validation_data=validation_data)
        catboost, catboost_meta = train_catboost(*training_data, validation_data=validation_data)
        predictors = {
            "FAISS + LightGBM": partial(lightgbm.predict, num_threads=CPU_THREADS),
            "FAISS + CatBoost": partial(catboost.predict, thread_count=CPU_THREADS),
        }
        result = evaluate_split(test_users, events_by_user, bundle, predictors)
        result["fold"] = fold_number
        result["user_split"] = {
            "train": [user["id"] for user in train_users],
            "validation": [user["id"] for user in validation_users],
            "test": [user["id"] for user in test_users],
        }
        result["best_rounds"] = {
            "LightGBM": lightgbm_meta["validation"]["best_round"],
            "CatBoost": catboost_meta["validation"]["best_round"],
        }
        folds.append(result)

    evaluated_users = sum(fold["evaluated_users"] for fold in folds)
    methods = {}
    for name, metrics in folds[0]["methods"].items():
        methods[name] = {
            metric: sum(
                fold["methods"][name][metric] * fold["evaluated_users"] for fold in folds
            ) / evaluated_users
            for metric in metrics
        }
    return {
        "seed": SPLIT_SEED,
        "evaluated_users": evaluated_users,
        "cpu_threads": CPU_THREADS,
        "candidate_recall_at_80": sum(
            fold["candidate_recall_at_80"] * fold["evaluated_users"] for fold in folds
        ) / evaluated_users,
        "methods": methods,
        "folds": folds,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=EXPERIMENT_DIR / "ranker_cross_validation.json")
    output = parser.parse_args().output.resolve()
    if output.exists():
        parser.error(f"Output already exists: {output}")
    result = compare()
    output.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(dir=output.parent) as temporary:
        staged = Path(temporary) / output.name
        staged.write_text(json.dumps(result, indent=2, allow_nan=False) + "\n", encoding="utf-8")
        staged.rename(output)
    print(f"Saved cross-validation scores to {output}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    main()
