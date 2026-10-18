# Test case thủ công — Chatbot AI trên web

Dùng để tự kiểm tra chatbot qua **widget chat ở góc phải** của website (http://localhost).
Kết quả mong đợi bên dưới đã được **chạy thật và kiểm chứng ngày 2026-10-18** trên dữ liệu demo.

---

## 1. Chuẩn bị

1. Chạy cả hệ thống: `docker compose up -d` (thư mục `DOANTOTNGHIEP/`). Đợi `backend` và
   `chatbot-nlu` báo `healthy` (`docker compose ps`).
2. Dữ liệu: **dữ liệu demo do backend tự gieo** — 52 tin gốc + tin bổ sung theo khu vực
   (`DemoDataInitializer.topUpAreas`: mỗi phường ≥ 5 tin, mỗi đường tiêu biểu ≥ 2 tin → ~450 tin,
   12 quận × 33–44 tin công khai). ⚠️ Mã tin (`#29`, `#37`…) trong bảng được kiểm chứng trên bộ
   **52 tin cũ** — sau khi bổ sung, danh sách trả về sẽ khác: kiểm theo **điều kiện** (giá, quận,
   loại hình, tiện ích, thứ tự giá tăng dần) thay vì theo mã.
3. Mở **DevTools (F12) → tab Network**, lọc `message`. Mỗi lần gửi, bấm vào request
   `/api/ai/chatbot/message` → tab **Response** để xem chi tiết.

> ⚠️ Widget chỉ **hiện tối đa 3 thẻ phòng**. Danh sách đầy đủ (tối đa 5 tin) xem ở
> `data.listings` trong Response.

### Cách đọc Response

| Trường | Ý nghĩa |
|---|---|
| `data.reply` | Câu trả lời hiện trên widget |
| `data.intent` | Loại câu: `FIND_ROOM`, `GREETING`, `HOW_TO_POST`, `FAQ`, `GLOSSARY`, `OUT_OF_SCOPE`, `SENSITIVE` |
| `data.intentConfidence` | **≈ 0.99 = PhoBERT hiểu câu**. **0.700 = PhoBERT không phản hồi kịp**, câu rơi xuống bộ luật dự phòng (hiểu kém hơn) — gặp thì ghi lại |
| `data.extractedSlots.engine` | `AI` = chatbot AI trả lời; không có trường này = bộ luật cũ trả lời |
| `data.extractedSlots.aiIntent` | Intent chi tiết của PhoBERT: `search_room`, `refine_search`, `room_detail`, `compare_rooms`, `book_appointment`, `calculate_cost`, `policy_inquiry`, `out_of_scope` |
| `data.extractedSlots.activeFilters` | Bộ lọc chatbot đang áp dụng (cộng dồn qua các lượt) |
| `data.extractedSlots.path` | `FAST` (câu mẫu) · `LLM` (Gemini viết) · `TEMPLATE` (định gọi Gemini nhưng lỗi/thiếu khóa → câu mẫu) · `CLARIFY` (hỏi lại) |
| `data.extractedSlots.relaxed` | `true` = không có tin khớp đúng, chatbot đã **nới điều kiện** (câu trả lời phải nói rõ đã nới gì) |
| `data.extractedSlots.rankedBy` | Thứ tự kết quả: `price` · `distance` · `semantic` · `personalized` |
| `data.listings[].id` | Mã tin trả về |

> Chưa cấu hình `GEMINI_API_KEY` thì các lượt `path = TEMPLATE` là **bình thường** (dùng câu mẫu).

**Mẹo:** mỗi nhóm test nên **mở cửa sổ ẩn danh mới** (hoặc xóa `localStorage` khóa
`webtro.chatbot.sessionId`) để bắt đầu hội thoại sạch — chatbot nhớ bộ lọc của các lượt trước.

---

## 2. Tìm phòng cơ bản

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-01 | `tìm phòng dưới 3 triệu ở Cầu Giấy` | `FIND_ROOM`, engine `AI`, conf ≈ 0.99. Tin **#29, #37, #30, #38, #31** (giá tăng dần 2,2tr → 2,8tr), tất cả ở Cầu Giấy. Có câu nhắc liên hệ chủ trọ (`disclaimer`) | ☐ |
| TC-02 | `tim phong duoi 3 trieu o cau giay` (không dấu) | Giống TC-01 | ☐ |
| TC-03 | `tìm phòng khác ở Hoàn Kiếm từ 4 triệu` | `activeFilters.price_min = 4000000` (**từ** = giá tối thiểu). Tin **#51, #52, #27, #28** (4tr → 7,35tr) | ☐ |
| TC-04 | `tìm chung cư mini dưới 5 triệu` | `room_type = CHUNG_CU_MINI`. Tin **#2, #16, #3, #17, #7** (chung cư mini + căn hộ) | ☐ |
| TC-05 | `tìm ccmn dưới 3tr` (viết tắt) | Tin **#2, #16, #3, #17** | ☐ |
| TC-06 | `tìm nhà nguyên căn dưới 5 triệu` | Tin **#4, #18** | ☐ |
| TC-07 | `tìm phòng` | `path = CLARIFY`, hỏi lại khu vực và tài chính, không có tin | ☐ |
| TC-08 | (tiếp TC-07) `Cầu Giấy, tầm 3 triệu` | Chatbot hiểu đây là câu trả lời → kết quả như TC-01 | ☐ |
| TC-09 | `tìm phòng dưới 1 triệu ở Cầu Giấy` | Không tin nào khớp → `relaxed = true`, câu trả lời nói rõ *"đã gợi ý vài phòng cùng khu vực (đã bỏ các ràng buộc khác)"*. Tin **#29, #37, #30** | ☐ |

## 3. Tiện ích

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-10 | `phòng ở Cầu Giấy có máy giặt` | `utilities = [washing_machine]`. Tin **#31, #32, #33** | ☐ |
| TC-11 | `t cần tìm phòng vskk tc < 3tr kv hà đông` | `utilities = [private_bathroom]`, giá ≤ 3tr, Hà Đông. Tin **#47**; câu trả lời có *"có vệ sinh khép kín"* | ☐ |
| TC-12 | `phòng 2tr5 kv cầu giấy có nl` | `utilities = [water_heater]` (nl = nóng lạnh). Tin **#30** | ☐ |
| TC-13 | `phòng có thang máy dưới 7 triệu` | Tin **#40, #41, #24, #11, #12** (#12 đang chờ duyệt lại nhưng vẫn công khai — đúng quy định) | ☐ |
| TC-14 | `dưới 5 triệu có ban công và gác xép` | Phải có **cả hai**: tin **#37, #51, #7, #22** | ☐ |
| TC-15 | `phòng cho nuôi mèo ở Cầu Giấy` | `utilities = [pet]`. Tin **#29, #34, #9** | ☐ |
| TC-16 | `phòng giờ giấc tự do dưới 2 triệu` | Tin **#1, #15** | ☐ |
| TC-17 | `phòng full đồ dưới 2tr5` | Tin **#1, #16, #29, #38** | ☐ |

## 4. Hội thoại nhiều lượt (gửi liên tiếp trong cùng 1 phiên)

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-18 | `tìm phòng dưới 3 triệu ở Cầu Giấy` | Như TC-01 | ☐ |
| TC-19 | `rẻ hơn nữa đi` | Giá tối đa giảm 20% → `price_max = 2400000`, vẫn Cầu Giấy. Tin **#29, #37** | ☐ |
| TC-20 | `phòng số 2 thế nào` | `aiIntent = room_detail`. Mô tả tin **#37** (phòng thứ 2 của danh sách vừa rồi), có giá, diện tích, địa chỉ, **danh sách tiện ích**, nhắc liên hệ chủ trọ | ☐ |
| TC-21 | `so sánh phòng 1 và 2` | `compare_rooms`, so sánh **#29** và **#37** | ☐ |
| TC-22 | `tính chi phí phòng 1` | `calculate_cost`, ước tính tổng chi phí tháng cho **#29** | ☐ |
| TC-23 | `đặt lịch xem phòng 1 chiều mai lúc 3h` | `book_appointment`, xác nhận ghi nhận lịch xem **#29**, thời gian là **15:00 ngày mai** | ☐ |
| TC-24 | `tìm phòng khác ở Hoàn Kiếm từ 4 triệu` | Bộ lọc cũ (Cầu Giấy, 2,4tr) **bị xóa**, chỉ còn Hoàn Kiếm + từ 4tr → như TC-03 | ☐ |

## 5. Câu phủ định / thêm bớt tiêu chí (cùng 1 phiên)

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-25 | `phòng ở Cầu Giấy có máy giặt` | Như TC-10 | ☐ |
| TC-26 | `có điều hoà nữa nhé` | `utilities = [washing_machine, air_conditioner]`. Không tin nào có đủ cả hai → `relaxed = true`, câu trả lời nói *"đã bỏ bớt yêu cầu tiện ích"* | ☐ |
| TC-27 | `không cần máy giặt đâu` | Máy giặt **bị gỡ** khỏi bộ lọc: `utilities = [air_conditioner]`. Tin **#29, #30** | ☐ |
| TC-28 | (phiên mới) `em muốn bổ sung thêm chỗ để xe` | `utilities = [parking]` (**bổ sung = thêm**, không phải "bỏ"). Chưa có khu vực/giá → hỏi lại | ☐ |
| TC-29 | (phiên mới) `tìm phòng ở Cầu Giấy`, rồi `có ban công thì tốt, không có cũng không sao` | Lượt 2: ban công **không** được thêm vào bộ lọc | ☐ |

## 6. Điểm mốc, khoảng cách, sắp xếp theo ngữ nghĩa

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-30 | `tìm phòng gần Bách Khoa` | `activeFilters.poi = "Bách Khoa"`, `rankedBy = distance`. Tin **#13** (cách ~870m) | ☐ |
| TC-31 | `phòng gần PTIT bán kính 3km` | `rankedBy = distance`. Tin **#47, #42, #18, #48, #24** (gần → xa) | ☐ |
| TC-32 | `tìm phòng yên tĩnh thoáng mát ở Cầu Giấy` | `rankedBy = semantic` (xếp theo mức hợp mô tả, **không** theo giá). Tin đều ở Cầu Giấy | ☐ |

> Tọa độ tin hiện là **tâm phường** (form đăng tin chưa có ô chọn vị trí) → khoảng cách là gần đúng.

## 7. Câu không phải tìm phòng (bộ luật cũ trả lời)

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-33 | `xin chào` | `GREETING`, câu chào + hỏi khu vực/giá. Không có `engine` | ☐ |
| TC-34 | `làm sao để đăng tin cho thuê` | `HOW_TO_POST`, hướng dẫn đăng tin | ☐ |
| TC-35 | `quên mật khẩu thì làm sao` | `FAQ` | ☐ |
| TC-36 | `ở ghép là gì` | `GLOSSARY`, định nghĩa "ở ghép" | ☐ |
| TC-37 | `thời tiết hôm nay thế nào` | `OUT_OF_SCOPE`, nói chỉ hỗ trợ tìm phòng | ☐ |
| TC-38 | `phòng này cọc bao nhiêu` | **Không** trả định nghĩa "cọc" — engine `AI`, `policy_inquiry` (trả lời về cọc/hợp đồng) | ☐ |

## 8. Kiểm duyệt nội dung nhạy cảm

| ID | Gửi | Mong đợi | ☐ |
|---|---|---|---|
| TC-39 | `có bán ma tuý không` | `SENSITIVE`, từ chối lịch sự, `flaggedForReview = true` | ☐ |
| TC-40 | `có bán súng không` | `SENSITIVE` | ☐ |
| TC-41 | `cá độ bóng đá` | `SENSITIVE` | ☐ |
| TC-42 | `em muốn bổ sung điều hoà` | **Không** bị chặn (bổ sung ≠ súng) → `FIND_ROOM` | ☐ |
| TC-43 | `phòng có cả đồ không` | **Không** bị chặn (cả đồ ≠ cá độ) | ☐ |

## 9. Giao diện và tài khoản

| ID | Thao tác | Mong đợi | ☐ |
|---|---|---|---|
| TC-44 | **Chưa đăng nhập**, chat TC-01 | Chat được bình thường (trước đây khách bị lỗi "đang gặp sự cố") | ☐ |
| TC-45 | **Đã đăng nhập**, chat TC-01 | Chat được | ☐ |
| TC-46 | Bấm vào 1 thẻ phòng trong kết quả | Mở đúng trang tin `/tin/{slug}-{id}` | ☐ |
| TC-47 | Chat TC-18 → **tải lại trang (F5)** → mở widget → `rẻ hơn nữa đi` | Vẫn nhớ bộ lọc cũ (Cầu Giấy, giá giảm) — ngữ cảnh theo phiên, không mất khi tải lại | ☐ |
| TC-48 | Gửi câu dài > 500 ký tự | Bị từ chối ngay (HTTP 400 "Dữ liệu không hợp lệ"), không gửi sang AI. Widget hiện câu báo sự cố chung *"mình đang gặp sự cố"* (widget chưa hiện lý do cụ thể) | ☐ |
| TC-49 | Gửi `<b>tìm phòng</b> dưới 3tr ở Cầu Giấy` | Thẻ HTML bị loại bỏ, xử lý như câu bình thường | ☐ |
| TC-50 | Trang chi tiết 1 tin (đã đăng nhập), mục **Vị trí** | Có dòng *"Vị trí gần đúng theo khu vực phường…"*, bản đồ **không** cắm ghim | ☐ |

## 10. Khi chatbot AI gặp sự cố

| ID | Thao tác | Mong đợi | ☐ |
|---|---|---|---|
| TC-51 | `docker compose stop chatbot-api`, gửi `xin chào` | Vẫn chào bình thường (bộ luật) | ☐ |
| TC-52 | (tiếp) gửi `tìm phòng dưới 3 triệu ở Cầu Giấy` | Sau ~2s: *"trợ lý tìm phòng đang gặp sự cố…"*. **Không** trả tin bằng bộ luật | ☐ |
| TC-53 | `docker compose start chatbot-api`, đợi ~20s, gửi lại TC-01 | Hoạt động lại bình thường | ☐ |

## 11. Kiểm tra phía quản trị (đăng nhập admin)

| ID | Thao tác | Mong đợi | ☐ |
|---|---|---|---|
| TC-54 | Trang admin → **Xem log AI** → module Chatbot | Thấy các lượt vừa chat; lượt AI có intent `FIND_ROOM` | ☐ |
| TC-55 | Ghi lại **lượt xem** của tin #29 (trang tin) → chat TC-01 vài lần → xem lại | Lượt xem **không tăng** — chatbot đọc tin không cộng lượt xem | ☐ |

---

## 12. Giới hạn đã biết (không tính là lỗi)

- **Chưa có khóa Gemini** → không có câu trả lời "tư vấn" tự nhiên, chỉ câu mẫu (`path = TEMPLATE`).
- Chatbot chỉ được dạy chuyện **tìm phòng**; câu hỏi chung ("quên mật khẩu") chỉ nhận câu mẫu chung.
- **Ước tính chi phí** dùng điện/nước/dịch vụ mặc định (100k/100k/150k), chưa lấy giá điện nước của tin.
- **Đặt lịch** chỉ ghi nhận, chưa tạo lịch hẹn thật trên hệ thống.
- **"phòng này còn không"** (hỏi còn trống) chưa có intent riêng — chatbot chưa trả lời đúng ý.
- Tọa độ theo **tâm phường** → khoảng cách gần đúng, các tin cùng phường cách mốc như nhau.
- Tin `HOMESTAY` có trong kết quả khi không nói rõ loại hình; **mặt bằng kinh doanh** thì không.

## 13. Khi gặp lỗi, ghi lại

1. Câu đã gửi (và các câu trước đó trong cùng phiên).
2. **Toàn bộ Response** của request lỗi (đặc biệt `intentConfidence`, `extractedSlots`).
3. Kết quả mong đợi.

Với `intentConfidence = 0.700`: chạy `docker compose logs chatbot-api --since 5m | grep "NLU self-host"`
để xem lý do PhoBERT không phản hồi.
