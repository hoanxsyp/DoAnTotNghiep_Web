# -*- coding: utf-8 -*-
"""
Huấn luyện mạng nơ-ron (Keras MLP) dự đoán giá thuê, LƯU RA FILE .keras.
Sau khi train xong, model.keras + keras_bundle.joblib được dùng để test/dự đoán
(qua predict_core / API / giao diện) mà KHÔNG cần train lại.

Chạy:  python train_keras.py
Ra:    models/hanoi_all/model.keras          (mạng nơ-ron)
       models/hanoi_all/keras_bundle.joblib  (bộ tiền xử lý + scaler target)
       models/hanoi_all/keras_metadata.json  (metrics, feature list)
"""
import json, os, sys
from datetime import date
from pathlib import Path

os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
os.environ.setdefault("TF_ENABLE_ONEDNN_OPTS", "0")

import numpy as np
import pandas as pd
import joblib
from sklearn.compose import ColumnTransformer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler, OneHotEncoder
from sklearn.impute import SimpleImputer
from sklearn.model_selection import train_test_split
from sklearn.metrics import (mean_absolute_error, mean_squared_error,
                             r2_score, mean_absolute_percentage_error)

import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

from train_compare import NUM, CAT, BIN, TARGET   # dùng chung định nghĩa feature

sys.stdout.reconfigure(encoding="utf-8")
keras.utils.set_random_seed(42)


def build_preprocessor():
    """Giống train_compare nhưng OUTPUT DENSE (mạng nơ-ron cần ma trận đặc)."""
    return ColumnTransformer([
        ("num", Pipeline([("imp", SimpleImputer(strategy="median")),
                          ("sc", StandardScaler())]), NUM),
        ("cat", Pipeline([("imp", SimpleImputer(strategy="constant", fill_value="unknown")),
                          ("oh", OneHotEncoder(handle_unknown="ignore",
                                               min_frequency=8, sparse_output=False))]), CAT),
        ("bin", "passthrough", BIN),
    ])


def build_mlp(input_dim: int) -> keras.Model:
    model = keras.Sequential([
        keras.Input(shape=(input_dim,)),
        layers.Dense(128, activation="relu"),
        layers.BatchNormalization(),
        layers.Dropout(0.3),
        layers.Dense(64, activation="relu"),
        layers.Dropout(0.2),
        layers.Dense(32, activation="relu"),
        layers.Dense(1),
    ], name="rental_price_mlp")
    model.compile(optimizer=keras.optimizers.Adam(1e-3), loss="mse", metrics=["mae"])
    return model


def main():
    root = Path(__file__).resolve().parents[1]   # thư mục ai_rental (data/, models/ nằm ở đây)
    df = pd.read_csv(root / "data" / "processed" / "hanoi_all_clean.csv")
    X, y = df[NUM + CAT + BIN], df[TARGET].values.reshape(-1, 1)
    print(f"Dataset: {len(df)} bản ghi | {df['district'].nunique()} quận")

    # 70/15/15
    Xtr, Xtmp, ytr, ytmp = train_test_split(X, y, test_size=0.30, random_state=42)
    Xval, Xte, yval, yte = train_test_split(Xtmp, ytmp, test_size=0.50, random_state=42)

    pre = build_preprocessor()
    Xtr_m = pre.fit_transform(Xtr)                 # fit CHỈ trên train
    Xval_m, Xte_m = pre.transform(Xval), pre.transform(Xte)

    ys = StandardScaler().fit(ytr)                  # scale target cho NN ổn định
    ytr_s, yval_s = ys.transform(ytr), ys.transform(yval)

    model = build_mlp(Xtr_m.shape[1])
    print(f"Input dim: {Xtr_m.shape[1]} | tham số: {model.count_params():,}")
    cbs = [
        keras.callbacks.EarlyStopping(patience=30, restore_best_weights=True, monitor="val_loss"),
        keras.callbacks.ReduceLROnPlateau(patience=12, factor=0.5, min_lr=1e-5),
    ]
    model.fit(Xtr_m, ytr_s, validation_data=(Xval_m, yval_s),
              epochs=400, batch_size=32, callbacks=cbs, verbose=0)

    # đánh giá trên test (đưa về đơn vị triệu VND)
    pred = ys.inverse_transform(model.predict(Xte_m, verbose=0)).ravel()
    yte_r = yte.ravel()
    metrics = {
        "MAE": float(mean_absolute_error(yte_r, pred)),
        "RMSE": float(np.sqrt(mean_squared_error(yte_r, pred))),
        "R2": float(r2_score(yte_r, pred)),
        "MAPE": float(mean_absolute_percentage_error(yte_r, pred) * 100),
    }

    # lưu artifacts
    out = root / "models" / "hanoi_all"; out.mkdir(parents=True, exist_ok=True)
    model.save(out / "model.keras")                                   # <-- FILE .keras
    joblib.dump({"preprocessor": pre, "target_scaler": ys}, out / "keras_bundle.joblib")
    json.dump({"model_type": "Keras (MLP)", "framework": f"tensorflow {tf.__version__}",
               "features": {"numeric": NUM, "categorical": CAT, "binary": BIN},
               "target": TARGET, "input_dim": int(Xtr_m.shape[1]),
               "metrics_holdout": {k: round(v, 4) for k, v in metrics.items()},
               "n_samples": int(len(df)), "trained_at": str(date.today())},
              (out / "keras_metadata.json").open("w", encoding="utf-8"),
              ensure_ascii=False, indent=2)

    print("\n" + "=" * 60)
    print("KERAS MLP — kết quả trên test (giá triệu/tháng)")
    print("=" * 60)
    print(f"  MAE  = {metrics['MAE']:.3f} triệu")
    print(f"  RMSE = {metrics['RMSE']:.3f}")
    print(f"  R2   = {metrics['R2']:.3f}")
    print(f"  MAPE = {metrics['MAPE']:.1f}%")

    # so với model cây tốt nhất (nếu có)
    mp = out / "metadata.json"
    if mp.exists():
        cb = json.load(mp.open(encoding="utf-8"))
        c = cb["metrics_holdout"]
        print(f"\n  (Tham chiếu {cb['model_type']}: MAE {c['MAE']:.3f} | "
              f"R2 {c['R2']:.3f} | MAPE {c['MAPE']:.1f}%)")
    print(f"\nĐÃ LƯU -> {out/'model.keras'}")


if __name__ == "__main__":
    main()
