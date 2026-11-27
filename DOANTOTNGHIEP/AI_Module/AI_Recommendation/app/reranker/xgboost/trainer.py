"""Train a LambdaMART ranker on the shared user-room features."""

import numpy as np
import pandas as pd
from xgboost import XGBRanker, __version__ as xgboost_version

from app.reranker.dataset import RankingData, validate_ranking_data
from app.reranker.features import get_feature_names

XGB_PARAMS = {
    "objective": "rank:ndcg",
    "eval_metric": "ndcg@10-",
    "ndcg_exp_gain": True,
    # Sample pairs across the full group for this small dataset.
    "lambdarank_pair_method": "mean",
    "lambdarank_num_pair_per_sample": 4,
    "tree_method": "hist",
    "n_estimators": 400,
    "learning_rate": 0.05,
    "max_depth": 4,
    "min_child_weight": 2,
    "subsample": 0.8,
    "colsample_bytree": 0.8,
    "reg_lambda": 1.0,
    "random_state": 42,
    "n_jobs": 4,
}


def train_model(
    X: np.ndarray,
    y: np.ndarray,
    groups: list[int],
    *,
    validation_data: RankingData | None = None,
) -> tuple[XGBRanker, dict]:
    """Fit XGBoost with contiguous rows per user, as produced by dataset.py."""
    validate_ranking_data((X, y, groups), "Training data", require_label_variation=True)

    feature_names = get_feature_names()
    params = XGB_PARAMS.copy()
    fit_options = {}
    if validation_data is not None:
        validate_ranking_data(validation_data, "Validation data")
        X_val, y_val, val_groups = validation_data
        params["early_stopping_rounds"] = 30
        fit_options["eval_set"] = [(pd.DataFrame(X_val, columns=feature_names), y_val)]
        fit_options["eval_group"] = [val_groups]

    model = XGBRanker(**params)
    model.fit(pd.DataFrame(X, columns=feature_names), y, group=groups, verbose=False, **fit_options)
    meta = {
        "model_type": "XGBRanker",
        "xgboost_version": xgboost_version,
        "parameters": params,
        "feature_names": feature_names,
        "n_samples": len(X),
        "n_features": X.shape[1],
        "n_query_groups": len(groups),
        "label_distribution": {str(label): int(np.sum(y == label)) for label in range(4)},
    }
    if validation_data is not None:
        meta["validation"] = {
            "n_samples": len(X_val),
            "n_query_groups": len(val_groups),
            "best_round": model.best_iteration + 1,
            "ndcg_at_10": float(model.best_score),
            "history": model.evals_result()["validation_0"][params["eval_metric"]],
        }
    return model, meta
