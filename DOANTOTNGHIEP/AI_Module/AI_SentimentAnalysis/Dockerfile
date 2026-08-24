# Sử dụng Python 3.12 slim image
FROM python:3.12-slim

# Thiết lập thư mục làm việc
WORKDIR /app

# Copy file requirements và cài đặt dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy code và model files
COPY sentiment_api.py .
COPY sentiment_preprocessing.py .
COPY vietnamese-stopwords.txt .
COPY phan_tich_cam_xuc.keras .
COPY tokenizer.pkl .

# Expose port 5000
EXPOSE 5000

# Chạy ứng dụng
CMD ["python", "sentiment_api.py"]
