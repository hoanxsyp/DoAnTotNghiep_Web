# ai_rental — Module dự đoán giá thuê phòng trọ Hà Nội

Pipeline: **crawl 2 nguồn → gộp/làm sạch → train (4 model + Keras) → API FastAPI**.
Model tổng dùng `district` làm feature → 1 model phục vụ mọi quận.

## Cấu trúc thư mục (theo vai trò)
```
ai_rental/
├── crawlers/        # thu thập dữ liệu
│   ├── crawl_phongtro123.py   # phongtro123.com theo quận (JSON-LD từ card)
│   ├── crawl_mogi.py          # mogi.vn (list→detail, có toạ độ GPS + mô tả đầy đủ)
│   └── enrich_detail.py       # vào trang chi tiết lấy mô tả đầy đủ + tầng + ngày đăng
├── preprocessing/   # làm sạch + chuẩn hoá
│   ├── normalizers.py         # hàm chuẩn hoá dùng chung (giá/diện tích/quận/tiện ích/haversine)
│   └── preprocess_all.py      # gộp mọi nguồn → dataset sạch + distance + ward_centroids
├── training/        # huấn luyện
│   ├── train_compare.py       # so sánh 4 model (Ridge/RF/LightGBM/CatBoost) + tune
│   ├── train_monotonic.py     # LightGBM có RÀNG BUỘC ĐƠN ĐIỆU (model PHỤC VỤ mặc định)
│   └── train_keras.py         # mạng nơ-ron Keras MLP → file .keras
├── serving/         # phục vụ
│   ├── predict_core.py        # lõi dự đoán (tự chọn backend keras/sklearn)
│   └── serve_api.py           # FastAPI: /predict, /meta, /health
├── data/            # raw/ (jsonl thô), processed/ (csv sạch, ward_centroids.json)
├── models/hanoi_all/          # model.keras, keras_bundle.joblib, model.joblib, metadata
├── requirements.txt           # đầy đủ (crawl+train+serve)
├── requirements-docker.txt    # chỉ serve
└── Dockerfile
```
> Giao diện (UI) nằm ở thư mục **`../ui`** (dùng chung cho cả 2 module — gọi API qua HTTP).

## Cài đặt
```bash
pip install -r requirements.txt
```

## Quy trình chạy (từ thư mục `ai_rental`)
```bash
# 1) Crawl phongtro123 (lặp từng quận) + mogi
python crawlers/crawl_phongtro123.py --district quan-thanh-xuan --max-pages 40
# slug khác: quan-cau-giay, quan-dong-da, quan-ha-dong, quan-hai-ba-trung, quan-hoang-mai,
#            quan-bac-tu-liem, quan-nam-tu-liem, quan-ba-dinh, quan-long-bien, quan-tay-ho, quan-hoan-kiem
python crawlers/crawl_mogi.py --max-details 450
python crawlers/enrich_detail.py            # làm giàu mô tả đầy đủ + tầng + ngày đăng

# 2) Gộp + tiền xử lý toàn Hà Nội
python preprocessing/preprocess_all.py      # -> data/processed/hanoi_all_clean.csv (+ ward_centroids.json)

# 3) Train 4 model (cây) + so sánh + tune
python training/train_compare.py            # -> models/hanoi_all/model.joblib + metadata.json

# 3b) Train LightGBM MONOTONIC — model PHỤC VỤ mặc định (hành vi hợp lý: tiện ích/diện tích
#     không bao giờ làm GIẢM giá). Xem "Lưu ý hành vi model" bên dưới.
python training/train_monotonic.py          # -> models/hanoi_all/monotonic_bundle.joblib

# 3c) (Tùy chọn) Train mạng nơ-ron Keras -> file .keras (deliverable học sâu)
python training/train_keras.py              # -> models/hanoi_all/model.keras
#     Chọn backend: RENTAL_BACKEND = monotonic (mặc định) | keras | sklearn

# 4) API dự đoán (serve_api ở serving/ -> dùng --app-dir)
uvicorn serve_api:app --app-dir serving --host 0.0.0.0 --port 8000
#     Docs: http://127.0.0.1:8000/docs
```

## API
| Endpoint | Mô tả |
|---|---|
| `POST /predict` | Dự đoán giá: `{district, ward, area_m2, room_type, amenities[], floor?}` → giá + khoảng |
| `GET /meta` | Danh sách quận/phường + tiện ích + thống kê giá (cho UI dựng dropdown/biểu đồ) |
| `GET /health` | Trạng thái + loại model |

## Feature của model
| Nhóm | Feature |
|---|---|
| Số | `area_m2`, `number_of_amenities`, `distance_to_center_km`, `floor` |
| Hạng mục | `district`, `ward`, `room_type` |
| Nhị phân (10) | `has_dieu_hoa, has_khep_kin, has_ban_cong, has_thang_may, has_full_do, has_gac, has_may_giat, has_nong_lanh, has_wifi, has_de_xe` |
| **Target** | `price_million` (triệu VND/tháng) |

## Lưu ý hành vi model (vì sao dùng MONOTONIC)
Trong dữ liệu tin đăng, vài tiện ích **tương quan ÂM** với giá: `khép kín` (−0.11), `wifi`,
`để xe` — vì chúng là **dấu hiệu phân khúc phòng trọ giá rẻ** (phòng rẻ quảng cáo tiện ích cơ
bản; CCMN/căn hộ đắt tiền nhấn `thang máy`/`full nội thất` và thường bỏ qua). Model học **đúng
dữ liệu** nhưng cho kết quả **phản trực giác** khi giữ nguyên các yếu tố khác (thêm khép kín →
giá giảm). Đây là **confounding**, không phải quan hệ nhân quả.

**Cách xử lý:** `train_monotonic.py` áp **ràng buộc đơn điệu** — buộc `area`, `number_of_amenities`
và mọi `has_*` chỉ được làm giá **tăng hoặc giữ nguyên**. Kết quả: 0/10 tiện ích còn làm giảm giá;
`thang máy +0.83`, `full đồ +0.61`, `AC +0.11`... vẫn dương; `khép kín/wifi/để xe` về `+0.00`
(không thưởng giá trị không có trong dữ liệu, nhưng **không còn phạt**). Đánh đổi: R² 0.61→0.58
(giảm ~3%) để lấy hành vi đúng thực tế — phù hợp cho công cụ gợi ý giá.

## Docker — chạy CẢ 2 module + giao diện bằng 1 lệnh
File `docker-compose.yml` ở **thư mục gốc** (`Module_DoAnTotNghiep/`):
```bash
cd ..
docker compose up --build
```
| Service | Vai trò | URL |
|---|---|---|
| `sentiment-api` | Phân tích cảm xúc (Flask + Keras) | http://localhost:5000 |
| `rental-api` | API giá thuê (FastAPI + Keras) — build từ `ai_rental/` | http://localhost:8000/docs |
| `ui` | Giao diện chung — build từ `ui/` (chỉ gọi API) | http://localhost:8501 |

> **Pháp lý:** chỉ dùng học thuật; tôn trọng robots.txt; delay 1–3s/request;
> không lưu/tái phát tán SĐT–thông tin cá nhân (Nghị định 13/2023/NĐ-CP).
