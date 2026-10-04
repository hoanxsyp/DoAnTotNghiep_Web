"""End-to-end evaluation for FAISS retrieval + LightGBM ranking.

The model's user-group cross-validation score remains the primary unbiased
ranking metric. This script additionally checks temporal, end-to-end behavior
and reports candidate retrieval separately from final ranking quality.
"""

from __future__ import annotations

import json
import math
import sys
import time
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from app.recommender.indexer import get_index
from app.recommender.interactions import aggregate_interactions, relevance_label
from app.reranker.ranker import rerank
from app.reranker.trainer import _temporal_split


DATA_DIR = ROOT / "data"
EVALUATION_PATH = ROOT / "storage" / "evaluation_meta.json"
K_VALUES = [5, 10, 20]
MIN_INTERACTIONS = 5
CANDIDATE_K = 80


def _relevance_map(events: list[dict]) -> dict[str, int]:
    return {
        room_id: relevance_label(interaction)
        for room_id, interaction in aggregate_interactions(events).items()
    }


def ndcg_at_k(recommended: list[str], relevance: dict[str, int], k: int) -> float:
    gains = [2 ** relevance.get(room_id, 0) - 1 for room_id in recommended[:k]]
    dcg = sum(gain / math.log2(rank + 2) for rank, gain in enumerate(gains))
    ideal_gains = sorted(
        (2 ** label - 1 for label in relevance.values()), reverse=True
    )[:k]
    idcg = sum(gain / math.log2(rank + 2) for rank, gain in enumerate(ideal_gains))
    return dcg / idcg if idcg else 0.0


def recall_at_k(recommended: list[str], relevant_ids: set[str], k: int) -> float:
    if not relevant_ids:
        return 0.0
    return len(set(recommended[:k]) & relevant_ids) / len(relevant_ids)


def reciprocal_rank(recommended: list[str], relevant_ids: set[str]) -> float:
    for rank, room_id in enumerate(recommended, 1):
        if room_id in relevant_ids:
            return 1.0 / rank
    return 0.0


def _average(values: list[float]) -> float:
    return sum(values) / len(values) if values else 0.0


def evaluate() -> dict:
    users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    events = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    bundle = get_index()

    events_by_user: dict[str, list[dict]] = {}
    for event in events:
        events_by_user.setdefault(event["user_id"], []).append(event)

    eligible = [
        user for user in users
        if len(aggregate_interactions(events_by_user.get(user["id"], [])))
        >= MIN_INTERACTIONS
    ]
    metrics = {
        k: {"ndcg": [], "recall": [], "favorite_recall": [], "favorite_mrr": []}
        for k in K_VALUES
    }
    candidate_recalls: list[float] = []
    evaluated = 0
    started = time.perf_counter()

    for user in eligible:
        train_events, test_events = _temporal_split(events_by_user[user["id"]])
        relevance = _relevance_map(test_events)
        if not relevance:
            continue

        results = rerank(user, train_events, bundle, k=CANDIDATE_K)
        recommended = [result.room["id"] for result in results]
        relevant_ids = {room_id for room_id, label in relevance.items() if label > 0}
        favorite_ids = {room_id for room_id, label in relevance.items() if label == 3}

        candidate_recalls.append(recall_at_k(recommended, relevant_ids, CANDIDATE_K))
        for k in K_VALUES:
            metrics[k]["ndcg"].append(ndcg_at_k(recommended, relevance, k))
            metrics[k]["recall"].append(recall_at_k(recommended, relevant_ids, k))
            if favorite_ids:
                metrics[k]["favorite_recall"].append(
                    recall_at_k(recommended, favorite_ids, k)
                )
                metrics[k]["favorite_mrr"].append(
                    reciprocal_rank(recommended[:k], favorite_ids)
                )
        evaluated += 1

    summary = {
        "evaluated_users": evaluated,
        "candidate_recall_at_80": round(_average(candidate_recalls), 4),
        "elapsed_seconds": round(time.perf_counter() - started, 3),
        "metrics": {
            str(k): {
                name: round(_average(values), 4)
                for name, values in metrics[k].items()
            }
            for k in K_VALUES
        },
    }

    EVALUATION_PATH.parent.mkdir(exist_ok=True)
    EVALUATION_PATH.write_text(
        json.dumps(summary, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    return summary


if __name__ == "__main__":
    evaluate()
