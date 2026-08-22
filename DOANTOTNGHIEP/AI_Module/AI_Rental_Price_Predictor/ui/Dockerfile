# ui — Giao diện chung (portal) cho 2 module. Nhẹ: chỉ streamlit + requests, gọi API qua HTTP.
FROM python:3.12-slim

WORKDIR /app
ENV PYTHONUNBUFFERED=1

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY *.py ./

EXPOSE 8501

CMD ["streamlit", "run", "home.py", "--server.port", "8501", \
     "--server.address", "0.0.0.0", "--server.headless", "true", \
     "--browser.gatherUsageStats", "false"]
