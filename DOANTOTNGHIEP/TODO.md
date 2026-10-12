# TODO — việc để sau, chưa làm ngay

## 1. Gửi mail thật qua Gmail SMTP (thay MailHog)

Hiện backend gửi mọi mail vào MailHog (http://localhost:8025), không tới hộp thư thật.

- `docker-compose.yml` → service `backend`: `MAIL_HOST`, `MAIL_PORT`, `MAIL_SMTP_AUTH`,
  `MAIL_SMTP_STARTTLS` đang gán cứng cho MailHog → đổi sang đọc từ `.env`
  (giữ default = MailHog), thêm `MAIL_USERNAME`, `MAIL_PASSWORD`.
- `.env.example`: thêm các biến trên kèm hướng dẫn.
- Cấu hình Gmail: bật xác minh 2 bước → tạo App Password
  (https://myaccount.google.com/apppasswords) → `.env`:
  `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_SMTP_AUTH=true`,
  `MAIL_SMTP_STARTTLS=true`, `MAIL_USERNAME=<gmail>`, `MAIL_PASSWORD=<app password>`,
  `MAIL_FROM_ADDRESS=<gmail>` (Gmail yêu cầu trùng người gửi).
- Giới hạn Gmail cá nhân ~500 mail/ngày; có thể rơi vào Spam.

## 2. Không có chỗ nhập OTP nếu user không xác thực ngay sau khi đăng ký

**Tình huống:** đăng ký xong nhưng rời trang / reload trước khi nhập OTP → quay lại
đăng nhập thấy "Tài khoản chưa được xác thực" + nút "Gửi lại email" → bấm gửi lại thì
nhận OTP mới nhưng **không còn ô nào để nhập OTP**.

**Nguyên nhân (thuần frontend — backend đã hỗ trợ đủ):**

- Ô nhập OTP chỉ có ở `RegisterPage.jsx`, và chỉ hiện khi state `successEmail` có giá trị
  (set ngay sau khi gọi `register` thành công). State này nằm trong component → reload
  hoặc chuyển trang là mất, không có cách nào quay lại bước nhập OTP.
- `LoginPage.jsx` (khi `ACCOUNT_NOT_VERIFIED`): chỉ có nút "Gửi lại email", không có ô OTP.
- `VerifyEmailPage.jsx` (`/xac-thuc-email`): chỉ xác thực bằng `?token=` trong link;
  vào trang không có token → hiện lỗi; dialog trong trang cũng chỉ gửi lại mail.
- Backend `POST /api/auth/verify-email` **đã nhận `{ email, otp }`**
  (`AuthServiceImpl.resolveEmailVerification`) → chỉ thiếu UI.
- Đăng ký lại bằng email cũ → backend trả `EMAIL_ALREADY_EXISTS` (409), không dẫn user tới
  bước xác thực.

**Cách xoay xở hiện tại:** bấm link trong mail (`/xac-thuc-email?token=…`) vẫn xác thực được.

**Hướng sửa đề xuất:**

1. Tách form OTP trong `RegisterPage` thành component dùng chung (vd. `EmailOtpForm`:
   ô OTP 6 số + nút xác thực + nút gửi lại có cooldown 60s).
2. `VerifyEmailPage`: không có `token` mà có `?email=` → hiện `EmailOtpForm` thay vì báo lỗi.
3. `LoginPage`: khi `ACCOUNT_NOT_VERIFIED` → thêm nút "Nhập mã OTP" điều hướng tới
   `/xac-thuc-email?email=<email>` (hoặc nhúng luôn `EmailOtpForm` vào Alert).
4. `RegisterPage`: khi `EMAIL_ALREADY_EXISTS` → gợi ý "Nếu bạn đã đăng ký nhưng chưa
   xác thực, nhập mã OTP tại đây" + link sang `/xac-thuc-email?email=…`.

**Lỗi nhỏ đi kèm (sửa cùng lúc):**

- `RegisterPage.jsx` bắt `err.errorCode === 'EMAIL_CONFLICT'` / `'PHONE_CONFLICT'` nhưng
  backend trả `EMAIL_ALREADY_EXISTS` / `PHONE_ALREADY_EXISTS` → lỗi không gắn vào ô
  email/SĐT mà rơi xuống banner chung.
- `LoginPage.handleResend` gửi giá trị ô `emailOrPhone` làm `email` → user đăng nhập
  bằng SĐT thì bấm "Gửi lại email" bị 400 `VALIDATION_FAILED` ("Email không đúng định dạng").

## 3. Giới hạn 5 lần nhập sai OTP email không có tác dụng (bảo mật)

`AuthServiceImpl.resolveEmailVerification`: sai OTP thì tăng `attemptCount` rồi ném
exception → `verifyEmail` là `@Transactional` nên rollback, `attempt_count` luôn = 0,
không bao giờ ra `OTP_ATTEMPT_EXCEEDED`. Đã kiểm chứng: 7 lần sai liên tiếp, DB vẫn 0.
Hiện chỉ còn chặn 10 lần/giờ theo IP.

Sửa: ghi tăng `attemptCount` trong transaction riêng (`REQUIRES_NEW`) hoặc dùng
`noRollbackFor` cho lỗi OTP. Kiểm tra luôn `verifyPhone` xem có cùng lỗi không.

## 4. Nối Module-AI-CHATBOT (RoomFinder) vào website

> **Làm sau khi fine-tune ViSoBERT xong** (`ml/out-intent`, `ml/out-ner` có model).

**Hiện trạng:** website chưa dùng module AI. Widget gọi `POST /api/ai/chatbot/message` →
backend chính xử lý bằng `RuleBasedChatbotEngine` (keyword/regex). Module
`AI_Module/Module-AI-CHATBOT/chatbot` là app Spring Boot tách rời:

| | Website (`backend_webtro`) | Module-AI-CHATBOT |
|---|---|---|
| NLU | Rule-based | LLM (Gemini) → ViSoBERT → rule, guardrail, context Redis |
| API | `/api/ai/chatbot/message` | `/api/v1/chat` (container `chatbot-api`, cổng 8081) |
| Dữ liệu phòng | Bảng `listings` thật | DB riêng `roomfinder` (59 phòng seed) |
| UI | `frontend_webtro/.../ChatbotWidget.jsx` | `chatbot/frontend/` riêng |

Backend chính không có client nào gọi sang `chatbot-api`; `docker-compose.yml` cũng
không truyền URL chatbot cho `backend`.

**Việc cần làm:**

1. **Nguồn dữ liệu:** cho module truy vấn tin đăng thật của webtro (bảng `listings` +
   `wards`/`districts`, chỉ tin `ACTIVE`) thay cho bảng `room` của DB `roomfinder`.
   Chọn: (a) module đọc thẳng DB webtro (read-only user), hoặc (b) module gọi API
   `/api/search/listings` của backend. Cập nhật `RetrievalService`/`RoomRepository`,
   geo (`latitude`/`longitude` của listings), bảng `poi`.
2. **Nối backend → module:** `ChatbotServiceImpl` gọi `chatbot-api` qua HTTP client
   (URL + timeout từ env, vd. `CHATBOT_API_URL`), fallback về `RuleBasedChatbotEngine`
   khi module chết/timeout. Map response (`reply`, danh sách phòng → `listings` với
   id/slug thật để widget link được, `quickReplies`).
3. **docker-compose:** `backend` thêm `CHATBOT_API_URL: http://chatbot-api:8080` và
   `depends_on` (không bắt buộc healthy, vì có fallback). Xem lại có còn cần
   `chatbot-mysql` không nếu đã chuyển sang DB webtro.
4. **Phiên chat:** ánh xạ `conversationId`/`sessionId` của website sang context Redis của
   module; user đăng nhập thì truyền `user_id` để bật recommendation cá nhân hóa.
   Lưu ý: widget hiện **không gửi `sessionId`** → khách chưa đăng nhập luôn bị
   `"Cần sessionId khi chưa đăng nhập"`; phải sửa khi nối (sinh UUID, lưu `localStorage`).
5. **Log & admin:** giữ ghi `chatbot_conversations`/`chatbot_messages` ở backend chính
   để trang Admin AI vẫn thống kê được.
6. **Test e2e:** khách + user đăng nhập, multi-turn ("Cầu Giấy" → "dưới 3 triệu" →
   "cho 2 người"), quận có chữ "Đ" (Đống Đa), module tắt → fallback.
