# Train lại định kỳ module AI dự đoán giá thuê

## Mục tiêu

Module AI được cập nhật định kỳ sau mỗi 7 ngày để mô hình không bị lỗi thời so với mặt bằng giá thuê mới. Mỗi chu kỳ sẽ:

1. Crawl thêm dữ liệu mới từ các nguồn đã dùng: `phongtro123.com`, `mogi.vn`.
2. Enrich chi tiết tin đăng: mô tả đầy đủ, ngày đăng, tầng.
3. Gộp và làm sạch dữ liệu thành `data/processed/hanoi_all_clean.csv`.
4. Train lại mô hình so sánh và mô hình phục vụ mặc định `LightGBM monotonic`.
5. Ghi log và metadata để kiểm chứng lần train.

## Các file đã bổ sung

- `ai_rental/automation/retrain_weekly.py`: chạy một vòng crawl -> preprocess -> train.
- `ai_rental/automation/install_weekly_retrain_task.ps1`: cài Windows Task Scheduler chạy mỗi tuần.
- `ai_rental/logs/retrain_*.log`: log chi tiết từng lần chạy.
- `ai_rental/models/hanoi_all/retrain_runs.jsonl`: lịch sử các lần retrain.

## Chạy thủ công một lần

Từ thư mục `ai_rental`:

```powershell
python automation/retrain_weekly.py --max-pages 20 --mogi-max-details 450 --workers 6
```

Nếu chỉ muốn train lại trên dữ liệu raw hiện có, không crawl web:

```powershell
python automation/retrain_weekly.py --skip-crawl --skip-enrich
```

## Cài lịch chạy mỗi 7 ngày trên Windows

Từ thư mục `ai_rental`:

```powershell
powershell -ExecutionPolicy Bypass -File automation/install_weekly_retrain_task.ps1
```

Nếu dùng virtual environment, truyền Python cụ thể:

```powershell
powershell -ExecutionPolicy Bypass -File automation/install_weekly_retrain_task.ps1 -PythonExe ".\.venv\Scripts\python.exe"
```

Mặc định task chạy lúc 02:00 Chủ nhật hàng tuần. Có thể đổi:

```powershell
powershell -ExecutionPolicy Bypass -File automation/install_weekly_retrain_task.ps1 -DayOfWeek Monday -AtHour 1
```

## Cách đưa dữ liệu từ website hiện tại vào pipeline

Pipeline tiền xử lý đang đọc tất cả file `*.jsonl` trong `ai_rental/data/raw/`. Vì vậy website hiện tại có thể export tin phòng đã duyệt sang một file ví dụ:

```text
ai_rental/data/raw/webtro_internal.jsonl
```

Mỗi dòng là một JSON object có các trường tương thích:

```json
{
  "title": "Phòng trọ Cầu Giấy full đồ",
  "price_raw": "3500000",
  "area_raw": "25",
  "address": "Phường Dịch Vọng, Quận Cầu Giấy, Hà Nội",
  "district": "Cầu Giấy",
  "description": "Có điều hòa, khép kín, chỗ để xe",
  "posted_date": "28/09/2026",
  "source_url": "internal://listing/123",
  "source_name": "webtro_internal",
  "crawled_at": "2026-09-28T00:00:00+00:00"
}
```

Sau đó `retrain_weekly.py` sẽ tự động gộp dữ liệu website nội bộ cùng dữ liệu crawl bên ngoài khi chạy bước preprocess.

## Liên hệ với yêu cầu cải tiến của cô

- Fine-tune trên tập dữ liệu tự xây: mỗi chu kỳ bổ sung dữ liệu mới và train lại model trên snapshot mới.
- Feature frecency/recency weighting: pipeline có `listing_age_days`, `recency_weight`, `frecency_weight`, `sample_weight`; model train dùng `sample_weight` để ưu tiên tin mới. Ngoài ra preprocess sinh `market_*` từ giá các tin mới crawl theo `district/ward/room_type` và lưu `data/processed/market_stats.json` cho API dùng lúc dự đoán.
- Dữ liệu dummy phù hợp khu vực: có thể thêm vào `data/raw/` hoặc file processed riêng, nhưng nên giữ giá trong khoảng IQR theo quận/phường để không làm lệch mô hình.
- Mật độ dân số: đã bổ sung `data/reference/hanoi_population_density_2024.csv`, join thành feature `population_density_km2` trong preprocess và thêm vào `NUM` trong training.
