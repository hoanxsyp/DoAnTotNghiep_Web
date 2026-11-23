# Hướng sửa BUG-15 — Chatbot AI trả phòng sai giới tính / loại tin

> Bug gốc: [11_KIEM_THU_LUONG_TENANT.md](11_KIEM_THU_LUONG_TENANT.md), mục BUG-15. Test tái hiện: `E2E-BOT-04` (`TenantChatbotE2EIT`, đang `@Disabled`).
> Trạng thái: **chưa sửa** — tài liệu này chốt hướng sửa để nhóm duyệt.

## 1. Hiện tượng

Hỏi chatbot: **"phòng ở ghép cho nữ ở Đống Đa"**.

- Bot trả lời "Mình tìm được 5 phòng phù hợp với yêu cầu của bạn".
- Thực tế 4 phòng là tin ở ghép **chỉ nhận nam** (`MALE_ONLY`: #384, #166, #169, #190), 1 phòng là phòng trọ thường (#376).
- Vi phạm checklist docs/10 mục 7: "không trả phòng sai điều kiện".

## 2. Nguyên nhân

| # | Chỗ | Vấn đề |
|---|---|---|
| 1 | Model AI (`AI_Module/Module-AI-CHATBOT`) | Model NLU **không có nhãn giới tính**; danh sách loại phòng (`label_data_md.py:85`) không có "ở ghép". AI chỉ hiểu được "Đống Đa". |
| 2 | `ChatbotServiceImpl.visibleItems()` (dòng ~354) | Backend nhận `ai.rooms()` và chỉ kiểm tin còn công khai, **không đối chiếu** loại tin / giới tính. |
| 3 | `RuleBasedChatbotEngine` (dòng ~281) | Luật nhận giới tính bị sót: "cho nữ" ở **cuối câu** không bắt được (đang so `"nu "` có dấu cách); "cho nam" / "bạn nam" không bắt được (chỉ nhận "chỉ nam", "giới tính nam"). |

**Điểm có sẵn để tận dụng:** bộ luật vẫn chạy trên mọi câu (`engine.interpret(...)` trong `handleMessage`), tách được `categoryCode = ROOMMATE` và `genderRequirement`, và lưu vào `chatbot_conversations.collected_filters` qua các lượt. Chỉ là khi AI trả lời thì kết quả này không được dùng.

## 3. Hướng sửa (chỉ backend, không train lại AI)

### 3.1. Lọc lại phòng AI trả về

Trong `ChatbotServiceImpl.visibleItems(...)`, truyền thêm `ChatSlots` của luật (`result.slots()` — đã gộp ngữ cảnh lượt trước) và lọc bằng `ListingDataGateway.ListingAttr` (đã có `categoryCode`, `genderRequirement`):

| Người dùng nói | Quy tắc lọc |
|---|---|
| "ở ghép" (`categoryCode = ROOMMATE`) | Chỉ giữ tin `ROOMMATE` |
| Loại tin khác (chung cư mini, nguyên căn…) | Chỉ giữ tin đúng `categoryCode` đó |
| "nữ" (`FEMALE_ONLY`) | Bỏ tin `MALE_ONLY` (giữ `FEMALE_ONLY`, `ANY`) |
| "nam" (`MALE_ONLY`) | Bỏ tin `FEMALE_ONLY` (giữ `MALE_ONLY`, `ANY`) |
| Không nói gì | Không lọc — giữ hành vi hiện tại |

Giới tính chỉ áp dụng cho tin `ROOMMATE` (tin loại khác có `genderRequirement = null`, không bị loại vì giới tính).

### 3.2. Sửa câu trả lời khi có tin bị lọc

Làm giống cách xử lý tin bị ẩn ở BUG-17 (`ROOMS_GONE_REPLY` / `ROOMS_GONE_NOTE`):

- **Lọc hết** → không dùng câu của AI; trả: *"Mình chưa tìm thấy phòng ở ghép nhận nữ ở khu vực này. Bạn thử mở rộng khu vực hoặc mức giá nhé."* + `expansionSuggestions` mặc định, `fallback = true`.
- **Lọc bớt** → không để câu "tìm được 5 phòng" khi chỉ còn 2: thay bằng câu có số lượng thật, ví dụ *"Mình tìm được 2 phòng phù hợp:"*.
- **Link "xem thêm"** (`buildSearchUrl`): thêm `categoryCode` và `genderRequirement` vào query để trang Tìm kiếm ra đúng điều kiện.

### 3.3. Sửa luật nhận giới tính

Trong `RuleBasedChatbotEngine`, so theo **từ** (tách theo khoảng trắng sau khi bỏ dấu) thay vì chuỗi con:

- Nữ: `nu`, `con gai`, `ban nu`, `chi nu`, `gioi tinh nu`.
- Nam: `nam`, `con trai`, `ban nam`, `chi nam`, `gioi tinh nam`.
- **Bẫy cần tránh:** "nam" nằm trong địa danh **"Nam Từ Liêm"** (`nam tu liem`) và trong "năm" (`nam` sau khi bỏ dấu). Chỉ nhận "nam" khi đứng sau `cho`, `ban`, `chi`, `gioi tinh`, `con trai`, hoặc trước `/nu`; không nhận khi theo sau là `tu liem`.

### 3.4. (Tùy chọn) Tìm lại khi lọc hết

Nhược điểm của lọc sau: AI trả 5 phòng nam → lọc còn 0, dù DB vẫn có phòng ở ghép nữ ở Đống Đa.
Nếu muốn bot luôn có kết quả: khi lọc xong còn 0, gọi `searchListings(toSearchRequest(slots), principal)` (luồng tìm theo luật đã có sẵn) với đúng `categoryCode` + `genderRequirement` + khu vực, lấy tối đa 5 tin. Khoảng ~15 dòng.

> **Cần nhóm chốt:** có làm 3.4 hay không.

## 4. Không chọn: sửa ở model AI

Muốn AI tự hiểu giới tính phải thêm nhãn mới (`GENDER`), sinh lại dữ liệu, train lại model và sửa bộ lọc phía chatbot-api. Tốn thời gian, và backend vẫn phải kiểm lại kết quả (AI có thể sai). Lọc ở backend là lớp bảo vệ bắt buộc dù sau này có cải thiện AI.

## 5. Test

| ID | Loại | Nội dung |
|---|---|---|
| E2E-BOT-04 | E2E (đã có) | Bỏ `@Disabled`: "phòng ở ghép cho nữ ở Đống Đa" → mọi tin trả về là `ROOMMATE` và không `MALE_ONLY` |
| UT-BOT-xx | Unit `ChatbotServiceImpl` | AI giả trả lẫn tin `MALE_ONLY`, `FEMALE_ONLY`, `ANY`, phòng trọ thường → chỉ còn tin ở ghép nhận nữ; số lượng trong câu trả lời đúng |
| UT-BOT-xx | Unit `ChatbotServiceImpl` | AI giả trả toàn tin `MALE_ONLY` → câu "chưa tìm thấy", có gợi ý mở rộng, `fallback = true` |
| UT-BOT-xx | Unit `ChatbotServiceImpl` | Không nói giới tính / loại tin → không lọc gì |
| UT-BOT-xx | Unit `RuleBasedChatbotEngine` | "ở ghép cho nữ" (cuối câu) → `FEMALE_ONLY`; "tìm bạn nam ở ghép" → `MALE_ONLY`; "phòng trọ Nam Từ Liêm" → giới tính `null` |

## 6. File dự kiến sửa

- `backend_webtro/src/main/java/com/webtro/modules/ai/service/impl/ChatbotServiceImpl.java`
- `backend_webtro/src/main/java/com/webtro/modules/ai/engine/RuleBasedChatbotEngine.java`
- `backend_webtro/src/test/java/com/webtro/e2e/TenantChatbotE2EIT.java` (bỏ `@Disabled`)
- Test unit tương ứng của hai lớp trên
- `docs/11_KIEM_THU_LUONG_TENANT.md` — cập nhật trạng thái BUG-15 sau khi sửa
