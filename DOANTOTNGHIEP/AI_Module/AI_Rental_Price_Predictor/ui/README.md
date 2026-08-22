# ui — Giao diện chung 2 module

Portal Streamlit **dùng chung** cho cả `ai_rental` và `PhanTichCamXuc`.
Không import model trực tiếp — chỉ **gọi API** của 2 module qua HTTP:

| Trang | Gọi API | Biến môi trường (mặc định) |
|---|---|---|
| Dự đoán giá thuê | `POST /predict`, `GET /meta` | `RENTAL_API_URL` (`http://localhost:8000`) |
| Phân tích cảm xúc | `POST /predict` | `SENTIMENT_API_URL` (`http://localhost:5000`) |

## Chạy
```bash
# Cần 2 API đang chạy trước (rental-api:8000, sentiment-api:5000)
streamlit run home.py          # http://localhost:8501
# hoặc chỉ trang giá thuê:
streamlit run app_demo.py
```
Đơn giản nhất: dùng `docker compose up` ở thư mục gốc (tự chạy cả 2 API + UI).

## File
| File | Vai trò |
|---|---|
| `home.py` | Trang chủ chung (Streamlit multipage) |
| `ui_rental.py` | Trang dự đoán giá thuê (gọi rental-api) |
| `ui_sentiment.py` | Trang phân tích cảm xúc (gọi sentiment-api) |
| `app_demo.py` | Chỉ trang giá thuê (standalone) |
