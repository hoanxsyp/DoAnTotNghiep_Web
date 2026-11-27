"""Train LightGBM on the same held-out split as the other rankers."""

import numpy as np
from lightgbm import Booster, Dataset, early_stopping, record_evaluation, train
from lightgbm import __version__ as lightgbm_version

from app.reranker.dataset import RankingData, validate_ranking_data
from app.reranker.features import get_feature_names
from app.reranker.lightgbm.trainer import LGBM_PARAMS

MAX_ROUNDS = 400
EARLY_STOPPING_ROUNDS = 30
EVAL_AT = 10


def train_model(
    X: np.ndarray,
    y: np.ndarray,
    groups: list[int],
    *,
    validation_data: RankingData,
) -> tuple[Booster, dict]:
    """Choose the best boosting round on the separate validation users."""
    validate_ranking_data((X, y, groups), "Training data", require_label_variation=True)
    validate_ranking_data(validation_data, "Validation data")
    X_val, y_val, val_groups = validation_data
    feature_names = get_feature_names()
    params = {**LGBM_PARAMS, "eval_at": [EVAL_AT], "n_jobs": 4}
    params.pop("n_estimators")

    training_set = Dataset(X, label=y, group=groups, feature_name=feature_names)
    validation_set = training_set.create_valid(X_val, label=y_val, group=val_groups)
    evaluation = {}
    model = train(
        params,
        training_set,
        num_boost_round=MAX_ROUNDS,
        valid_sets=[validation_set],
        valid_names=["validation"],
        callbacks=[
            record_evaluation(evaluation),
            early_stopping(EARLY_STOPPING_ROUNDS, verbose=False),
        ],
    )

    history = evaluation["validation"][f"ndcg@{EVAL_AT}"]
    best_round = model.best_iteration
    meta = {
        "model_type": "LGBMRanker",
        "lightgbm_version": lightgbm_version,
        "parameters": {
            **params,
            "num_boost_round": MAX_ROUNDS,
            "early_stopping_rounds": EARLY_STOPPING_ROUNDS,
        },
        "feature_names": feature_names,
        "n_samples": len(X),
        "n_features": X.shape[1],
        "n_query_groups": len(groups),
        "label_distribution": {str(label): int(np.sum(y == label)) for label in range(4)},
        "validation": {
            "n_samples": len(X_val),
            "n_query_groups": len(val_groups),
            "best_round": best_round,
            "ndcg_at_10": history[best_round - 1],
            "history": history,
        },
    }
    return model, meta
