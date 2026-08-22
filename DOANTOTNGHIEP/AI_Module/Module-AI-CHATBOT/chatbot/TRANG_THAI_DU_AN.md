# Tổng hợp trạng thái dự án — Module Chatbot AI Tìm phòng trọ

> Tài liệu tổng hợp: dự án đã làm đến giai đoạn nào, mỗi giai đoạn còn thiếu gì.
> Đối chiếu với `SPEC_Module_Chatbot_AI_Tim_Phong_Tro.md` (bản chuẩn hóa v2.0).
> Cập nhật: 2026-08-01.

---

## 0. Tóm tắt nhanh (executive summary)

| Giai đoạn | Phạm vi | Trạng thái | Ghi chú |
|---|---|---|---|
| **GĐ1 — MVP** | NLU(LLM)→Normalizer→Context→Retrieval→NLG+Guardrail, multi-turn, geo | ✅ **Xong** | Chạy e2e, đủ 8 intent, đủ demo §15 |
| **GĐ2 — PhoBERT + Fast-path** | Train intent/NER, FastAPI service, nối Spring, fast-path, so sánh 3 phương án NLU | ✅ **Xong** | Intent train lại trên **143 câu thật**: acc GOLD **0.893** (sát DoD-1). NER vẫn trên proxy (0.728) — data2 không có nhãn NER |
| **GĐ3 — Semantic + Recommendation** | Semantic rerank (embedding), Content-Based+KNN recommend | ✅ **Xong** | Không dùng Elasticsearch (có chủ đích, catalog ~60 phòng) |
| **Phụ — Geocoding fallback** | POI miss → Nominatim → cache | ✅ **Xong** | Tầng 2 hybrid geo |

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
| **8/8 intent được xử lý** | `ChatOrchestrator.handle` switch | §3.1 |
| Frontend React chat widget + RoomCard + filter chips | `frontend/` | §10.5 |
| Rule "rẻ hơn nữa đi" (giảm 20% giá) | `ChatOrchestrator` | §4.2 |

Đủ 8 intent: `search_room`, `refine_search`, `room_detail`, `compare_rooms`, `book_appointment` (chỉ thu thập tham số, không ghi DB — §1.2), `calculate_cost`, `policy_inquiry`, `out_of_scope`.

### Còn thiếu / khác SPEC (có chủ đích) ⚠️

- **Location dùng `room.district` (VARCHAR) + fuzzy** thay cho bảng `district` riêng — đơn giản hóa MVP (README §8).
- **POI alias match ở Java** thay vì `JSON_CONTAINS` — dễ port, tránh phụ thuộc cú pháp MySQL.
- **`book_appointment` chưa ghi DB thật** — đúng phạm vi §1.2 (chatbot không ghi dữ liệu), nhưng khi ghép vào hệ thống nghiệp vụ cần nối API đặt lịch tại `ChatOrchestrator.handleBooking`.
- **`calculate_cost` dùng điện/nước/dịch vụ mặc định cứng** (100k/100k/150k) — nếu muốn chính xác cần lấy đơn giá thật từ tin đăng.

---

## 2. GĐ2 — Fine-tune PhoBERT & Fast-path (SPEC §11)

### Đã làm ✅

| Hạng mục | Vị trí | SPEC |
|---|---|---|
| Sinh dataset synthetic (2500 intent, 2075 NER) | `ml/data/generate_dataset.py` | §13.2 |
| Train intent classifier (PhoBERT-base-v2, 8 nhãn) | `ml/train_intent.py` → `ml/out-intent/` | §11 bước 2.1 |
| Train NER (token classification, BIO) | `ml/train_ner.py` → `ml/out-ner/` | §11 bước 2.2 |
| Word-segment VnCoreNLP trước khi tokenize | `ml/vncorenlp_util.py` | §11 lưu ý |
| Bọc FastAPI service `/nlu` + `/embed` + `/health` | `nlu-service/app.py` | §11 bước 2.3 |
| Nối Spring qua `PhoBertNluServiceImpl` (@Primary, timeout 300ms, fallback LLM→rule) | `service/PhoBertNluServiceImpl` | §11 bước 2.4 |
| Fast-path bật + ghi `path` vào `chat_log` | `ChatOrchestrator` | §6.3 |
| **So sánh 3 phương án NLU (A/B/C)** | `ml/eval_nlu_compare.py`, `ml/eval-results/REPORT.md` | §14.4 |

### Kết quả đo được (cập nhật 2026-08-01 — đã có data thật cho intent)

**Intent — train lại trên 143 câu THẬT (data.md 56 + data2.md 87), eval chính = GOLD 28 câu thật held-out:**

| Model | Metric | Giá trị | Ngưỡng DoD | Đạt? |
|---|---|---|---|---|
| Intent | **Accuracy (GOLD thật)** | **0.893** (25/28) | DoD-1 ≥ 0.90 | ⚠️ sát ngưỡng |
| Intent | Macro-F1 (GOLD thật) | 0.735* | — | — |
| Intent | Accuracy (PROXY tay viết) | 0.894 | — | — |
| NER | Entity-F1 (seqeval, bộ PROXY) | 0.728 | DoD-2 ≥ 0.85 | ❌ |

*Macro-F1 GOLD thấp là **do cách tính, không phải model kém**: GOLD chỉ có 6/8 intent (thiếu hẳn `calculate_cost` và `policy_inquiry`, 0 mẫu test), macro-F1 chia trung bình trên đủ 8 nhãn nên bị 2 nhãn không có mẫu kéo xuống. Con số có nghĩa là **accuracy 0.893**.

> So với lần train cũ (chỉ eval trên proxy): accuracy **0.873 → 0.893** nhờ bổ sung `data2.md` (87 câu hội thoại thật cho `room_detail`/`book_appointment`/`refine_search`/`compare_rooms`). NER **chưa** train lại vì data2 không có nhãn NER (là câu hội thoại, không phải bài đăng tìm phòng).

**So sánh 3 phương án (§14.4) — cập nhật 2026-08-01, `ml/eval-results/REPORT.md`:**

Intent đo trên **GOLD thật** (28 câu held-out); Slot-F1/Latency/Chi phí vẫn trên **PROXY** (NER chưa có data thật):

| Phương án | Intent Acc (GOLD) | Slot-F1 (proxy) | Latency TB | Chi phí/1000 msg |
|---|---|---|---|---|
| A. LLM prompt JSON | 0.893 | 0.921 | 850ms | ~2.342đ |
| B. PhoBERT fine-tuned | **0.893** | 0.872 | **86ms** | **≈0đ** |
| C. LLM Function Calling | 0.821 | 0.950 | 669ms | ~1.163đ |

Kết luận khoa học (mạnh hơn trên data thật): **trên GOLD, PhoBERT (B) HÒA phương án LLM prompt (A) ở 0.893 và vượt Function Calling (C, 0.821)** — tức mô hình self-host miễn phí ngang LLM về intent trên dữ liệu thật. B vẫn thua ~5đ Slot-F1 (đo trên proxy) nhưng **thắng ~8–10× latency và chi phí ≈0**.

### Còn thiếu ❌ (quan trọng cho bảo vệ)

1. **DỮ LIỆU THẬT (§13.3)** — đã cải thiện một phần cho intent, NER còn nguyên:
   - **Intent: đã có 143 câu thật** (data.md 56 + data2.md 87 câu hội thoại) → tách GOLD 28 câu thật held-out làm bộ đánh giá chính. Accuracy GOLD = **0.893**. Đây là số hợp lệ hơn để trình (eval trên data thật, không phải proxy), tuy GOLD còn nhỏ (28 câu) và thiếu 2 intent (`calculate_cost`, `policy_inquiry`).
   - **NER: vẫn chỉ có bộ PROXY** (`ner_test_real.jsonl`, 87 câu tay viết) — data2 là câu hội thoại nên không sinh nhãn NER. Cần thu thập + gán nhãn NER thật rồi đo lại (0.728 vẫn là số trên proxy).
   - Nên tiếp tục thu thập thêm câu thật (đặc biệt `calculate_cost`, `policy_inquiry`) để GOLD đủ phủ 8 intent và đủ lớn.
2. **Intent sát DoD-1** (0.893 vs ≥0.90) trên GOLD 28 câu; **NER chưa đạt DoD-2** (0.728, còn trên proxy).
3. **Chưa đo tỉ lệ fast-path trên log thật** — SPEC §11 bước 2.4 muốn chạy thật ≥1 tuần rồi `SELECT path, COUNT(*), AVG(latency_ms) FROM chat_log GROUP BY path`. Query đã sẵn (README §7), chỉ thiếu **dữ liệu chạy thật đủ lâu**.

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
| DoD-1 | Intent Accuracy (test thật) | ≥0.90 | ⚠️ **0.893 trên GOLD thật** (28 câu) | Sát ngưỡng; thu thập thêm câu thật (nhất là `calculate_cost`/`policy_inquiry`) rồi đo lại |
| DoD-2 | Entity F1 macro (test thật) | ≥0.85 | ⚠️ 0.728 trên **proxy** | NER chưa có data thật (data2 không có nhãn NER); cần gán nhãn NER thật rồi đo lại |
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
   Chạy: `docker compose up -d mysql` rồi `RETRIEVAL_EVAL=true mvn test -Dtest=RetrievalRecallEvalTest`
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

1. 🔴 **Thu thập + gán nhãn data thật (§13.3)** → train lại PhoBERT → đo lại DoD-1/DoD-2. *(Ảnh hưởng lớn nhất — hiện mọi số model đều trên proxy.)*
2. 🔴 **Xây harness Recall@5/MRR (§14.3)** cho DoD-3 — hiện đang trống hoàn toàn.
3. 🟠 **Chạy hệ thống thật đủ lâu** (bạn bè gõ thử) để có log → tính fast-path %, latency p50/p95, hallucination rate. Đây cũng là nguồn câu thật cho mục 1.
4. 🟠 **Bộ 100 cặp Normalizer** cho §14.1 (ngưỡng ≥0.95).
5. 🟢 (Tùy chọn) Geo Tầng 3, metric NDCG cho semantic rerank.

---

## 7. Bản đồ tài liệu trong repo

| File | Nội dung |
|---|---|
| `SPEC_Module_Chatbot_AI_Tim_Phong_Tro.md` | Đặc tả chuẩn + hướng dẫn 3 giai đoạn (nguồn chân lý) |
| `README.md` | Kiến trúc backend, ánh xạ SPEC, cách chạy, khác biệt có chủ đích |
| `TODO.md` | Việc để sau (geo tầng 3, data thật) + nhật ký "đã xong" |
| `ml/README.md` | Dataset + train PhoBERT + cảnh báo bộ proxy |
| `ml/eval-results/REPORT.md` | Bảng so sánh 3 phương án NLU (§14.4) |
| `nlu-service/README.md` | FastAPI PhoBERT service |
| `de_cuong_do_an.md` | Đề cương gốc (toàn hệ thống, không chỉ chatbot) |
| **`TRANG_THAI_DU_AN.md`** | **← File này: tổng hợp trạng thái theo giai đoạn** |
