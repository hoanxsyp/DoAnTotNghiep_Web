# Báo cáo tiến độ module AI Chatbot tìm phòng trọ

Ngày ghi nhận: 15/10/2026  
Base model: `uitnlp/visobert`  
Trạng thái model: đã train chính thức lần 1 trên GPU (RTX 3050 4GB), đã đo (`ml/out-intent`, `ml/out-ner`)

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

Train chính thức và đo ViSoBERT (15/10/2026):

```powershell
python train_intent.py --epochs 5 --output-dir out-intent
python train_ner.py --epochs 8 --output-dir out-ner

# nlu-service chạy local ở cổng 8000 để đo phương án ViSoBERT
cd ..\nlu-service
uvicorn app:app --host 127.0.0.1 --port 8000

cd ..\ml
python eval_local_intent_gold.py
python eval_nlu_compare.py --side visobert
python eval_nlu_compare.py --report
```

Thời gian train: intent khoảng 3 phút, NER khoảng 15 phút. Bộ model cũ train ngày
30/09 (`max_length=64`, chưa từng đo) được giữ lại ở `out-intent.0930.bak`, `out-ner.0930.bak`.

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
- Dữ liệu thật đã gán nhãn: `56` câu / `209` entity (`data/ner_data_md_labeled.jsonl`), đã đưa vào train
  (tổng train NER: `2,131` câu = `2,075` synthetic + `56` thật)

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
| `max_length` | 64 | 128 |
| Chọn checkpoint theo | F1 trên GOLD | F1 trên proxy |
| Checkpoint được chọn | epoch 2 | epoch 7 |

Base model ViSoBERT: khoảng `97M` tham số, `12` layer, hidden `768`, vocab `15,004`.

Pipeline encode NER đã kiểm tra: encode rồi decode lại toàn bộ nhãn gold khớp `100%`
(train `tp=3635 fp=0 fn=0`, proxy `tp=133 fp=0 fn=0`), không câu nào bị cắt ở `max_length=64`.

NER nâng `max_length` từ `64` lên `128` vì câu thật dài hơn câu synthetic. Kiểm tra trên `56` câu thật:

| `max_length` | Câu bị cắt | Entity khớp | Mất | Lệch |
|---:|---:|---:|---:|---:|
| 64 | 11/56 | 185/209 | 24 | 5 |
| 128 | 1/56 | 200/209 | 9 | 1 |

9 entity còn lỗi ở `128`:

- 8 entity nằm cuối một bài đăng dài khoảng `307` token, bị cắt sau token thứ `128`.
- 1 entity do tokenizer gộp `"5-6tr"` thành một mảnh, nhãn gold chỉ là `"6tr"`.

Sửa lỗi trong `train_ner.py`: learning rate bị ghi cứng `3e-5`, cờ `--learning-rate` không có
tác dụng. Đã sửa để dùng giá trị truyền vào, mặc định vẫn `3e-5`.

## So sánh phương án NLU trong `eval_nlu_compare.py`

Hai phương án LLM (Gemini `gemini-3.1-flash-lite`) đã đo để làm mốc so sánh với ViSoBERT.

Intent trên bộ test thật GOLD (`28` câu):

| Phương án | Accuracy | Macro-F1 |
|---|---:|---:|
| LLM prompt JSON | 0.893 | 0.757 |
| LLM Function Calling | 0.821 | 0.674 |
| ViSoBERT fine-tuned | 0.750 | 0.605 |

Slot, latency, chi phí trên bộ proxy:

| Phương án | Intent Acc | Slot-F1 | Span-F1 strict | Latency TB | p95 | Chi phí / 1000 tin |
|---|---:|---:|---:|---:|---:|---:|
| LLM prompt JSON | 0.923 | 0.921 | — | 850 ms | 1004 ms | ~2,342 đ |
| LLM Function Calling | 0.908 | 0.950 | — | 669 ms | 803 ms | ~1,163 đ |
| ViSoBERT fine-tuned | 0.690 | 0.843 | 0.580 | 95 ms | 127 ms | ≈ 0 đ |

Latency ViSoBERT đo trên CPU local (nlu-service không dùng GPU). Span-F1 strict (khớp đúng
label + vị trí) chỉ đo được với ViSoBERT vì LLM không trả vị trí.

Kết quả chi tiết: `ml/eval-results/REPORT.md`.

### Nhận xét kết quả ViSoBERT

- Độ chính xác thấp hơn hai phương án LLM: intent trên GOLD thấp hơn LLM prompt JSON 4 câu (21/28 so với 25/28).
- Bù lại nhanh hơn khoảng 7–9 lần và không tốn phí gọi API.
- 7 câu sai trên GOLD, chủ yếu nhầm `refine_search` thành `search_room`
  (ví dụ: "em tăng được lên 3tr5, có đồ cơ bản càng tốt ạ", "em đổi sang tìm quanh Cầu Giấy nhé, bán kính tầm 3km").
- Nguyên nhân chính là dữ liệu thật quá ít. Intent chỉ có `115` câu thật để train, trong đó
  `out_of_scope` có `2` câu và `policy_inquiry` có `1` câu. Câu synthetic viết "chuẩn" hơn cách người dùng thật gõ.

Hạn chế của số liệu:

- GOLD chỉ `28` câu, sai thêm 1 câu là lệch `3.6` điểm accuracy.
- Checkpoint được chọn ngay trên bộ test (intent theo GOLD, NER theo proxy), nên số đo đang lạc quan hơn thực tế.
  Cần tách thêm một tập validation riêng.

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
sang Gemini, rồi sang bộ luật. Trước 15/10/2026 model chưa train nên các lần chạy thử đều đi qua
nhánh dự phòng này. Đã chạy `nlu-service` local với model mới: `/health` trả `ok`,
`max_length` intent `64`, NER `128`. Sau khi train xong sẽ bỏ dự phòng, để ViSoBERT là NLU duy nhất; hai
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

Đã xong (15/10/2026):

- ~~Đưa `209` entity NER thật vào train, nâng `max_length` NER lên `128`.~~
- ~~Train chính thức intent và NER.~~
- ~~Đo ViSoBERT và điền vào bảng so sánh.~~

Còn lại:

1. Thu thập thêm câu thật, ưu tiên:
   - câu `refine_search` (đang nhầm nhiều nhất)
   - `calculate_cost`, `policy_inquiry`, `out_of_scope`
   - câu có entity
2. Tách tập validation riêng để chọn checkpoint, không chọn trên bộ test.
3. Train lại với `--real-repeat 3` để tăng trọng số dữ liệu thật, so sánh với lần train này.
4. Cắt bài đăng dài `307` token trong `ner_data_md_labeled.jsonl` thành các câu ngắn.
5. Bỏ cơ chế dự phòng LLM/luật trong `chatbot-api`.

## Kiểm thử nhanh qua API

Chạy từ thư mục `DOANTOTNGHIEP`:

```powershell
docker compose up --build chatbot-api chatbot-nlu chatbot-mysql chatbot-redis
```

Kiểm tra NLU service:

```text
http://localhost:8003/health
```

Chưa có model thì `/health` trả `degraded`. Có model thì trả `ok`.

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
