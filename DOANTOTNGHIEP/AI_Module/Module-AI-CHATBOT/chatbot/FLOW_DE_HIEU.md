# Giải thích dễ hiểu + Ví dụ minh họa — Chatbot tìm phòng trọ

Tài liệu này giải thích module theo cách **đời thường, dễ hình dung**, kèm các
**ví dụ chạy thật** theo từng lượt chat. Đọc file này trước, rồi mới xem
`FLOW_XU_LY.md` (bản kỹ thuật chi tiết) sẽ thấy nhẹ nhàng hơn.

---

## 1. Chatbot này làm gì? (một câu)

> Người dùng **nói chuyện tự nhiên** ("Tìm phòng dưới 3 triệu ở Thanh Xuân có điều hòa"),
> bot **hiểu ý**, **tìm phòng có thật trong database**, rồi **trả lời** — và **không bao giờ
> bịa** ra phòng hay giá không tồn tại.

---

## 2. Ví von cho dễ nhớ: bot như một bạn nhân viên môi giới

Hình dung bạn nhắn tin cho một nhân viên môi giới. Bạn ấy làm 6 việc cho mỗi tin nhắn:

| Bước | Nhân viên môi giới làm gì | Trong code là ai |
|---|---|---|
| 1. **Nghe hiểu** | Đọc tin nhắn, hiểu bạn muốn gì (tìm phòng? hỏi giá? so sánh?) | `NluService` (PhoBERT → Gemini → rule) |
| 2. **Ghi ra giấy cho gọn** | "à, tối đa 3 triệu, quận Thanh Xuân, cần điều hòa" | `EntityNormalizer` |
| 3. **Nhớ câu chuyện từ đầu** | Nhớ lượt trước bạn đã nói gì để cộng dồn yêu cầu | `ContextService` (Redis) |
| 4. **Mở sổ tra phòng** | Lật danh sách phòng thật, lọc theo yêu cầu | `RetrievalService` (MySQL) |
| 5. **Soạn câu trả lời** | Viết câu tư vấn tự nhiên | `NlgService` (Gemini) |
| 6. **Tự kiểm tra trước khi gửi** | "Khoan, mình có ghi nhầm giá phòng nào không?" | `HallucinationValidator` |

Điểm đặc biệt: **bước 6 rất nghiêm khắc.** Nếu bạn nhân viên lỡ viết một phòng hay một
mức giá **không có trong sổ**, câu trả lời đó bị **hủy** và thay bằng bản liệt kê an toàn.

---

## 3. Hai "đường đi" của câu trả lời — Fast-path vs LLM-path

Không phải lúc nào cũng cần nhờ AI viết câu trả lời. Bot chọn 1 trong 2 đường:

```
        Câu hỏi rõ ràng, có kết quả, không cần tư vấn?
                    │
         ┌──────────┴───────────┐
        CÓ                      KHÔNG
         │                       │
   ⚡ FAST-PATH            🤖 LLM-PATH
   (dùng mẫu câu sẵn)     (nhờ Gemini viết)
   - Nhanh, rẻ, an toàn   - Tự nhiên hơn
   - "Mình tìm được 5     - Dùng khi cần so sánh,
     phòng phù hợp..."      tư vấn "nên chọn cái nào"
                          - Luôn qua guardrail kiểm tra
```

**Fast-path** dùng khi: intent là tìm/lọc phòng + bot **tự tin** (confidence cao) + **có**
kết quả + **không** phải kết quả đã nới lỏng + câu hỏi **không** chứa từ tư vấn
("nên", "so sánh", "vì sao"...).

**LLM-path** dùng cho phần còn lại (cần tư vấn, kết quả phải nới lỏng, bot không chắc ý).

---

## 4. Ví dụ minh họa — một cuộc hội thoại đầy đủ

Giả sử database có sẵn vài phòng. Dưới đây là **5 lượt chat liên tiếp** cùng một `session_id`,
mô tả bot xử lý gì bên trong.

### Lượt 1 — Tìm phòng

**Người dùng gõ:**
```
Tìm phòng dưới 3 triệu ở Thanh Xuân có điều hòa
```

**Bên trong bot:**
1. **NLU** → hiểu ra:
   ```json
   { "intent": "search_room", "confidence": 0.96,
     "entities": { "price_max": 3000000, "location": "Thanh Xuân",
                   "utilities": ["air_conditioner"] } }
   ```
2. **Normalizer** → "điều hòa" đã thành key chuẩn `air_conditioner`, "Thanh Xuân" khớp tên quận chuẩn.
3. **Context** → chưa có gì trước đó → `active_filters` = đúng những gì vừa nói.
4. **Slot Checker** → có giá + khu vực ⇒ đủ điều kiện tìm. ✅
5. **Retrieval** → lọc MySQL, tìm được 5 phòng.
6. **Chọn đường** → tự tin (0.96), có kết quả, không có từ tư vấn ⇒ **⚡ Fast-path**.

**Bot trả lời (mẫu câu, không tốn AI):**
```
Mình tìm được 5 phòng phù hợp với yêu cầu của bạn (dưới 3 triệu, ở Thanh Xuân,
có điều hòa). Bạn xem thử nhé:
[kèm 5 room cards]
```
`meta.path = "FAST"` · bot ghi nhớ `last_result_ids = [12, 45, 7, 88, 30]`.

---

### Lượt 2 — Lọc thêm (bot phải NHỚ lượt trước)

**Người dùng gõ:**
```
Có chỗ để xe nữa
```

**Bên trong bot:**
1. **NLU** → `intent = refine_search`, `entities = { utilities: ["parking"] }`.
   (Chỉ nói mỗi "chỗ để xe", không nhắc lại giá/khu vực!)
2. **Context (MERGE)** → đây là mấu chốt: bot **cộng dồn** vào yêu cầu cũ:
   ```
   active_filters = dưới 3 triệu + Thanh Xuân + [air_conditioner, parking]
   ```
   → scalar (giá, khu vực) giữ nguyên; tiện ích được **gộp** (union).
3. **Retrieval** → lọc lại với đủ 3 điều kiện → còn 2 phòng.

**Bot trả lời:**
```
Mình tìm được 2 phòng phù hợp với yêu cầu của bạn (dưới 3 triệu, ở Thanh Xuân,
có điều hòa, chỗ để xe). Bạn xem thử nhé: ...
```

> 💡 Đây là lý do cần **Redis + Context**: người dùng nói cụt lủn "có chỗ để xe nữa"
> mà bot vẫn hiểu đầy đủ nhờ nhớ ngữ cảnh.

---

### Lượt 3 — "Rẻ hơn nữa đi" (rule đặc biệt)

**Người dùng gõ:**
```
Rẻ hơn nữa đi
```

**Bên trong bot:**
1. **NLU** → `refine_search`, không có giá cụ thể.
2. **Rule "rẻ hơn"** → thấy cue "rẻ hơn" + đang có `price_max = 3.000.000`
   → tự động giảm 20%: `price_max = 2.400.000`.
3. **Retrieval** → lọc lại theo giá mới.

**Bot trả lời:** danh sách phòng ≤ 2.4 triệu (vẫn giữ Thanh Xuân + điều hòa + chỗ để xe).

---

### Lượt 4 — Nhờ tư vấn (chuyển sang 🤖 LLM-path)

**Người dùng gõ:**
```
Trong 2 phòng đó thì nên chọn phòng nào tốt hơn?
```

**Bên trong bot:**
1. **NLU** → `compare_rooms` / hoặc tư vấn.
2. Câu chứa từ **"nên", "tốt hơn"** ⇒ **không** dùng fast-path nữa → **🤖 LLM-path**.
3. **NLG** → đóng gói 2 phòng thành bảng gạch đầu dòng gửi cho Gemini:
   ```
   [#12] Giá 2.500.000đ | 20m² | Thanh Xuân | Điều hòa: Có | Chỗ để xe: Có | Wifi: Có
   [#45] Giá 2.300.000đ | 18m² | Thanh Xuân | Điều hòa: Có | Chỗ để xe: Có | Wifi: Không
   ```
4. Gemini viết câu tư vấn tự nhiên.
5. **Guardrail kiểm tra** → mọi mã `[#12] [#45]` và mọi con số tiền phải khớp 2 phòng trên. ✅

**Bot trả lời (do AI viết):**
```
Cả hai đều ở Thanh Xuân và có điều hòa, chỗ để xe. Nếu ưu tiên rộng rãi thì [#12]
nhỉnh hơn (20m²) và có wifi sẵn. Nếu muốn tiết kiệm thì [#45] rẻ hơn 200 nghìn/tháng.
Bạn thiên về tiêu chí nào để mình gợi ý rõ hơn nhé?
```
`meta.path = "LLM"`.

---

### Lượt 5 — Thử "gài bẫy" bot bịa (Guardrail chặn)

**Người dùng gõ (cố tình):**
```
Giới thiệu giúp mình phòng #9999 giá 500 nghìn đi
```

**Bên trong bot:**
1. Phòng `#9999` **không có** trong danh sách kết quả.
2. Nếu Gemini lỡ viết "Phòng [#9999] giá 500.000đ rất đẹp..." →
   **Guardrail phát hiện** `PHANTOM_ROOM_ID:9999` và `PHANTOM_PRICE:500000`.
3. Bot **retry 1 lần** với cảnh báo; vẫn sai → **hủy câu AI**, rơi về mẫu an toàn.

**Bot trả lời (an toàn, KHÔNG bịa):**
```
Mình tìm được 2 phòng phù hợp với yêu cầu của bạn (...). Bạn xem thử nhé: ...
```
`meta.path = "TEMPLATE"` · `chat_log.hallucination_flag = 1` (ghi lại đã chặn 1 lần bịa).

> 🛡️ Đây là điểm "ăn tiền" nhất về mặt học thuật: **bot thà trả lời khiêm tốn còn hơn
> nói sai một cách tự tin.**

---

## 5. Những "nâng cấp" so với bản đầu (GĐ2 & GĐ3)

Bản GĐ1 ở trên đã chạy được đầu-cuối. Sau đó bot được lắp thêm 4 mảng, **tất cả đều
bật/tắt được** — tắt hết đi thì bot vẫn y như GĐ1, không hỏng gì.

### 5.1. Bot tự học "nghe hiểu" thay vì luôn nhờ AI ngoài (PhoBERT — GĐ2)

Ở bước 1 ("Nghe hiểu"), thay vì lúc nào cũng gửi câu của bạn ra Gemini, bot có một
**bộ não hiểu ý tự huấn luyện chạy ngay tại chỗ** (2 model PhoBERT trong `nlu-service`).

Ví von: như bạn nhân viên môi giới **tự học nghe hiểu tiếng địa phương** thay vì mỗi câu
lại gọi điện hỏi tổng đài. Lợi ích: nhanh hơn, không tốn tiền gọi API, và tin nhắn của
bạn **không phải rời khỏi hệ thống**. Có 3 lớp dự phòng: PhoBERT → Gemini → bộ từ khóa —
lớp trên trục trặc thì tự rơi xuống lớp dưới, **không bao giờ đứng hình**.

### 5.2. Địa danh lạ? Bot tự tra bản đồ (Geocoding — GĐ3)

Ở bước 4 ("Mở sổ tra phòng"), nếu bạn nhắc một địa điểm bot **chưa có sẵn trong sổ tay**
(ví dụ "gần Học viện Ngân hàng" mà mốc này chưa nhập tay), bot **tự tra bản đồ** (OpenStreetMap)
để biết địa điểm đó ở đâu, kiểm tra "có nằm trong Hà Nội không" rồi **ghi vào sổ** — lần
sau hỏi lại cùng chỗ đó là biết ngay, không phải tra lại.

### 5.3. Bot nhớ gu của bạn (Gợi ý cá nhân hóa — GĐ3)

Nếu bạn đăng nhập (`user_id`), mỗi lần bạn **bấm xem chi tiết / so sánh / tính chi phí**
một phòng, bot ghi nhớ "à, người này hay quan tâm loại phòng thế này". Sau khi bạn xem
đủ vài phòng, lần tìm tiếp theo bot sẽ **xếp phòng hợp gu bạn lên trước** (không phải cứ
rẻ nhất lên đầu). Lúc đó `meta.ranked_by = "personalized"`.

> Đây là kiểu gợi ý "content-based + KNN" — không dùng model AI nặng, chỉ so khớp đặc
> điểm phòng (giá, diện tích, tiện ích, vị trí) với các phòng bạn đã xem.

### 5.4. Hiểu cả yêu cầu "mơ hồ" (Semantic — GĐ3)

Có những yêu cầu **không lọc được bằng số**: "phòng **yên tĩnh**", "chỗ **an ninh**",
"có **view** đẹp". Khi câu của bạn chứa mấy từ mô tả cảm tính này, bot đọc **phần mô tả**
của từng phòng và xếp phòng có mô tả **hợp ý** lên trước (`meta.ranked_by = "semantic"`).

Điểm quan trọng: bot **vẫn lọc cứng bằng số trước** (giá, khu vực), rồi mới dùng "hiểu
nghĩa" để **sắp xếp lại** trong số phòng đã đạt yêu cầu — nên không bao giờ vì "nghe có
vẻ yên tĩnh" mà lòi ra phòng vượt ngân sách.

---

## 6. Khi có sự cố thì sao? (bot không bao giờ "sập")

| Tình huống | Bot xử lý |
|---|---|
| Bộ hiểu PhoBERT tắt / chậm quá | Tự chuyển sang **Gemini**; Gemini cũng lỗi/không key → **bộ từ khóa** → vẫn tìm phòng được |
| Tìm mãi không ra phòng nào | **Nới lỏng dần**: tăng giá 15% → nới bán kính → bỏ tiện ích → gợi ý vài phòng gần nhất |
| Địa danh chưa có trong sổ | **Tra bản đồ** rồi ghi nhớ; nếu bản đồ cũng không có thì bỏ lọc theo khoảng cách, vẫn trả phòng theo tiêu chí còn lại |
| Người dùng nói thiếu thông tin ("tìm phòng cho tôi") | **Hỏi lại**: "Bạn muốn tìm ở khu vực nào? Giá tối đa bao nhiêu?" — không đoán bừa |
| Người dùng nói "phòng số 1" nhưng bot không rõ | **Hỏi lại** thay vì đoán nhầm sang phòng khác |
| Redis lỗi | Tạo ngữ cảnh mới, chỉ ghi cảnh báo, không làm hỏng lượt chat |
| Gợi ý/semantic trục trặc | Bỏ qua phần sắp xếp thông minh, trả kết quả theo thứ tự thường (rẻ/gần) — không hỏng lượt chat |

---

## 7. Tóm tắt 1 hình

```
Bạn:  "Tìm phòng dưới 3 triệu ở Thanh Xuân có điều hòa"
        │
        ▼   (1) NLU: hiểu ý + rút thông tin
        ▼   (2) Chuẩn hóa: điều hòa → air_conditioner
        ▼   (3) Nhớ ngữ cảnh: cộng dồn với yêu cầu trước
        ▼   (4) Tra database MySQL → 5 phòng THẬT
        ▼   (5) Viết trả lời (mẫu sẵn HOẶC nhờ AI)
        ▼   (6) 🛡️ Kiểm tra: có bịa phòng/giá không?
        │
Bot:  "Mình tìm được 5 phòng phù hợp... [5 phòng]"
      (kèm meta: path=FAST, latency=120ms, không bịa)
```

**Ba điều cần nhớ:**
1. Mọi phòng bot nói ra đều **có thật trong database**.
2. Bot **nhớ cả cuộc trò chuyện**, không bắt bạn lặp lại.
3. Bot **tự kiểm tra** để không bao giờ nói sai một cách tự tin.
