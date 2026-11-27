"""Train CatBoost YetiRank for Top-10 room ranking."""

import numpy as np
from catboost import CatBoostRanker, Pool, __version__ as catboost_version

from app.reranker.dataset import RankingData, validate_ranking_data
from app.reranker.features import get_feature_names

CATBOOST_PARAMS = {
    "loss_function": "YetiRank:mode=NDCG;top=10;dcg_type=Exp;dcg_denominator=LogPosition",
    "eval_metric": "NDCG:top=10;type=Exp",
    "iterations": 400,
    "learning_rate": 0.05,
    "depth": 4,
    "l2_leaf_reg": 3.0,
    "random_seed": 42,
    "task_type": "CPU",
    "thread_count": 4,
    "allow_writing_files": False,
    "verbose": False,
}
EARLY_STOPPING_ROUNDS = 30


def _make_pool(data: RankingData, feature_names: list[str]) -> Pool:
    X, y, groups = data
    group_ids = np.repeat(np.arange(len(groups)), groups)
    return Pool(X, label=y, group_id=group_ids, feature_names=feature_names)


def train_model(
    X: np.ndarray,
    y: np.ndarray,
    groups: list[int],
    *,
    validation_data: RankingData | None = None,
) -> tuple[CatBoostRanker, dict]:
    """Fit a CatBoost ranker on contiguous groups of rooms for each user."""
    training_data = X, y, groups
    validate_ranking_data(training_data, "Training data", require_label_variation=True)
    feature_names = get_feature_names()
    fit_options = {}
    recorded_params = CATBOOST_PARAMS.copy()
    if validation_data is not None:
        validate_ranking_data(validation_data, "Validation data")
        fit_options = {
            "eval_set": _make_pool(validation_data, feature_names),
            "early_stopping_rounds": EARLY_STOPPING_ROUNDS,
            "use_best_model": True,
        }
        recorded_params.update(
            early_stopping_rounds=EARLY_STOPPING_ROUNDS, use_best_model=True
        )

    model = CatBoostRanker(**CATBOOST_PARAMS)
    model.fit(_make_pool(training_data, feature_names), **fit_options)
    meta = {
        "model_type": "CatBoostRanker",
        "catboost_version": catboost_version,
        "parameters": recorded_params,
        "feature_names": feature_names,
        "n_samples": len(X),
        "n_features": X.shape[1],
        "n_query_groups": len(groups),
        "label_distribution": {str(label): int(np.sum(y == label)) for label in range(4)},
    }
    if validation_data is not None:
        X_val, _, val_groups = validation_data
        best_iteration = model.get_best_iteration()
        history = model.get_evals_result()["validation"][CATBOOST_PARAMS["eval_metric"]]
        meta["validation"] = {
            "n_samples": len(X_val),
            "n_query_groups": len(val_groups),
            "best_round": best_iteration + 1,
            "ndcg_at_10": history[best_iteration],
            "history": history,
        }
    return model, meta
