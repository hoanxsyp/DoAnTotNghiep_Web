# -*- coding: utf-8 -*-
"""
API dự đoán giá thuê phòng trọ Hà Nội (FastAPI). Model tổng có district là feature.
  • POST /predict : dự đoán giá thuê
  • GET  /meta    : danh sách quận/phường + tiện ích + thống kê (cho giao diện dựng UI)
  • GET  /health  : trạng thái

Chạy (từ thư mục ai_rental):  uvicorn serve_api:app --app-dir serving --host 0.0.0.0 --port 8000
Docs: http://127.0.0.1:8000/docs
"""
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field, model_validator

import predict_core as core

app = FastAPI(title="API Dự đoán giá thuê phòng trọ Hà Nội", version="4.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"],
                   allow_methods=["*"], allow_headers=["*"])

DATA = Path(__file__).resolve().parents[1] / "data" / "processed" / "hanoi_all_clean.csv"


class PredictRequest(BaseModel):
    district: str = Field(..., example="Cầu Giấy")
    ward: str = Field("unknown", example="Dịch Vọng")
    area_m2: float = Field(..., ge=8, le=80, example=28)
    room_type: str = Field("phong_tro", example="can_ho_mini")
    amenities: List[str] = Field(default_factory=list, example=["dieu_hoa", "khep_kin", "thang_may", "wifi"])
    floor: Optional[int] = Field(None, ge=0, le=30, example=3)
    latitude: Optional[float] = Field(None, ge=20.45, le=21.55)
    longitude: Optional[float] = Field(None, ge=105.25, le=106.10)

    @model_validator(mode="after")
    def coordinates_are_a_pair(self):
        if (self.latitude is None) != (self.longitude is None):
            raise ValueError("latitude and longitude must be provided together")
        return self


class PredictResponse(BaseModel):
    predicted_price_million: float
    price_range: List[float]
    currency: str = "triệu VND/tháng"
    distance_to_center_km: float
    model_type: str
    mape_pct: Optional[float] = None


@lru_cache(maxsize=1)
def _meta_payload():
    """Metadata cho UI: quận/phường, tiện ích, thống kê giá theo quận (histogram)."""
    df = pd.read_csv(DATA)
    _, meta, _ = core.load_model()
    wards = {d: sorted([w for w in g["ward"].dropna().unique().tolist() if w != "unknown"])
             for d, g in df.groupby("district")}
    bins = np.arange(1, 10.5, 0.5)
    stats = {}
    for d, g in df.groupby("district"):
        h = pd.cut(g["price_million"], bins=bins).value_counts().sort_index()
        stats[d] = {"mean": round(float(g["price_million"].mean()), 2),
                    "hist_labels": [f"{iv.left:.1f}" for iv in h.index],
                    "hist_counts": [int(c) for c in h.values]}
    return {
        "model_type": meta["model_type"], "n_samples": meta["n_samples"],
        "metrics": meta["metrics_holdout"],
        "districts": sorted(df["district"].dropna().unique().tolist()),
        "wards": wards, "district_stats": stats,
        "amenities": [[k, label] for k, _, label in core.AMENITIES],
        "room_types": core.ROOM_TYPES,
    }


@lru_cache(maxsize=1)
def _valid_locations():
    df = pd.read_csv(DATA)
    return {district: set(group["ward"].dropna().astype(str))
            for district, group in df.groupby("district")}


@app.get("/health")
def health():
    _, meta, _ = core.load_model()
    return {"status": "ok", "model": meta["model_type"], "n_samples": meta["n_samples"]}


@app.get("/meta")
def meta():
    return _meta_payload()


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    locations = _valid_locations()
    if req.district not in locations:
        raise HTTPException(status_code=422, detail="district is not supported by this model")
    if req.ward != "unknown" and req.ward not in locations[req.district]:
        raise HTTPException(status_code=422, detail="ward does not belong to the selected district")
    if req.room_type not in core.ROOM_TYPES:
        raise HTTPException(status_code=422, detail="room_type is not supported by this model")
    invalid_amenities = sorted(set(req.amenities) - set(core.KEY2COL))
    if invalid_amenities:
        raise HTTPException(status_code=422, detail=f"unsupported amenities: {invalid_amenities}")
    r = core.predict(req.district, req.ward, req.area_m2, req.room_type,
                     req.amenities, req.floor, req.latitude, req.longitude)
    return PredictResponse(
        predicted_price_million=r["predicted_price_million"],
        price_range=r["price_range"],
        distance_to_center_km=r["distance_to_center_km"],
        model_type=r["model_type"],
        mape_pct=r["mape_pct"],
    )
