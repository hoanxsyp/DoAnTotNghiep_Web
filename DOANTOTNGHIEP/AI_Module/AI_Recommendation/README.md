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
  recommender/     User profile và FAISS retrieval
  reranker/        LightGBM training và re-ranking
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

- Swagger UI: <http://localhost:8000/docs>
- Health check: <http://localhost:8000/api/v1/health>
- Danh sách phòng: <http://localhost:8000/api/v1/rooms>

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
python -m app.recommender.indexer
python scripts/train_reranker.py
```

Khởi động API:

```bash
uvicorn app.main:app --reload
```

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
