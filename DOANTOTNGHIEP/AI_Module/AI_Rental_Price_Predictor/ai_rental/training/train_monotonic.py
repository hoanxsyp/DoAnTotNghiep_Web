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
from train_compare import NUM, CAT, BIN, TARGET, prep, scores, listing_groups

# Hướng đơn điệu: 1 = tăng, -1 = giảm, 0 = không ràng buộc
MONO_NUM = {"area_m2": 1, "number_of_amenities": 1, "distance_to_center_km": 0,
            "floor": 0, "posted_month": 0, "listing_age_days": 0,
            "latitude": 0, "longitude": 0}


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
    root = Path(__file__).resolve().parents[1]
    df = pd.read_csv(root / "data" / "processed" / "hanoi_all_clean.csv")
    X, y = df[NUM + CAT + BIN], df[TARGET]
    groups = listing_groups(df)

    # Hold out all listings from a landlord together to avoid duplicate leakage.
    splitter = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
    train_idx, test_idx = next(splitter.split(X, y, groups))
    Xtr, Xte = X.iloc[train_idx], X.iloc[test_idx]
    ytr, yte = y.iloc[train_idx], y.iloc[test_idx]
    pe = prep(); Xtr_m = pe.fit_transform(Xtr); Xte_m = pe.transform(Xte)
    m_eval = make_lgbm(constraints_for(pe)); m_eval.fit(Xtr_m, ytr)
    mt = scores(yte, m_eval.predict(Xte_m))
    te = Xte.copy(); te["err"] = np.abs(m_eval.predict(Xte_m) - yte)
    mae_by_district = te.groupby("district")["err"].mean().to_dict()
    print(f"Holdout (LightGBM monotonic): MAE={mt['MAE']:.3f} triệu | "
          f"MAPE={mt['MAPE']:.1f}% | R2={mt['R2']:.3f}")

    # final: prep fit trên toàn bộ -> lưu bundle
    pf = prep(); Xf = pf.fit_transform(X)
    final = make_lgbm(constraints_for(pf)); final.fit(Xf, y)
    out = root / "models" / "hanoi_all"; out.mkdir(parents=True, exist_ok=True)
    joblib.dump({"preprocessor": pf, "model": final}, out / "monotonic_bundle.joblib")
    json.dump({"model_type": "LightGBM (monotonic)", "framework": "lightgbm",
               "features": {"numeric": NUM, "categorical": CAT, "binary": BIN}, "target": TARGET,
               "monotone": "area + number_of_amenities + tất cả has_* -> tăng",
               "metrics_holdout": {k: round(float(v), 4) for k, v in mt.items()},
               "mae_by_district": {k: round(float(v), 4) for k, v in mae_by_district.items()},
               "validation": {"strategy": "GroupShuffleSplit by phone", "n_groups": int(groups.nunique())},
               "n_samples": int(len(df)), "trained_at": str(date.today())},
              (out / "monotonic_metadata.json").open("w", encoding="utf-8"),
              ensure_ascii=False, indent=2)
    print(f"Đã lưu -> {out / 'monotonic_bundle.joblib'}")


if __name__ == "__main__":
    main()
