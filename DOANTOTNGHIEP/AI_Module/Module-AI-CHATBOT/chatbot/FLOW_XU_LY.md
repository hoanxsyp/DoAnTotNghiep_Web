# Flow xử lý chính — Module Chatbot AI tìm phòng trọ (GĐ1 → GĐ3)

Tài liệu này tổng hợp và giải thích **luồng xử lý một lượt chat** từ lúc request đến
lúc trả response, cùng các cơ chế cốt lõi (NLU nhiều tầng, context, retrieval + geo,
rerank cá nhân hóa/ngữ nghĩa, guardrail). Dùng để nắm nhanh kiến trúc khi đọc code
hoặc bảo vệ đồ án.

> Nguồn code: `com.roomfinder.chat` — điểm vào là `ChatController` →
> `ChatOrchestrator.handle()`. Kiến trúc theo §2.1 của SPEC. Bản dễ hiểu (ví von +
> ví dụ hội thoại): `FLOW_DE_HIEU.md`.
>
> **Phạm vi:** đã gộp cả xử lý GĐ1 (MVP) lẫn phần mở rộng GĐ2 (NLU PhoBERT qua
> `nlu-service`) và GĐ3 (geocoding fallback, recommendation cá nhân hóa, semantic
> rerank). Các phần GĐ2/GĐ3 đều **bật/tắt được** và có fallback về hành vi GĐ1.

---

## 1. Sơ đồ luồng tổng quát

```
POST /api/v1/chat  { session_id, user_id, message }
        │
        ▼
┌───────────────────────────────────────────────────────────────────────┐
│ ChatOrchestrator.handle()                                              │
│                                                                        │
│  0. Sinh sessionId nếu thiếu · load ChatContext từ Redis · gán userId  │
│  1. NLU:  message → NluResult { intent, confidence, entities(Filters) } │
│        PhoBERT (nlu-service) → LLM → rule-based  (fallback 3 tầng)      │
│  2. Normalizer:  chuẩn hóa entities (giá/tiện ích/khu vực/thời gian)    │
│  3. Context:  MERGE / OVERRIDE / RESET → activeFilters                  │
│  4. Định tuyến theo intent (switch)                                     │
│        ├─ SEARCH_ROOM / REFINE_SEARCH → handleSearch()                  │
│        │     Retrieval (MySQL geo + geocoding fallback)                 │
│        │     → Rerank GĐ3 (semantic / personalized) → Fast/LLM-path     │
│        ├─ ROOM_DETAIL     → handleRoomDetail()   (+ ghi room_view)      │
│        ├─ COMPARE_ROOMS   → handleCompare()      (+ ghi room_view)      │
│        ├─ CALCULATE_COST  → handleCalculateCost()(+ ghi room_view)      │
│        ├─ BOOK_APPOINTMENT→ handleBooking()                             │
│        ├─ POLICY_INQUIRY  → câu trả lời mẫu                             │
│        └─ OUT_OF_SCOPE    → câu dẫn hướng lại                           │
│  5. Lưu context · ghi chat_log · build ChatResponse                    │
└───────────────────────────────────────────────────────────────────────┘
        │
        ▼
ChatResponse { reply, intent, rooms[], active_filters,
               meta{ path, ranked_by, relaxed, latency, ... } }
```

---

## 2. Các bước chi tiết trong một lượt chat

### Bước 0 — Nhận request & nạp ngữ cảnh
- `ChatController` (`POST /api/v1/chat`) nhận `ChatRequest`, gọi `orchestrator.handle()`.
- Nếu `session_id` trống → sinh `s-<UUID>`. Nếu request có `user_id` → gán vào context
  (khóa cho recommendation cá nhân hóa ở GĐ3).
- `ContextService.load(sessionId)` đọc `chat:session:{id}` từ **Redis** (JSON).
  Không có → tạo `ChatContext.empty()`. TTL 30 phút, refresh mỗi lượt.

`ChatContext` giữ trạng thái hội thoại: `activeFilters`, `lastResultIds` (danh sách
phòng của lượt trước — nguồn để giải nghĩa "phòng số 1"), `pendingSlot`, `turnCount`, `userId`.

### Bước 1 — NLU (hiểu ngôn ngữ) — fallback 3 tầng
`NluService.parse(message)` → `NluResult { intent, confidence, entities }`.

Thứ tự ưu tiên cài đặt (tầng trên chết → tự rơi xuống tầng dưới, hệ thống **không bao
giờ sập vì NLU**):

| Ưu tiên | Cài đặt | Khi nào dùng | Ghi chú |
|---|---|---|---|
| 1 (`@Primary`) | `PhoBertNluServiceImpl` | GĐ2 bật (`NLU_ENABLED=true`) | Gọi `nlu-service` (FastAPI, 2 model PhoBERT) qua RestClient, timeout 300ms |
| 2 | `LlmNluServiceImpl` | PhoBERT tắt/chết, có `GEMINI_API_KEY` | Gọi **Gemini**, ép trả **JSON thuần** theo schema cố định |
| 3 | `RuleBasedNluService` | LLM lỗi / không key / parse JSON hỏng | Regex + từ khóa — luôn trả về được |

- `nlu-service` chỉ trả **span thô** (offset ký tự); việc quy span → giá trị máy đọc
  được vẫn do `EntityNormalizer` phía Java làm (một nơi duy nhất giữ luật chuẩn hóa —
  xem `nlu-service/README.md`).
- `Intent` là enum đóng 8 nhãn; nhãn lạ được map an toàn về `OUT_OF_SCOPE`.

> Điểm cắm mở rộng đúng như SPEC §9.1: đổi cài đặt NLU chỉ là đổi `@Primary`, tầng
> trên (`ChatOrchestrator`) **không đổi một dòng nào**. Đây là thứ cho phép so sánh
> 3 phương án NLU ở SPEC §14.4 (`ml/eval-results/REPORT.md`).

### Bước 2 — Normalizer (chuẩn hóa entity)
`EntityNormalizer.normalize(entities)` biến span "thô" thành giá trị máy đọc được:
- **Giá** → số nguyên VND (`PriceNormalizer`: "3 củ" → 3.000.000, "3tr5" → 3.500.000).
- **Tiện ích** → key chuẩn (`air_conditioner`, `parking`, `wifi`, `washing_machine`).
- **Khu vực** → fuzzy match tên quận chuẩn (`LocationNormalizer`).
- **POI** → chỉ trim (việc khớp alias/geocode để ở `RetrievalService`).
- **DateTime** → chuẩn ISO nếu đang là span tiếng Việt ("chiều mai 3h").

Đây là **mạng an toàn** cho output của cả LLM lẫn PhoBERT (cả hai chỉ nhả span/chuỗi).

### Bước 3 — Context: MERGE / OVERRIDE / RESET
`ContextService.apply(ctx, nlu, message)` hợp nhất entity lượt này vào `activeFilters`:

| Phép | Khi nào | Hành vi |
|---|---|---|
| **RESET** | `search_room` + từ khóa khởi tạo ("tìm phòng khác", "bắt đầu lại"...) | `activeFilters.clear()` rồi mới merge |
| **MERGE** (refine) / **OVERRIDE** (search) | các trường hợp còn lại | `mergeNonNull`: scalar bị **ghi đè**, `utilities` được **union** |

`Filters` dùng chung cho cả "entities lượt này" lẫn "active_filters tích lũy", nên logic
merge chỉ nằm một chỗ. Quy ước then chốt: **`null` = người dùng không nhắc đến** (bỏ qua
khi build query), khác với `false`.

**Hai rule ngữ cảnh đặc biệt** trong orchestrator:
- **Pending slot** (§4.3): nếu lượt trước bot hỏi lại (đang chờ slot) và lượt này người
  dùng cung cấp slot định vị → ép intent thành `SEARCH_ROOM` (coi như tiếp nối tìm kiếm).
- **"Rẻ hơn nữa đi"** (§4.2): `refine_search` không kèm giá mới mà có cue "rẻ hơn" →
  giảm `priceMax` hiện tại theo `cheaperStepPercent` (mặc định 20%).

### Bước 4 — Định tuyến theo intent
`switch (nlu.getIntent())` rẽ nhánh sang các handler. Nhánh quan trọng nhất là tìm kiếm.

---

## 3. Nhánh tìm kiếm — `handleSearch()` (SEARCH_ROOM / REFINE_SEARCH)

Đây là xương sống của module, thể hiện rõ mô hình **RAG có guardrail**, và là nơi
GĐ3 (rerank) cắm vào giữa retrieval và sinh câu trả lời.

```
handleSearch()
  │
  ├─ Slot Checker: activeFilters thiếu CẢ giá lẫn khu vực/POI?
  │      → set pendingSlot="location", trả câu hỏi lại (path=CLARIFY), KHÔNG gọi LLM
  │
  ├─ Chọn poolLimit:
  │      · có thể rerank (semantic HOẶC personalize) → lấy pool rộng = topK × multiplier
  │      · không rerank → lấy đúng topK (rerank vô nghĩa nếu không dư candidate)
  │
  ├─ RetrievalService.search(activeFilters, poolLimit) → RetrievalResult { rooms, relaxed, note }
  │      · POI: resolve qua bảng poi nội bộ; miss → geocoding fallback (§3.6)
  │
  ├─ Rerank GĐ3 (chỉ khi pool > topK) — §3.7:
  │      · câu có từ mô tả định tính → semantic rerank   → ranked_by="semantic"
  │      · else có userId + đủ lượt xem → personalization → ranked_by="personalized"
  │      · else cắt topK theo thứ tự gốc               → ranked_by="distance"/"price"
  │      · lưu lastResultIds = id các phòng topK cuối cùng
  │
  └─ Chọn đường sinh câu trả lời:
        ├─ rooms rỗng           → template "chưa có phòng phù hợp"        (path=FAST)
        ├─ canFastPath == true  → template liệt kê phòng                  (path=FAST)
        └─ ngược lại            → NlgService.generate() (LLM + guardrail)
              ├─ valid  → dùng câu LLM                                    (path=LLM)
              └─ invalid→ fallback template an toàn                       (path=TEMPLATE)
```

### 3.1. Slot Checker & Ask Clarifying (§4.3)
Nếu `activeFilters.hasAnyLocatorSlot()` = false (không có giá, khu vực, lẫn POI) →
**không đủ để tìm**. Bot đặt `pendingSlot="location"` và hỏi lại người dùng — **tiết kiệm
LLM** và tránh trả kết quả vô nghĩa.

### 3.2. Retrieval (§5) — `RetrievalService.search()`
Filter cứng bằng **SQL trên MySQL**, có hỗ trợ geo và **chiến lược nới lỏng** khi 0 kết quả:

1. **Truy vấn gốc** — filter cứng theo giá/diện tích/tiện ích/khu vực.
   - Nếu có **POI** ("gần PTIT"): resolve POI (§3.6), dùng `ST_Distance_Sphere` với bán
     kính mặc định 1500m; gán `distance_m` (Haversine) cho card.
2. Nếu rỗng → **nới lỏng theo bậc** (không bao giờ dừng ở "không tìm thấy"):
   - (a) nới `priceMax` **+15%**;
   - (b) nếu tìm theo POI → **gấp đôi bán kính**;
   - (c) **bỏ yêu cầu tiện ích**;
   - (d) fallback cuối: vài phòng gần nhất / cùng khu vực, bỏ mọi ràng buộc phụ.
   - Mỗi lần nới trả kèm `relaxationNote` để câu trả lời nói rõ đã nới gì (`relaxed=true`).

> **Nguyên tắc vàng (§5.1):** filter cứng LUÔN bằng SQL. Semantic/personalization
> (§3.7) chỉ **sắp xếp lại** candidate ĐÃ lọc — không bao giờ được dùng để filter,
> tránh trả phòng 5 triệu cho yêu cầu "dưới 3 triệu".

### 3.3. Fast-path vs LLM-path (§6.3) — `canFastPath()`
Bỏ qua LLM (rẻ, nhanh, không rủi ro bịa) khi **tất cả** điều kiện sau đúng:
- intent là `SEARCH_ROOM` / `REFINE_SEARCH`;
- `confidence >= ngưỡng` (fast-path threshold);
- có kết quả (`rooms` không rỗng);
- **không** phải kết quả đã nới lỏng (`relaxed=false`);
- **không** có cue tư vấn ("nên", "tư vấn", "so sánh", "vì sao", "tốt hơn"...).

→ Fast-path trả **template liệt kê phòng**. Ngược lại (cần tư vấn / kết quả đã nới /
confidence thấp) mới đi LLM-path.

### 3.4. NLG (RAG) + Guardrail (§6.1–6.4) — `NlgService.generate()`
- **Serialize** danh sách phòng thành **bảng gạch đầu dòng** (`[#id] | giá | m² | địa chỉ |
  tiện ích | khoảng cách`), kèm bộ lọc & note nới lỏng — LLM đọc chính xác hơn JSON lồng nhau.
- System prompt đặt **quy tắc tuyệt đối**: chỉ nói về phòng trong danh sách, không bịa
  giá/diện tích/mã phòng, luôn nhắc mã dạng `[#id]`.
- Gọi LLM → **HallucinationValidator** kiểm tra:
  - Mọi `[#id]` LLM nhắc phải nằm trong context (`allowedIds`);
  - Mọi con số tiền phải khớp giá một phòng trong context (`allowedPrices`);
  - Sai → `PHANTOM_ROOM_ID` / `PHANTOM_PRICE`.
- **Retry 1 lần** với cảnh báo bổ sung nếu lượt 1 fail. Vẫn fail → trả `valid=false`.

### 3.5. Ghi nhận `path` trung thực
- LLM chạy & qua guardrail → `path=LLM`.
- Guardrail chặn hoặc LLM lỗi → orchestrator **fallback template**, ghi `path=TEMPLATE`
  (không ghi "LLM" khi LLM thực chất không cho ra câu trả lời).
- `hallucinationDetected` phân biệt: guardrail chặn (`true`) vs LLM lỗi kỹ thuật (`false`).

### 3.6. Geocoding fallback cho POI (GĐ3 — TODO.md, tắt bằng `GEOCODING_ENABLED=false`)
Bảng `poi` nội bộ chỉ có vài mốc nhập tay. Khi người dùng nhắc một POI **chưa có** trong
bảng, `RetrievalService.resolvePoi()` không dừng lại mà:

1. Gọi `GeocodingClient` (`NominatimGeocodingClient` — OSM Nominatim miễn phí) geocode tên POI.
2. **Validate** tọa độ trả về nằm trong bounding box Hà Nội (`GeocodingProperties`) —
   chặn tọa độ sai/nhầm thành phố.
3. **Cache** vào bảng `poi` với `source="geocoded"` → lần hỏi sau cùng POI là **hit DB**,
   không tốn thêm API call (lần đầu ~8s, lần sau ~1.4s).

Kiến trúc tách qua interface `GeocodingClient`: đổi sang provider trả phí (Goong/VietMap)
chỉ cần thêm 1 impl, không sửa `RetrievalService`.

### 3.7. Rerank GĐ3 — semantic & personalization (chỉ khi pool > topK)
Hai tầng rerank, **chỉ một tầng áp dụng mỗi lượt** (không blend điểm số — giữ đơn giản):

**(a) Semantic rerank** (`SemanticRerankService`, tắt bằng `roomfinder.semantic.enabled=false`)
- Kích hoạt khi câu hỏi chứa **từ khóa mô tả định tính** (`DESCRIPTIVE_CUES`: "yên tĩnh",
  "thoáng", "an ninh", "view", "ban công"...). Câu tìm phòng **thuần cấu trúc** ("dưới 3
  triệu ở Thanh Xuân") không kích hoạt — mọi candidate đã khớp filter như nhau, không có
  gì để semantic phân biệt.
- Embedding tiếng Việt (`keepitreal/vietnamese-sbert`, 768d) qua `nlu-service:/embed`;
  cosine brute-force trong Java trên `Room.description_vector` đã cache. → `ranked_by="semantic"`.

**(b) Personalization** (`RecommendationService`, tắt bằng `roomfinder.recommendation.enabled=false`)
- Kích hoạt khi có `user_id` **và** đủ số lượt xem (`min-views`, mặc định 2).
- Content-Based + KNN **thuần Java** (không cần model ML): mỗi phòng là vector 9 chiều
  chuẩn hóa min-max `[price, area, lat, lng, has_ac, has_parking, has_wifi,
  has_washing_machine, is_private_bathroom]`; hồ sơ người dùng = trung bình có trọng số
  (half-life) các phòng trong `room_view`; sắp phòng chưa xem theo cosine. → `ranked_by="personalized"`.

**Thứ tự ưu tiên:** semantic (phản ánh đúng câu hỏi hiện tại) **trước** personalization.
Nếu không tầng nào áp dụng → cắt topK theo thứ tự gốc, `ranked_by` = `"distance"` (khi
tìm theo POI) hoặc `"price"`.

> **Nguồn hồ sơ cá nhân hóa:** các handler `room_detail` / `compare_rooms` /
> `calculate_cost` gọi `logRoomViews()` — khi có `user_id`, ghi lượt "xem phòng" vào
> bảng `room_view`. Đây là dữ liệu để `RecommendationService` học sở thích. Lỗi ghi
> `room_view` chỉ warn, không hỏng lượt chat.

---

## 4. Các nhánh intent khác

| Intent | Handler | Tóm tắt |
|---|---|---|
| `ROOM_DETAIL` | `handleRoomDetail` | Giải nghĩa `room_refs` (ordinal 1-based) theo `lastResultIds`; trả chi tiết 1 phòng. Nhắc phòng không phân giải được → **hỏi lại** thay vì đoán. Ghi `room_view`. |
| `COMPARE_ROOMS` | `handleCompare` | Cần ≥2 phòng; thiếu chỉ định → lấy 2 phòng đầu của kết quả trước; xuất bảng so sánh nhanh. Ghi `room_view`. |
| `CALCULATE_COST` | `handleCalculateCost` | Ước tính chi phí/tháng = giá phòng + điện/nước/dịch vụ mặc định. Ghi `room_view`. |
| `BOOK_APPOINTMENT` | `handleBooking` | **Chỉ thu thập tham số** (phòng + thời gian) & xác nhận — **không ghi DB** (đúng §1.2, chỗ tích hợp API nghiệp vụ). |
| `POLICY_INQUIRY` | `simpleReply` | Câu trả lời mẫu về hợp đồng/đặt cọc. |
| `OUT_OF_SCOPE` | `simpleReply` | Dẫn hướng người dùng về đúng phạm vi (tìm phòng). |

**Nguyên tắc chống "tự tin sai" (`unresolvedRefReply`)**: khi người dùng chỉ đích danh một
phòng mà không phân giải được (`namedARoom` nhưng `resolveRefs` rỗng), bot **hỏi lại** thay
vì đoán sang phòng khác — vì câu trả lời sai nghe vẫn rất tự tin, người dùng khó phát hiện.

`resolveRefs()`: `room_refs` là **thứ tự 1-based** trong `lastResultIds` (nói "phòng 1" =
phòng đầu danh sách trước), hoặc khớp trực tiếp nếu ref trùng id thật.

---

## 5. Bước cuối — Lưu & trả kết quả

Mọi nhánh đều kết thúc bằng:
1. `contextService.save(ctx)` — ghi context (kèm `lastResultIds` mới) về Redis, refresh TTL.
2. `logTurn(...)` — ghi **`chat_log`** (MySQL): message, intent dự đoán, confidence,
   entities, id phòng trả về, `path`, `hallucination_flag`, `latency_ms`. Lỗi ghi log
   **không** làm hỏng lượt chat (bắt exception, chỉ warn).
3. `build(...)` → `ChatResponse`:

```json
{
  "session_id": "...",
  "reply": "câu trả lời",
  "intent": "search_room",
  "rooms": [ { RoomCardDto } ],
  "active_filters": { Filters },
  "meta": {
    "path": "FAST|LLM|TEMPLATE|CLARIFY",
    "ranked_by": "price|distance|semantic|personalized",
    "relaxed": false,
    "latency_ms": 123,
    "nlu_confidence": 0.96,
    "hallucination_detected": false
  }
}
```

`meta` là bằng chứng để đánh giá/bảo vệ: tỉ lệ fast-path, latency, có nới lỏng không,
**cách sắp xếp kết quả** (`ranked_by` — chứng minh rerank GĐ3 hoạt động), guardrail có
bắt hallucination không (`chat_log` truy vấn được).

Endpoint phụ:
- `POST /api/v1/chat/reset` — xóa `chat:session:{id}` khỏi Redis.
- `GET /api/v1/chat/health` — health check.

---

## 6. Cơ chế an toàn (không sập) xuyên suốt

| Lớp | Khi lỗi/thiếu | Hành vi thay thế |
|---|---|---|
| NLU PhoBERT (`nlu-service`) | service chết / timeout 300ms / `NLU_ENABLED=false` | fallback `LlmNluServiceImpl` |
| NLU LLM | lỗi / không key / JSON hỏng | fallback `RuleBasedNluService` |
| Intent lạ | nhãn không thuộc 8 nhãn | map về `OUT_OF_SCOPE` |
| Thiếu slot | không có giá & khu vực | hỏi lại (CLARIFY), không gọi LLM |
| POI không có trong bảng | — | geocoding fallback (Nominatim) → cache; miss cả geocode → không lọc theo khoảng cách |
| Retrieval 0 kết quả | — | nới lỏng theo bậc (giá → bán kính → tiện ích → fallback) |
| Semantic rerank | `nlu-service:/embed` lỗi / tắt | giữ thứ tự gốc (`ranked_by` không đổi) |
| Personalization | chưa đủ lượt xem / không userId / tắt | giữ thứ tự gốc |
| NLG (LLM) | lỗi / guardrail chặn | fallback template liệt kê phòng |
| Guardrail | LLM bịa id/giá | chặn cứng, retry 1 lần, rồi template |
| Redis | đọc/ghi lỗi | tạo context mới / bỏ qua, chỉ warn |
| chat_log / room_view | ghi lỗi | bắt exception, không ảnh hưởng response |

> Tổng kết: hệ thống **luôn trả được câu trả lời hợp lệ dựa trên dữ liệu thật**, và
> **không bao giờ để LLM bịa thông tin phòng** lọt tới người dùng — đó là hai đảm bảo
> cốt lõi của module. Mọi phần mở rộng GĐ2/GĐ3 đều là **tùy chọn có fallback**: tắt hết
> đi vẫn còn nguyên một chatbot GĐ1 chạy được.
