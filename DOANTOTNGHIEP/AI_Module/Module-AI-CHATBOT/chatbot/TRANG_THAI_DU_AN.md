# Tổng hợp trạng thái dự án — Module Chatbot AI Tìm phòng trọ

> Tài liệu tổng hợp: dự án đã làm đến giai đoạn nào, mỗi giai đoạn còn thiếu gì.
> Đối chiếu với `SPEC_Module_Chatbot_AI_Tim_Phong_Tro.md` (bản chuẩn hóa v2.0).
> Cập nhật: 2026-10-17 (chuyển tầng NLU về PhoBERT, gộp còn 6 intent, đã train + đo — xem mục 2).

---

## 0. Tóm tắt nhanh (executive summary)

| Giai đoạn | Phạm vi | Trạng thái | Ghi chú |
|---|---|---|---|
| **GĐ1 — MVP** | NLU(LLM)→Normalizer→Context→Retrieval→NLG+Guardrail, multi-turn, geo | ✅ **Xong** | Chạy e2e, đủ intent (6 nhãn NLU, 8 nhánh xử lý), đủ demo §15 |
| **GĐ2 — NLU PhoBERT + Fast-path** | Train intent/NER, FastAPI service, nối Spring, fast-path, so sánh các phương án NLU | 🟢 **Đã train + đo** | Intent GOLD 0.929 (đạt DoD-1); NER GOLD Span-F1 0.935 (đạt DoD-2) |
| **GĐ3 — Semantic + Recommendation** | Semantic rerank (embedding), Content-Based+KNN recommend | ✅ **Xong** | Không dùng Elasticsearch (có chủ đích, catalog ~60 phòng) |
| **Phụ — Geocoding fallback** | POI miss → Nominatim → cache | ✅ **Xong** | Tầng 2 hybrid geo |
| **Tích hợp web** (2026-10-18) | Widget của web → `backend_webtro` → `chatbot-api`; tìm trên **tin đăng thật** qua API công khai của backend | ✅ **Chạy e2e** | Luật cũ chỉ còn nhạy cảm / thuật ngữ / câu mẫu; AI sập → báo lỗi. Recall@5 trên tin thật **chưa đo lại** (bộ vàng hiện gán trên 59 phòng demo) |

**Kết luận:** Cả 3 giai đoạn code đã hoàn thành và chạy được đầu-cuối. **Việc còn thiếu chủ yếu KHÔNG phải code, mà là phần ĐÁNH GIÁ (§14) và DỮ LIỆU THẬT (§13.3)** — đây mới là chỗ quyết định điểm khi bảo vệ.

---

## 1. GĐ1 — MVP (SPEC §10)

### Đã làm ✅

| Hạng mục | Lớp Java | SPEC |
|---|---|---|
| NLU bằng LLM (@Primary GĐ1) | `LlmNluServiceImpl` | §10.1 |
| NLU dự phòng rule-based (khi LLM lỗi/không key) | `RuleBasedNluService` | — |
| Normalizer giá/thời gian/khu vực/tiện ích | `normalizer/*` (Price/DateTime/Location/Utility + Entity) | §3.3 |
| Context MERGE/OVERRIDE/RESET (Redis, TTL 30p) | `ContextService` | §4 |
| Slot Checker + Ask Clarifying (không gọi LLM) | `ChatOrchestrator.handleSearch` | §4.3 |
| Retrieval MySQL + geo `ST_Distance_Sphere` + nới lỏng | `RetrievalService`, `RoomRepository` | §5 |
| NLG (RAG) + retry 1 lần | `NlgService`, `GeminiClient` | §6.1–6.2 |
| **Guardrail chống hallucination** | `HallucinationValidator` | §6.4 (DoD-4) |
| Fast-path (bỏ LLM) | `ChatOrchestrator.canFastPath` | §6.3 |
| API `POST /api/v1/chat`, `/reset`, `meta` | `ChatController` | §7 |
| Schema `room`+`poi`+`chat_log`, seed 59 phòng | `schema.sql`, `data.sql` | §8 |
| **Đủ intent được xử lý** (6 nhãn NLU; `room_detail` tách tiếp ra chi phí / quy định) | `ChatOrchestrator.handle` switch | §3.1 |
| Frontend React chat widget + RoomCard + filter chips | `frontend/` | §10.5 |
| Rule "rẻ hơn nữa đi" (giảm 20% giá) | `ChatOrchestrator` | §4.2 |

6 intent NLU: `search_room`, `refine_search`, `room_detail`, `compare_rooms`, `book_appointment` (chỉ thu thập tham số, không ghi DB — §1.2), `out_of_scope`. Câu `room_detail` hỏi chi phí / quy định được `ChatOrchestrator` chuyển sang handler riêng (`CALCULATE_COST`, `POLICY_INQUIRY` vẫn còn trong enum Java).

### Còn thiếu / khác SPEC (có chủ đích) ⚠️

- **Location dùng `room.district` (VARCHAR) + fuzzy** thay cho bảng `district` riêng — đơn giản hóa MVP (README §8).
- **POI alias match ở Java** thay vì `JSON_CONTAINS` — dễ port, tránh phụ thuộc cú pháp MySQL.
- **`book_appointment` chưa ghi DB thật** — đúng phạm vi §1.2 (chatbot không ghi dữ liệu), nhưng khi ghép vào hệ thống nghiệp vụ cần nối API đặt lịch tại `ChatOrchestrator.handleBooking`.
- **Ước tính chi phí (`handleCalculateCost`) dùng điện/nước/dịch vụ mặc định cứng** (100k/100k/150k) — nếu muốn chính xác cần lấy đơn giá thật từ tin đăng.

---

## 2. GĐ2 — Fine-tune PhoBERT & Fast-path (SPEC §11)

> ✅ **Đã train (2026-10-17).** Model PhoBERT ở `ml/out-intent/`, `ml/out-ner/`,
> `nlu-service` chạy được và đã đo lại toàn bộ. Lịch sử: PhoBERT (7-8) → ViSoBERT
> (09-29) → so sánh 2 model trên cùng dữ liệu (10-15) → **quay về PhoBERT** vì tốt
> hơn ở mọi chỉ số; đồng thời gộp 8 intent còn 6.

### Đã làm ✅

| Hạng mục | Vị trí | SPEC |
|---|---|---|
| Sinh dataset synthetic v2 theo văn phong thật (2500 intent, 2075 NER / 6965 entity) | `ml/data/generate_dataset_v2.py` | §13.2 |
| **So sánh PhoBERT vs ViSoBERT** (cùng data, 3 seed, không chọn checkpoint trên test) | `ml/compare_backbones.py` → `ml/eval-results/backbone_compare_full.md` | §2.2 |
| Train intent classifier (PhoBERT, **6 nhãn**) | `ml/train_intent.py` → `ml/out-intent/` | §11 bước 2.1 |
| Train NER (BIO, nhãn theo từ VnCoreNLP) | `ml/train_ner.py` → `ml/out-ner/` | §11 bước 2.2 |
| Word-segment VnCoreNLP kèm offset ký tự, encode dùng chung train + service | `ml/phobert_encoding.py`, `ml/vncorenlp_util.py` | §11 lưu ý |
| Bọc FastAPI service `/nlu` + `/embed` + `/health` (JRE trong Docker, warm-up lúc startup) | `nlu-service/app.py`, `nlu-service/Dockerfile` | §11 bước 2.3 |
| Nối Spring qua `LocalNluServiceImpl` (@Primary, timeout 300ms, fallback LLM→rule) | `service/LocalNluServiceImpl` | §11 bước 2.4 |
| Fast-path bật + ghi `path` vào `chat_log` | `ChatOrchestrator` | §6.3 |
| **So sánh 3 phương án NLU (A/B/C)** | `ml/eval_nlu_compare.py`, `ml/eval-results/REPORT.md` | §14.4 |

**Gộp intent:** `calculate_cost` (0 câu thật) và `policy_inquiry` (1 câu thật) gộp
vào `room_detail`. `ChatOrchestrator` tách lại theo từ khóa nên người dùng vẫn được
ước tính chi phí / trả lời quy định như trước (SPEC §3.1).

### Kết quả đo được (2026-10-17)

Train: intent 3650 câu (2500 synthetic v2 + 115 câu thật ×10); NER 2190 câu
(2075 synthetic v2 + 115 câu thật, gồm 87 câu chat `data2.md` gán nhãn NER tay
ngày 2026-10-17). Model lấy ở epoch cuối.

| Model | Metric | Giá trị | Ngưỡng DoD |
|---|---|---|---|
| Intent | Accuracy (GOLD thật, 28 câu) | **0.929** (26/28) | DoD-1 ≥ 0.90 ✅ |
| Intent | Macro-F1 (GOLD thật) | 0.916 | — |
| Intent | Accuracy (PROXY tay viết, 142 câu) | 0.775 | — |
| NER | Span-F1 strict (GOLD thật, 28 câu / 69 entity) | **0.935** (P 0.929 / R 0.942) | DoD-2 ≥ 0.85 ✅ |
| NER | Span-F1 strict (PROXY, 87 câu) | 0.759 | — |
| NER | Slot-F1 sau chuẩn hoá (PROXY, model NER trước) | 0.962 | — |

GOLD giờ phủ đủ cả 6 intent. 2 câu GOLD còn sai: *"Còn ko em ở luôn ạ"* (thiếu
intent kiểu hỏi tình trạng phòng — `TODO.md`) và *"em cần vào luôn đầu tuần sau nhé
mọi người"*. NER GOLD dùng **cùng 28 câu** với GOLD intent; model NER trước đo trên
cùng bộ này chỉ được 0.720 (chi tiết + việc sửa quy ước nhãn: `ml/README.md`).

**So sánh 3 phương án (§14.4) — `ml/eval-results/REPORT.md`:**

| Phương án | Intent Acc (GOLD) | Slot-F1 (proxy) | Latency TB / p95 | Chi phí/1000 msg |
|---|---|---|---|---|
| A. LLM prompt JSON | 0.929 | 0.921 | 850 / 1004ms | ~2.342đ |
| **B. PhoBERT fine-tuned** | **0.929** | **0.962** | **87 / 107ms** | **≈0đ** |
| C. LLM Function Calling | 0.857 | 0.950 | 669 / 803ms | ~1.163đ |

A và C được đo lúc còn 8 intent; khi chấm, nhãn được gộp ở cả gold lẫn dự đoán
nên tương đương chấm trên 6 intent (không gọi lại LLM). Kết luận: B **ngang A về
intent, hơn cả A lẫn C về slot**, nhanh hơn ~10× và chi phí ≈0. GOLD chỉ 28 câu
(1 câu = 3,6 điểm) nên "ngang nhau" là kết luận đúng mức, không phải "hơn".

### Còn thiếu ❌ (quan trọng cho bảo vệ)

1. ~~DoD-2 chưa đạt~~ — **xong 2026-10-17**: gán nhãn NER cho `data2.md`, có GOLD
   NER thật, Span-F1 0.935. Còn: GOLD nhỏ (69 entity), nhãn chưa có người thứ hai rà.
2. **Data thật còn ít**: 143 câu intent / 56 câu NER; GOLD 28 câu, `out_of_scope` chỉ 1 câu.
3. **Chưa đo tỉ lệ fast-path trên log thật** — SPEC §11 bước 2.4 muốn chạy thật ≥1 tuần rồi `SELECT path, COUNT(*), AVG(latency_ms) FROM chat_log GROUP BY path`. Query đã sẵn (README §7), chỉ thiếu **dữ liệu chạy thật đủ lâu**.
4. Khoảng giá "1tr5-2tr" ra 1 span `PRICE_MAX` — normalizer Java cần tách min/max.
   ~~`UtilityNormalizer` mới map 4 nhóm~~ — **xong 2026-10-18**: 28 key theo danh mục tiện ích
   của web, nhận diện **91%** span UTILITY thật (trước 20%; phần còn lại cố ý không map), có
   xử lý phủ định ("không cần X", "có X thì tốt, không có cũng được").

---

## 3. GĐ3 — Semantic Search & Recommendation (SPEC §12)

### Đã làm ✅

| Hạng mục | Vị trí | SPEC |
|---|---|---|
| Semantic rerank (embedding `vietnamese-sbert` 768d qua `/embed`) | `SemanticRerankService`, `embedding/` | §12.1 |
| Kích hoạt semantic chỉ khi có từ khóa mô tả định tính | `ChatOrchestrator.DESCRIPTIVE_CUES` | §12.1 |
| Filter cứng SQL trước, chỉ rerank sau (không để semantic filter) | `RetrievalService`→`SemanticRerankService` | §5.1 |
| Cache `description_vector` để không embed lại mỗi lượt | `Room.descriptionVector` | — |
| Recommendation Content-Based + KNN (thuần Java) | `RecommendationService` | §12.2 |
| Ghi lượt xem `room_view`, hồ sơ = trung bình có trọng số (half-life) | `RoomView`, `RoomViewRepository` | §12.2 |
| Cần ≥2 lượt xem mới personalize, ghi `meta.ranked_by` | `ChatOrchestrator.handleSearch` | §12.2 |
| Seed 59 phòng (đủ để rerank/recommend khác biệt) | `scripts/generate_extra_rooms.py` | — |

**Test e2e thật (2026-07-20)** đã xác nhận: recommendation đổi thứ tự theo hồ sơ, semantic đẩy phòng "yên tĩnh/thoáng" lên top, cache giảm latency lượt 2.

### Khác SPEC (có chủ đích) ⚠️

- **KHÔNG dùng Elasticsearch `dense_vector`** (SPEC §12.1 giả định đã có ES): catalog thực ~60 phòng → cosine brute-force trong Java trên vector đã cache nhanh hơn round-trip ES, tránh thêm hạ tầng trang trí. Nếu >10.000 phòng thì mới chuyển ES.
- **Không blend điểm số** semantic + personalization — chỉ áp 1 trong 2 (semantic trước). Giữ đơn giản.

### Còn thiếu ❌

1. **Chưa đánh giá định lượng chất lượng semantic rerank** — hiện chỉ kiểm tra định tính qua demo, chưa có metric kiểu NDCG/MRR.
2. **`DESCRIPTIVE_CUES` là danh sách từ khóa cứng** — cần mở rộng khi có data thật cho thấy cách diễn đạt khác.

---

## 4. Phần ĐÁNH GIÁ (SPEC §14) — trạng thái theo từng DoD

> Đây là phần **quyết định điểm** và là câu hỏi chắc chắn của hội đồng ("làm sao biết chatbot tốt?"). Tổng hợp riêng vì nằm rải rác.

| DoD | Tiêu chí | Ngưỡng | Trạng thái | Việc còn thiếu |
|---|---|---|---|---|
| DoD-1 | Intent Accuracy (test thật) | ≥0.90 | ✅ **ĐẠT — 0.929** (GOLD 28 câu thật, 26/28) | GOLD còn nhỏ (1 câu = 3,6 điểm) — thu thêm câu thật để con số vững hơn |
| DoD-2 | Entity F1 macro (test thật) | ≥0.85 | ✅ **ĐẠT — 0.935** Span-F1 strict (GOLD thật 28 câu / 69 entity) | GOLD nhỏ; nhờ người thứ hai rà nhãn; thu thêm câu thật |
| DoD-3 | **Recall@5 của retrieval** | ≥0.80 | ✅ **ĐẠT — Recall@5 = 1.000** (30 câu) | Harness JUnit `RetrievalRecallEvalTest` + bộ vàng `retrieval_eval.jsonl`; báo cáo `ml/eval-results/retrieval_recall.md`. (Mở rộng lên ~50 câu + thêm data thật nếu muốn) |
| DoD-4 | Hallucination rate | =0 sau guardrail | ✅ Validator đã cài + có `hallucination_flag` | Chỉ cần chạy query báo cáo trên log thật |
| DoD-5 | Latency p95 | ≤2.5s LLM / ≤400ms fast | ⚠️ Đo được từ `chat_log`, chưa tổng hợp trên log thật đủ lớn | Chạy thật rồi query p50/p95 |
| DoD-6 | Multi-turn ≥3 lượt giữ ngữ cảnh | ≥3 | ✅ Đã test e2e | — |

### Các thiếu sót cụ thể của §14

1. ~~**DoD-3 Recall@5 — chưa có gì.**~~ — **XONG 2026-07-21.** Harness JUnit
   `RetrievalRecallEvalTest` (@SpringBootTest, gọi thẳng `RetrievalService`, bỏ qua
   NLU) + bộ vàng `src/test/resources/retrieval_eval.jsonl` (30 câu, tập phòng đúng
   `G` gán độc lập từ seed `data.sql`, mọi |G|≤5). Đo `Recall@5` và `MRR`, ghi báo
   cáo `ml/eval-results/retrieval_recall.md`, assert ≥0.80. **Kết quả: Recall@5 =
   1.000, MRR = 1.000** → DoD-3 đạt. Cổng bằng biến môi trường `RETRIEVAL_EVAL=true`
   (cần MySQL vì query dùng `ST_Distance_Sphere`), nên `mvn test` thường vẫn bỏ qua.
   Chạy: `docker compose up -d mysql` (từ `DOANTOTNGHIEP/` — compose đã gom về root) rồi `RETRIEVAL_EVAL=true mvn test -Dtest=RetrievalRecallEvalTest`
   (đặt `DB_PORT=3307` nếu dùng MySQL Docker của repo).
   *Còn có thể mở rộng*: nâng lên ~50 câu (SPEC §14.3) và bổ sung câu từ log thật.
2. **Normalizer exact-match accuracy (§14.1, ngưỡng ≥0.95) — chưa có bộ 100 cặp.** Hiện có unit test (`PriceNormalizerTest`, `UtilityNormalizerTest`) nhưng chưa phải bộ 100 cặp span→value để báo cáo con số accuracy.
3. **Bảng chỉ số hệ thống (p50/p95 latency, cost/1000 msg) trên log thật** — công thức + query có sẵn, thiếu **dữ liệu chạy thật đủ lâu**.
4. **Hai con số hallucination "trước/sau guardrail" (§14.2)** — cơ chế đã có, cần chạy đủ lượt (gồm vài case injection) để có mẫu báo cáo.

---

## 5. Việc còn để sau (từ TODO.md) — tùy chọn, không chặn bảo vệ

- **Geo Tầng 3 — quãng đường đi thực** (Distance Matrix/Routes API) cho top-K hiển thị, thay đường chim bay. Hiện dùng Haversine (đúng chuẩn ngành cho lọc thô). Nếu không kịp → ghi vào mục "hạn chế & hướng phát triển".
- **Giới hạn độ phủ Nominatim** — không có data cho vài trường nhỏ (vd "ĐH Thủy Lợi"). Kiến trúc đã tách interface `GeocodingClient`, đổi sang Goong.io/VietMap chỉ cần thêm 1 impl.
- **Elasticsearch** — chỉ khi catalog >10.000 phòng.

---

## 6. Ưu tiên hành động trước khi bảo vệ (đề xuất)

Xếp theo mức độ ảnh hưởng tới điểm số:

1. ~~Gán nhãn NER thật → đo lại DoD-2~~ — **xong 2026-10-17** (0.935 trên GOLD thật).
2. 🟠 **Thu thêm câu intent thật** cho GOLD (28 câu, 1 câu = 3,6 điểm acc), nhất là `out_of_scope` và câu hỏi tình trạng phòng.
3. ~~Train intent + NER~~ — **xong 2026-10-17** (PhoBERT). ~~Harness Recall@5/MRR~~ — **xong** (DoD-3).
4. 🟠 **Chạy hệ thống thật đủ lâu** (bạn bè gõ thử) để có log → tính fast-path %, latency p50/p95, hallucination rate. Đây cũng là nguồn câu thật cho mục 1.
5. 🟠 **Bộ 100 cặp Normalizer** cho §14.1 (ngưỡng ≥0.95).
6. 🟢 (Tùy chọn) Geo Tầng 3, metric NDCG cho semantic rerank.

---

## 7. Bản đồ tài liệu trong repo

| File | Nội dung |
|---|---|
| `SPEC_Module_Chatbot_AI_Tim_Phong_Tro.md` | Đặc tả chuẩn + hướng dẫn 3 giai đoạn (nguồn chân lý) |
| `README.md` | Kiến trúc backend, ánh xạ SPEC, cách chạy, khác biệt có chủ đích |
| `TODO.md` | Việc để sau (geo tầng 3, data thật) + nhật ký "đã xong" |
| `ml/README.md` | Dataset + train PhoBERT + kết quả + cảnh báo bộ proxy |
| `ml/eval-results/backbone_compare_full.md` | So sánh PhoBERT vs ViSoBERT (lý do chọn PhoBERT) |
| `ml/TRANG_THAI_VISOBERT.md` | Lịch sử giai đoạn ViSoBERT (đã thay thế) |
| `ml/eval-results/REPORT.md` | Bảng so sánh 3 phương án NLU (§14.4) |
| `nlu-service/README.md` | FastAPI NLU service (PhoBERT, cần Java cho VnCoreNLP) |
| `de_cuong_do_an.md` | Đề cương gốc (toàn hệ thống, không chỉ chatbot) |
| **`TRANG_THAI_DU_AN.md`** | **← File này: tổng hợp trạng thái theo giai đoạn** |
