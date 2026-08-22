# -*- coding: utf-8 -*-
"""
Lõi dự đoán dùng chung cho API (serve_api.py) và giao diện (app_demo.py).
Tự chọn backend:
  - Nếu có models/hanoi_all/model.keras  -> dùng MẠNG NƠ-RON KERAS (.keras)
  - Ngược lại                            -> dùng model cây (model.joblib, CatBoost...)
Đặt biến môi trường RENTAL_BACKEND=sklearn để ép dùng model cây kể cả khi có .keras.
"""
import json
import os
import sys
import warnings
from datetime import date
from functools import lru_cache
from pathlib import Path

import joblib
import pandas as pd

# LGBM cảnh báo khi predict bằng ma trận không có tên cột — vô hại, tắt cho gọn log
warnings.filterwarnings("ignore", message="X does not have valid feature names")

# normalizers.py nằm ở ../preprocessing — thêm vào path để import cross-folder
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "preprocessing"))
import normalizers as N

ROOT = Path(__file__).resolve().parents[1]   # thư mục ai_rental (models/, data/ nằm ở đây)
MODEL_DIR = ROOT / "models" / "hanoi_all"

AMENITIES = [
    ("dieu_hoa", "has_dieu_hoa", "Điều hòa"),
    ("khep_kin", "has_khep_kin", "Khép kín (WC riêng)"),
    ("nong_lanh", "has_nong_lanh", "Nóng lạnh"),
    ("may_giat", "has_may_giat", "Máy giặt"),
    ("thang_may", "has_thang_may", "Thang máy"),
    ("ban_cong", "has_ban_cong", "Ban công"),
    ("gac", "has_gac", "Gác xép"),
    ("full_do", "has_full_do", "Full nội thất"),
    ("wifi", "has_wifi", "Wifi"),
    ("de_xe", "has_de_xe", "Chỗ để xe"),
]
KEY2COL = {k: c for k, c, _ in AMENITIES}
ALL_BIN = [c for _, c, _ in AMENITIES]
ROOM_TYPES = ["phong_tro", "can_ho_mini", "nha_nguyen_can", "o_ghep", "can_ho"]


@lru_cache(maxsize=1)
def _backend():
    """Trả về (kind, model, bundle, meta, wards). Nạp 1 lần, cache lại.

    Ưu tiên mặc định: monotonic (hợp lý nhất — tiện ích/diện tích không bao giờ làm giảm giá)
    > keras > sklearn. Ép bằng biến RENTAL_BACKEND = monotonic | keras | sklearn.
    """
    wc = ROOT / "data" / "processed" / "ward_centroids.json"
    wards = json.load(wc.open(encoding="utf-8")) if wc.exists() else {}

    has_mono = (MODEL_DIR / "monotonic_bundle.joblib").exists()
    has_keras = ((MODEL_DIR / "model.keras").exists()
                 and (MODEL_DIR / "keras_bundle.joblib").exists())
    # model.joblib is the winner selected by train_compare.py. The monotonic
    # backend remains available when interpretability is preferred.
    choice = os.environ.get("RENTAL_BACKEND") or "sklearn"

    if choice == "monotonic" and has_mono:
        bundle = joblib.load(MODEL_DIR / "monotonic_bundle.joblib")   # {preprocessor, model(LGBM)}
        meta = json.load((MODEL_DIR / "monotonic_metadata.json").open(encoding="utf-8"))
        return "monotonic", bundle["model"], bundle, meta, wards

    if choice == "keras" and has_keras:
        import tensorflow as tf   # lazy import (chỉ nạp TF khi thực sự dùng Keras)
        model = tf.keras.models.load_model(MODEL_DIR / "model.keras", compile=False)
        bundle = joblib.load(MODEL_DIR / "keras_bundle.joblib")
        meta = json.load((MODEL_DIR / "keras_metadata.json").open(encoding="utf-8"))
        return "keras", model, bundle, meta, wards

    model = joblib.load(MODEL_DIR / "model.joblib")
    meta = json.load((MODEL_DIR / "metadata.json").open(encoding="utf-8"))
    return "sklearn", model, None, meta, wards


def load_model():
    """Tương thích ngược: trả (model, meta, wards)."""
    _, model, _, meta, wards = _backend()
    return model, meta, wards


def distance_to_center(district, ward, lat, lng, wards):
    if lat is not None and lng is not None:
        return N.haversine_km(lat, lng)
    c = wards.get(f"{district}||{ward}")
    if c:
        return N.haversine_km(c[0], c[1])
    dc = N.DISTRICT_CENTER.get(district)
    return N.haversine_km(dc[0], dc[1]) if dc else 6.0


def predict(district, ward="unknown", area_m2=20.0, room_type="phong_tro",
            amenities=(), floor=None, latitude=None, longitude=None):
    kind, model, bundle, meta, wards = _backend()
    bins = {c: 0 for c in ALL_BIN}
    for a in amenities:
        col = KEY2COL.get(str(a).strip().lower())
        if col:
            bins[col] = 1
    dist = distance_to_center(district, ward, latitude, longitude, wards)
    has_coordinates = latitude is not None and longitude is not None
    row = {"area_m2": area_m2, "number_of_amenities": sum(bins.values()),
           "distance_to_center_km": dist, "floor": floor,
           "posted_month": date.today().month, "latitude": latitude,
           "longitude": longitude, "listing_age_days": 0,
           "has_coordinates": int(has_coordinates),
           "district": district, "ward": ward, "room_type": room_type, **bins}
    df = pd.DataFrame([row])

    if kind == "keras":
        X = bundle["preprocessor"].transform(df)
        if hasattr(X, "toarray"):
            X = X.toarray()
        pred_s = model.predict(X, verbose=0)
        price = float(bundle["target_scaler"].inverse_transform(pred_s)[0][0])
    elif kind == "monotonic":
        X = bundle["preprocessor"].transform(df)      # LightGBM nhận thẳng ma trận (kể cả sparse)
        price = float(model.predict(X)[0])
    else:
        price = float(model.predict(df)[0])

    mae = meta.get("mae_by_district", {}).get(
        district, meta["metrics_holdout"].get("MAE", 0.5))
    return {
        "predicted_price_million": round(price, 2),
        "price_range": [round(max(price - mae, 0.5), 2), round(price + mae, 2)],
        "distance_to_center_km": round(dist, 2),
        "number_of_amenities": sum(bins.values()),
        "model_type": meta["model_type"],
        "mape_pct": meta["metrics_holdout"].get("MAPE"),
        "mae": round(mae, 2),
    }
