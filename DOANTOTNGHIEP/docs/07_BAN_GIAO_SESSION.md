# 🧭 Bàn giao Session — Website Quảng cáo & Tìm kiếm Phòng trọ

<!-- WEBTRO_ROLE_ONLY_UPDATE_START -->
> **Cập nhật 2026-08-09:** phân quyền hiện hành là **role-only**. Hệ thống không còn entity/repository/bảng nghiệp vụ `permissions` hay `role_permissions`; Flyway `V15__drop_permission_tables.sql` drop hai bảng này sau các migration lịch sử. Backend kiểm tra bằng `@PreAuthorize("hasRole/hasAnyRole")` và `SecurityUtils.hasRole/hasAnyRole`; JWT chỉ chứa `role`. Tenant được phép tạo tin nhưng service chỉ chấp nhận `categoryCode = ROOMMATE`; Landlord/Admin tạo được mọi loại tin. Access token 15 phút, refresh token 1 ngày, cả hai lưu `localStorage`; khi refresh token còn dưới 15 phút và access token vẫn còn hạn, frontend chủ động gọi `/api/auth/refresh` để xoay refresh token.
<!-- WEBTRO_ROLE_ONLY_UPDATE_END -->

> **Mục đích file này:** ghi lại **thật chi tiết & tường minh** toàn bộ những gì đã trao đổi và thực hiện trong session làm việc, để **session mới đọc file này là hiểu ngay luồng dự án, trạng thái hiện tại, các quyết định kỹ thuật, những bẫy (gotcha) và việc còn lại** — không cần hỏi lại từ đầu.
>
> **Cập nhật lần cuối:** 2026-08-05. Người dùng: `nguyenxuanhoa8b@gmail.com`, branch `main`.

---

## 0. Đọc nhanh (TL;DR)

- Đây là **đồ án tốt nghiệp**: website trung gian kết nối **chủ trọ ↔ người thuê** (đăng tin, tìm kiếm, lưu tin, liên hệ, chat, bình luận, đánh giá, báo cáo, thanh toán đẩy tin, kiểm duyệt) + **4 module AI** (cảm xúc, gợi ý, chatbot, dự đoán giá).
- **Stack:** Spring Boot 3.3.5 / Java 21 (backend) · React 18 + Vite + MUI v5 (frontend) · MySQL 8.4 · Redis 7.4 · MailHog · Docker Compose (5 service).
- **Hệ thống ĐANG CHẠY ĐƯỢC & đã verify end-to-end.** Toàn bộ 90 mã chức năng đạt **90/90**. Cả 4 AI đều gọi được thật.
- **Session này đã hoàn thành 4 yêu cầu của người dùng** + phát hiện/sửa vài bug + tạo 1 tài liệu phân công review.
- **Bẫy quan trọng nhất** (đọc mục 7 trước khi làm gì): **không có JDK local → compile qua Docker maven**; dùng **PowerShell** cho lệnh docker có mount (Bash mangle path); PowerShell 5.1 **mangle tiếng Việt trong JSON** → gửi UTF-8 bytes; đăng nhập dùng field **`emailOrPhone`**; phân trang trả về **`items`** không phải `content`; đổi migration/seed phải **`docker compose down -v`**.

---

## 1. Bối cảnh & mục tiêu dự án

Xây dựng **hoàn chỉnh, production-ready** một website tìm phòng trọ dựa trên tài liệu nghiệp vụ tiếng Việt `docs/PHAN_TICH_NGHIEP_VU_WEBSITE_PHONG_TRO.md` (2224 dòng, 90 mã chức năng).

Ràng buộc gốc của người dùng:
- Backend Spring Boot 3.x + Java 21 + Maven — **KHÔNG dùng MapStruct** (mapper builder thủ công).
- Frontend ReactJS + Vite + JavaScript + MUI.
- MySQL · JWT (access + refresh) · Swagger · Docker (`docker compose up --build` chạy được ngay).
- Clean Architecture, SOLID, **không hardcode, không TODO/demo/stub** — cài đặt đầy đủ mọi tính năng.
- Có tài liệu thiết kế (.md) rồi mới code.
- **Thư mục gốc:** `DOANTOTNGHIEP/` gồm `backend_webtro/`, `frontend_webtro/`, `docker-compose.yml`.

---

## 2. Cấu trúc dự án (đường dẫn tuyệt đối)

**Gốc làm việc:** `c:\Users\NguyenXuanHoa\Desktop\Workspace_D\DoAnTotNghiep\DOANTOTNGHIEP`

```
DOANTOTNGHIEP/
├── docker-compose.yml            # 5 service, mọi giá trị đọc từ .env
├── .env  /  .env.example         # biến môi trường (secret ở đây)
├── README.md                     # có bảng tài khoản demo + ghi chú phạm vi Hà Nội
├── backend_webtro/
│   ├── Dockerfile                # multi-stage maven build
│   └── src/main/
│       ├── java/com/webtro/
│       │   ├── config/           # AppProperties, DemoDataInitializer, AdminAccountInitializer...
│       │   ├── common/           # event/, enums/, response (ApiResponse, PageResponse)...
│       │   ├── storage/          # LocalFileStorage, FileController (phục vụ /api/files/**)
│       │   ├── scheduler/        # 10 job @Scheduled
│       │   └── modules/          # auth, user, listing, search, catalog, interaction,
│       │       │                 #   moderation, payment, notification, ai, admin
│       │       └── <module>/     # controller / service / repository / entity / dto / spi / listener / mapper
│       └── resources/
│           ├── application.yml    # ★ FILE CẤU HÌNH DUY NHẤT (xem mục 4)
│           ├── db/migration/      # Flyway V1..V12
│           └── templates/mail/    # email HTML (Thymeleaf)
├── frontend_webtro/
│   ├── Dockerfile                # build Vite -> nginx
│   └── src/
│       ├── api/                  # authApi, userApi, listingApi, searchApi, catalogApi,
│       │                         #   interactionApi, paymentApi, aiApi, adminApi
│       ├── pages/                # auth/, public/, tenant/, landlord/, admin/
│       ├── routes/router.jsx     # lazy-load + RoleRoute
│       └── components/           # NotificationBell, chatbot/ChatbotWidget...
└── docs/
    ├── 00_CANONICAL_DECISIONS.md          # "canonical v3" — nguồn chân lý (45 bảng, 10 job)
    ├── 01_KIEN_TRUC_HE_THONG.md
    ├── 02_THIET_KE_DATABASE.md
    ├── 03_THIET_KE_API.md
    ├── 04_THIET_KE_GIAO_DIEN.md
    ├── 05_TONG_KET_TRIEN_KHAI.md
    ├── PHAN_TICH_NGHIEP_VU_WEBSITE_PHONG_TRO.md   # ★ tài liệu nghiệp vụ gốc (90 mã chức năng)
    ├── 06_PHAN_CONG_REVIEW_HE_THONG.md            # ★ TẠO TRONG SESSION NÀY (xem mục 6.6)
    └── 07_BAN_GIAO_SESSION.md                      # ★ CHÍNH FILE NÀY
```

**Module backend (`com.webtro.modules.*`):** `auth`, `user`, `listing`, `search`, `catalog`, `interaction`, `moderation`, `payment`, `notification`, `ai`, `admin`. Giao tiếp chéo module qua **SPI Gateway** (`ListingGateway`, `UserGateway`...) và **ApplicationEvent** (`@TransactionalEventListener` AFTER_COMMIT).

---

## 3. Docker Compose — 5 service

| Service | Image | Cổng host→container | Ghi chú |
|---|---|---|---|
| `webtro-mysql` | mysql:8.4 | `3307→3306` | utf8mb4, ngram_token_size=2 (FULLTEXT tiếng Việt) |
| `webtro-redis` | redis:7.4-alpine | `6380→6379` | cache / rate-limit / JWT blacklist, requirepass |
| `webtro-mailhog` | mailhog:v1.0.1 | `1025` (SMTP) · `8025` (UI) | xem mail dev tại http://localhost:8025 |
| `webtro-backend` | build ./backend_webtro | `8080→8080` | Swagger `http://localhost:8080/swagger-ui.html` |
| `webtro-frontend` | build ./frontend_webtro | `80→80` | nginx, reverse-proxy `/api` sang backend |

Healthcheck frontend đã sửa dùng `http://127.0.0.1/healthz` (tránh IPv6 `::1`).

---

## 4. YÊU CẦU 1 — Chỉ 1 file cấu hình YAML ✅ (đã xong session này)

Người dùng: *"Tôi chỉ làm với 1 file cấu hình yml thôi (code, fixbug, deploy đều trên file đó)"*.

- **Chỉ còn** `backend_webtro/src/main/resources/application.yml`.
- **Đã XÓA** `application-dev.yml`, `application-docker.yml`, `application-prod.yml`.
- Đã bỏ `spring.profiles.active` trong `application.yml` và bỏ `SPRING_PROFILES_ACTIVE` trong `docker-compose.yml`.
- Cơ chế: mọi giá trị hạ tầng đọc từ **biến môi trường** dạng `${VAR:mặc-định}`. `docker-compose.yml` truyền biến vào để deploy. Muốn đổi hành vi (Swagger, seed, log, rate-limit...) chỉ đổi biến môi trường, **không sửa file yml**. Secret (`JWT_SECRET`, `PAYMENT_CALLBACK_SECRET`, `ADMIN_PASSWORD`) **không có default** → fail-fast nếu thiếu.

> Ngưỡng nghiệp vụ (thời hạn tin, ngưỡng AI, điểm uy tín...) **KHÔNG** nằm trong yml — chúng ở bảng `system_configs`, Admin sửa runtime qua giao diện.

---

## 5. YÊU CẦU 2 — Phạm vi Hà Nội ✅ (đã xong session này)

Người dùng: *"Phạm vi web trong khu vực Hà Nội"*.

- File `db/migration/V4__seed_administrative_units.sql` đã viết lại: **chỉ seed 1 tỉnh (Thành phố Hà Nội, id=1) · 12 quận nội thành (id 101–112) · 62 phường**.
  - Districts: Ba Đình/001, Hoàn Kiếm/002, Tây Hồ/003, Long Biên/004, Cầu Giấy/005, Đống Đa/006, Hai Bà Trưng/007, Hoàng Mai/008, Thanh Xuân/009, Nam Từ Liêm/019, Bắc Từ Liêm/021, Hà Đông/268.
  - Ward id = `districtId*100 + index` (vd 10101). Ward code = string(ward id). District/ward **code và slug đều UNIQUE toàn cục**; ward slug = `<district-slug>-<ward-slug>` nên luôn duy nhất.
  - Sinh bằng script `scratchpad/gen_v4_hanoi.py` (in ra "V4 Ha Noi: 1 tinh, 12 quan, 62 phuong").
- **Danh mục tự chặn phạm vi:** UI chỉ chọn được địa giới Hà Nội → không đăng/tìm tin ngoài Hà Nội.
- README đã thêm blockquote ghi rõ phạm vi Hà Nội.
- **Verify live:** `GET /api/provinces` → 1 tỉnh; `GET /api/provinces/1/districts` → 12 quận.

---

## 6. Những gì ĐÃ LÀM trong session này (chi tiết)

### 6.1. Sửa lỗi AI dự đoán giá (AI-06)
- **Bug:** `ComparableHedonicPriceEstimator.ESTIMATOR_VERSION` = `"comparable-hedonic-1.0"` (22 ký tự) > cột `prediction_histories.estimator_version VARCHAR(20)` → **lỗi 500 "Data too long"**.
- **Fix:** đổi thành `"hedonic-1.0"` (11 ký tự). Đồng thời rút gọn `RuleBasedChatbotEngine.ENGINE_VERSION` → `"chatbot-rule-1.0"`.

### 6.2. Gieo cụm dữ liệu "Phòng trọ" dày để AI-06 đủ mẫu
- **Vấn đề:** sau khi thu hẹp về Hà Nội, dữ liệu quá thưa (~2 tin/quận) → AI-06 luôn trả `INSUFFICIENT_DATA` (cần ≥ `AI_PRICE_MIN_SAMPLES` = 8 tin tương đương cùng loại + diện tích gần nhau + đang ACTIVE/CLOSED).
- **Fix:** trong `config/DemoDataInitializer.java`, sau vòng lặp 28 tin curated, thêm vòng lặp gieo **24 tin BOARDING_HOUSE (Phòng trọ) ACTIVE**: 10 tin dồn vào **1 phường trọng điểm (Cầu Giấy)** với diện tích sát nhau (20–26m²) để đủ mẫu ở **phạm vi PHƯỜNG**, + 14 tin rải toàn thành phố. Tổng tin published sau seed = **46**.
- Logic tìm comparable ở `PriceEstimationServiceImpl`: nới dần **WARD → DISTRICT → PROVINCE** cho tới khi đủ `minSamples`.

### 6.3. Xác minh CÂU HỎI 3 — Đủ 4 AI chưa? → **RỒI**, đều gọi được (verify thật)
4 AI đều **rule-based in-process** (không gọi dịch vụ ngoài). Endpoint + quyền:

| AI | Endpoint | Quyền | Kết quả verify |
|---|---|---|---|
| AI-01 Cảm xúc | `POST /api/ai/sentiment/analyze` | `AI_LOG_VIEW` | 200 · `POSITIVE` 0.71–0.73 |
| AI-04 Gợi ý | `POST /api/ai/recommendations` (body cần `source`: HOMEPAGE/SIMILAR_LISTING/AFTER_FAVORITE/LOW_RESULT_SEARCH/CHATBOT/NOTIFICATION) · `GET /api/listings/suggested` | isAuthenticated | 200 · trả tin |
| AI-05 Chatbot | `POST /api/ai/chatbot/message` (body `message`; nếu ẩn danh cần `sessionId` UUID v4) | isAuthenticated | 200 · `intent=FIND_ROOM` |
| AI-06 Dự đoán giá | `POST /api/ai/price-prediction` (body: categoryId, provinceId, districtId, wardId, area, roomCount, toiletCount, furnitureStatus, toiletType, curfewType, isStreetFront, inputPrice) | `LISTING_CREATE` | 200 · **3.481.846đ**, samples=10, scope=WARD |

### 6.4. Xác minh CÂU HỎI 4 — Đã phát triển hết tính năng chưa? → **90/90** ✅
- Đã dùng 1 agent rà soát **toàn bộ 90 mã chức năng** (AUTH/USER/LIST/SRCH/FAV/CONT/CMT/REV/RPT/PAY/NOTI/AI/ADM) đối chiếu 45 controller + 10 job + engine AI + FE pages.
- Kết quả ban đầu: **89 DONE, 1 PARTIAL** (NOTI-04).

### 6.5. Sửa NOTI-04 + phát hiện bug tiềm ẩn FollowListener
- **NOTI-04** ("Thông báo có người liên hệ"): sự kiện `ContactCreatedEvent` được phát (bởi `ContactServiceImpl.logContact` và `ConversationServiceImpl`) nhưng **không có listener nào tiêu thụ** → chủ trọ không được báo.
- **Fix:** tạo mới `modules/interaction/listener/ContactNotificationListener.java` — `@TransactionalEventListener(AFTER_COMMIT)` gọi `notificationService.notifyUser(ownerId, NEW_CONTACT, ...)` (in-app + email vì NEW_CONTACT thuộc `EMAIL_DEFAULT_ON`).
- **BUG TIỀM ẨN phát hiện thêm:** listener ban đầu để `@Transactional(readOnly = true)` (copy từ `FollowListener`). Vì `notifyUser` là `@Transactional` (REQUIRED) nên **nhập vào** transaction read-only của listener → MySQL từ chối INSERT: *"Connection is read-only. Queries leading to data modification are not allowed"*. **`FollowListener` mắc ĐÚNG cùng lỗi** (thông báo `FOLLOWED_LANDLORD_NEW_LISTING` cũng âm thầm fail).
- **Fix:** đổi cả `ContactNotificationListener` và `user/listener/FollowListener` thành `@Transactional(propagation = Propagation.REQUIRES_NEW)` (bỏ `readOnly = true`).
- **Verify:** người thuê liên hệ tin của landlord1 → landlord1 nhận **thông báo in-app (count +1)** + **email trong MailHog**. → **90/90**.

### 6.6. Tạo tài liệu phân công review — `docs/06_PHAN_CONG_REVIEW_HE_THONG.md`
- Người dùng yêu cầu chia hệ thống thành **3 phần cân bằng** cho 3 người *review* (không phải code mới): kiểm tra luồng, nghiệp vụ, API, dữ liệu DB→BE→API→FE, tìm bug, đề xuất sửa.
- Đã tạo file **3.319 dòng / ~420 KB**, viết từ **source thật** (dùng workflow 3 agent song song, mỗi agent 1 cụm):
  - **Người 1** (29 mã): Auth/RBAC · User/Follow · Notification · Moderation người dùng · Admin console người dùng (Dashboard/Users/Landlords/Statistics/Audit/BannedKeyword).
  - **Người 2** (30 mã): Listing lifecycle · Search/Filter · Catalog địa giới · AI Recommend/Price/TrustScore · Admin Listing/Moderation Queue · Landlord Dashboard.
  - **Người 3** (31 mã): Favorite/History · Contact/Chat · Comment/Review · Payment/Promotion · AI Sentiment/Chatbot/Log/Config · System Config.
- Mỗi module có đủ **12 mục** (mục đích → FE → BE → luồng Mermaid → dòng chảy dữ liệu → DB → bảng API → dependency → checklist → lỗi dễ gặp → điểm review → kết quả mong đợi). Có bảng **Module dùng chung**, **Bản đồ phụ thuộc**, **Sơ đồ quy trình review**, **mẫu ghi bug**.
- File có **18 chỗ đánh dấu "Cần bổ sung theo source code"** (những điểm agent chưa chắc chắn — người review cần tự xác minh).

### 6.7. (Từ trước session này, đã có) — nền tảng đã hoàn thiện
- `storage/FileController.java` phục vụ ảnh `/api/files/**` (ảnh lưu ngoài webroot).
- `AuditLogServiceImpl.toJson()` bọc chuỗi thường thành JSON hợp lệ trước khi lưu cột JSON (`old_value`/`new_value`).
- `DemoDataInitializer` gieo dữ liệu **thật** (idempotent, guard `seed.demo-enabled && listingRepository.count()==0`): 19 user (1 moderator + chủ trọ + người thuê), tin đăng + **ảnh JPEG thật**, bình luận, đánh giá, lưu tin, lượt xem, theo dõi, báo cáo, thông báo, thanh toán+đẩy tin.

---

## 7. ⚠️ CÁC BẪY (GOTCHA) — ĐỌC TRƯỚC KHI THAO TÁC

> Đây là những điều gây mất thời gian nhất. Session mới PHẢI nắm.

1. **KHÔNG có JDK/Maven local.** Compile backend qua Docker maven container:
   ```powershell
   # Chạy trong PowerShell, tại thư mục backend_webtro
   docker run --rm -v "${PWD}:/app" -v webtro_m2_cache:/root/.m2 -w /app `
     maven:3.9-eclipse-temurin-21 mvn -B -ntp compile -DskipTests
   ```
   Volume `webtro_m2_cache` giữ cache `.m2` để lần sau nhanh.
2. **Dùng PowerShell cho lệnh docker có `-v` mount, KHÔNG dùng Bash.** Git Bash (MSYS) mangle path: `-w /app` biến thành `C:/Program Files/Git/app`.
3. **PowerShell 5.1 mangle tiếng Việt trong body JSON** (gây lỗi 400 "Nội dung request không đọc được"). Phải gửi **UTF-8 bytes**:
   ```powershell
   $body = @{ text='Phòng đẹp' } | ConvertTo-Json -Compress
   $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
   Invoke-WebRequest $url -Method POST -ContentType 'application/json; charset=utf-8' -Body $bytes ...
   ```
4. **Đăng nhập:** body dùng field **`emailOrPhone`** (KHÔNG phải `email`). Nếu để `email` → 400 validation. Phản hồi trả `user.role` là **chuỗi** (không phải mảng `roles`).
5. **Phân trang:** danh sách nằm ở **`data.items`** (KHÔNG phải `data.content`). `PageResponse { items, page, size, totalElements, totalPages, first, last }`.
6. **Đổi migration hoặc logic seed → phải `docker compose down -v`.** Lý do: (a) Flyway checksum sẽ fail nếu sửa file `Vx` đã chạy; (b) seeder chỉ chạy khi `listingRepository.count()==0`.
7. **Docker Desktop tự tắt khi máy nghỉ.** Khởi động lại:
   ```powershell
   Start-Process "C:\Program Files\Docker\Docker\Docker Desktop.exe"
   # rồi chờ: docker version --format '{{.Server.Version}}'
   ```
8. **MySQL không hỗ trợ CHECK constraint đầy đủ** → nhiều ràng buộc xử lý ở tầng ứng dụng. Hibernate `ddl-auto=validate` (schema thuộc Flyway).
9. **Console PowerShell hiển thị tiếng Việt bị mojibake** (vd "ThÃ nh phá»‘") — chỉ là lỗi hiển thị console, **dữ liệu UTF-8 lưu vẫn đúng**.

---

## 8. Cách chạy & verify (lệnh cụ thể)

### 8.1. Chạy toàn hệ thống
```powershell
cd "c:\Users\NguyenXuanHoa\Desktop\Workspace_D\DoAnTotNghiep\DOANTOTNGHIEP"
docker compose up --build -d              # lần đầu / sau khi sửa code
# Rebuild sạch (khi đổi migration/seed):
docker compose down -v ; docker compose up --build -d
# Rebuild CHỈ backend, GIỮ DB (khi chỉ sửa code Java):
docker compose up -d --build backend
```
Backend healthy khi `GET http://localhost:8080/actuator/health` → `{"status":"UP"}`.

### 8.2. Tài khoản demo
| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin | `admin@webtro.local` | `Admin@12345` (từ `.env` `ADMIN_PASSWORD`) |
| Chủ trọ | `landlord1@webtro.local` … `landlordN@` | `Test@1234` |
| Người thuê | `tenant1@webtro.local` … `tenantN@` | `Test@1234` |
| Moderator | `moderator@webtro.local` | `Test@1234` |

### 8.3. Kịch bản verify AI + scope + NOTI-04 (PowerShell, đã dùng thật)
```powershell
$base="http://localhost:8080/api"; $enc=[System.Text.Encoding]::UTF8
function Login($id,$pw){ (Invoke-RestMethod "$base/auth/login" -Method POST -ContentType 'application/json' -Body (@{emailOrPhone=$id;password=$pw}|ConvertTo-Json)).data.accessToken }
function PostU($url,$tok,$obj){ $b=$enc.GetBytes(($obj|ConvertTo-Json -Compress));
  Invoke-WebRequest $url -Method POST -ContentType 'application/json; charset=utf-8' -Headers @{Authorization="Bearer $tok"} -Body $b -UseBasicParsing }
# provinces=1, districts=12, price 200 tại Cầu Giấy (district code '005'), recommend/sentiment/chatbot 200...
```
**Kết quả verify cuối cùng (hệ thống đang chạy):**
```
[SCOPE ] provinces=1  districts=12  published_listings=46
[AI-06 ] PRICE      200  3.481.846đ  samples=10  scope=WARD
[AI-04 ] RECOMMEND  200
[AI-01 ] SENTIMENT  200  POSITIVE 0.71
[AI-05 ] CHATBOT    200  intent=FIND_ROOM
[NOTI-04] contact → in-app +1 & email (MailHog) ✓
5 container healthy
```

---

## 9. Kiến trúc & quy ước quan trọng (session mới cần nhớ)

- **Envelope response:** `ApiResponse { success, message, data, errorCode, timestamp, path, traceId }`.
- **Phân trang:** `PageResponse { items, ... }` (xem bẫy #5).
- **JWT:** access 15 phút + refresh 1 ngày (rotation + reuse detection + `family_id` + Redis blacklist). **Cả hai token nằm ở `localStorage` phía client** (`webtro_access_token` / `webtro_refresh_token`); backend KHÔNG đặt cookie nào. Claim JWT chỉ còn `role` (chuỗi đơn), không có `permissions[]`.
- **Phân quyền role-only:** 4 vai trò; `@PreAuthorize("hasRole/hasAnyRole")`. **Mỗi user đúng MỘT vai trò** (`users.role_id`, NOT NULL) — không có bảng `user_roles`, không có cấp quyền riêng cho từng người.
- **State machine tin đăng:** `ListingStateMachine` là **cổng DUY NHẤT** đổi trạng thái (DRAFT/PENDING/ACTIVE/REJECTED/HIDDEN/EXPIRED/CLOSED/LOCKED/NEED_REVIEW/DELETED).
- **Sự kiện AI/notify:** `@TransactionalEventListener(phase = AFTER_COMMIT)`; listener có GHI dữ liệu **PHẢI** `@Transactional(propagation = REQUIRES_NEW)` và **KHÔNG** `readOnly=true` (xem bug 6.5).
- **Giao tiếp chéo module:** qua **SPI Gateway** (adapter hexagonal) + ApplicationEvent — module này không phụ thuộc trực tiếp entity module kia.
- **Mapper:** builder thủ công, không MapStruct.
- **Cấu hình runtime:** ngưỡng nghiệp vụ ở bảng `system_configs` (Admin sửa được), không phải `application.yml`.
- **4 AI** rule-based in-process: `SentimentAnalyzer` (từ điển tiếng Việt), `ContentBasedRecommendationEngine`, `RuleBasedChatbotEngine` (intent+slot), `ComparableHedonicPriceEstimator` (so sánh + hedonic).
- **10 job `@Scheduled`:** DataRetention, ListingExpiry, ListingExpiryReminder, NewMatchingListingNotify, PaymentReconcile, PromotionExpiry, RecommendationPrecompute, SentimentRetry, TokenCleanup, TrustScoreRecalc.
- **Flyway V1..V15:** V1 baseline schema · V2 roles/permissions · V3 catalog · **V4 địa giới Hà Nội** · V5 system_configs · V6 banned_keywords · V7 promotion_packages · V8 admin note · V9 amenity groups note · V10 fulltext index · V11/V12 mở rộng enum audit/notification/moderation · **V13 chuyển user↔role sang `users.role_id` và bỏ bảng `user_roles`** · **V14 xóa 2 config gây khác quyền giữa người cùng vai trò** · **V15 drop `role_permissions` và `permissions`**.

---

## 10. File đã tạo/sửa trong session này (để tra nhanh)

| File | Loại | Nội dung |
|---|---|---|
| `backend_webtro/src/main/resources/application.yml` | Sửa | Bỏ profile; file cấu hình duy nhất |
| `application-dev/docker/prod.yml` | **Xóa** | Gộp về 1 file |
| `docker-compose.yml` | Sửa | Bỏ `SPRING_PROFILES_ACTIVE` |
| `db/migration/V4__seed_administrative_units.sql` | Viết lại | Chỉ Hà Nội (1 tỉnh/12 quận/62 phường) |
| `modules/ai/engine/ComparableHedonicPriceEstimator.java` | Sửa | `ESTIMATOR_VERSION="hedonic-1.0"` |
| `modules/ai/engine/RuleBasedChatbotEngine.java` | Sửa | `ENGINE_VERSION="chatbot-rule-1.0"` |
| `config/DemoDataInitializer.java` | Sửa | Thêm cụm 24 tin Phòng trọ dày (AI-06 đủ mẫu) |
| `modules/interaction/listener/ContactNotificationListener.java` | **Tạo mới** | NOTI-04: báo chủ trọ khi có liên hệ |
| `modules/user/listener/FollowListener.java` | Sửa | Bỏ `readOnly=true` (bug INSERT read-only) |
| `README.md` | Sửa | Ghi chú phạm vi Hà Nội |
| `docs/06_PHAN_CONG_REVIEW_HE_THONG.md` | **Tạo mới** | Tài liệu phân công review 3 người |
| `docs/07_BAN_GIAO_SESSION.md` | **Tạo mới** | Chính file này |

> Lưu ý: các thay đổi trên **đã build & verify chạy được**, nhưng **CHƯA commit git** (branch `main`, working tree có thay đổi). Session mới cân nhắc commit khi người dùng yêu cầu.

---

## 10b. SESSION 2026-08-05 — Ba thay đổi về phân quyền & phiên đăng nhập

Yêu cầu của người dùng: (1) mỗi user chỉ có duy nhất 1 role; (2) hai người cùng role phải dùng
được đúng cùng bộ chức năng; (3) refresh token và access token lưu ở `localStorage`.

Chi tiết đầy đủ ở **`docs/00_CANONICAL_DECISIONS.md` mục 17**. Tóm tắt để tra nhanh:

### Mô hình vai trò
- Bảng nối `user_roles` **đã bị bỏ** (V13). Vai trò nằm ở **`users.role_id`** (NOT NULL, FK).
- `RoleRepository.findRoleCodesByUserId` → **`findRoleCodeByUserId`** trả `Optional<String>`.
- Entity `UserRole` và `UserRoleRepository` **đã xóa** — đừng tìm nữa.
- Đăng ký: chọn chủ trọ → **chỉ** `ROLE_LANDLORD` (không kèm `ROLE_TENANT` như trước). An toàn vì
  ma trận quyền có `ROLE_LANDLORD ⊇ ROLE_TENANT`.
- API đổi vai trò: **`PUT /api/admin/users/{id}/role`** body `{role, reason}` (đường cũ `/roles`
  với `{roles[], reason}` đã bỏ, gọi vào sẽ 404).
- DTO: `roles: string[]` → **`role: string`** ở mọi response; `UserActionResponse` dùng
  `previousRole` / `role`.
- JWT: claim `roles[]` → **`role`** (chuỗi). Token phát hành trước V13 sẽ mất authority (cố ý —
  fail rõ ràng thay vì lỗi ép kiểu ngầm).

### Cùng vai trò = cùng chức năng
Đã gỡ 5 cổng chặn theo từng người trong `ListingServiceImpl` và `UserServiceImpl`: hạn mức riêng
cho tài khoản mới, cấm đăng vĩnh viễn theo `warning_count`, tự duyệt tin theo uy tín/xác minh,
chặn theo `email_verified_at`, và `isLandlord = role || có hồ sơ chủ trọ`.
**Giữ nguyên** (không phải vi phạm): kiểm tra quyền sở hữu dữ liệu, chế tài **có thời hạn**
(`posting_restricted_until`), trạng thái `LOCKED`, hạn mức chu kỳ áp dụng bằng nhau cho mọi người.

### Token
- Backend **không đặt cookie nào**. `AuthController` đã gỡ hết `@CookieValue` / `Set-Cookie`.
- FE lưu `webtro_access_token` + `webtro_refresh_token` trong `localStorage`
  (`services/tokenService.js`).
- `POST /auth/refresh` **bắt buộc** có `refreshToken` trong body (`@NotBlank`).
- `ChangePasswordRequest` **thêm** trường `refreshToken` (không bắt buộc) — thiếu nó thì người dùng
  bị đăng xuất khỏi chính thiết bị đang đổi mật khẩu, và lỗi này **âm thầm** không ném ngoại lệ.
- ⚠ **Bẫy dễ vỡ nhất:** sau mỗi lần `/auth/refresh` phải ghi đè **CẢ HAI** token. Gửi lại refresh
  token cũ ở lần sau → reuse detection thu hồi cả họ token → mất phiên. Lỗi chỉ lộ ở lần refresh
  thứ hai.

### Hai lỗi có sẵn phát hiện và sửa kèm
1. `POST /api/auth/register` **luôn trả 400** — FE gửi `role: 'ROLE_TENANT'` trong khi BE đòi
   `requestedRole` (enum `TENANT|LANDLORD`), `confirmPassword`, `acceptTerms`. Đã sửa payload FE.
2. Hộp thoại đổi vai trò ở màn quản trị **luôn trả 400** — không có ô "Lý do" trong khi `reason`
   là bắt buộc (10–500 ký tự). Đã thêm ô nhập + kiểm tra độ dài.

### Đã kiểm chứng trên hệ thống đang chạy (2026-08-05)
```
14 migration ap dung, schema v14 · 45 bang nghiep vu · users.role_id NOT NULL + FK
20/20 nguoi dung dung 1 vai tro (ADMIN 1 · MODERATOR 1 · LANDLORD 6 · TENANT 12)
6 chu tro -> 1 bo quyen duy nhat (8) · 4 nguoi thue -> 1 bo quyen duy nhat (5)
login khong co Set-Cookie · claim JWT `role` la chuoi
refresh xoay vong 2 lan lien tiep OK · dung lai token cu -> 401 · thieu token -> 400
doi mat khau: phien hien tai con song
dang ky TENANT/LANDLORD -> 201, dung 1 vai tro trong DB
PUT /admin/users/{id}/role -> 200 · /roles cu -> 404 · vai tro la -> 422
ma tran quyen 4 vai tro x 7 endpoint: dung theo V2 seed
frontend + reverse proxy /api: 200 · 5 container healthy
```

---

## 11. Việc còn lại / gợi ý bước tiếp theo

- [ ] **Commit** các thay đổi session này (khi người dùng đồng ý) — nên tạo branch thay vì commit thẳng `main`.
- [ ] **Xác minh 18 chỗ "Cần bổ sung theo source code"** trong `06_PHAN_CONG_REVIEW_HE_THONG.md` (agent chưa chắc chắn) — đối chiếu source, điền chính xác.
- [ ] **Verify end-to-end trên FRONTEND** (session này chủ yếu verify backend qua API): đăng nhập UI, đăng tin, tìm kiếm, chatbot widget, ước giá, thông báo liên hệ hiển thị trên `NotificationBell`.
- [ ] **Verify FollowListener** sau khi sửa bug: theo dõi chủ trọ → chủ trọ đăng tin mới được duyệt → follower nhận `FOLLOWED_LANDLORD_NEW_LISTING` (in-app + email).
- [ ] Cân nhắc cập nhật `docs/05_TONG_KET_TRIEN_KHAI.md` phản ánh: single-config, phạm vi Hà Nội, cụm dữ liệu AI, NOTI-04, 90/90.
- [ ] (Tùy chọn) Rà soát các listener/service khác xem còn `@Transactional(readOnly=true)` bao quanh thao tác GHI không (đã kiểm: chỉ 2 listener dính, đã sửa; `CommentSentimentListener` không dính).

---

## 12. Cách tiếp tục trong SESSION MỚI

1. Mở dự án tại `c:\Users\NguyenXuanHoa\Desktop\Workspace_D\DoAnTotNghiep\DOANTOTNGHIEP`.
2. Đọc file này (`docs/07_BAN_GIAO_SESSION.md`) + `docs/06_PHAN_CONG_REVIEW_HE_THONG.md` để nắm luồng.
3. Bảo đảm Docker Desktop chạy (bẫy #7), rồi `docker compose up -d` (hoặc `--build` nếu vừa sửa code).
4. Chờ backend `UP`, dùng kịch bản verify ở **mục 8.3** để chắc chắn hệ thống ổn.
5. Khi sửa code Java: compile qua Docker maven (mục 8.1), rồi `docker compose up -d --build backend` (giữ DB). Khi đổi migration/seed: `down -v` trước.
6. Tuân thủ mọi **bẫy ở mục 7** và **quy ước ở mục 9**.

> **Tóm tắt trạng thái để session mới tự tin bắt đầu:** hệ thống đầy đủ **90/90 chức năng**, **4 AI hoạt động thật**, **phạm vi Hà Nội**, **1 file cấu hình**, đã verify end-to-end ở tầng API và đang chạy 5 container. Việc chính còn lại là *review chất lượng* (theo tài liệu `06`), *xác minh phía FE*, và *commit*.

*— Hết file bàn giao —*
