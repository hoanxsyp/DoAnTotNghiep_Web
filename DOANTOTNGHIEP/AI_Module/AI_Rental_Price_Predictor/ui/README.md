# ui - Giao diện module AI dự đoán giá thuê

Giao diện Streamlit cho module `ai_rental`.
Không import model trực tiếp - chỉ **gọi API** dự đoán giá thuê qua HTTP:

| Trang | Gọi API | Biến môi trường mặc định |
|---|---|---|
| Dự đoán giá thuê | `POST /predict`, `GET /meta` | `RENTAL_API_URL=http://localhost:8000` |

`rental-api` mặc định dùng `RENTAL_BACKEND=sklearn`, tức model `LightGBM` tốt nhất từ
`training/train_compare.py`.

## Chạy

```bash
# Cần rental-api đang chạy trước ở port 8000
# PowerShell local:
# $env:RENTAL_BACKEND="sklearn"
streamlit run home.py          # http://localhost:8501

# hoặc chạy thẳng trang dự đoán giá thuê:
streamlit run app_demo.py
```

Đơn giản nhất: dùng `docker compose up` ở thư mục gốc để chạy `rental-api` và `ai-portal`.

## File

| File | Vai trò |
|---|---|
| `home.py` | Trang chủ module dự đoán giá thuê |
| `ui_rental.py` | Trang dự đoán giá thuê, gọi `rental-api` |
| `app_demo.py` | Chỉ trang giá thuê, chạy standalone |
