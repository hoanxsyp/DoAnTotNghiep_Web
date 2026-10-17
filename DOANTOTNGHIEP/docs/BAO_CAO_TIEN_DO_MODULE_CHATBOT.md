# Báo cáo tiến độ module AI Chatbot tìm phòng trọ

Ngày ghi nhận: 17/10/2026 (bản trước: 15/10/2026)  
Base model: `vinai/phobert-base-v2` (PhoBERT) — thay cho `uitnlp/visobert` dùng đến 15/10  
Trạng thái model: đã train trên GPU (RTX 3050 4GB), đã đo (`ml/out-intent`, `ml/out-ner`)

## Thay đổi so với bản 15/10

1. **Đổi base model ViSoBERT → PhoBERT**, quyết định dựa trên thí nghiệm so sánh (mục bên dưới).
2. **Gộp intent từ 8 xuống 6 nhãn**: `calculate_cost`, `policy_inquiry` gộp vào `room_detail`.
3. **Không chọn checkpoint trên bộ test nữa**: model lấy ở epoch cuối. Bản 15/10 chọn
   checkpoint theo chính GOLD/proxy nên số đo lạc quan hơn thực tế.
4. Dùng dữ liệu synthetic v2 (sinh theo văn phong người dùng thật) và nhân dữ liệu thật ×10
   khi train intent.

Kết quả: intent trên GOLD từ `0.750` lên `0.929`, NER Span-F1 từ `0.580` lên `0.743`.

## Vì sao chọn PhoBERT

So sánh hai model trên **cùng dữ liệu, cùng cấu hình, 3 seed**, lấy model ở epoch cuối
(`ml/compare_backbones.py`, kết quả `ml/eval-results/backbone_compare_full.md`):

| | ViSoBERT | PhoBERT |
|---|---:|---:|
| Intent GOLD accuracy | 0.905 ± 0.021 | **0.929 ± 0.000** |
| Intent GOLD macro-F1 | 0.884 | **0.917** |
| Intent proxy accuracy | 0.721 | **0.770** |
| NER Span-F1 (proxy) | 0.595 | **0.745** |
| Latency CPU / câu | 82 ms | **61 ms** |

PhoBERT tốt hơn ở mọi chỉ số, rõ nhất ở NER. Để kiểm tra chênh lệch NER có phải do cách gán
nhãn hay không, chạy thêm ViSoBERT gán nhãn theo từ y hệt PhoBERT: chỉ đạt `0.631`. Vậy khoảng
cách chủ yếu đến từ model.

Bảng trên đo lúc còn 8 intent. Về intent, chênh lệch tương đương khoảng 1 câu trên 28 câu
GOLD nên chỉ nên kết luận "PhoBERT không kém hơn"; lợi thế chắc chắn của PhoBERT là ở NER.

PhoBERT được pretrain trên văn bản đã tách từ, nên pipeline thêm bước tách từ bằng
**VnCoreNLP** (thư viện Java). Máy train và máy chạy `nlu-service` đều cần Java; Docker image
đã cài sẵn.

## Lệnh đã chạy

```powershell
cd "AI_Module\Module-AI-CHATBOT\chatbot\ml"

python data\generate_dataset_v2.py
python data\build_proxy_testset.py
python data\split_real_data.py

python compare_backbones.py          # so sánh ViSoBERT / PhoBERT, 3 seed

python train_intent.py               # mặc định = cấu hình bên dưới
python train_ner.py

cd ..\nlu-service
uvicorn app:app --host 127.0.0.1 --port 8010

cd ..\ml
python eval_nlu_compare.py --side phobert --phobert-url http://127.0.0.1:8010
python eval_nlu_compare.py --side phobert --dataset gold --phobert-url http://127.0.0.1:8010
python eval_nlu_compare.py --report
```

Thời gian train: intent khoảng 4 phút, NER khoảng 4 phút.

## Dữ liệu train

Intent:

- Synthetic v2: `2,500` câu (`data/intent_train_v2.jsonl`)
- Dữ liệu thật dùng train: `115` câu (`data/intent_train_real.jsonl`), nhân `×10`
  (tổng train: `3,650` câu)
- Bộ test thật (GOLD): `28` câu (`data/intent_test_gold.jsonl`)
- Bộ test proxy tự viết tay: `142` câu (`data/intent_test_real.jsonl`)

`143` câu thật lấy từ:

- `ml/data.md`: `56` bài đăng tìm phòng
- `ml/data2.md`: `87` câu hội thoại

Tách stratified 80/20, seed `42`, bằng `data/split_real_data.py`.

NER:

- Synthetic v2: `2,075` câu / `6,965` entity (`data/ner_train_v2.jsonl`)
- Dữ liệu thật đã gán nhãn: `56` câu / `209` entity (`data/ner_data_md_labeled.jsonl`)
  (tổng train NER: `2,131` câu)
- Bộ test proxy tự viết tay: `87` câu / `133` entity (`data/ner_test_real.jsonl`)

Synthetic v2 khác v1 ở văn phong: khoảng giá (`2tr5-3tr`), viết tắt ngân sách (`tc`, `tchinh`),
tên phố/phường, POI viết tắt (`ptit`, `hvnh`), `51` biến thể tiện ích, bỏ dấu lỗ chỗ theo từ,
emoji. Từ vựng chỉ rút từ `115` câu train thật, không lấy từ bộ test.

## Nhãn đang dùng

Intent, `6` nhãn:

- `search_room`, `refine_search`, `room_detail`
- `compare_rooms`, `book_appointment`, `out_of_scope`

`calculate_cost` (`0` câu thật) và `policy_inquiry` (`1` câu thật) đã gộp vào `room_detail`:
không đủ dữ liệu để học và đo riêng, và bản chất đều là hỏi thông tin một phòng. Người dùng
không mất tính năng: `ChatOrchestrator` tách lại câu `room_detail` theo từ khoá ("chi phí",
"điện nước" → ước tính chi phí; "cọc", "hợp đồng", "nuôi" → trả lời về quy định).

Bộ test GOLD giờ có đủ cả `6` intent.

Entity, `10` nhãn, gán theo BIO:

- `LOCATION`, `POI`, `RADIUS`
- `PRICE_MIN`, `PRICE_MAX`, `AREA_MIN`
- `UTILITY`, `ROOM_TYPE`, `ROOM_REF`, `DATETIME`

## Cấu hình huấn luyện

| Tham số | Intent | NER |
|---|---:|---:|
| Script | `train_intent.py` | `train_ner.py` |
| Bài toán | Sequence classification | Token classification (BIO) |
| Epochs | 5 | 8 |
| Batch size | 16 | 16 |
| Learning rate | 2e-5 | 3e-5 |
| `max_length` | 128 | 128 |
| Nhân dữ liệu thật | ×10 | ×1 |
| Checkpoint | epoch cuối | epoch cuối |

Base model PhoBERT: khoảng `135M` tham số, `12` layer, hidden `768`, vocab `64,001`.

Nhãn NER gán cho subword đầu của mỗi từ (theo cách tách từ của VnCoreNLP). Encode nhãn gold
rồi decode lại cho trần Span-F1 `0.977` trên proxy và `0.950` trên `56` câu thật: vài entity
có ranh giới nằm giữa một từ nên không biểu diễn được. Ở `max_length=128` chỉ `1/2,785` câu bị cắt.

## So sánh phương án NLU trong `eval_nlu_compare.py`

Intent trên bộ test thật GOLD (`28` câu):

| Phương án | Accuracy | Macro-F1 |
|---|---:|---:|
| LLM prompt JSON | 0.929 | 0.915 |
| **PhoBERT fine-tuned** | **0.929** | **0.916** |
| LLM Function Calling | 0.857 | 0.800 |

Slot, latency, chi phí trên bộ proxy:

| Phương án | Intent Acc | Slot-F1 | Span-F1 strict | Latency TB | p95 | Chi phí / 1000 tin |
|---|---:|---:|---:|---:|---:|---:|
| LLM prompt JSON | 0.965 | 0.921 | — | 850 ms | 1004 ms | ~2,342 đ |
| LLM Function Calling | 0.958 | 0.950 | — | 669 ms | 803 ms | ~1,163 đ |
| **PhoBERT fine-tuned** | 0.775 | **0.962** | 0.743 | **87 ms** | **107 ms** | **≈ 0 đ** |

Hai phương án LLM (Gemini `gemini-3.1-flash-lite`) được đo lúc còn 8 intent, không gọi lại.
Khi chấm, nhãn cũ được gộp ở cả đáp án lẫn dự đoán, tương đương chấm trên 6 intent. Vì vậy số
của LLM cao hơn bản 15/10 (prompt JSON trên GOLD `0.893` → `0.929`): phần lớn lỗi cũ của LLM
là nhầm giữa `room_detail`, `calculate_cost` và `policy_inquiry`.

Latency PhoBERT đo trên CPU local qua `nlu-service`, đã gồm bước tách từ. Span-F1 strict
(khớp đúng nhãn + vị trí) chỉ đo được với PhoBERT vì LLM không trả vị trí.

Kết quả chi tiết: `ml/eval-results/REPORT.md`.

### Nhận xét kết quả PhoBERT

- Intent trên GOLD ngang LLM prompt JSON (`26/28` câu) và hơn Function Calling, đạt ngưỡng
  DoD-1 (≥ `0.90`).
- Slot-F1 cao nhất trong 3 phương án.
- Nhanh hơn khoảng 8–10 lần và không tốn phí gọi API.
- Accuracy trên bộ proxy (`0.775`) thấp hơn LLM nhiều. Bộ proxy viết tay theo văn phong "sạch",
  còn model được train cho văn phong người dùng thật; GOLD (data thật) là bộ đánh giá chính.
- 2 câu sai trên GOLD:
  - "Còn ko em ở luôn ạ" — đáp án `room_detail`, model đoán `refine_search`. Thực chất đây là
    câu hỏi phòng còn trống không, chưa có intent riêng.
  - "em cần vào luôn đầu tuần sau nhé mọi người" — đáp án `refine_search`, model đoán `book_appointment`.
- NER chưa đạt ngưỡng DoD-2 (Span-F1 `0.743` < `0.85`). Lỗi hay gặp: khoảng giá `1tr5-2tr`
  ra một span `PRICE_MAX` thay vì tách min/max; bỏ sót tiện ích ở cuối câu.

Hạn chế của số liệu:

- GOLD chỉ `28` câu, sai thêm 1 câu là lệch `3.6` điểm accuracy.
- NER chưa có bộ test thật, mọi số NER đo trên bộ proxy tự viết tay.

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

`nlu-service` đã chạy local với model PhoBERT: `/health` trả `ok`, `max_length` intent và NER
đều `128`. Service làm một lượt "khởi động" ngay khi bật: nếu không, tin nhắn đầu tiên mất khoảng
`1.4 s` (Java và PyTorch khởi tạo lần đầu), vượt timeout `300 ms` của `chatbot-api` và bị chuyển
sang Gemini. Sau khi thêm bước này, tin nhắn đầu tiên còn `167 ms`.

`chatbot-api` vẫn giữ cơ chế dự phòng: `nlu-service` lỗi thì chuyển sang Gemini, rồi sang bộ luật.

Cấu hình trong `docker-compose.yml`:

```yaml
chatbot-api:  port 8081
chatbot-nlu:  port 8003   # mount ml/out-intent, ml/out-ner; image cài sẵn Java + VnCoreNLP
NLU_URL=http://chatbot-nlu:8000
NLU_ENABLED=true
```

Website chính (`backend_webtro`) chưa gọi sang `chatbot-api`; chatbot trên website hiện dùng
bộ luật riêng `RuleBasedChatbotEngine`.

## Việc tiếp theo

Đã xong (17/10/2026):

- ~~So sánh ViSoBERT và PhoBERT trên cùng dữ liệu, chuyển sang PhoBERT.~~
- ~~Không chọn checkpoint trên bộ test.~~
- ~~Gộp 2 intent không có dữ liệu thật, GOLD phủ đủ các intent.~~
- ~~Train lại với dữ liệu thật được nhân trọng số (`×10` cho intent).~~

Còn lại:

1. Gán nhãn NER cho `87` câu hội thoại trong `data2.md`: có bộ test NER thật và thêm dữ liệu
   train. Đây là việc chính để đạt DoD-2.
2. Thu thập thêm câu thật, ưu tiên `out_of_scope` và câu hỏi phòng còn trống; cân nhắc thêm
   intent riêng cho loại câu này.
3. Tách khoảng giá `1tr5-2tr` thành `PRICE_MIN` + `PRICE_MAX` ở bộ chuẩn hoá phía Java.
4. Mở rộng bộ chuẩn hoá tiện ích phía Java (hiện mới `4` nhóm, dữ liệu có `51` biến thể).
5. Chạy thử `docker compose build chatbot-nlu` với Dockerfile mới (cài Java).

## Kiểm thử nhanh qua API

Chạy từ thư mục `DOANTOTNGHIEP`:

```powershell
docker compose up --build chatbot-api chatbot-nlu chatbot-mysql chatbot-redis
```

Kiểm tra NLU service:

```text
http://localhost:8003/health
```

Chưa có model hoặc model không phải PhoBERT thì `/health` trả `degraded`. Có model thì trả `ok`.

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
6. `Tính chi phí phòng 1` (kiểm tra nhánh ước tính chi phí sau khi gộp intent)

Bấm `Chi tiết kỹ thuật` dưới mỗi câu trả lời để xem nhánh xử lý (`FAST`/`LLM`/`CLARIFY`),
độ tin cậy NLU và latency.
