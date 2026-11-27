# AI Room Recommendation

Backend API gợi ý phòng trọ sử dụng mô hình hai tầng:

1. **FAISS** truy xuất các phòng tương đồng dựa trên đặc trưng nội dung.
2. **LightGBM** xếp hạng lại các ứng viên dựa trên hồ sơ và lịch sử xem của người dùng.

API được xây dựng bằng FastAPI và có xử lý cold-start dựa trên thành phố, quận của người dùng khi chưa có đủ lịch sử xem.

## Kiến trúc

```text
Thông tin phòng ──> Trích xuất đặc trưng ──> FAISS index
                                                │
Người dùng + lịch sử xem ──> User profile ──────┤
                                                v
                                      Candidate retrieval
                                                │
                                                v
                                      LightGBM re-ranking
                                                │
                                                v
                                      Top-K recommendations
```

## Công nghệ

- Python 3.11
- FastAPI và Uvicorn
- FAISS
- LightGBM
- NumPy, pandas và scikit-learn
- Docker

## Cấu trúc dự án

```text
app/
  api/             API routes
  data/            Data provider đọc dữ liệu JSON
  features/        Trích xuất đặc trưng phòng
  models/          Pydantic models
  recommender/     Hồ sơ sở thích và tổng hợp tương tác
    faiss/         Xây chỉ mục và truy xuất ứng viên bằng FAISS
  reranker/        Dữ liệu, đặc trưng và luồng xếp hạng dùng chung
    lightgbm/      Huấn luyện và lưu/nạp mô hình LightGBM
    xgboost/       Huấn luyện mô hình xếp hạng XGBoost
    catboost/      Huấn luyện mô hình xếp hạng CatBoost
data/
  rooms.json       Dữ liệu phòng
  users.json       Dữ liệu người dùng
  view_history.json Lịch sử xem
scripts/           Tiền xử lý, huấn luyện, kiểm thử và đánh giá
storage/           Index/model được sinh tự động, không commit lên Git
```

## Chạy nhanh bằng Docker

Yêu cầu: đã cài Docker Desktop hoặc Docker Engine.

### Cách 1: Docker Compose

```bash
docker compose up --build
```

Nếu máy đang dùng bản Compose cũ:

```bash
docker-compose up --build
```

### Cách 2: Docker CLI

```bash
docker build -t ai-room-recommendation .
docker run --rm -p 8000:8000 --name ai-room-recommendation-api ai-room-recommendation
```

Trong lần build đầu tiên, Docker sẽ cài dependencies, dựng FAISS index và huấn luyện LightGBM từ dữ liệu JSON trong repo. Vì vậy quá trình build có thể mất vài phút. Các lần build sau sẽ sử dụng Docker cache nếu mã nguồn và dữ liệu không thay đổi.

Sau khi container khởi động:

- Demo UI: <http://localhost:8000/demo/>
- Swagger UI: <http://localhost:8000/docs>
- Health check: <http://localhost:8000/api/v1/health>
- Danh sách phòng: <http://localhost:8000/api/v1/rooms>

## Demo giao diện độc lập

Module có sẵn một giao diện tối giản tại <http://localhost:8000/demo/>. Giao diện
được FastAPI phục vụ trực tiếp nên không cần cài Node.js và không gọi backend Java
hoặc MySQL.

Ba hồ sơ dựng sẵn minh họa ba trường hợp:

- Người dùng mới: chưa có lịch sử, FAISS khởi tạo theo vị trí.
- Người dùng ít lịch sử: 1-4 lượt xem, xếp hạng bằng FAISS.
- Người dùng đủ lịch sử: từ 5 lượt xem, FAISS kết hợp LightGBM.

Khi bấm **Ghi nhận lượt xem**, sự kiện chỉ được thêm vào bộ nhớ của tiến trình
FastAPI. Nút **Đặt lại dữ liệu** khôi phục trạng thái ban đầu. Các file trong
`data/` luôn được xem là dữ liệu nền chỉ đọc và không bị giao diện demo sửa đổi.

Các API dành riêng cho giao diện:

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| `GET` | `/api/v1/demo/users` | Ba hồ sơ người dùng mẫu |
| `GET` | `/api/v1/demo/users/{user_id}/history` | Lịch sử xem đã làm giàu thông tin phòng |
| `GET` | `/api/v1/demo/recommendations/{user_id}` | Kết quả, điểm và lý do gợi ý |
| `POST` | `/api/v1/demo/view-events` | Ghi lượt xem vào bộ nhớ |
| `POST` | `/api/v1/demo/reset` | Đặt lại phiên demo |

Dừng dịch vụ Compose:

```bash
docker compose down
```

## Kiểm thử API

Lấy một người dùng mẫu có sẵn:

```http
GET http://localhost:8000/api/v1/users/c4ec6157-37af-4724-a8b6-57edb76cb1b0
```

Lấy 10 phòng gợi ý:

```http
GET http://localhost:8000/api/v1/recommendations/c4ec6157-37af-4724-a8b6-57edb76cb1b0?k=10
```

Ghi nhận một lượt xem:

```http
POST http://localhost:8000/api/v1/view-events
Content-Type: application/json

{
  "user_id": "<user_id>",
  "room_id": "<room_id>"
}
```

Có thể xem danh sách `user_id` và `room_id` trong thư mục `data/`, hoặc thử trực tiếp bằng Swagger UI.

## Chạy trực tiếp không dùng Docker

Tạo môi trường Python ảo và cài dependencies:

```bash
python -m venv .venv
```

Windows PowerShell:

```powershell
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
```

Linux/macOS:

```bash
source .venv/bin/activate
pip install -r requirements.txt
```

Dựng index và huấn luyện model:

```bash
python -m app.recommender.faiss.indexer
python scripts/train_reranker.py
```

Khởi động API:

```bash
uvicorn app.main:app --reload
```

## Thực nghiệm các mô hình xếp hạng

Chạy các lệnh dưới đây từ thư mục `AI_Recommendation`.

```bash
python -m pip install -r requirements-experiments.txt
python scripts/train_xgboost.py
python scripts/train_catboost.py --split-from storage/experiments/xgboost/training_meta.json
python scripts/train_lightgbm_experiment.py --split-from storage/experiments/xgboost/training_meta.json
python scripts/compare_rankers.py
```

Script chia người dùng theo tỷ lệ 70/15/15. Cờ `--split-from` tái sử dụng nguyên danh sách người dùng của một lần chạy trước. Mỗi người dùng vẫn được chia lịch sử theo thời gian để tạo hồ sơ và nhãn. XGBoost dùng `rank:ndcg`, CatBoost dùng `YetiRank` ở chế độ NDCG@10 và LightGBM dùng `lambdarank`. Cả ba mô hình học từ nhãn 0–3 và chọn vòng huấn luyện theo NDCG@10 trên các mẫu xác thực.

Kết quả huấn luyện được lưu dưới `storage/experiments/`, mỗi mô hình một thư mục, kèm file `training_meta.json`. Điểm xác thực khi huấn luyện được tính trên mẫu có bổ sung phòng mục tiêu.

`compare_rankers.py` lấy tối đa 80 ứng viên FAISS cho mỗi người dùng và áp dụng bộ lọc phòng đã tương tác hoặc hết chỗ. Tất cả cấu hình dùng chung tập ứng viên. Người dùng có dưới 5 tín hiệu trong phần lịch sử dùng thứ tự FAISS cho mọi cấu hình. Script chọn theo NDCG@10 trên tập xác thực, sau đó chỉ chấm cấu hình được chọn và mốc FAISS trên tập kiểm thử. Thời gian trung bình gồm dựng hồ sơ, truy xuất và xử lý xếp hạng trong bộ nhớ. Kết quả nằm trong `storage/experiments/comparison/` dưới dạng CSV và JSON. Dùng `--output-dir` để lưu lần chạy tiếp theo vào thư mục mới.

## Các API chính

| Method | Endpoint | Chức năng |
| --- | --- | --- |
| `GET` | `/api/v1/health` | Kiểm tra trạng thái API và FAISS index |
| `GET` | `/api/v1/rooms` | Danh sách và lọc phòng |
| `GET` | `/api/v1/rooms/{room_id}` | Chi tiết phòng |
| `GET` | `/api/v1/users/{user_id}` | Thông tin người dùng |
| `POST` | `/api/v1/view-events` | Ghi nhận lịch sử xem |
| `GET` | `/api/v1/recommendations/{user_id}` | Nhận danh sách gợi ý Top-K |
| `POST` | `/api/v1/admin/rebuild-index` | Dựng lại FAISS index |
| `POST` | `/api/v1/admin/retrain` | Huấn luyện lại LightGBM |

## Dữ liệu và artifact

Dữ liệu hiện tại phục vụ mục đích học tập/demo. Các file nhị phân trong `storage/` không được commit vì phụ thuộc môi trường và có thể được sinh lại từ dữ liệu bằng các lệnh phía trên.

Khi thay đổi dữ liệu phòng hoặc `app/features/feature_config.json`, cần dựng lại FAISS index và huấn luyện lại model trước khi chạy API.
