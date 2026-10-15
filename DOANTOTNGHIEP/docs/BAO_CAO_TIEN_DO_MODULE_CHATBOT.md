# Báo cáo tiến độ module AI Chatbot tìm phòng trọ

Ngày ghi nhận: 15/10/2026  
Base model: `uitnlp/visobert`  
Trạng thái model: chưa train chính thức (`ml/out-intent`, `ml/out-ner` chưa có)

## Lệnh đã chạy

```powershell
cd "AI_Module\Module-AI-CHATBOT\chatbot\ml"

python data\generate_dataset.py
python data\build_proxy_testset.py
python data\split_real_data.py

python train_intent.py --max-train 40 --epochs 1 --output-dir out-intent-smoke
python train_ner.py --max-train 40 --epochs 1 --output-dir out-ner-smoke

python eval_nlu_compare.py --side llm
python eval_nlu_compare.py --side fc
python eval_nlu_compare.py --side llm --dataset gold
python eval_nlu_compare.py --side fc --dataset gold
python eval_nlu_compare.py --report
```

Smoke test chạy hết, không lỗi. Đây chỉ là bước xác nhận code chạy đúng, chưa phải model dùng được.

## Dữ liệu train

Intent:

- Synthetic: `2,500` câu (`data/intent_train.jsonl`)
- Dữ liệu thật dùng train: `115` câu (`data/intent_train_real.jsonl`)
- Bộ test thật (GOLD): `28` câu (`data/intent_test_gold.jsonl`)
- Bộ test proxy tự viết tay: `142` câu (`data/intent_test_real.jsonl`)

`143` câu thật lấy từ:

- `ml/data.md`: `56` bài đăng tìm phòng
- `ml/data2.md`: `87` câu hội thoại

Tách stratified 80/20, seed `42`, bằng `data/split_real_data.py`.

NER:

- Synthetic: `2,075` câu / `3,635` entity (`data/ner_train.jsonl`)
- Bộ test proxy tự viết tay: `87` câu / `133` entity (`data/ner_test_real.jsonl`)
- Dữ liệu thật đã gán nhãn: `56` câu / `209` entity (`data/ner_data_md_labeled.jsonl`), **chưa đưa vào train**

## Nhãn đang dùng

Intent, `8` nhãn:

- `search_room`, `refine_search`, `room_detail`, `compare_rooms`
- `book_appointment`, `calculate_cost`, `policy_inquiry`, `out_of_scope`

Entity, `10` nhãn, gán theo BIO:

- `LOCATION`, `POI`, `RADIUS`
- `PRICE_MIN`, `PRICE_MAX`, `AREA_MIN`
- `UTILITY`, `ROOM_TYPE`, `ROOM_REF`, `DATETIME`

Bộ test GOLD hiện chỉ có `6/8` intent (chưa có câu nào cho `calculate_cost`, `policy_inquiry`).

## Cấu hình huấn luyện

| Tham số | Intent | NER |
|---|---:|---:|
| Script | `train_intent.py` | `train_ner.py` |
| Bài toán | Sequence classification | Token classification (BIO) |
| Epochs | 5 | 8 |
| Batch size | 16 | 16 |
| Learning rate | 2e-5 | 3e-5 |
| `max_length` | 64 | 64 |
| Chọn checkpoint theo | F1 | F1 |

Base model ViSoBERT: khoảng `97M` tham số, `12` layer, hidden `768`, vocab `15,004`.

Pipeline encode NER đã kiểm tra: encode rồi decode lại toàn bộ nhãn gold khớp `100%`
(train `tp=3635 fp=0 fn=0`, proxy `tp=133 fp=0 fn=0`), không câu nào bị cắt ở `max_length=64`.

## So sánh phương án NLU trong `eval_nlu_compare.py`

Hai phương án LLM (Gemini `gemini-3.1-flash-lite`) đã đo để làm mốc so sánh với ViSoBERT.

Intent trên bộ test thật GOLD (`28` câu):

| Phương án | Accuracy | Macro-F1 |
|---|---:|---:|
| LLM prompt JSON | 0.893 | 0.757 |
| LLM Function Calling | 0.821 | 0.674 |
| ViSoBERT fine-tuned | chưa đo | chưa đo |

Slot, latency, chi phí trên bộ proxy:

| Phương án | Intent Acc | Slot-F1 | Latency TB | p95 | Chi phí / 1000 tin |
|---|---:|---:|---:|---:|---:|
| LLM prompt JSON | 0.923 | 0.921 | 850 ms | 1004 ms | ~2,342 đ |
| LLM Function Calling | 0.908 | 0.950 | 669 ms | 803 ms | ~1,163 đ |
| ViSoBERT fine-tuned | chưa đo | chưa đo | chưa đo | chưa đo | ≈ 0 đ |

Kết quả chi tiết: `ml/eval-results/REPORT.md`.

## Kết quả tầng tìm phòng

Đo trực tiếp `RetrievalService` (không qua NLU) bằng `RetrievalRecallEvalTest`, bộ vàng
`src/test/resources/retrieval_eval.jsonl`:

| Metric | Giá trị |
|---|---:|
| Số truy vấn | 30 |
| Recall@5 | 1.000 |
| MRR | 1.000 |

Ngưỡng đặt ra: Recall@5 ≥ `0.80`. Kết quả chi tiết: `ml/eval-results/retrieval_recall.md`.

## Tích hợp hệ thống

Luồng một tin nhắn:

1. Chat widget gọi `POST /api/v1/chat` của `chatbot-api` (Spring Boot).
2. `chatbot-api` gọi `nlu-service` tại `POST /nlu` để lấy intent và entity.
3. Entity được chuẩn hoá (giá, khu vực, tiện ích), gộp với ngữ cảnh hội thoại lưu ở Redis.
4. Thiếu thông tin thì hỏi lại; đủ thì tìm phòng trong MySQL, lọc theo bán kính nếu có địa điểm.
5. Trả danh sách phòng và câu trả lời, ghi `chat_log`.

Hiện `chatbot-api` vẫn còn cơ chế dự phòng: `nlu-service` lỗi hoặc chưa có model thì chuyển
sang Gemini, rồi sang bộ luật. Vì model chưa train nên các lần chạy thử đến nay đều đi qua
nhánh dự phòng này. Sau khi train xong sẽ bỏ dự phòng, để ViSoBERT là NLU duy nhất; hai
phương án LLM chỉ giữ lại trong phần đánh giá.

Cấu hình trong `docker-compose.yml`:

```yaml
chatbot-api:  port 8081
chatbot-nlu:  port 8003   # mount ml/out-intent, ml/out-ner
NLU_URL=http://chatbot-nlu:8000
NLU_ENABLED=true
```

Website chính (`backend_webtro`) chưa gọi sang `chatbot-api`; chatbot trên website hiện dùng
bộ luật riêng `RuleBasedChatbotEngine`.

## Việc tiếp theo

1. Đưa `209` entity NER thật vào `train_ner.py`, nâng `max_length` lên `128` (10–20% câu thật vượt 64 token).
2. Train chính thức:

   ```powershell
   python train_intent.py --epochs 5 --output-dir out-intent
   python train_ner.py --epochs 8 --output-dir out-ner
   ```

3. Đo ViSoBERT và điền vào bảng so sánh:

   ```powershell
   python eval_nlu_compare.py --side visobert
   python eval_local_intent_gold.py
   python eval_nlu_compare.py --report
   ```

4. Bỏ cơ chế dự phòng LLM/luật trong `chatbot-api`.
5. Thu thập thêm câu thật, ưu tiên `calculate_cost`, `policy_inquiry` và câu có entity.

## Kiểm thử nhanh qua API

Chạy từ thư mục `DOANTOTNGHIEP`:

```powershell
docker compose up --build chatbot-api chatbot-nlu chatbot-mysql chatbot-redis
```

Kiểm tra NLU service:

```text
http://localhost:8003/health
```

Chưa có model thì `/health` trả `degraded`.

Gửi tin nhắn:

```powershell
curl.exe -s -X POST http://localhost:8081/api/v1/chat `
  -H "Content-Type: application/json" `
  -d '{\"session_id\":\"demo-1\",\"message\":\"Tìm phòng dưới 3 triệu ở Thanh Xuân\"}'
```

## Cách test trên giao diện UI

Backend chạy sẵn theo mục trên, sau đó:

```powershell
cd "AI_Module\Module-AI-CHATBOT\chatbot\frontend"

npm install
copy .env.example .env
npm run dev
```

Trong `.env`, đặt `VITE_API_BASE_URL=http://localhost:8081` nếu backend chạy bằng Docker.

Mở UI:

```text
http://localhost:5173
```

Gợi ý test, cùng một cuộc trò chuyện:

1. `Tìm phòng dưới 3 triệu ở Thanh Xuân`
2. `Có điều hòa nữa`
3. `Rẻ hơn nữa đi`
4. `Có phòng nào gần PTIT dưới 3 triệu không?`
5. `So sánh 2 phòng đó`

Bấm `Chi tiết kỹ thuật` dưới mỗi câu trả lời để xem nhánh xử lý (`FAST`/`LLM`/`CLARIFY`),
độ tin cậy NLU và latency.
