# 05 — Tổng kết triển khai & hướng dẫn vận hành

<!-- WEBTRO_ROLE_ONLY_UPDATE_START -->
> **Cập nhật 2026-08-09:** phân quyền hiện hành là **role-only**. Hệ thống không còn entity/repository/bảng nghiệp vụ `permissions` hay `role_permissions`; Flyway `V15__drop_permission_tables.sql` drop hai bảng này sau các migration lịch sử. Backend kiểm tra bằng `@PreAuthorize("hasRole/hasAnyRole")` và `SecurityUtils.hasRole/hasAnyRole`; JWT chỉ chứa `role`. Tenant được phép tạo tin nhưng service chỉ chấp nhận `categoryCode = ROOMMATE`; Landlord/Admin tạo được mọi loại tin. Access token 15 phút, refresh token 1 ngày, cả hai lưu `localStorage`; khi refresh token còn dưới 15 phút và access token vẫn còn hạn, frontend chủ động gọi `/api/auth/refresh` để xoay refresh token.
<!-- WEBTRO_ROLE_ONLY_UPDATE_END -->

Tài liệu này tổng kết những gì đã xây dựng và cách chạy/kiểm thử toàn hệ thống. Đây là bản mô tả
sản phẩm **thực tế đã chạy được**, không phải kế hoạch.

## 1. Trạng thái xác minh (đã chạy thật bằng Docker)

| Hạng mục | Kết quả đã kiểm chứng |
|---|---|
| Flyway migration | **14 file** (V1…V14), **45 bảng**, seed đầy đủ — áp dụng thành công trên MySQL 8.4 |
| Hibernate `validate` | PASS — 45 entity khớp chính xác schema |
| Khởi động Spring context | Thành công, ~500 bean (controller, service, adapter, listener, scheduler) |
| Số REST endpoint phục vụ | **198** operation trên **170** path (Swagger `/v3/api-docs`) |
| Tài khoản admin | Tự tạo lúc khởi động từ biến môi trường (hash BCrypt) |
| Scheduler | Đã chạy thật (log `SentimentRetryJob hoàn tất`) |
| Smoke test API | Đăng nhập (JWT role-only), `/users/me`, dashboard, tìm kiếm, danh mục, khu vực — PASS |
| Ghi dữ liệu | Đăng ký chủ trọ thành công → tạo user (đúng **1 vai trò**) + `landlord_profiles` + verification |
| Email bất đồng bộ | MailHog nhận email chào mừng + xác thực |
| Frontend | `npm run build` OK, Docker image (nginx) OK |
| End-to-end qua nginx | SPA phục vụ + proxy `/api` + đăng nhập — PASS |

## 2. Quy mô mã nguồn

**Backend** (`backend_webtro/`): ~590 file Java.
- 45 entity + 45 repository, 43 controller, ~70 service (interface + impl), ~180 DTO, ~32 mapper thủ công.
- 12 SPI adapter nối chéo module (mẫu hexagonal, giữ ranh giới module).
- Các engine sentiment, recommendation và chatbot chạy trong Spring Boot; dự đoán giá dùng
  `RentalApiLightgbmPriceEstimator` gọi FastAPI `rental-api` phục vụ model LightGBM.
- Security: JWT access + refresh token (rotation + reuse detection + blacklist Redis), phân quyền role-only
  (4 role, role-only), rate limit Redis. **Mỗi người dùng có đúng một vai trò**
  (`users.role_id`); quyền suy 100% từ vai trò nên hai người cùng vai trò có bộ chức năng y hệt nhau.
  Access token + refresh token do client giữ trong `localStorage`, backend không đặt cookie nào.

**Frontend** (`frontend_webtro/`): ~127 file.
- 51 trang (public/auth/tenant/landlord/admin), 34 component dùng lại, 18 API module, router lazy-load,
  3 guard (ProtectedRoute/RoleRoute), theme MUI sáng/tối, axios interceptor tự refresh.

**Docs** (`docs/`): hiện có 15 file Markdown (kết quả đếm trực tiếp trong thư mục), gồm bộ tài liệu
đánh số 00–10 và các báo cáo/tài liệu nghiệp vụ bổ sung.

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
| FastAPI dự đoán giá | http://localhost:8002 |
| Giao diện độc lập của module dự đoán giá | http://localhost:8501 |

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
- Import khu vực của Admin nhận tệp JSON đúng schema API; frontend không còn luồng giả lập hay marker `TODO-BE`.
- AI dùng rule-based/thống kê trong Java (đúng §13.2: "không cần thuật toán phức tạp"), có thể nâng cấp
  lên mô hình ML thật vì đã tách sau interface.
