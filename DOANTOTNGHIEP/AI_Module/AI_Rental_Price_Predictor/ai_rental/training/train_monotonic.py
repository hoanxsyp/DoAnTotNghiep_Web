# -*- coding: utf-8 -*-
"""
Train LightGBM có RÀNG BUỘC ĐƠN ĐIỆU (monotonic constraints) — sửa hành vi phản trực giác.

Vấn đề: trong dữ liệu, vài tiện ích (khép kín, wifi, để xe) TƯƠNG QUAN ÂM với giá vì là dấu
hiệu phân khúc phòng trọ giá rẻ (phòng rẻ hay quảng cáo tiện ích cơ bản; CCMN/căn hộ đắt tiền
nhấn thang máy/full nội thất và bỏ qua). Model học đúng dữ liệu nhưng phi lý khi giữ nguyên
các yếu tố khác. Ràng buộc đơn điệu buộc: CÓ tiện ích / diện tích lớn hơn -> giá KHÔNG BAO GIỜ giảm.

Ra: models/hanoi_all/monotonic_bundle.joblib + monotonic_metadata.json
Chạy: python training/train_monotonic.py
"""
import argparse
import json, sys
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
import joblib
from sklearn.model_selection import GroupShuffleSplit
from lightgbm import LGBMRegressor

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).resolve().parent))       # train_compare.py cùng folder
from train_compare import (NUM, CAT, BIN, TARGET, prep, scores, listing_groups,
                           ensure_derived_columns)

# Hướng đơn điệu: 1 = tăng, -1 = giảm, 0 = không ràng buộc
MONO_NUM = {"area_m2": 1, "number_of_amenities": 1, "distance_to_center_km": 0,
            "floor": 0, "posted_month": 0, "listing_age_days": 0,
            "latitude": 0, "longitude": 0, "population_density_km2": 0,
            "market_unit_price_million_m2": 1, "market_price_million": 1,
            "market_sample_count": 0, "market_frecency_score": 0,
            "market_freshness_days": 0, "market_scope_level": 0}


def constraints_for(pre):
    """Vector ràng buộc khớp thứ tự cột sau ColumnTransformer [num | onehot(cat) | bin]."""
    ohe = pre.named_transformers_["cat"].named_steps["oh"]
    n_cat = len(ohe.get_feature_names_out(CAT))
    return [MONO_NUM[c] for c in NUM] + [0] * n_cat + [1] * len(BIN)  # tiện ích: đều +1


def make_lgbm(constraints):
    return LGBMRegressor(n_estimators=800, learning_rate=0.03, num_leaves=31, max_depth=7,
                         subsample=0.8, colsample_bytree=0.8,
                         monotone_constraints=constraints, random_state=42, verbose=-1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--data", default="data/processed/hanoi_all_clean.csv")
    ap.add_argument("--name", default="hanoi_all")
    args = ap.parse_args()

    root = Path(__file__).resolve().parents[1]
    df = pd.read_csv(root / args.data)
    listing_dates = ensure_derived_columns(df)
    X, y = df[NUM + CAT + BIN], df[TARGET]
    groups = listing_groups(df)
    weights = pd.to_numeric(df["sample_weight"], errors="coerce").fillna(1.0)

    # Hold out all listings from a landlord together to avoid duplicate leakage.
    splitter = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
    train_idx, test_idx = next(splitter.split(X, y, groups))
    Xtr, Xte = X.iloc[train_idx], X.iloc[test_idx]
    ytr, yte = y.iloc[train_idx], y.iloc[test_idx]
    pe = prep(); Xtr_m = pe.fit_transform(Xtr); Xte_m = pe.transform(Xte)
    m_eval = make_lgbm(constraints_for(pe))
    m_eval.fit(Xtr_m, ytr, sample_weight=weights.iloc[train_idx])
    mt = scores(yte, m_eval.predict(Xte_m))
    te = Xte.copy(); te["err"] = np.abs(m_eval.predict(Xte_m) - yte)
    mae_by_district = te.groupby("district")["err"].mean().to_dict()
    print(f"Holdout (LightGBM monotonic): MAE={mt['MAE']:.3f} triệu | "
          f"MAPE={mt['MAPE']:.1f}% | R2={mt['R2']:.3f}")

    # final: prep fit trên toàn bộ -> lưu bundle
    pf = prep(); Xf = pf.fit_transform(X)
    final = make_lgbm(constraints_for(pf))
    final.fit(Xf, y, sample_weight=weights)
    out = root / "models" / args.name; out.mkdir(parents=True, exist_ok=True)
    joblib.dump({"preprocessor": pf, "model": final}, out / "monotonic_bundle.joblib")
    json.dump({"model_type": "LightGBM (monotonic)", "framework": "lightgbm",
               "features": {"numeric": NUM, "categorical": CAT, "binary": BIN}, "target": TARGET,
               "monotone": "area + number_of_amenities + tất cả has_* -> tăng",
               "metrics_holdout": {k: round(float(v), 4) for k, v in mt.items()},
               "mae_by_district": {k: round(float(v), 4) for k, v in mae_by_district.items()},
               "validation": {"strategy": "GroupShuffleSplit by phone", "n_groups": int(groups.nunique())},
               "recency": {"half_life_days": 365, "minimum_weight": 0.05,
                           "frecency_half_life_days": 180,
                           "sample_weight_column": "sample_weight",
                           "reference_date": str(listing_dates.max().date())},
               "external_features": {
                   "population_density": "data/reference/hanoi_population_density_2024.csv",
                   "market_stats": "data/processed/market_stats.json",
                   "market_features": [
                       "market_unit_price_million_m2", "market_price_million",
                       "market_sample_count", "market_frecency_score",
                       "market_freshness_days", "market_scope_level"
                   ],
               },
               "n_samples": int(len(df)), "trained_at": str(date.today())},
              (out / "monotonic_metadata.json").open("w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"Đã lưu -> {out / 'monotonic_bundle.joblib'}")


if __name__ == "__main__":
    main()
