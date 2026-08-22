# ai_rental — API dự đoán giá thuê (FastAPI). Phục vụ model MONOTONIC (LightGBM) — nhẹ, không TF.
FROM python:3.12-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1

# libgomp1: LightGBM / scikit-learn cần OpenMP runtime
RUN apt-get update && apt-get install -y --no-install-recommends libgomp1 \
    && rm -rf /var/lib/apt/lists/*

COPY requirements-docker.txt requirements.txt
RUN pip install --no-cache-dir -r requirements.txt

# code phục vụ (serving) + tiện ích dùng chung (preprocessing/normalizers.py)
COPY serving/ serving/
COPY preprocessing/normalizers.py preprocessing/normalizers.py

# artifacts: model monotonic (mặc định) + model cây (fallback sklearn) + dữ liệu cho /meta
COPY models/hanoi_all/monotonic_bundle.joblib   models/hanoi_all/monotonic_bundle.joblib
COPY models/hanoi_all/monotonic_metadata.json   models/hanoi_all/monotonic_metadata.json
COPY models/hanoi_all/model.joblib              models/hanoi_all/model.joblib
COPY models/hanoi_all/metadata.json             models/hanoi_all/metadata.json
COPY data/processed/hanoi_all_clean.csv         data/processed/hanoi_all_clean.csv
COPY data/processed/ward_centroids.json         data/processed/ward_centroids.json

EXPOSE 8000

# serve_api nằm trong serving/ -> dùng --app-dir để import như module top-level
CMD ["uvicorn", "serve_api:app", "--app-dir", "serving", "--host", "0.0.0.0", "--port", "8000"]
