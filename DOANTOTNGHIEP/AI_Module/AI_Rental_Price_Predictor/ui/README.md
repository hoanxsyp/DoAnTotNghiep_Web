# ui — Giao diện module dự đoán giá thuê

Portal Streamlit cho `ai_rental`.
Không import model trực tiếp — chỉ **gọi API** của module qua HTTP:

| Trang | Gọi API | Biến môi trường (mặc định) |
|---|---|---|
| Dự đoán giá thuê | `POST /predict`, `GET /meta` | `RENTAL_API_URL` (`http://localhost:8000`) |

## Chạy
```bash
# Cần rental-api đang chạy trước (cổng 8000)
streamlit run home.py          # http://localhost:8501
# hoặc chỉ trang giá thuê:
streamlit run app_demo.py
```
Đơn giản nhất: dùng `docker compose up` ở thư mục gốc (tự chạy API + UI).

## File
| File | Vai trò |
|---|---|
| `home.py` | Trang chủ (Streamlit multipage) |
| `ui_rental.py` | Trang dự đoán giá thuê (gọi rental-api) |
| `app_demo.py` | Chỉ trang giá thuê (standalone) |
