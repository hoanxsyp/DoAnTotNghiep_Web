# -*- coding: utf-8 -*-
"""
So sánh 3 mô hình tương thích runtime trên dataset TỔNG Hà Nội, tune model thắng, lưu.
Models: Ridge | RandomForest | LightGBM | target = price_million

Chạy:  python train_compare.py                      # dùng hanoi_all_clean.csv
       python train_compare.py --data data/processed/thanh-xuan.csv --name thanh_xuan
"""
import argparse, json, sys, warnings
from datetime import date
from pathlib import Path
import numpy as np
import pandas as pd
import joblib

from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer
from sklearn.model_selection import GroupKFold, GroupShuffleSplit, GridSearchCV
from sklearn.base import clone
from sklearn.linear_model import Ridge
from sklearn.ensemble import RandomForestRegressor
from sklearn.metrics import (mean_absolute_error, mean_squared_error,
                             r2_score, mean_absolute_percentage_error)
from lightgbm import LGBMRegressor

warnings.filterwarnings("ignore")
sys.stdout.reconfigure(encoding="utf-8")

NUM = ["area_m2", "number_of_amenities", "distance_to_center_km", "floor",
       "posted_month", "listing_age_days", "latitude", "longitude"]
CAT = ["district", "ward", "room_type"]
BIN = ["has_dieu_hoa", "has_khep_kin", "has_ban_cong", "has_thang_may",
       "has_full_do", "has_gac", "has_may_giat", "has_nong_lanh",
       "has_wifi", "has_de_xe", "has_coordinates"]
TARGET = "price_million"


def prep():
    return ColumnTransformer([
        ("num", Pipeline([("imp", SimpleImputer(strategy="median")),
                          ("sc", StandardScaler())]), NUM),
        ("cat", Pipeline([("imp", SimpleImputer(strategy="constant", fill_value="unknown")),
                          ("oh", OneHotEncoder(handle_unknown="ignore", min_frequency=8))]), CAT),
        ("bin", "passthrough", BIN),
    ])


def base_models():
    return {
        "Ridge": Ridge(alpha=1.0, random_state=42),
        "RandomForest": RandomForestRegressor(n_estimators=400, max_depth=14,
                        min_samples_leaf=3, n_jobs=-1, random_state=42),
        "LightGBM": LGBMRegressor(n_estimators=600, learning_rate=0.05, num_leaves=31,
                    max_depth=6, subsample=0.8, colsample_bytree=0.8,
                    random_state=42, verbose=-1),
    }


TUNE_GRID = {
    "LightGBM": {"model__n_estimators": [400, 700, 1000], "model__num_leaves": [20, 31, 45],
                 "model__learning_rate": [0.03, 0.05], "model__max_depth": [5, 7]},
    "RandomForest": {"model__n_estimators": [400, 700], "model__max_depth": [12, 16, None],
                     "model__min_samples_leaf": [2, 3, 5]},
    "Ridge": {"model__alpha": [0.3, 1.0, 3.0, 10.0]},
}


def scores(y, p):
    return dict(MAE=mean_absolute_error(y, p), RMSE=np.sqrt(mean_squared_error(y, p)),
               R2=r2_score(y, p), MAPE=mean_absolute_percentage_error(y, p) * 100)


def listing_groups(df):
    """Keep listings from the same building/point in one validation fold."""
    if "listing_group" in df:
        return df["listing_group"].fillna("").astype(str)
    fallback = "row_" + df.index.astype(str)
    phone = df["phone"].fillna("").astype(str).str.strip()
    return phone.where(phone.str.len() >= 8, fallback)


def ensure_derived_columns(df):
    """Support both current and older processed CSV files."""
    if "has_coordinates" not in df:
        df["has_coordinates"] = (df["latitude"].notna() & df["longitude"].notna()).astype("int8")
    listing_date = pd.to_datetime(df.get("posted_date"), format="%d/%m/%Y", errors="coerce")
    if "listing_age_days" not in df:
        reference = listing_date.max()
        df["listing_age_days"] = (reference - listing_date).dt.days.clip(lower=0).fillna(365).astype("int16")
    if "recency_weight" not in df:
        df["recency_weight"] = np.exp(-np.log(2) * df["listing_age_days"] / 365.0).clip(0.05, 1.0)
    if "sample_weight" not in df:
        df["sample_weight"] = df["recency_weight"]
    return listing_date


def grouped_cv_scores(pipe, X, y, groups, weights, cv):
    """Evaluate weighted fits without leaking records from one building."""
    fold_scores, train_mae = [], []
    for train_idx, test_idx in cv.split(X, y, groups):
        fitted = clone(pipe)
        fitted.fit(X.iloc[train_idx], y.iloc[train_idx], model__sample_weight=weights.iloc[train_idx])
        pred = fitted.predict(X.iloc[test_idx])
        fold_scores.append(scores(y.iloc[test_idx], pred))
        train_mae.append(mean_absolute_error(y.iloc[train_idx], fitted.predict(X.iloc[train_idx])))
    metrics = {key: float(np.mean([row[key] for row in fold_scores])) for key in fold_scores[0]}
    return metrics, float(np.mean(train_mae)), float(np.std([row["R2"] for row in fold_scores]))


def temporal_holdout(pipe, X, y, dates, weights):
    """Test future listings using only older listings for training."""
    valid = dates.notna()
    cutoff = dates.loc[valid].quantile(0.80)
    train_idx = dates.index[valid & (dates <= cutoff)]
    test_idx = dates.index[valid & (dates > cutoff)]
    fitted = clone(pipe)
    fitted.fit(X.loc[train_idx], y.loc[train_idx], model__sample_weight=weights.loc[train_idx])
    return scores(y.loc[test_idx], fitted.predict(X.loc[test_idx])), cutoff


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="data/processed/hanoi_all_clean.csv")
    ap.add_argument("--name", default="hanoi_all")
    args = ap.parse_args()
    root = Path(__file__).resolve().parents[1]   # thư mục ai_rental (data/, models/ nằm ở đây)
    df = pd.read_csv(root / args.data)
    listing_dates = ensure_derived_columns(df)
    X, y = df[NUM + CAT + BIN], df[TARGET]
    groups = listing_groups(df)
    weights = pd.to_numeric(df["sample_weight"], errors="coerce").fillna(1.0)
    print(f"Dataset '{args.name}': {len(df)} bản ghi | {df['district'].nunique()} quận | "
          f"giá TB {y.mean():.2f} triệu\n")

    cv = GroupKFold(n_splits=5)
    rows = []
    for name, mdl in base_models().items():
        pipe = Pipeline([("prep", prep()), ("model", mdl)])
        metric, mae_train, r2_std = grouped_cv_scores(pipe, X, y, groups, weights, cv)
        rows.append({"Model": name, "MAE": metric["MAE"], "RMSE": metric["RMSE"],
                     "R2": metric["R2"], "MAPE%": metric["MAPE"],
                     "MAE_train": mae_train, "R2_std": r2_std})
    res = pd.DataFrame(rows).sort_values("MAE").reset_index(drop=True)
    print("=" * 78)
    print(f"SO SÁNH 3 MÔ HÌNH (5-fold CV, target=price_million triệu) — {args.name}")
    print("=" * 78)
    print(res.to_string(index=False, float_format=lambda v: f"{v:.3f}"))

    best_mae = res["MAE"].min()
    winner = res[res["MAE"] <= best_mae + 0.01].sort_values("R2_std").iloc[0]["Model"]
    print(f"\n>>> Thắng vòng CV: {winner} — tune siêu tham số...")

    gs = GridSearchCV(Pipeline([("prep", prep()), ("model", base_models()[winner])]),
                      TUNE_GRID[winner], scoring="neg_mean_absolute_error", cv=cv, n_jobs=1)
    gs.fit(X, y, groups=groups, model__sample_weight=weights)
    print(f"    Best params: { {k.replace('model__',''): v for k,v in gs.best_params_.items()} }")
    print(f"    CV MAE sau tune: {-gs.best_score_:.3f} triệu")

    # holdout + per-district
    splitter = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
    train_idx, test_idx = next(splitter.split(X, y, groups))
    Xtr, Xte = X.iloc[train_idx], X.iloc[test_idx]
    ytr, yte = y.iloc[train_idx], y.iloc[test_idx]
    best = clone(gs.best_estimator_)
    best.fit(Xtr, ytr, model__sample_weight=weights.iloc[train_idx])
    m = scores(yte, best.predict(Xte))
    print(f"\n    Holdout test (20%): MAE={m['MAE']:.3f} triệu | MAPE={m['MAPE']:.1f}% | R2={m['R2']:.3f}")
    te = Xte.copy(); te["err"] = np.abs(best.predict(Xte) - yte)
    mae_by_district = te.groupby("district")["err"].mean().to_dict()
    print("    MAE theo quận (holdout):")
    print(te.groupby("district")["err"].mean().round(3).sort_values().to_string())

    temporal_metrics, cutoff = temporal_holdout(best, X, y, listing_dates, weights)
    print(f"    Temporal test (after {cutoff.date()}): MAE={temporal_metrics['MAE']:.3f} triệu | "
          f"MAPE={temporal_metrics['MAPE']:.1f}% | R2={temporal_metrics['R2']:.3f}")

    best.fit(X, y, model__sample_weight=weights)  # fit lại trên toàn bộ để lưu
    out = root / "models" / args.name; out.mkdir(parents=True, exist_ok=True)
    joblib.dump(best, out / "model.joblib")
    json.dump({"name": args.name, "model_type": winner,
               "params": {k.replace("model__", ""): v for k, v in gs.best_params_.items()},
               "features": {"numeric": NUM, "categorical": CAT, "binary": BIN},
               "target": TARGET, "metrics_holdout": {k: round(float(v), 4) for k, v in m.items()},
               "metrics_temporal": {k: round(float(v), 4) for k, v in temporal_metrics.items()},
               "temporal_cutoff": str(cutoff.date()),
               "mae_by_district": {k: round(float(v), 4) for k, v in mae_by_district.items()},
               "validation": {"strategy": "GroupKFold / GroupShuffleSplit by building group",
                              "n_groups": int(groups.nunique())},
               "recency": {"half_life_days": 365, "minimum_weight": 0.05,
                           "reference_date": str(listing_dates.max().date())},
               "cv_table": res.round(4).to_dict("records"),
               "n_samples": int(len(df)), "trained_at": str(date.today())},
              (out / "metadata.json").open("w", encoding="utf-8"), ensure_ascii=False, indent=2)
    print(f"\n    Đã lưu -> {out/'model.joblib'}")


if __name__ == "__main__":
    main()
