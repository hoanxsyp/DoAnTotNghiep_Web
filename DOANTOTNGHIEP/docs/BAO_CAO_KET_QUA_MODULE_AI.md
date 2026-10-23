# Báo cáo kết quả train module AI dự đoán giá thuê

Ngày ghi nhận: 13/10/2026  
Metadata model: `trained_at = 2026-10-12`

## Lệnh đã chạy

```powershell
cd "C:\Users\nguye\OneDrive\Máy tính\DoAnTotNghiep_Web\DOANTOTNGHIEP\AI_Module\AI_Rental_Price_Predictor\ai_rental"

python preprocessing\preprocess_all.py
python training\train_compare.py
```

## Dữ liệu train

- File dữ liệu sạch: `ai_rental/data/processed/hanoi_all_clean.csv`
- Số bản ghi hợp lệ: `5,175`
- Số nhóm listing/building dùng để chống leakage: `3,582`
- Số quận: `12`
- Target: `price_million`, đơn vị triệu VND/tháng
- Nguồn dữ liệu:
  - `phongtro123.com`: `4,791` tin
  - `mogi.vn`: `384` tin

Sau preprocess, pipeline cũng sinh:

- `ai_rental/data/processed/market_stats.json`: benchmark giá thị trường gần đây theo khu vực.
- `population_density_km2`: join từ `ai_rental/data/reference/hanoi_population_density_2024.csv`.

## Feature đang dùng

Tổng cộng model dùng `29` feature thô trước one-hot:

- Numeric: `15`
- Categorical: `3`
- Binary: `11`

Nhóm feature mới đã bổ sung:

- `population_density_km2`: mật độ dân số quận.
- `market_unit_price_million_m2`: giá thuê thị trường gần đây theo m².
- `market_price_million`: giá thuê thị trường gần đây.
- `market_sample_count`: số tin tương tự dùng để tính benchmark.
- `market_frecency_score`: điểm tổng hợp độ mới + số lượng tin.
- `market_freshness_days`: số ngày của tin mới nhất trong benchmark.
- `market_scope_level`: mức khớp khu vực của benchmark.
- `frecency_weight`: trọng số train, dùng làm `sample_weight`, không phải input khi predict.

## So sánh model trong `train_compare.py`

| Model | MAE | RMSE | R2 | MAPE | Train MAE | R2 std |
|---|---:|---:|---:|---:|---:|---:|
| LightGBM | 0.4751 | 0.6860 | 0.7142 | 16.2210% | 0.2890 | 0.0086 |
| RandomForest | 0.5740 | 0.7890 | 0.6217 | 20.6212% | 0.4034 | 0.0223 |
| Ridge | 0.6097 | 0.8111 | 0.5998 | 21.8173% | 0.5936 | 0.0216 |

Model thắng: `LightGBM`

Best params:

```json
{
  "learning_rate": 0.05,
  "max_depth": 7,
  "n_estimators": 1000,
  "num_leaves": 31
}
```

## Kết quả model phục vụ mặc định: LightGBM tốt nhất

Artifact:

- `ai_rental/models/hanoi_all/model.joblib`
- `ai_rental/models/hanoi_all/metadata.json`

Đây là model đang được `rental-api` ưu tiên dùng mặc định sau cập nhật tích hợp website:

- `RENTAL_BACKEND=sklearn`
- `predict_core.py` default backend: `sklearn`
- `backend_webtro` gọi sang FastAPI `rental-api /predict` thông qua `RENTAL_API_BASE_URL`

Holdout test 20%:

| Metric | Giá trị |
|---|---:|
| MAE | 0.4488 triệu |
| RMSE | 0.6639 triệu |
| R2 | 0.7487 |
| MAPE | 15.2267% |

Temporal test, train trên tin cũ và test trên tin sau `2025-06-17`:

| Metric | Giá trị |
|---|---:|
| MAE | 0.5992 triệu |
| RMSE | 0.8313 triệu |
| R2 | 0.6006 |
| MAPE | 15.9808% |

MAE theo quận trên holdout:

| Quận | MAE |
|---|---:|
| Hai Bà Trưng | 0.3432 |
| Bắc Từ Liêm | 0.3995 |
| Thanh Xuân | 0.4086 |
| Hà Đông | 0.4311 |
| Tây Hồ | 0.4353 |
| Long Biên | 0.4437 |
| Cầu Giấy | 0.4608 |
| Đống Đa | 0.4617 |
| Hoàng Mai | 0.4836 |
| Ba Đình | 0.5065 |
| Nam Từ Liêm | 0.6727 |
| Hoàn Kiếm | 0.7054 |

## Tích hợp website chính

Sau cập nhật, website trong `backend_webtro` / `frontend_webtro` dùng module Python như sau:

1. `frontend_webtro` gọi `POST /api/ai/price-prediction`.
2. `backend_webtro` map dữ liệu catalog của web sang input model:
   - `districtId`, `wardId` → tên quận/phường Hà Nội, bỏ tiền tố `Quận`, `Phường`.
   - `categoryId` → `room_type`.
   - `amenityIds`, `furnitureStatus`, `toiletType` → danh sách tiện ích model Python.
3. `backend_webtro` gọi FastAPI `rental-api` tại `POST /predict`.
4. `rental-api` dùng `LightGBM` tốt nhất (`model.joblib`) vì `RENTAL_BACKEND=sklearn`; nếu phường của website chưa có trong tập train thì fallback về `unknown` để model vẫn dự đoán theo cấp quận.
5. Backend lưu `prediction_histories`, tính cảnh báo lệch giá và trả `PricePredictionResponse`.
6. Nếu request có `listingId`, backend kiểm tra tin thuộc người gọi trước khi dự đoán rồi cập nhật
   `listings.price_prediction_id` và `listings.price_deviation_flag`.
7. `POST /api/listings` không gọi AI và `ListingCreateResponse` không có `pricePrediction`.
   `ListingWizard` gọi endpoint AI riêng; sau khi tạo tin thì gọi lại với id mới. Lỗi/tắt/timeout
   AI bị bắt riêng và không làm thất bại luồng lưu/gửi tin.

Cấu hình liên quan:

```yaml
RENTAL_API_BASE_URL=http://rental-api:8000
RENTAL_BACKEND=sklearn
```

Nếu chạy `backend_webtro` trực tiếp ngoài Docker và chạy `uvicorn` thủ công ở port `8000`, đặt:

```powershell
$env:RENTAL_API_BASE_URL="http://localhost:8000"
```

## Kiểm thử nhanh qua Python

```powershell
cd AI_Module/AI_Rental_Price_Predictor/ai_rental

python -c "import sys; sys.path.insert(0, 'serving'); import predict_core as c; print(c.predict('Cầu Giấy', 'Dịch Vọng', 28, 'can_ho_mini', ['dieu_hoa','khep_kin','thang_may','wifi'], 3))"
```

Kết quả test đã trả về đủ các trường mới:

- `population_density_km2`
- `market_unit_price_million_m2`
- `market_price_million`
- `market_sample_count`
- `market_frecency_score`
- `market_freshness_days`
- `market_scope_level`

## Cách test trên giao diện UI

Mở 2 terminal.

Terminal 1, chạy API:

```powershell
cd "C:\Users\nguye\OneDrive\Máy tính\DoAnTotNghiep_Web\DOANTOTNGHIEP\AI_Module\AI_Rental_Price_Predictor\ai_rental"

$env:RENTAL_BACKEND="sklearn"
uvicorn serve_api:app --app-dir serving --host 0.0.0.0 --port 8000
```

Mở kiểm tra API docs:

```text
http://127.0.0.1:8000/docs
```

Terminal 2, chạy UI:

```powershell
cd "C:\Users\nguye\OneDrive\Máy tính\DoAnTotNghiep_Web\DOANTOTNGHIEP\AI_Module\AI_Rental_Price_Predictor\ui"

streamlit run home.py
```

Mở UI:

```text
http://localhost:8501
```

Gợi ý test:

1. Chọn quận `Cầu Giấy`.
2. Chọn phường `Dịch Vọng` nếu có.
3. Diện tích `28`.
4. Loại phòng `can_ho_mini`.
5. Tick tiện ích: điều hòa, khép kín, thang máy, wifi.
6. Nhấn `Dự đoán giá`.

UI hiện tại đã được cập nhật để chỉ còn module dự đoán giá thuê và hiển thị thêm các tín hiệu mới: mật độ dân số, giá thị trường/m², giá thị trường, số mẫu thị trường, độ mới dữ liệu và frecency score.
