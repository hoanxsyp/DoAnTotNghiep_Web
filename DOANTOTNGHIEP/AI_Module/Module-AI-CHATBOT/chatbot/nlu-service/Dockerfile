# NLU service (SPEC §11 bước 2.3) — model PhoBERT + word-segment VnCoreNLP.
# Build context là CHATBOT ROOT (không phải nlu-service/) vì cần COPY các file
# ml/*.py — xem service `chatbot-nlu` trong DOANTOTNGHIEP/docker-compose.yml:
#   build: { context: ./AI_Module/Module-AI-CHATBOT/chatbot,
#            dockerfile: nlu-service/Dockerfile }
# Model đã fine-tune (~515MB/cái) KHÔNG bake vào image — mount volume ./ml/out-*.
FROM python:3.12-slim

# VnCoreNLP là jar Java, gọi qua pyjnius -> cần JRE + JAVA_HOME.
RUN apt-get update \
    && apt-get install -y --no-install-recommends default-jre-headless \
    && rm -rf /var/lib/apt/lists/*
ENV JAVA_HOME=/usr/lib/jvm/default-java

WORKDIR /app/nlu-service

COPY nlu-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# app.py import các module này từ $NLU_ML_DIR (mặc định /app/ml).
COPY ml/nlu_encoding.py ml/phobert_encoding.py ml/vncorenlp_util.py /app/ml/

# Tải sẵn jar + model word-segment (~30MB) lúc build để container không cần
# mạng lúc khởi động. Đường dẫn KHÔNG được có dấu cách (lỗi của VnCoreNLP).
ENV VNCORENLP_DIR=/opt/vncorenlp
RUN cd /app/ml && python -c "from pathlib import Path; from vncorenlp_util import _download_wseg_only; _download_wseg_only(Path('/opt/vncorenlp'))"

COPY nlu-service/app.py .

EXPOSE 8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
