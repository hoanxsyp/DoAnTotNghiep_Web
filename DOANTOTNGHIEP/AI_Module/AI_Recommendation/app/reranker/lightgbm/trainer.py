"""Training and model persistence for the LightGBM ranker."""

import json
import pickle
from pathlib import Path

import numpy as np
from lightgbm import LGBMRanker
from sklearn.metrics import ndcg_score
from sklearn.model_selection import GroupKFold

from app.reranker.features import get_feature_names

N_SPLITS          = 5      # số fold cross-validation theo user group
STORAGE_DIR       = Path(__file__).resolve().parents[3] / "storage"
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
