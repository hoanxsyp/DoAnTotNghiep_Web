# TODO — việc để sau, chưa làm ngay

## ~~Docs — rà soát & đồng bộ toàn bộ tài liệu~~ — **XONG 2026-08-01**

Đọc lại toàn bộ cấu trúc dự án, hợp nhất/dọn tài liệu cho khớp code hiện tại:

- **Gộp xử lý mới (GĐ2/GĐ3) vào 2 file FLOW** — trước đó chỉ mô tả GĐ1:
  - `FLOW_XU_LY.md` (kỹ thuật): NLU fallback 3 tầng (PhoBERT `nlu-service` → LLM →
    rule), geocoding fallback (§3.6), tầng rerank GĐ3 semantic/personalization + logic
    pool & `ranked_by` (§3.7), ghi `room_view`, thêm `ranked_by` vào response JSON.
  - `FLOW_DE_HIEU.md` (dễ hiểu): thêm mục "Những nâng cấp so với bản đầu (GĐ2 & GĐ3)".
- **Xóa `MEMO_Xay_dung_Chatbot_AI_Ho_tro_Tim_phong_tro.md`** — thừa, nội dung bị SPEC
  v2.0 thay thế hoàn toàn (còn trong lịch sử git nếu cần lại).
- **`README.md`**: sửa lỗi hướng dẫn `cd backend` (repo phẳng — `pom.xml`/`src/` ở gốc);
  thêm §0 "Bản đồ tài liệu"; cập nhật tiêu đề/scope (đủ GĐ1-3), port Docker, cấu trúc.
- **`nlu-service/README.md`**: bổ sung endpoint `/embed` (semantic GĐ3) vốn đã có trong
  `app.py` nhưng README thiếu.

> Còn lại về docs: cập nhật **bảng số DoD trong `ml/README.md`** — hiện vẫn là số
> proxy-only cũ (intent 0.873 / NER-F1 0.728); đợi retrain trên data thật (mục
> "ML — dữ liệu thật" bên dưới, việc còn lại #4).

## Geo/POI — nâng cấp theo mô hình hybrid (bàn ngày 2026-07-17)

Hiện tại: chỉ tra bảng `poi` nội bộ (3 mốc nhập tay), lọc bán kính bằng
`ST_Distance_Sphere`, hiển thị khoảng cách đường chim bay (Haversine).
Hạn chế: POI chưa có trong DB thì không tìm được theo khoảng cách.

### ~~Tầng 2 — Geocoding fallback + cache~~ — **XONG 2026-07-20**

Cài đặt: `geocoding/GeocodingClient` (+ `NominatimGeocodingClient`, OSM Nominatim
miễn phí), `config/GeocodingProperties` (bounding box Hà Nội — validate trước khi
dùng, chặn tọa độ sai), `Poi.source` (`manual`/`geocoded`) + cột DB tương ứng.
`RetrievalService.resolvePoi()` miss bảng nội bộ → gọi geocode → cache vào `poi`.
Bật/tắt qua `roomfinder.geocoding.enabled` (`GEOCODING_ENABLED`).

**Test e2e thật (2026-07-20)**: "Đại học Giao thông Vận tải" và "Học viện Ngân
hàng" — miss lần đầu → geocode qua Nominatim (~8s) → cache → lần hỏi sau cùng POI
chỉ ~1.4s (hit DB, không gọi API lại). `distance_m` hiển thị đúng trong RoomCard.

**Hạn chế đã quan sát được (ghi lại để không tưởng bở khi bảo vệ)**: Nominatim
(miễn phí) **không có** dữ liệu cho "Đại học Thủy Lợi" dù thử nhiều cách viết
(có/không dấu, tên tiếng Anh đầy đủ) — trong khi vẫn khớp tốt các địa danh lớn
(Hồ Gươm, Bách Khoa). Đây là giới hạn độ phủ dữ liệu OSM với trường/địa danh nhỏ
hơn — nếu gặp thường xuyên trong data thật thu thập được, cân nhắc đổi sang
Goong.io/VietMap (trả phí, data VN tốt hơn) — kiến trúc đã tách qua interface
`GeocodingClient` nên chỉ cần thêm 1 impl mới, không sửa `RetrievalService`.

Chưa làm (để sau nếu cần): Tầng 3 — quãng đường đi thực (Distance Matrix API) cho
top-K phòng hiển thị, xem mục dưới.

### Tầng 3 — Quãng đường đi thực cho kết quả hiển thị (tuỳ chọn)

- Giữ nguyên lọc thô toàn DB bằng đường chim bay (như hiện tại — đúng chuẩn ngành).
- Chỉ với top-K phòng sẽ hiển thị (≤5): gọi Distance Matrix / Routes API (Google
  hoặc Goong) lấy quãng đường + thời gian di chuyển thực → RoomCard hiện
  "cách X 1.2km, ~7 phút xe máy" thay vì đường chim bay.
- Cache theo cặp `(room_id, poi_id)` — phòng và mốc đều đứng yên, gọi 1 lần là đủ.
- Nếu không kịp làm: ghi vào mục "hạn chế & hướng phát triển" của báo cáo.

## ~~GĐ3 — Recommendation Engine + Semantic Rerank~~ — **XONG 2026-07-20**

Cài đặt theo SPEC §12, xem README §1 (bảng kiến trúc) và §8 (quyết định không
dùng Elasticsearch):

- **Seed data**: mở rộng từ 9 → 59 phòng (`scripts/generate_extra_rooms.py`) —
  9 phòng gốc quá ít để rerank/recommend có gì khác biệt để thể hiện.
- **Recommendation** (`service/RecommendationService`, thuần Java, không cần
  model ML): feature vector 9 chiều chuẩn hóa min-max, hồ sơ = trung bình có
  trọng số (half-life cấu hình được) các phòng trong `room_view`. Cần ≥2 lượt
  xem mới personalize (`roomfinder.recommendation.min-views`).
- **Semantic rerank** (`service/SemanticRerankService` + `embedding/`): nlu-service
  thêm endpoint `/embed` (`keepitreal/vietnamese-sbert`, 768d). Kích hoạt bằng
  danh sách từ khóa mô tả định tính (`ChatOrchestrator.DESCRIPTIVE_CUES`) —
  KHÔNG phải mọi câu tìm phòng đều gọi embedding, chỉ khi câu hỏi có nội dung
  định tính (SPEC muốn tránh áp semantic lên câu hỏi thuần cấu trúc).
- Ưu tiên đơn giản khi cả 2 tầng đều áp dụng được: semantic trước, personalization
  sau — KHÔNG blend 2 điểm số (giữ đơn giản, xem README).

**Test e2e thật (2026-07-20)**:
- Recommendation: xem 2 phòng rẻ+điều hòa+chỗ để xe → tìm lại → thứ tự đổi từ
  `[42,4,1,2,3]` (giá) sang `[42,1,11,3,5]` (`ranked_by=personalized`) — phòng
  11 (6tr, đắt) vượt lên trên phòng 4 (rẻ, không điều hòa) nhờ khớp tổ hợp tiện
  ích, đúng ngữ nghĩa content-based KNN.
- Semantic: "Tìm phòng yên tĩnh ở Thanh Xuân" → `ranked_by=semantic`, các phòng
  có mô tả nhắc "yên tĩnh"/"thoáng" (id 26, 11, 42, 5) lên top dù không rẻ nhất.
  Cache `description_vector` hoạt động: lượt 2 cùng câu latency giảm từ ~967ms
  xuống ~190ms (không phải embed lại mô tả phòng).
- Câu không có từ khóa mô tả + không có `user_id`: hành vi giữ nguyên như GĐ1/GĐ2
  (`ranked_by=price`), không có tác dụng phụ.

**Việc còn để sau nếu cần**: mở rộng `DESCRIPTIVE_CUES` khi có data thật cho thấy
cách diễn đạt khác; đánh giá định lượng chất lượng semantic rerank (hiện chỉ
kiểm tra định tính qua demo, chưa có metric kiểu NDCG); nếu catalog vượt hẳn quy
mô đồ án (>10.000 phòng) thì chuyển sang Elasticsearch `dense_vector` như SPEC gốc.

## ML — chuyển từ README (`ml/README.md` §Việc còn lại)

1. Thay `ml/data/*_test_real.jsonl` (proxy tay viết) bằng dữ liệu thật thu thập
   + gán nhãn tay (Doccano/Label Studio) — SPEC §13.3; đánh giá lại DoD.
   → **ĐANG LÀM** (bắt đầu 2026-07-30) — xem mục
   "## ML — dữ liệu thật + đo lại DoD" ở cuối file để biết chi tiết & việc còn lại.
2. ~~So sánh PhoBERT với baseline LLM (SPEC §14.4)~~ — **XONG 2026-07-18**, đo
   đủ cả 3 phương án (A prompt-JSON, B PhoBERT, C function-calling), bảng ở
   `ml/eval-results/REPORT.md`, harness `ml/eval_nlu_compare.py`. Kết luận:
   B hòa A về intent (acc 0.923), thua ~5đ Slot-F1, thắng ~8–10× latency và
   chi phí ≈0đ. Số liệu trên bộ PROXY — có data thật (mục 1) thì chạy lại
   (xóa `ml/eval-results/*.jsonl` rồi chạy 3 side + `--report`).
3. ~~Bọc FastAPI (`nlu-service/`) + `PhoBertNluServiceImpl` nối vào Spring~~ —
   **XONG 2026-07-18** (SPEC §11 bước 2.3 + 2.4, xem `nlu-service/README.md`).
   PhoBERT là NLU @Primary, timeout 300ms, fallback LLM → rule-based; fast-path
   §6.3 + ghi `path` vào `chat_log` vốn đã có sẵn trong `ChatOrchestrator` từ GĐ1.
   Đã test e2e 6 lượt hội thoại + test fallback khi tắt nlu-service.

## ML — dữ liệu thật + đo lại DoD (bắt đầu 2026-07-30)

Bổ sung dữ liệu THẬT (thu thập từ post/chat, khác synthetic) vào pipeline NLU và
đo lại DoD-1/DoD-2 theo SPEC §13.3 / §14.1.

### ~~Đánh nhãn data thật có sẵn~~ — **XONG 2026-07-30**
- `ml/data.md` (bài đăng tìm phòng) → đánh nhãn tự động bằng
  `ml/data/label_data_md.py` (gazetteer + regex, offset ký tự có assert):
  - `intent_data_md_labeled.jsonl` — 56 câu, toàn `search_room`.
  - `ner_data_md_labeled.jsonl` — 56 câu / **209 entity** (LOCATION 72, UTILITY 60,
    PRICE_MAX 50, POI 16, DATETIME 10, AREA_MIN 1).
- `ml/data2.md` (câu chat hội thoại) → `intent_data2_labeled.jsonl` — 87 câu
  (refine_search 20, book_appointment 21, compare_rooms 20, room_detail 19,
  out_of_scope 3, policy_inquiry 1). **Mới chỉ có nhãn intent, CHƯA trích NER.**

### ~~Nối data thật vào train INTENT + tách test thật~~ — **XONG 2026-07-30**
- `ml/data/split_real_data.py`: gộp 2 file thật (143 câu) → tách stratified 80/20
  (seed 42) → `intent_train_real.jsonl` (115) + `intent_test_gold.jsonl` (28).
- `ml/train_intent.py`: train = synthetic (2500) + real (115); đánh giá chính trên
  GOLD (data thật held-out), báo cáo kèm PROXY cũ để so sánh. Smoke test pass
  (`--max-train 100 --epochs 1`, EXIT=0).

### Việc CÒN LẠI (ưu tiên từ trên xuống)

1. **[NER] Nối data thật vào `train_ner.py`** — hiện `train_ner.py:143-144` vẫn CHỈ
   đọc `ner_train.jsonl` (synthetic) + `ner_test_real.jsonl` (proxy). File
   `ner_data_md_labeled.jsonl` (209 entity, đã có) CHƯA được dùng. Cần làm tương tự
   intent: mở rộng `split_real_data.py` (hoặc script NER riêng) tách NER thật
   train/gold, rồi sửa `train_ner.py` đọc synthetic + real và eval GOLD + PROXY.
   → Đây là bất đối xứng lớn nhất: intent đã dùng data thật, NER thì chưa.

2. **[NER] Trích entity cho `data2.md`** — nhiều câu chat có `ROOM_REF`
   ("phòng này", "căn A/B", "phòng đầu"), `DATETIME` (khối book_appointment:
   "chiều nay", "9h sáng mai"), `LOCATION` (Cầu Giấy, Mỹ Đình). Hiện bỏ trống.

3. **[Intent] Thu thêm data thật cho 3 lớp thiếu**:
   - `calculate_cost`: **0 câu thật** → không có trong GOLD test, không đo được.
   - `policy_inquiry` (1 câu), `out_of_scope` (3 câu): quá ít.
   - Tổng data thật 143 < mục tiêu SPEC §13.3 (~200 câu test). Nguồn: nhóm FB
     "Tìm phòng trọ Hà Nội", phongtro123/chotot, log chat bạn bè gõ thử ở GĐ1.

4. **[Train + đo] Retrain full 2 model với data thật rồi đo lại DoD**:
   - `python train_intent.py --epochs 5` và `python train_ner.py --epochs 8`.
   - So DoD-1 (Intent Acc ≥ 0.90) / DoD-2 (Entity-F1 ≥ 0.85) trên GOLD thật.
   - Cập nhật bảng kết quả trong `ml/README.md` (hiện đang là số proxy-only cũ:
     intent 0.873 / NER-F1 0.728).

5. **[So sánh §14.4] Chạy lại `eval_nlu_compare.py` trên data thật** — kết quả hiện
   tại (`ml/eval-results/REPORT.md`) đo trên PROXY. Xóa `ml/eval-results/*.jsonl`
   rồi chạy lại 3 phương án + `--report`.

6. **[Backend] Mở rộng `UtilityNormalizer` SYNONYMS** — data thật có tiện ích ngoài
   4 nhóm hiện tại (air_conditioner/parking/wifi/washing_machine): tủ lạnh, thang
   máy, ban công, cửa sổ, gác xép, nóng lạnh, vskk (vệ sinh khép kín), xe điện,
   máy sấy... NER nhận ra span UTILITY nhưng normalizer chưa map được → nên bổ sung
   để khớp end-to-end.

7. **[Đo lường còn thiếu] DoD-3 Recall@5** (SPEC §14.3, ngưỡng ≥ 0.80): chưa có
   harness. Cần 50 truy vấn + tập phòng đúng tay gán trên seed data. Cùng lúc rà
   DoD-4 (hallucination rate) / DoD-5 (latency p95) — SQL đã có sẵn trong SPEC §14.2.

### Ghi chú thiết kế cần quyết
- **Thiếu intent `check_availability`**: data chat thật có nhiều câu "phòng này còn
  không", "còn ko em ở luôn" (data2.md dòng 21, 41, 46) — hiện tạm gán `room_detail`.
  Nếu xuất hiện nhiều, cân nhắc thêm intent mới (phải sửa `LABELS` ở cả
  `train_intent.py`, backend, và sinh data cho lớp này).
- **`JAVA_HOME` chưa set cố định**: `train_intent.py`/`train_ner.py` cần JVM cho
  VnCoreNLP. Máy dev hiện mượn JBR của IntelliJ
  (`C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.5\jbr`). Nên `setx JAVA_HOME`
  một lần cho ổn định.
