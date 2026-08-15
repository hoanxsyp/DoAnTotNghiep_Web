# DOANTOTNGHIEP — Website quảng cáo và tìm kiếm phòng trọ

Hệ thống website trung gian kết nối **chủ trọ** với **người thuê**, hỗ trợ đăng tin, tìm kiếm,
lọc, lưu tin, liên hệ, bình luận, đánh giá, báo cáo vi phạm, thanh toán đẩy tin và kiểm duyệt —
kèm **4 module AI**: phân tích cảm xúc bình luận, gợi ý tin đăng, chatbot tìm trọ và dự đoán
giá thuê.

> **Phạm vi khu vực:** hệ thống giới hạn trong **khu vực Hà Nội** — danh mục địa giới chỉ
> seed 1 tỉnh (Thành phố Hà Nội) với 12 quận nội thành và các phường thực tế; người dùng chỉ
> chọn/đăng/tìm tin trong phạm vi này.

> **Nguyên tắc phân quyền:** mỗi tài khoản có **đúng một vai trò**
> (`ROLE_TENANT` / `ROLE_LANDLORD` / `ROLE_MODERATOR` / `ROLE_ADMIN`), lưu ở cột `users.role_id`.
> Không có bảng `permissions`/`role_permissions`; hai người cùng role luôn có cùng quyền hạn. Backend kiểm tra trực tiếp bằng role.
>
> **Quy tắc đăng tin:** `ROLE_TENANT` được tạo tin nhưng chỉ với loại `ROOMMATE` (tìm người ở ghép).
> `ROLE_LANDLORD` và `ROLE_ADMIN` tạo được tất cả loại tin.
>
> **Phiên đăng nhập:** access token 15 phút và refresh token 1 ngày đều lưu trong `localStorage`.
> Khi refresh token còn dưới 15 phút mà access token vẫn còn hạn, frontend chủ động gọi `/auth/refresh` để sinh refresh token mới. Phiên chỉ hết hạn khi cả access token và refresh token đều hết hạn/không còn dùng được.
| Thành phần | Công nghệ |
|---|---|
| Backend | Java 21, Spring Boot 3.3.5, Spring Security, Spring Data JPA, Flyway, JWT |
| Frontend | ReactJS 18, Vite, JavaScript, MUI v5, Redux Toolkit, React Router v6 |
| Database | MySQL 8.4 |
| Cache / Rate limit | Redis 7.4 |
| Mail (dev) | MailHog |
| Triển khai | Docker Compose |

---

## 1. Chạy toàn hệ thống

Yêu cầu duy nhất: **Docker Desktop**. Không cần cài Java, Maven hay Node trên máy.

```bash
cd DOANTOTNGHIEP

# 1. Tạo file cấu hình môi trường
cp .env.example .env        # Windows PowerShell: Copy-Item .env.example .env

# 2. Mở .env và đổi các giá trị bí mật (BẮT BUỘC):
#    MYSQL_ROOT_PASSWORD, DB_PASSWORD, REDIS_PASSWORD,
#    JWT_SECRET (>= 32 byte), PAYMENT_CALLBACK_SECRET, ADMIN_PASSWORD

# 3. Chạy
docker compose up --build
```

Sau khi các container báo `healthy`:

| Dịch vụ | Địa chỉ |
|---|---|
| Website | http://localhost |
| API | http://localhost:8080/api |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Hộp thư (MailHog) | http://localhost:8025 |
| MySQL | `localhost:3307` |
| Redis | `localhost:6380` |

Dừng: `docker compose down` — Xóa sạch cả dữ liệu: `docker compose down -v`

> **Cấu hình database theo ý bạn:** sửa `DB_HOST`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`
> trong `.env`. Không có thông tin kết nối nào bị hardcode trong source.

---

## 2. Tài khoản & dữ liệu mẫu

**Tài khoản quản trị** được tạo tự động lúc khởi động từ `ADMIN_EMAIL` / `ADMIN_PASSWORD` trong
`.env` (chỉ khi `ADMIN_SEED_ENABLED=true` và tài khoản chưa tồn tại). Mật khẩu được hash bằng BCrypt
— không có hash nào được nhúng sẵn trong migration.

**Dữ liệu mẫu THẬT** được gieo vào DB lúc khởi động lần đầu (khi DB trống, `SEED_DEMO_ENABLED=true`):
19 người dùng (1 kiểm duyệt viên + 6 chủ trọ + 12 người thuê — **mỗi tài khoản đúng một vai trò**),
tin đăng kèm ảnh sinh tự động, bình luận, đánh giá, lưu tin, theo dõi, báo cáo, giao dịch thanh
toán + đẩy tin. Đây là dữ liệu ghi thật vào DB (không phải mock ở
frontend), idempotent — chỉ chạy khi DB chưa có tin đăng nào. Tắt bằng `SEED_DEMO_ENABLED=false`.

Tài khoản mẫu để đăng nhập thử:

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Quản trị | `admin@webtro.local` | `ADMIN_PASSWORD` trong `.env` (hiện tại `Admin@12345`) |
| Kiểm duyệt | `moderator@webtro.local` | `Test@1234` |
| Chủ trọ | `landlord1@webtro.local` … `landlord6@webtro.local` | `Test@1234` |
| Người thuê | `tenant1@webtro.local` … `tenant12@webtro.local` | `Test@1234` |

> Muốn gieo lại từ đầu: `docker compose down -v` (xóa DB) rồi `docker compose up --build`.

---

## 3. Cấu trúc thư mục

```
DOANTOTNGHIEP/
├── docs/                                   # Tài liệu phân tích & thiết kế
│   ├── PHAN_TICH_NGHIEP_VU_...md           #   Nghiệp vụ gốc (đầu vào)
│   ├── 00_CANONICAL_DECISIONS.md           #   Hợp đồng kỹ thuật — nguồn sự thật
│   ├── 01_KIEN_TRUC_HE_THONG.md            #   Thiết kế kiến trúc
│   ├── 02_THIET_KE_DATABASE.md             #   Thiết kế database
│   ├── 03_THIET_KE_API.md                  #   Thiết kế API
│   ├── 04_THIET_KE_GIAO_DIEN.md            #   Thiết kế giao diện
│   ├── 05_TONG_KET_TRIEN_KHAI.md           #   Tổng kết & hướng dẫn vận hành
│   ├── 06_PHAN_CONG_REVIEW_HE_THONG.md     #   Phân công review 3 người
│   └── 07_BAN_GIAO_SESSION.md              #   Bàn giao trạng thái dự án
├── backend_webtro/                         # Spring Boot API
├── frontend_webtro/                        # ReactJS SPA
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 4. Phát triển cục bộ (không dùng Docker cho app)

```bash
# Chỉ dựng hạ tầng
docker compose up -d mysql redis mailhog

# Backend  (cần JDK 21 + Maven)
cd backend_webtro
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend (cần Node 20+)
cd frontend_webtro
npm install
npm run dev            # http://localhost:5173
```

Ở profile `dev`, backend đọc `DB_HOST=localhost`, `DB_PORT=3306`. Nếu bạn dùng MySQL trong
compose (đang publish ra cổng `3307`), hãy đặt `DB_PORT=3307` khi chạy.

---

## 5. Nguyên tắc kỹ thuật

- **Không hardcode**: mọi thông tin hạ tầng đọc từ biến môi trường; mọi ngưỡng nghiệp vụ
  (thời hạn tin, ngưỡng cảnh báo AI, trọng số điểm uy tín…) nằm trong bảng `system_configs`,
  sửa được qua giao diện Admin lúc chạy.
- **Schema thuộc quyền Flyway**: `ddl-auto=validate`, sai lệch entity ↔ migration làm ứng dụng
  fail ngay lúc khởi động thay vì âm thầm hỏng dữ liệu.
- **Xóa mềm toàn hệ thống**: không xóa cứng dữ liệu nghiệp vụ.
- **Phân quyền ở backend**: RBAC role-only, kiểm tra bằng `@PreAuthorize("hasRole/hasAnyRole")`,
  không chỉ ẩn nút ở frontend.
- **AI hỗ trợ, không thay thế kiểm duyệt con người**: AI chỉ đề xuất và cảnh báo, mọi quyết
  định nặng (khóa tin, khóa tài khoản) đều cần Admin/Moderator xác nhận.
