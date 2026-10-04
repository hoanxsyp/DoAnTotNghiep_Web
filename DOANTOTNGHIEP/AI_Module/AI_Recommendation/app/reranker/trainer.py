"""
Training pipeline cho LightGBM Re-ranker.

Chiến lược sinh training data:
  - Temporal split: 80% history cũ → compute user stats + generate pairs
  - 80% user-room interactions cũ tạo profile; 20% mới làm ground truth
  - Relevance: favorite=3, xem nhiều=2, xem ít=1, không tương tác=0
  - Hard negatives lấy từ candidates FAISS, tỉ lệ tối đa 1:4
  - Mỗi user là một query group của LGBMRanker
"""

import json
import pickle
import sys
from pathlib import Path

import numpy as np
from lightgbm import LGBMRanker
from sklearn.metrics import ndcg_score
from sklearn.model_selection import GroupKFold

from app.reranker.features import compute_user_stats, compute_features, get_feature_names, FEATURE_DIM
from app.recommender.indexer import IndexBundle, get_index
from app.recommender.interactions import aggregate_interactions, relevance_label
from app.recommender.profile import build_history_profile

import faiss

# ─── Config ───────────────────────────────────────────────────────────────────

TRAIN_RATIO       = 0.8    # 80% history cũ dùng để train
NEG_POS_RATIO     = 4      # tối đa 4 hard negatives mỗi positive
CANDIDATE_FETCH   = 80     # lấy bao nhiêu KNN candidates để tạo negatives
MIN_VIEWS         = 5      # bỏ qua user có < MIN_VIEWS phòng đã tương tác
N_SPLITS          = 5      # số fold cross-validation theo user group
STORAGE_DIR       = Path(__file__).parent.parent.parent / "storage"
MODEL_PATH        = STORAGE_DIR / "reranker.pkl"
META_PATH         = STORAGE_DIR / "reranker_meta.json"

LGBM_PARAMS = {
    "objective":      "lambdarank",
    "metric":         "ndcg",
    "label_gain":     [0, 1, 3, 7],
    "n_estimators":   500,
    "learning_rate":  0.05,
    "max_depth":      6,
    "num_leaves":     31,
    "min_child_samples": 20,
    "subsample":      0.8,
    "colsample_bytree": 0.8,
    "reg_alpha":      0.1,
    "reg_lambda":     1.0,
    "random_state":   42,
    "n_jobs":         -1,
    "verbose":        -1,
}


# ─── Training data generation ─────────────────────────────────────────────────

def _temporal_split(history: list[dict]) -> tuple[list[dict], list[dict]]:
    """Split chronologically while keeping all events of a room together.

    Keeping a user-room interaction in one side preserves its complete view
    count/favorite label and prevents the same room from leaking into both the
    profile history and the ranking target.
    """
    def event_time(event: dict) -> str:
        return event.get("occurred_at") or event.get("viewed_at", "")

    events_by_room: dict[str, list[dict]] = {}
    for event in history:
        room_id = event.get("room_id")
        if room_id:
            events_by_room.setdefault(room_id, []).append(event)

    room_timelines = sorted(
        events_by_room.values(),
        key=lambda events: max(event_time(event) for event in events),
    )
    split_idx = max(1, int(len(room_timelines) * TRAIN_RATIO))
    train_rooms = room_timelines[:split_idx]
    test_rooms = room_timelines[split_idx:]
    train_events = sorted(
        [event for events in train_rooms for event in events], key=event_time
    )
    test_events = sorted(
        [event for events in test_rooms for event in events], key=event_time
    )
    return train_events, test_events


def _get_faiss_candidates(
    train_history: list[dict],
    bundle: IndexBundle,
    user: dict,
    k: int = CANDIDATE_FETCH,
) -> list[tuple[str, float]]:
    """Trả về list (room_id, faiss_score) từ FAISS search."""
    profile, _ = build_history_profile(train_history, bundle.room_cache)

    if profile is None:
        # cold start: dùng district của user
        from app.recommender.profile import build_address_profile
        profile = build_address_profile(user.get("city"), user.get("district"))

    if profile is None:
        return []

    query = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(query)

    scores, positions = bundle.index.search(query, k)
    results = []
    for pos, score in zip(positions[0], scores[0]):
        if pos == -1:
            continue
        room_id = bundle.id_map[pos]
        results.append((room_id, float(score)))
    return results


def _compute_faiss_score(room: dict, profile: np.ndarray | None, bundle: IndexBundle) -> float:
    """Tính cosine similarity giữa user profile và một room bất kỳ."""
    if profile is None:
        return 0.0
    from app.features.extractor import get_extractor
    extractor = get_extractor()
    room_vec = extractor.extract(room).reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(room_vec)
    q = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(q)
    score = float(np.dot(q[0], room_vec[0]))
    return max(0.0, score)


def generate_training_data(
    users: list[dict],
    history_by_user: dict[str, list[dict]],
    bundle: IndexBundle,
) -> tuple[np.ndarray, np.ndarray, list[int]]:
    """
    Sinh (X, y, groups) cho LGBMRanker.

    Mỗi user là một query group. Tương tác trong phần holdout được gán mức
    relevance: favorite=3, xem nhiều=2, xem ít=1. Negative khó được lấy từ
    top candidates của FAISS để khớp với phân phối lúc inference.
    """
    X_rows, y_rows, groups = [], [], []
    n_pos = n_neg = n_skipped = 0
    label_counts = {0: 0, 1: 0, 2: 0, 3: 0}

    eligible = [
        user for user in users
        if len(aggregate_interactions(history_by_user.get(user["id"], []))) >= MIN_VIEWS
    ]
    print(f"  Eligible users: {len(eligible)}")

    for user in eligible:
        uid = user["id"]
        user_history = history_by_user[uid]

        train_history, test_history = _temporal_split(user_history)
        if not test_history:
            n_skipped += 1
            continue

        user_stats = compute_user_stats(train_history, bundle.room_cache)
        user_district = user.get("district")
        train_room_ids = set(aggregate_interactions(train_history))
        test_interactions = aggregate_interactions(test_history)
        relevance_by_room = {
            room_id: relevance_label(interaction)
            for room_id, interaction in test_interactions.items()
            if room_id in bundle.room_cache
        }

        if not relevance_by_room:
            n_skipped += 1
            continue

        # Tính profile và lấy hard negatives đúng từ candidate pool của FAISS.
        profile, _ = build_history_profile(train_history, bundle.room_cache)
        faiss_candidates = _get_faiss_candidates(train_history, bundle, user)
        faiss_scores = dict(faiss_candidates)

        positive_ids = list(relevance_by_room)
        negative_ids = [
            room_id
            for room_id, _ in faiss_candidates
            if room_id not in train_room_ids and room_id not in relevance_by_room
        ]
        negative_ids = negative_ids[: len(positive_ids) * NEG_POS_RATIO]

        # Positives được giữ lại dù nằm ngoài top-80 để model vẫn học được tín
        # hiệu relevance; negatives là các phòng gần profile nhưng không chọn.
        candidate_ids = positive_ids + negative_ids
        if not candidate_ids:
            n_skipped += 1
            continue

        group_size = 0
        for room_id in candidate_ids:
            room = bundle.room_cache[room_id]
            score = faiss_scores.get(room_id)
            if score is None:
                score = _compute_faiss_score(room, profile, bundle)
            feat  = compute_features(user_stats, room, score, user_district)
            label = relevance_by_room.get(room_id, 0)
            X_rows.append(feat)
            y_rows.append(label)
            label_counts[label] += 1
            n_pos += int(label > 0)
            n_neg += int(label == 0)
            group_size += 1
        groups.append(group_size)

    print(f"  Positives : {n_pos}")
    print(f"  Negatives : {n_neg}")
    print(f"  Skipped   : {n_skipped}")
    print(f"  Ratio     : 1:{n_neg//n_pos if n_pos else 0}")
    print(f"  Labels    : {label_counts}")
    print(f"  Groups    : {len(groups)}")

    if not X_rows:
        raise ValueError("No training samples generated from the interaction data.")
    X = np.stack(X_rows).astype(np.float32)
    y = np.array(y_rows, dtype=np.int32)
    return X, y, groups


# ─── Training ─────────────────────────────────────────────────────────────────

def train_model(X: np.ndarray, y: np.ndarray, groups: list[int]) -> tuple[LGBMRanker, dict]:
    """
    Train LGBMRanker với cross-validation tách theo user/query.

    Việc giữ nguyên toàn bộ group của một user trong cùng một fold tránh rò rỉ
    các mẫu của một user sang cả train và validation.
    """
    feature_names = get_feature_names()

    if sum(groups) != len(X):
        raise ValueError("Sum of query group sizes must equal the number of rows in X.")
    if len(groups) < 2:
        raise ValueError("At least two user groups are required to train the ranker.")

    print(f"\n  Training LightGBM Ranker  (samples={len(X)}, features={X.shape[1]})")
    print(f"  Query groups: {len(groups)}")

    offsets = np.cumsum([0] + groups)
    query_indices = np.arange(len(groups))
    n_splits = min(N_SPLITS, len(groups))
    group_kfold = GroupKFold(n_splits=n_splits)
    ndcg_scores = []

    for fold, (train_queries, val_queries) in enumerate(
        group_kfold.split(query_indices, groups=query_indices)
    ):
        train_idx = np.concatenate([
            np.arange(offsets[q], offsets[q + 1]) for q in train_queries
        ])
        val_idx = np.concatenate([
            np.arange(offsets[q], offsets[q + 1]) for q in val_queries
        ])
        train_groups = [groups[q] for q in train_queries]
        val_groups = [groups[q] for q in val_queries]
        X_tr, X_val = X[train_idx], X[val_idx]
        y_tr, y_val = y[train_idx], y[val_idx]

        model_fold = LGBMRanker(**LGBM_PARAMS)
        model_fold.fit(
            X_tr, y_tr,
            group=train_groups,
            eval_set=[(X_val, y_val)],
            eval_group=[val_groups],
            eval_at=[5, 10],
            feature_name=feature_names,
            callbacks=[],
        )

        predictions = model_fold.predict(X_val)
        query_ndcg = []
        cursor = 0
        for group_size in val_groups:
            end = cursor + group_size
            query_ndcg.append(ndcg_score(
                [y_val[cursor:end]],
                [predictions[cursor:end]],
                k=min(10, group_size),
            ))
            cursor = end
        fold_ndcg = float(np.mean(query_ndcg))
        ndcg_scores.append(fold_ndcg)
        print(f"    Fold {fold+1}: NDCG@10={fold_ndcg:.4f}")

    print(f"  CV NDCG@10: {np.mean(ndcg_scores):.4f} ± {np.std(ndcg_scores):.4f}")

    # ── Train final model trên toàn bộ data ───────────────────────────────────
    print("\n  Training final model on full data...")
    final_model = LGBMRanker(**LGBM_PARAMS)
    final_model.fit(X, y, group=groups, feature_name=feature_names)

    # ── Feature importance ────────────────────────────────────────────────────
    importances = final_model.feature_importances_
    top_features = sorted(
        zip(feature_names, importances),
        key=lambda x: -x[1]
    )[:10]

    print("\n  Top 10 important features:")
    max_importance = importances.max() if len(importances) else 0
    for fname, imp in top_features:
        bar = "█" * int(imp / max_importance * 20) if max_importance else ""
        print(f"    {fname:<35} {bar} {imp:.0f}")

    return final_model, {
        "model_type": "LGBMRanker",
        "cv_ndcg_at_10_mean": round(float(np.mean(ndcg_scores)), 4),
        "cv_ndcg_at_10_std":  round(float(np.std(ndcg_scores)), 4),
        "n_samples":   len(X),
        "n_features":  X.shape[1],
        "n_query_groups": len(groups),
        "label_distribution": {
            str(label): int(np.sum(y == label)) for label in range(4)
        },
    }


# ─── Save / Load ──────────────────────────────────────────────────────────────

def save_model(model: LGBMRanker, meta: dict) -> None:
    STORAGE_DIR.mkdir(exist_ok=True)
    MODEL_PATH.write_bytes(pickle.dumps(model))
    META_PATH.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n  Model saved → {MODEL_PATH.name}")


def load_model():
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"Re-ranker model not found. Run train_reranker.py first.")
    return pickle.loads(MODEL_PATH.read_bytes())


_model = None

def get_model():
    global _model
    if _model is None:
        _model = load_model()
    return _model


def reload_model():
    """Reload the saved model after a background retraining job."""
    global _model
    _model = load_model()
    return _model
