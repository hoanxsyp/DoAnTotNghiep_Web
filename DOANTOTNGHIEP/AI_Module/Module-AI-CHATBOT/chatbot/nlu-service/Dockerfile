# NLU service (SPEC §11 bước 2.3) — model ViSoBERT.
# Build context là BACKEND ROOT (không phải nlu-service/) vì cần COPY
# ml/nlu_encoding.py — xem docker-compose.yml:
#   build: { context: ., dockerfile: nlu-service/Dockerfile }
# Model đã fine-tune (~390MB/cái) KHÔNG bake vào image — mount volume ./ml/out-*.
FROM python:3.12-slim

WORKDIR /app/nlu-service

COPY nlu-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# app.py import nlu_encoding từ $NLU_ML_DIR (mặc định /app/ml).
COPY ml/nlu_encoding.py /app/ml/nlu_encoding.py

COPY nlu-service/app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
