# 05 — Tổng kết triển khai & hướng dẫn vận hành

Tài liệu này tổng kết những gì đã xây dựng và cách chạy/kiểm thử toàn hệ thống. Đây là bản mô tả
sản phẩm **thực tế đã chạy được**, không phải kế hoạch.

## 1. Trạng thái xác minh (đã chạy thật bằng Docker)

| Hạng mục | Kết quả đã kiểm chứng |
|---|---|
| Flyway migration | 10 file, 46 bảng, seed đầy đủ — áp dụng thành công trên MySQL 8.4 |
| Hibernate `validate` | PASS — 46 entity khớp chính xác schema |
| Khởi động Spring context | Thành công, ~500 bean (controller, service, adapter, listener, scheduler) |
| Số REST endpoint phục vụ | **160** (Swagger `/v3/api-docs`) |
| Tài khoản admin | Tự tạo lúc khởi động từ biến môi trường (hash BCrypt) |
| Scheduler | Đã chạy thật (log `SentimentRetryJob hoàn tất`) |
| Smoke test API | Đăng nhập (JWT + 27 permission), `/users/me`, dashboard, tìm kiếm, danh mục, khu vực — PASS |
| Ghi dữ liệu | Đăng ký chủ trọ thành công → tạo user + role + verification |
| Email bất đồng bộ | MailHog nhận email chào mừng + xác thực |
| Frontend | `npm run build` OK, Docker image (nginx) OK |
| End-to-end qua nginx | SPA phục vụ + proxy `/api` + đăng nhập — PASS |

## 2. Quy mô mã nguồn

**Backend** (`backend_webtro/`): ~590 file Java.
- 46 entity + 46 repository, 43 controller, ~70 service (interface + impl), ~180 DTO, ~32 mapper thủ công.
- 12 SPI adapter nối chéo module (mẫu hexagonal, giữ ranh giới module).
- 10 scheduler job, 4 module AI rule-based (sentiment tiếng Việt, gợi ý 9 số hạng, chatbot, dự đoán giá).
- Security: JWT access + refresh token (rotation + reuse detection + blacklist Redis), RBAC 2 tầng
  (4 role → 27 permission), rate limit Redis.

**Frontend** (`frontend_webtro/`): ~127 file.
- 51 trang (public/auth/tenant/landlord/admin), 34 component dùng lại, 18 API module, router lazy-load,
  3 guard (ProtectedRoute/RoleRoute/PermissionRoute), theme MUI sáng/tối, axios interceptor tự refresh.

**Docs** (`docs/`): 6 tài liệu (~30.000 dòng) — canonical, kiến trúc, database, API, giao diện, tổng kết.

## 3. Cách chạy

```bash
cd DOANTOTNGHIEP
cp .env.example .env          # đổi các bí mật: JWT_SECRET, mật khẩu DB/Redis, ADMIN_PASSWORD
docker compose up --build
```

Sau khi các container `healthy`:

| Dịch vụ | Địa chỉ |
|---|---|
| Website | http://localhost |
| API + Swagger | http://localhost:8080/swagger-ui.html |
| Hộp thư test (MailHog) | http://localhost:8025 |

Đăng nhập admin: `admin@webtro.local` + `ADMIN_PASSWORD` trong `.env`.

> **Lưu ý khởi động lần đầu:** backend nạp ~590 class + Flyway validate, lần chạy nguội đầu tiên trên
> máy tải nặng có thể mất tới ~3 phút mới `healthy` (healthcheck đã đặt `start-period=180s`). Lần sau
> khởi động ~15–70s.

## 4. Nguyên tắc kỹ thuật đã tuân thủ

- **Không hardcode**: hạ tầng qua biến môi trường; 105 ngưỡng nghiệp vụ trong bảng `system_configs`
  (sửa được qua giao diện Admin lúc chạy).
- **Schema thuộc quyền Flyway**, `ddl-auto=validate` — lệch entity/schema là fail ngay khi khởi động.
- **Xóa mềm toàn hệ thống**; **phân quyền kiểm ở backend** (`@PreAuthorize`), frontend chỉ điều hướng.
- **AI hỗ trợ, không thay thế kiểm duyệt con người**; chạy async, không tự khóa tài khoản.
- **State machine tin đăng** là cổng duy nhất đổi trạng thái; ranh giới module giữ bằng SPI + event.

## 5. Giới hạn phạm vi đã biết (đúng chủ đích đề án)

- Thanh toán ở chế độ **SANDBOX** (mô phỏng có ký HMAC), không tích hợp cổng thật (đúng §13.2).
- Một số thao tác admin nâng cao chưa có endpoint (hàng loạt/bulk, import khu vực CSV, một vài thao tác
  hạn chế chủ trọ) — frontend đánh dấu `TODO-BE`, không ảnh hưởng luồng chính.
- AI dùng rule-based/thống kê trong Java (đúng §13.2: "không cần thuật toán phức tạp"), có thể nâng cấp
  lên mô hình ML thật vì đã tách sau interface.
