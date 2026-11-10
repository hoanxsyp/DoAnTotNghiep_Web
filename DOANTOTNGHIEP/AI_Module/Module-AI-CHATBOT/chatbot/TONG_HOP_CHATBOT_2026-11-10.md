# Tổng hợp chatbot — 2026-11-10

> Ghi lại hiện trạng chatbot sau buổi rà soát ngày 2026-11-10: đã làm được gì, đã train
> gì, flow xử lý theo code hiện tại, điểm yếu phát hiện được và việc còn dở.
> Số liệu chi tiết theo giai đoạn xem thêm `TRANG_THAI_DU_AN.md`.

---

## 1. Tóm tắt

- Chatbot **đã nối vào website và chạy đầu-cuối**: widget → `backend_webtro` → `chatbot-api`
  → `chatbot-nlu` (PhoBERT). Tìm trên **tin đăng thật** qua `/api/search/listings`.
- Code cả 3 giai đoạn (MVP, NLU PhoBERT + fast-path, semantic + recommendation) **đã xong**.
- Model **đã train và đạt DoD** (intent 0.929, NER 0.935, Recall@5 1.000).
- Việc còn thiếu chủ yếu là **dữ liệu thật để đánh giá**, không phải code.
- ⚠️ `ml/out-intent/` và `ml/out-ner/` **đang trống** (bị `.gitignore`) → `chatbot-nlu`
  trả `/ready` 503; container `chatbot-api` lúc rà soát không chạy.

---

## 2. Kiến trúc và việc đã làm

### 2.1 Tích hợp web

| Thành phần | Vai trò |
|---|---|
| `backend_webtro` — `ChatbotServiceImpl` | Kiểm tra đầu vào, rate limit 30/phút, chạy luật trước, gọi AI, lọc tin ẩn, lưu log |
| `backend_webtro` — `AiChatbotClient` | HTTP client gọi `chatbot-api` (`/api/v1/chat`, `/api/v1/chat/reset`) |
| `chatbot-api` — `ChatOrchestrator` | NLU → chuẩn hoá → sửa intent → ngữ cảnh → chọn nhánh → trả lời |
| `chatbot-nlu` — `nlu-service/app.py` | FastAPI: `/nlu` (PhoBERT intent + NER), `/embed` (vietnamese-sbert) |
| `docker-compose.yml` | Đủ `chatbot-mysql`, `chatbot-redis`, `chatbot-nlu`, `chatbot-api`; tắt AI bằng `CHATBOT_AI_ENABLED=false` |

- Widget đã gửi `sessionId` → khách chưa đăng nhập chat được.
- Vẫn ghi `chatbot_conversations` / `chatbot_messages` ở backend chính cho trang Admin.
- AI sập → báo lỗi, **không** quay về tìm phòng bằng luật.

### 2.2 Chức năng

- NLU fallback: PhoBERT (timeout 800ms) → Gemini → luật.
- Chuẩn hoá giá, thời gian, khu vực, tiện ích (28 key, nhận 91% span tiện ích thật).
- Ngữ cảnh nhiều lượt trên Redis (TTL 30 phút), MERGE / OVERRIDE / RESET; hỏi lại khi thiếu
  khu vực và giá; "rẻ hơn nữa" → hạ giá trần 20%.
- Tìm theo địa điểm (bán kính), địa điểm lạ → Nominatim rồi cache.
- Sinh câu trả lời: câu mẫu (fast-path) hoặc Gemini + `HallucinationValidator` chống bịa.
- Xếp hạng lại: semantic (`vietnamese-sbert`) hoặc cá nhân hoá (Content-Based + KNN).

---

## 3. Đã train những gì

Train lần cuối **2026-10-17**, backbone `vinai/phobert-base-v2` (đã thử ViSoBERT, so cùng dữ
liệu thì PhoBERT tốt hơn mọi chỉ số nên quay về PhoBERT).

**Hai model riêng, hai bài toán khác nhau:**

| | Model intent | Model NER |
|---|---|---|
| Bài toán | Phân loại câu (sequence classification) | Gán nhãn từng từ (token classification) |
| Đầu ra | 1 nhãn / câu, 6 intent | Nhãn BIO / từ, 21 nhãn (`O` + B/I × 10 loại) |
| Script | `ml/train_intent.py` | `ml/train_ner.py` |
| Thư mục model | `ml/out-intent/` | `ml/out-ner/` |
| Dữ liệu train | 3650 câu (2500 synthetic v2 + 115 câu thật × 10) | 2190 câu (2075 synthetic v2 + 115 câu thật) |
| Kết quả GOLD thật | Accuracy **0.929** (26/28), macro-F1 0.916 | Span-F1 strict **0.935** (P 0.929 / R 0.942) |

`vietnamese-sbert` (embedding cho semantic rerank) là model **dùng sẵn, không train**.

**6 intent:** `search_room`, `refine_search`, `room_detail`, `compare_rooms`,
`book_appointment`, `out_of_scope`. `calculate_cost` và `policy_inquiry` đã gộp vào
`room_detail` (quá ít câu thật); `ChatOrchestrator` tách lại bằng từ khóa → **8 nhánh xử lý**.

**10 loại entity:** `PRICE_MAX`, `PRICE_MIN`, `LOCATION`, `POI`, `RADIUS`, `AREA_MIN`,
`UTILITY`, `ROOM_TYPE`, `DATETIME`, `ROOM_REF`.

Backend web có enum riêng `ChatbotIntent` (8 giá trị cho luật và log): `FIND_ROOM`,
`HOW_TO_POST`, `GLOSSARY`, `FAQ`, `GREETING`, `OUT_OF_SCOPE`, `SENSITIVE`, `UNKNOWN`.
Khi AI trả lời, backend chỉ ghi `FIND_ROOM` hoặc `OUT_OF_SCOPE`.

**Bảng DoD:**

| DoD | Tiêu chí | Kết quả |
|---|---|---|
| DoD-1 | Intent accuracy ≥ 0.90 | ✅ 0.929 (GOLD 28 câu) |
| DoD-2 | Entity F1 ≥ 0.85 | ✅ 0.935 (69 entity) |
| DoD-3 | Recall@5 ≥ 0.80 | ✅ 1.000 (30 câu, **trên 59 phòng demo**) |
| DoD-4 | Hallucination = 0 sau guardrail | ✅ có cơ chế, chưa có số trên log thật |
| DoD-5 | p95 ≤ 2.5s (LLM) / ≤ 400ms (fast) | ⚠️ chưa đo trên log thật |
| DoD-6 | Giữ ngữ cảnh ≥ 3 lượt | ✅ đã test e2e |

So sánh 3 cách NLU (`ml/eval-results/REPORT.md`): PhoBERT ngang Gemini về intent, hơn về
slot, ~87ms so với ~850ms, chi phí ≈ 0.

---

## 4. Flow xử lý một câu (theo code hiện tại)

```
Widget ─POST /api/ai/chatbot/message─▶ backend_webtro (ChatbotServiceImpl)
   kiểm tra đầu vào → luật chạy trước → nhạy cảm/thuật ngữ thì luật trả lời luôn
   ─POST /api/v1/chat─▶ chatbot-api (ChatOrchestrator)
        ─POST /nlu─▶ nlu-service (PhoBERT intent + NER)
        chuẩn hoá → sửa intent → gộp ngữ cảnh → 1 nhánh theo intent → trả lời
   ◀─ backend: lọc tin ẩn, lấy tiêu đề/giá từ DB, lưu log → widget
```

**nlu-service:** VnCoreNLP tách từ (giữ offset) → tokenizer PhoBERT → model intent (softmax
trên cả câu, lấy argmax) + model NER (BIO từng từ) → gộp BIO thành span
`{label, text, start, end, score}`.

**ChatOrchestrator:**
1. Lấy ngữ cảnh Redis.
2. NLU (PhoBERT → Gemini → luật).
3. Chuẩn hoá entity (`normalizer/`).
4. Luật sửa intent: LOCATION là địa điểm → POI; `room_detail` không chỉ phòng mà có khu vực/giá
   → `search_room`; `room_detail` hỏi tiện ích khi chưa có kết quả → `search_room`;
   `compare_rooms` không chỉ phòng mà có ý lọc → `refine_search`; đang chờ slot và user trả lời
   → `search_room`.
5. Gộp ngữ cảnh + luật "rẻ hơn".
6. `room_detail` + từ khóa chi phí → `CALCULATE_COST`; + từ khóa quy định → `POLICY_INQUIRY`.
7. Chạy 1 nhánh. Nhánh tìm phòng: thiếu cả khu vực lẫn giá → `CLARIFY`; tìm qua backend (nới
   điều kiện nếu rỗng) → xếp hạng (semantic / personalized / khoảng cách / giá) → top 5 →
   `FAST` nếu intent tìm phòng + confidence ≥ 0.90 + không nới + không xin tư vấn, ngược lại
   Gemini + guardrail (`LLM`, hỏng thì `TEMPLATE`).
8. Lưu Redis, ghi `chat_log`.

**Backend web ghép kết quả:** luật nhận ra chào hỏi / đăng tin / FAQ / ngoài phạm vi **và** AI
trả `out_of_scope` / `CLARIFY` / không có phòng → dùng câu mẫu của luật. Ngược lại dùng câu AI,
bỏ tin đã ẩn/khóa.

---

## 5. Một câu có mấy intent?

**Mỗi câu chỉ được gán 1 intent** (softmax + argmax; `NluResult` chỉ có 1 trường `intent`).
Entity thì lấy được nhiều. Câu gộp ý như *"Tìm phòng Cầu Giấy dưới 3tr, mà đặt cọc mấy
tháng?"* → chỉ xử lý phần tìm phòng, bỏ qua phần hỏi cọc.

**Hai hướng xử lý câu nhiều ý — đề xuất chọn hướng 1:**

| | Hướng 1: tách câu rồi xử lý từng đoạn | Hướng 2: train lại dạng multi-label |
|---|---|---|
| Dữ liệu | Không cần thêm | Gán nhãn lại, cần nhiều câu gộp ý (hiện gần như không có) |
| Model, số liệu DoD | Giữ nguyên | Train và đo lại, chỉnh ngưỡng từng intent |
| Orchestrator | Mỗi đoạn vẫn đi 1 nhánh | Vẫn phải viết phần chạy nhiều nhánh và ghép câu |
| Công sức | Vài giờ – 1 ngày | Vài ngày |

Lưu ý cho hướng 1: chỉ nhận kết quả tách khi các đoạn ra intent khác nhau và đều có confidence
cao, không thì giữ nguyên câu; xử lý tuần tự trên cùng ngữ cảnh; chỉ gọi Gemini 1 lần cho câu
ghép; tối đa 2 đoạn. Trước khi làm nên đếm xem dữ liệu thật có bao nhiêu câu nhiều ý — nếu
hiếm thì chỉ cần ghi vào "hạn chế & hướng phát triển".

**Trạng thái:** mới bàn phương án, **chưa làm**.

---

## 6. Phiên test mô phỏng và điểm yếu phát hiện được

> **Mô phỏng**, không phải log thật (lúc rà soát không chạy được service). Đường đi và ngưỡng
> bám đúng code + `application.yml` (fast-path ≥ 0.90, top-k 5, "rẻ hơn" 20%, NLU timeout
> 800ms, TTL 30'); ID phòng, giá, xác suất, thời gian là số giả.

| Lượt | Câu | Intent PhoBERT → sau khi sửa | Bộ lọc | Đường xử lý |
|---|---|---|---|---|
| 1 | tìm phòng trọ cho 2 người | search_room 0.97 | {} | CLARIFY |
| 2 | cầu giấy dưới 3tr có điều hòa | refine 0.88 → search_room (đang chờ slot) | CG, ≤ 3tr, điều hòa | LLM (0.88 < 0.90) |
| 3 | rẻ hơn nữa đi | refine 0.95 | CG, ≤ 2,4tr, điều hòa | FAST |
| 4 | phòng 2 đặt cọc mấy tháng | room_detail 0.93 → POLICY_INQUIRY | giữ nguyên | FAST, câu trả lời chung |

**Điểm yếu (chưa sửa):**
1. **Số người ở** ("cho 2 người") không có nhãn NER → bị bỏ qua.
2. **Ngưỡng fast-path dùng confidence của intent gốc** kể cả khi luật đã sửa intent → câu tìm
   phòng đơn giản vẫn phải gọi Gemini.
3. **`POLICY_INQUIRY` không dùng `roomRefs`**: hỏi cọc của "phòng 2" vẫn nhận câu chung và bị
   hỏi lại "phòng nào".
4. Một câu chỉ có 1 intent (mục 5).

---

## 7. Còn thiếu / việc dở

**Chatbot:**
1. ⚠️ Đưa model vào `ml/out-intent/`, `ml/out-ner/` (hoặc train lại) để `chatbot-nlu` ready,
   rồi chạy `docker compose up -d chatbot-nlu chatbot-api` và chạy lại phiên test mục 6 trên
   log thật.
2. GOLD còn nhỏ (28 câu, 1 câu = 3,6 điểm); `out_of_scope` chỉ 1 câu; còn 2 câu sai (một câu
   hỏi tình trạng phòng — chưa có intent).
3. Chưa có log thật đủ lâu → chưa có fast-path %, p50/p95, hallucination trước/sau guardrail.
4. Recall@5 chưa đo lại trên tin đăng thật.
5. Chưa có bộ 100 cặp normalizer (§14.1, ngưỡng ≥ 0.95); khoảng giá "1tr5-2tr" chưa tách
   min/max.
6. Ước tính chi phí dùng số cố định (100k / 100k / 150k).
7. Docs lệch code: mục 4 của `DOANTOTNGHIEP/TODO.md` vẫn ghi "website chưa dùng module AI";
   vài chỗ còn ghi ViSoBERT.
8. Sửa các điểm yếu ở mục 6 và (tuỳ chọn) xử lý câu nhiều ý theo hướng 1 ở mục 5.

**Ngoài chatbot — đang sửa dở, chưa commit (không nằm trong commit tạo file này):**
- Module nhắn tin (`interaction`): tin đăng đã xoá thì không cho nhắn thêm
  (`LISTING_NOT_ACTIVE`), nhưng chủ tin vẫn là thành viên hội thoại cũ nhờ
  `findOwnerIdIncludingDeleted`. Các file: `ConversationServiceImpl`, `ListingGateway`,
  `ListingGatewayAdapter`, `ListingRepository`, `ConversationServiceImplTest`.
