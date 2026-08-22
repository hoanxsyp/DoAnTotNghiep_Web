"""
Training pipeline cho LightGBM Re-ranker.

Chiến lược sinh training data:
  - Temporal split: 80% history cũ → compute user stats + generate pairs
  - Positive (y=1): rooms trong 20% mới nhất (ground truth)
  - Negative (y=0): FAISS candidates KHÔNG có trong toàn bộ view history
  - Hard negative sampling: lấy từ KNN candidates (khó → model học được biên quyết định)
  - Tỉ lệ: 1 positive : 4 negative
"""

import json
import pickle
import sys
from pathlib import Path

import numpy as np
from lightgbm import LGBMClassifier
from sklearn.metrics import roc_auc_score, average_precision_score
from sklearn.model_selection import StratifiedKFold

from app.reranker.features import compute_user_stats, compute_features, get_feature_names, FEATURE_DIM
from app.recommender.indexer import IndexBundle, get_index
from app.recommender.profile import build_history_profile

import faiss

# ─── Config ───────────────────────────────────────────────────────────────────

TRAIN_RATIO       = 0.8    # 80% history cũ dùng để train
NEG_POS_RATIO     = 4      # 4 negatives mỗi positive
CANDIDATE_FETCH   = 80     # lấy bao nhiêu KNN candidates để tạo negatives
MIN_VIEWS         = 5      # bỏ qua user có < MIN_VIEWS
N_SPLITS          = 5      # số fold cross-validation
STORAGE_DIR       = Path(__file__).parent.parent.parent / "storage"
MODEL_PATH        = STORAGE_DIR / "reranker.pkl"
META_PATH         = STORAGE_DIR / "reranker_meta.json"

LGBM_PARAMS = {
    "n_estimators":   500,
    "learning_rate":  0.05,
    "max_depth":      6,
    "num_leaves":     31,
    "min_child_samples": 20,
    "subsample":      0.8,
    "colsample_bytree": 0.8,
    "reg_alpha":      0.1,
    "reg_lambda":     1.0,
    "class_weight":   "balanced",
    "random_state":   42,
    "n_jobs":         -1,
    "verbose":        -1,
}


# ─── Training data generation ─────────────────────────────────────────────────

def _temporal_split(history: list[dict]) -> tuple[list[dict], list[dict]]:
    sorted_h  = sorted(history, key=lambda e: e["viewed_at"])
    split_idx = max(1, int(len(sorted_h) * TRAIN_RATIO))
    return sorted_h[:split_idx], sorted_h[split_idx:]


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
) -> tuple[np.ndarray, np.ndarray]:
    """
    Sinh (X, y) cho LightGBM.

    Chiến lược cải tiến:
      Positive: TẤT CẢ test rooms (không cần nằm trong FAISS top-80)
                → tính faiss_score riêng cho từng room
      Negative: sample ngẫu nhiên từ toàn bộ room pool (không trong view history)
                → đa dạng hơn, tránh bias về FAISS candidates
    """
    import random
    random.seed(42)

    X_rows, y_rows = [], []
    n_pos = n_neg = n_skipped = 0
    all_room_ids = list(bundle.room_cache.keys())

    eligible = [u for u in users if len(history_by_user.get(u["id"], [])) >= MIN_VIEWS]
    print(f"  Eligible users: {len(eligible)}")

    for user in eligible:
        uid = user["id"]
        user_history = history_by_user[uid]
        all_viewed_ids = {e["room_id"] for e in user_history}

        train_history, test_history = _temporal_split(user_history)
        if not test_history:
            n_skipped += 1
            continue

        user_stats = compute_user_stats(train_history, bundle.room_cache)
        user_district = user.get("district")

        # Tính user profile từ train history (để tính faiss_score)
        profile, _ = build_history_profile(train_history, bundle.room_cache)

        # ── Positives: tất cả test rooms ──────────────────────────────────────
        pos_rooms = [
            bundle.room_cache[e["room_id"]]
            for e in test_history
            if e["room_id"] in bundle.room_cache
        ]
        if not pos_rooms:
            n_skipped += 1
            continue

        for room in pos_rooms:
            score = _compute_faiss_score(room, profile, bundle)
            feat  = compute_features(user_stats, room, score, user_district)
            X_rows.append(feat)
            y_rows.append(1)
            n_pos += 1

        # ── Negatives: random từ toàn bộ pool (không trong view history) ─────
        neg_pool = [
            rid for rid in all_room_ids
            if rid not in all_viewed_ids and rid in bundle.room_cache
        ]
        n_neg_needed = min(len(pos_rooms) * NEG_POS_RATIO, len(neg_pool))
        sampled_neg_ids = random.sample(neg_pool, n_neg_needed)

        for room_id in sampled_neg_ids:
            room  = bundle.room_cache[room_id]
            score = _compute_faiss_score(room, profile, bundle)
            feat  = compute_features(user_stats, room, score, user_district)
            X_rows.append(feat)
            y_rows.append(0)
            n_neg += 1

    print(f"  Positives : {n_pos}")
    print(f"  Negatives : {n_neg}")
    print(f"  Skipped   : {n_skipped}")
    print(f"  Ratio     : 1:{n_neg//n_pos if n_pos else 0}")

    X = np.stack(X_rows).astype(np.float32)
    y = np.array(y_rows, dtype=np.int32)
    return X, y


# ─── Training ─────────────────────────────────────────────────────────────────

def train_model(X: np.ndarray, y: np.ndarray) -> LGBMClassifier:
    """
    Train LightGBM với Stratified K-Fold cross-validation.
    Trả về model train trên toàn bộ data (CV chỉ để đánh giá).
    """
    feature_names = get_feature_names()

    print(f"\n  Training LightGBM  (samples={len(X)}, features={X.shape[1]})")
    print(f"  Positive rate: {y.mean():.3f}")

    # ── Cross-validation để đánh giá ──────────────────────────────────────────
    skf = StratifiedKFold(n_splits=N_SPLITS, shuffle=True, random_state=42)
    auc_scores, ap_scores = [], []

    for fold, (train_idx, val_idx) in enumerate(skf.split(X, y)):
        X_tr, X_val = X[train_idx], X[val_idx]
        y_tr, y_val = y[train_idx], y[val_idx]

        model_fold = LGBMClassifier(**LGBM_PARAMS)
        model_fold.fit(
            X_tr, y_tr,
            eval_set=[(X_val, y_val)],
            feature_name=feature_names,
            callbacks=[],
        )

        proba = model_fold.predict_proba(X_val)[:, 1]
        auc = roc_auc_score(y_val, proba)
        ap  = average_precision_score(y_val, proba)
        auc_scores.append(auc)
        ap_scores.append(ap)
        print(f"    Fold {fold+1}: AUC={auc:.4f}  AP={ap:.4f}")

    print(f"  CV AUC : {np.mean(auc_scores):.4f} ± {np.std(auc_scores):.4f}")
    print(f"  CV AP  : {np.mean(ap_scores):.4f} ± {np.std(ap_scores):.4f}")

    # ── Train final model trên toàn bộ data ───────────────────────────────────
    print("\n  Training final model on full data...")
    final_model = LGBMClassifier(**LGBM_PARAMS)
    final_model.fit(X, y, feature_name=feature_names)

    # ── Feature importance ────────────────────────────────────────────────────
    importances = final_model.feature_importances_
    top_features = sorted(
        zip(feature_names, importances),
        key=lambda x: -x[1]
    )[:10]

    print("\n  Top 10 important features:")
    for fname, imp in top_features:
        bar = "█" * int(imp / importances.max() * 20)
        print(f"    {fname:<35} {bar} {imp:.0f}")

    return final_model, {
        "cv_auc_mean": round(float(np.mean(auc_scores)), 4),
        "cv_auc_std":  round(float(np.std(auc_scores)), 4),
        "cv_ap_mean":  round(float(np.mean(ap_scores)), 4),
        "n_samples":   len(X),
        "n_features":  X.shape[1],
        "pos_rate":    round(float(y.mean()), 4),
    }


# ─── Save / Load ──────────────────────────────────────────────────────────────

def save_model(model: LGBMClassifier, meta: dict) -> None:
    STORAGE_DIR.mkdir(exist_ok=True)
    MODEL_PATH.write_bytes(pickle.dumps(model))
    META_PATH.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n  Model saved → {MODEL_PATH.name}")


def load_model() -> LGBMClassifier:
    if not MODEL_PATH.exists():
        raise FileNotFoundError(f"Re-ranker model not found. Run train_reranker.py first.")
    return pickle.loads(MODEL_PATH.read_bytes())


_model: LGBMClassifier | None = None

def get_model() -> LGBMClassifier:
    global _model
    if _model is None:
        _model = load_model()
    return _model
