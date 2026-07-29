# 00 — Quyết định chuẩn (Canonical Decisions)

> **Đây là hợp đồng kỹ thuật của toàn dự án.**
> Mọi tài liệu thiết kế (01→04) và mọi dòng source code trong `backend_webtro/` và
> `frontend_webtro/` **bắt buộc** tuân theo file này. Khi có mâu thuẫn giữa các tài
> liệu, file này là nguồn sự thật duy nhất (single source of truth).
>
> Nguồn nghiệp vụ gốc: `PHAN_TICH_NGHIEP_VU_WEBSITE_PHONG_TRO.md`. Mọi mục có ký hiệu
> `[§x.y]` là tham chiếu trực tiếp tới tài liệu nghiệp vụ đó.

## Lịch sử phiên bản

| Phiên bản | Thay đổi |
|---|---|
| v1 | Bản chốt đầu tiên, viết trước khi soạn 4 tài liệu thiết kế. |
| **v2 (hiện hành)** | Hợp nhất 23 đề xuất `[BỔ SUNG NGOÀI CANONICAL]` từ `01` mục 15.1 và Phụ lục A của `02` sau vòng kiểm toán phủ nghiệp vụ + nhất quán chéo. Xem mục 16 để biết danh sách thay đổi và lý do chấp nhận. |

> **v2 sửa 4 lỗi thực chất của v1** (không phải bổ sung trang trí):
> 1. Công thức recommendation của v1 chỉ có 6 số hạng → **bỏ sót 3/11** mục "Dữ liệu đầu vào"
>    mà `[§9.2]` liệt kê (diện tích quan tâm, số người ở, giới tính ở ghép).
> 2. Công thức uy tín chủ trọ của v1 **không có số hạng nào** cho `[§5.7]` *"Chủ trọ phản hồi
>    người thuê nhanh và đầy đủ"* → một trong 5 sự kiện cập nhật điểm uy tín không có chỗ thực thi.
> 3. v1 **bỏ sót** khả năng tự động ẩn tin theo sentiment mà `[§5.3]` yêu cầu (state machine v1
>    chỉ có `HIDE_BY_OWNER` do LANDLORD thực hiện).
> 4. v1 **không có bảng** `notification_preferences` mà `[§11.12]` yêu cầu (*"có thể tắt một số
>    loại thông báo không quan trọng"*).

---

## 1. Tech stack — phiên bản chốt

### 1.1. Backend

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Java | 21 (LTS) | `maven.compiler.release=21` |
| Spring Boot | 3.3.5 | parent POM |
| Maven | 3.9.x | wrapper `mvnw` được commit |
| MySQL | 8.4 (LTS) | connector `com.mysql:mysql-connector-j` |
| Flyway | quản lý bởi Boot BOM | `flyway-core` + `flyway-mysql` (MySQL 8.4 cần module riêng) |
| Redis | 7.4-alpine | `spring-boot-starter-data-redis` (lettuce) |
| JJWT | 0.12.6 | `jjwt-api`, `jjwt-impl`, `jjwt-jackson` |
| springdoc-openapi | 2.6.0 | `springdoc-openapi-starter-webmvc-ui` (tương thích Boot 3.3.x) |
| Lombok | quản lý bởi Boot BOM | |
| MapStruct | **KHÔNG DÙNG** | Yêu cầu đề bài. Thay bằng mapper thủ công + Builder |
| Mail | `spring-boot-starter-mail` | dev dùng MailHog trong compose |
| Thymeleaf | `spring-boot-starter-thymeleaf` | **chỉ** để render template email HTML, không render web |

**Ràng buộc:** không thêm dependency ngoài danh sách trên trừ khi có lý do nghiệp vụ ghi
trong tài liệu. Rate limiting **tự viết** trên Redis (`INCR` + `EXPIRE`), không dùng bucket4j.

### 1.2. Frontend

| Thành phần | Phiên bản | Ghi chú |
|---|---|---|
| Node | 20-alpine (build image) | |
| React | 18.3.x | JavaScript, **không TypeScript** |
| Vite | 5.x | |
| MUI | 5.x (`@mui/material`, `@mui/icons-material`, `@mui/x-date-pickers`) | Material Design |
| Redux Toolkit | 2.x + `react-redux` 9.x | state toàn cục |
| React Router | 6.x | `createBrowserRouter` |
| Axios | 1.x | instance riêng + interceptor refresh token |
| React Hook Form | 7.x | + `@hookform/resolvers` |
| Yup | 1.x | schema validation |
| react-toastify | 10.x | toast |
| Chart.js + react-chartjs-2 | 4.x / 5.x | dashboard |
| DayJS | 1.x | + locale `vi` |
| jwt-decode | 4.x | |

### 1.3. Hạ tầng

`docker compose up --build` phải dựng đủ: `mysql` → `redis` → `backend` → `frontend` → `mailhog`.
Không hardcode host/user/password ở bất kỳ đâu; toàn bộ qua biến môi trường + file `.env`.

**Cổng mở ra máy host** (mặc định, đổi được qua `.env`):

| Service | Cổng trong mạng Docker | Cổng host | Lý do chọn |
|---|---|---|---|
| `frontend` (nginx) | 80 | **80** | Điểm vào chính: `http://localhost` |
| `backend` | 8080 | **8080** | Swagger cho việc chấm đồ án |
| `mysql` | 3306 | **3307** | Tránh đụng MySQL cài sẵn trên máy (3306) |
| `redis` | 6379 | **6380** | Tránh đụng Redis cài sẵn (6379) |
| `mailhog` | 1025 / 8025 | **1025 / 8025** | SMTP / giao diện xem mail |

Trong mạng Docker, các service gọi nhau bằng **tên service** (`mysql`, `redis`, `backend`),
không bao giờ bằng IP hay `localhost`.

---

## 2. Quy ước đặt tên (bắt buộc)

| Đối tượng | Quy ước | Ví dụ |
|---|---|---|
| Bảng DB | `snake_case`, **số nhiều** | `listings`, `listing_amenities` |
| Cột DB | `snake_case` | `expired_at`, `trust_score` |
| Khóa chính | `id` — `BIGINT UNSIGNED AUTO_INCREMENT` | |
| Khóa ngoại | `<bảng_số_ít>_id` | `listing_id`, `owner_id` |
| Index | `idx_<bảng>_<cột>[_<cột>]` | `idx_listings_status_expired_at` |
| FULLTEXT index | `ft_<bảng>_<cột>[_<cột>]` | `ft_listings_title_description` |
| Unique | `uk_<bảng>_<cột>` | `uk_users_email` |
| Foreign key | `fk_<bảng>_<bảng_đích>` | `fk_listings_users` |
| FK trỏ 2 lần cùng đích | `fk_<bảng>_<bảng_đích>_<vai_trò>` | `fk_follows_users_follower`, `fk_follows_users_landlord` |
| CHECK constraint | `ck_<bảng>_<ý_nghĩa>` | `ck_listings_price_positive` |
| Cột sinh ép unique có điều kiện | hậu tố `<cột>_uk` | `email_uk`, `phone_uk` (xem mục 6.2) |
| Java class | `PascalCase` | `ListingServiceImpl` |
| Enum value | `UPPER_SNAKE_CASE` | `NEED_REVIEW` |
| REST path | `kebab-case`, danh từ số nhiều | `/api/promotion-packages` |
| JSON field | `camelCase` | `trustScore` |
| React component | `PascalCase.jsx` | `ListingCard.jsx` |
| React hook | `useCamelCase.js` | `useAuth.js` |

**Java package gốc:** `com.webtro`

---

## 3. Bản đồ module (Clean Architecture)

Theo kết luận `[§15]`, hệ thống chia thành 9 module nghiệp vụ. Trong backend, mỗi module là
một **package dọc** dưới `com.webtro.modules`, dùng chung `common` + `security` + `config`.

```
com.webtro
├── WebtroApplication.java
├── common/            # ApiResponse, PageResponse, BaseEntity, AuditableEntity, enums dùng chung
│   └── event/         # ApplicationEvent dùng chung (CommentCreatedEvent, ListingApprovedEvent...)
│                      #   -> phương tiện giao tiếp NGƯỢC CHIỀU giữa các module (luật 7)
├── config/            # SecurityConfig, OpenApiConfig, RedisConfig, AsyncConfig, MailConfig,
│                      # JpaAuditingConfig, CorsConfig, WebMvcConfig, SchedulerConfig
├── constant/          # AppConstant, ConfigKey, ErrorCode, PermissionCode, RoleCode, CacheName
├── exception/         # GlobalExceptionHandler + hierarchy nghiệp vụ
├── security/          # JwtService, JwtAuthenticationFilter, CustomUserDetails(Service),
│                      # CurrentUser, PermissionEvaluator, AccessDeniedHandler, EntryPoint
├── filter/            # RateLimitFilter, RequestIdFilter
├── interceptor/       # (đăng ký trong WebMvcConfig)
├── storage/           # FileStorage (interface) + LocalFileStorage (impl duy nhất)
│                      #   -> tách interface NGAY để mở đường cloud storage [§11.6]
├── util/              # SlugUtil, HtmlSanitizer, PhoneUtil, MaskUtil, GeoUtil, TextNormalizer
├── validator/         # @ValidPhone, @ValidPassword, @ValidPriceRange, @NoBannedKeyword ...
├── scheduler/         # 9 job - xem mục 11
└── modules
    ├── auth/          # AUTH-01..08
    ├── user/          # USER-01..06, FOLLOW-01..02
    ├── catalog/       # Category, Province, District, Ward, Amenity  (ADM-05..07)
    ├── listing/       # LIST-01..12
    ├── search/        # SRCH-01..09
    ├── interaction/   # FAV, HIST, CONT, CMT, REV
    ├── moderation/    # RPT-01..06, ModerationAction
    ├── payment/       # PAY-01..06, PROMO-01..02
    ├── notification/  # NOTI-01..06
    ├── ai/            # AI-01..08  (sentiment, recommendation, chatbot, price)
    └── admin/         # ADM-01..14 (dashboard, statistics, systemconfig, auditlog)
```

Mỗi module con có cấu trúc chuẩn (đúng yêu cầu đề bài):

```
modules/<tên>/
├── controller/
├── service/          # interface
├── service/impl/
├── repository/
├── entity/
├── dto/request/
├── dto/response/
├── mapper/
├── listener/         # @EventListener - nhận ApplicationEvent từ module khác (luật 7)
└── specification/    # JPA Specification cho truy vấn lọc động (chỉ module listing/search/admin)
```

Ba package chuyên biệt chỉ xuất hiện ở nơi cần:

| Package | Ở module | Mục đích |
|---|---|---|
| `statemachine/` | `listing` | `ListingStateMachine` — cổng duy nhất đổi `ListingStatus` (mục 5.1) |
| `engine/` | `ai` | 4 interface AI + impl: `SentimentAnalyzer`, `RecommendationEngine`, `ChatbotEngine`, `PriceEstimator` |
| `gateway/` | `payment` | `PaymentGateway` (interface) + `SandboxPaymentGateway` (impl) — cô lập cổng thanh toán ngoài |

**Luật phụ thuộc (bắt buộc, không được vi phạm):**
1. `controller` chỉ gọi `service`, **không bao giờ** gọi `repository`.
2. `controller` **không bao giờ** nhận/trả `entity` — chỉ `dto`.
3. `mapper` là nơi duy nhất chuyển `entity ↔ dto`.
4. Module A gọi module B **chỉ qua interface `service`** của B, không qua `repository` của B.
5. Mọi phương thức ghi dữ liệu đều `@Transactional`; đọc dùng `@Transactional(readOnly = true)`.
6. Không có logic nghiệp vụ trong `controller` và trong `entity`.
7. **Giao tiếp ngược chiều chỉ qua `ApplicationEvent`.** Đồ thị phụ thuộc giữa các module phải là
   DAG. Khi module tầng dưới cần báo cho tầng trên (ví dụ: `interaction` tạo bình luận xong cần
   `ai` chạy sentiment `[§9.1]`), nó **publish event**, không `@Autowired` ngược. Nếu không có luật
   này, `interaction ↔ ai` tạo phụ thuộc vòng và Spring sẽ fail khi khởi tạo bean.
8. **Không `@ManyToOne` trỏ chéo module lên tầng trên.** Quan hệ chéo module giữ bằng khóa ngoại
   dạng `Long` (ví dụ `SentimentResult.commentId`), không map object. Map object sẽ kéo cả cụm
   entity của module khác vào persistence context và làm vỡ ranh giới module.

---

## 4. Role & Permission

### 4.1. Role (4 role người)

`[§1.1]`, `[§6.1]` — quan hệ `User ↔ Role` là **nhiều-nhiều** qua `user_roles`
(vì `[§1.2]`: *"Chủ trọ có toàn bộ quyền cơ bản của người thuê nếu hệ thống dùng chung tài khoản"*).

| Code | Tên hiển thị | Ghi chú |
|---|---|---|
| `ROLE_TENANT` | Người thuê | mặc định khi đăng ký |
| `ROLE_LANDLORD` | Chủ trọ | bao gồm "Người cho ở ghép" |
| `ROLE_MODERATOR` | Kiểm duyệt viên | |
| `ROLE_ADMIN` | Quản trị viên | |

**Quyết định kiến trúc:** "Người cho ở ghép" và "Người cần ở ghép" trong `[§1.1]` **không**
là role riêng. Chúng là ngữ cảnh: người cho ở ghép = `ROLE_LANDLORD` đăng tin
`category = ROOMMATE`; người cần ở ghép = `ROLE_TENANT` tìm tin đó. `[§7.3]` gộp chung
tiêu đề "Chủ trọ / Người cho ở ghép" xác nhận hướng này.
"Khách chưa đăng nhập" là trạng thái ẩn danh, không phải role.

### 4.2. Permission (RBAC 2 tầng: Role → Permission)

Cần thiết vì `[§1.2]` + `[§11.2]` đặt ranh giới tinh vi: *"Moderator chỉ có quyền kiểm duyệt,
không quản lý cấu hình tài chính"*. Kiểm tra ở backend bằng `@PreAuthorize("hasAuthority('...')")`,
**không** chỉ ẩn nút ở frontend `[§11.2]`.

| Permission code | TENANT | LANDLORD | MODERATOR | ADMIN |
|---|:--:|:--:|:--:|:--:|
| `LISTING_CREATE` | | ✔ | | ✔ |
| `LISTING_UPDATE_OWN` | | ✔ | | ✔ |
| `LISTING_UPDATE_ANY` | | | | ✔ |
| `LISTING_MODERATE` (duyệt/từ chối/gắn cờ/tạm ẩn) | | | ✔ | ✔ |
| `LISTING_LOCK` | | | | ✔ |
| `LISTING_VIEW_ANY` (xem cả tin non-public) | | | ✔ | ✔ |
| `FAVORITE_MANAGE` | ✔ | ✔ | | |
| `CONTACT_CREATE` | ✔ | ✔ | | |
| `COMMENT_CREATE` | ✔ | ✔ | | |
| `COMMENT_MODERATE` | | | ✔ | ✔ |
| `REVIEW_CREATE` | ✔ | ✔ | | |
| `REVIEW_MODERATE` | | | ✔ | ✔ |
| `REPORT_CREATE` | ✔ | ✔ | ✔ | ✔ |
| `REPORT_RESOLVE` | | | ✔ | ✔ |
| `WARNING_SEND` | | | ✔ | ✔ |
| `USER_MANAGE` (khóa/mở khóa) | | | | ✔ |
| `USER_ROLE_ASSIGN` | | | | ✔ |
| `LANDLORD_VERIFY` | | | ✔ | ✔ |
| `PAYMENT_VIEW_OWN` | | ✔ | | ✔ |
| `PAYMENT_MANAGE` | | | | ✔ |
| `PACKAGE_MANAGE` | | | | ✔ |
| `CATALOG_MANAGE` | | | | ✔ |
| `AI_CONFIG_MANAGE` | | | | ✔ |
| `AI_LOG_VIEW` | | | ✔ | ✔ |
| `SYSTEM_CONFIG_MANAGE` | | | | ✔ |
| `STATISTIC_VIEW` | | | | ✔ |
| `AUDIT_LOG_VIEW` | | | | ✔ |

> Cột trống = **không có quyền**. `MODERATOR` cố tình **không** có bất kỳ permission nào về
> `PAYMENT`, `PACKAGE`, `SYSTEM_CONFIG`, `USER_ROLE_ASSIGN`, `STATISTIC` — đúng `[§1.2]`.

---

## 5. Enum chuẩn (dùng chung backend + frontend)

Mọi enum lưu DB dưới dạng `VARCHAR` + `@Enumerated(EnumType.STRING)` (không dùng ORDINAL —
ordinal vỡ khi chèn giá trị mới).

```java
UserStatus        : ACTIVE, PENDING_VERIFY, LOCKED, DELETED                    [§6.3]
Gender            : MALE, FEMALE, OTHER, UNKNOWN
ListingStatus     : DRAFT, PENDING, ACTIVE, REJECTED, HIDDEN, EXPIRED,
                    CLOSED, LOCKED, NEED_REVIEW, DELETED                       [§0.4][§5.1]
CategoryCode      : BOARDING_HOUSE, MINI_APARTMENT, APARTMENT, WHOLE_HOUSE,
                    HOMESTAY, ROOMMATE, SMALL_PREMISES                         [§0.3]
GenderRequirement : MALE_ONLY, FEMALE_ONLY, ANY                                [§0.3][§3.3]
CurfewType        : FREE, CURFEW, UNKNOWN                                      [§3.7]
FurnitureStatus   : NONE, BASIC, FULL                                          [§3.7]
ToiletType        : PRIVATE, SHARED                                            [§3.7]
CommentStatus     : VISIBLE, PENDING, HIDDEN, DELETED                          [§3.11]
SentimentLabel    : POSITIVE, NEUTRAL, NEGATIVE, MIXED, PENDING_ANALYSIS       [§9.1]
SentimentAction   : NONE, WATCH, NEED_REVIEW                                   [§9.1]
ReviewStatus      : VISIBLE, HIDDEN, DELETED
ReportTargetType  : LISTING, COMMENT, USER, REVIEW                             [§2.8]
ReportReason      : WRONG_INFO, ALREADY_RENTED, SCAM, FAKE_IMAGE, WRONG_PRICE,
                    OFFENSIVE, SPAM, OTHER                                     [§3.13]
ReportStatus      : PENDING, REVIEWING, RESOLVED, REJECTED                     [§6.3]
ReportSeverity    : LOW, MEDIUM, HIGH, CRITICAL                                [§6.3]
ModerationResult  : NO_VIOLATION, MINOR_WARN, MEDIUM_HIDE, SEVERE_LOCK         [§10.8]
ModerationActionType : APPROVE, REJECT, HIDE, UNHIDE, LOCK, UNLOCK, WARN,
                       REQUEST_EDIT, FLAG_NEED_REVIEW, DISMISS                 [§4.4][§7.4]
PaymentStatus     : PENDING, SUCCESS, FAILED, CANCELLED, REFUNDED              [§6.3][§10.7]
PaymentMethod     : SANDBOX, VNPAY, MOMO, BANK_TRANSFER                        [§3.14]
SubscriptionStatus: PENDING, ACTIVE, EXPIRED, CANCELLED
NotificationType  : ACCOUNT_REGISTERED, LISTING_APPROVED, LISTING_REJECTED,
                    LISTING_EXPIRING, LISTING_EXPIRED, LISTING_LOCKED,
                    NEW_CONTACT, NEW_COMMENT, NEW_REVIEW, PAYMENT_SUCCESS,
                    PAYMENT_FAILED, REPORT_THRESHOLD, AI_NEGATIVE_ALERT,
                    ACCOUNT_LOCKED, VIOLATION_WARNING, FOLLOWED_LANDLORD_NEW_LISTING,
                    NEW_MATCHING_LISTING                                       [§5.6][§9.2]
NotificationChannel : IN_APP, EMAIL                                            [§5.6]
VerificationType  : EMAIL, PHONE, LANDLORD                                     [§6.1]
VerificationStatus: PENDING, VERIFIED, REJECTED, EXPIRED
ChatbotIntent     : FIND_ROOM, HOW_TO_POST, GLOSSARY, FAQ, GREETING,
                    OUT_OF_SCOPE, SENSITIVE, UNKNOWN                           [§9.3]
RecommendationSource : HOMEPAGE, SIMILAR_LISTING, AFTER_FAVORITE,
                       LOW_RESULT_SEARCH, CHATBOT, NOTIFICATION                [§9.2]
PriceConfidence   : HIGH, MEDIUM, LOW, INSUFFICIENT_DATA                       [§9.4]
AuditAction       : USER_LOCK, USER_UNLOCK, ROLE_CHANGE, LISTING_APPROVE,
                    LISTING_REJECT, LISTING_LOCK, LISTING_UNLOCK,
                    LISTING_EDIT, AI_CONFIG_CHANGE, PACKAGE_CHANGE,
                    SYSTEM_CONFIG_CHANGE, PAYMENT_REFUND                       [§11.4]
AuditActorType    : USER, SYSTEM                                               [§11.4]
```

**Enum bổ sung (v2)** — phát sinh khi đặc tả chi tiết từng bảng, mỗi cái đều bắt buộc:

```java
AmenityGroup      : FURNITURE, SECURITY, UTILITY, TRANSPORT                    [§10.5]
ContactType       : VIEW_PHONE, FORM, CHAT                                     [§3.10]
ConversationStatus: ACTIVE, ARCHIVED, BLOCKED                                  [§3.10]
BannedKeywordSeverity : MILD, SEVERE                                           [§5.3]
BannedKeywordScope    : LISTING, COMMENT, BOTH                                 [§3.3][§3.11]
CouponDiscountType    : PERCENT, FIXED                                         [§10.6]
ChatbotSender     : USER, BOT                                                  [§9.3]
ChatbotConversationStatus : ACTIVE, COMPLETED, ABANDONED                       [§9.3]
ConfigValueType   : STRING, INT, DECIMAL, BOOLEAN, JSON                        (SystemConfigService ép kiểu)
AiModule          : SENTIMENT, RECOMMENDATION, CHATBOT, PRICE                  (mục 10)
AutoHideReason    : REPORT_THRESHOLD, SENTIMENT_NEGATIVE, BANNED_KEYWORD       [§5.3]
```

Lý do từng cái (ngắn gọn):
- `AmenityGroup` — `[§10.5]` yêu cầu đúng 4 nhóm tiện ích: nội thất, an ninh, sinh hoạt, giao thông.
- `ContactType` — `[§3.10]` liệt kê "hình thức liên hệ" là dữ liệu vào.
- `ConversationStatus` — `[§3.10]` *"Người dùng bị report spam có thể bị hạn chế liên hệ"*.
- `BannedKeywordSeverity` — `[§5.3]` nói riêng *"từ khóa cấm **nghiêm trọng**"*, khác từ khóa thường.
- `BannedKeywordScope` — `[§3.3]` (tin) và `[§3.11]` (bình luận) xử lý từ khóa cấm khác nhau.
- `AutoHideReason` — bắt buộc để phân biệt 3 nguyên nhân tự động ẩn mà `[§5.3]` liệt kê.

**Enum bổ sung (v2.1)** — phát sinh khi đặc tả `02`/`03`, giá trị chi tiết ở `02`:

```java
ListingEditAction      : CREATE, UPDATE_MINOR, UPDATE_SENSITIVE, STATUS_CHANGE   [§3.4]  (listing_edit_histories)
CloseReason            : RENTED_OUT, NO_LONGER_AVAILABLE, OTHER                  [§3.6]  (listings.close_reason)
RejectReasonCode       : MISSING_INFO, WRONG_PRICE, FAKE_IMAGE, BANNED_CONTENT,
                         WRONG_AREA, DUPLICATE, OTHER                            [§3.3]  (listings.reject_reason_code)
TrustLabel             : GOOD, NORMAL, RISKY, NEED_REVIEW                        [§5.8]  (nhãn suy ra từ trust_score, không lưu cột)
PackagePurpose         : PUSH_TOP, HIGHLIGHT, BOTH                               [§2.9]  (promotion_packages.purpose)
PriceTriggerReason     : CREATE, EDIT_AREA, EDIT_CATEGORY, EDIT_LOCATION,
                         EDIT_AMENITY, ADMIN_RECALC                             [§5.9]  (prediction_histories.trigger_reason)
AdministrativeUnitType : PROVINCE, DISTRICT, WARD                               [§10.5] (dùng chung cho import khu vực)
ChatbotMessageRole     : = ChatbotSender (USER, BOT) — alias, KHÔNG tạo enum thứ hai. Dùng ChatbotSender.
AiLogModule            : = AiModule (SENTIMENT, RECOMMENDATION, CHATBOT, PRICE) — alias, dùng AiModule.
ReportGroupBy          : LISTING, USER, REASON  — tham số truy vấn admin, KHÔNG lưu DB           [§10.8]
```

> Hai "enum" `ChatbotMessageRole` và `AiLogModule` do agent đề xuất **bị gộp** vào enum đã có
> (`ChatbotSender`, `AiModule`) để tránh hai enum cùng nghĩa. `ReportGroupBy` và `TrustLabel`
> **không** map cột DB — chúng là tham số truy vấn / nhãn suy ra tại runtime.

### 5.1. State machine tin đăng — bảng chuyển trạng thái hợp lệ `[§5.1]`

Hiện thực bằng `ListingStateMachine` (một class duy nhất). Mọi chuyển trạng thái **phải** đi
qua nó; không service nào được `setStatus()` trực tiếp.

| Sự kiện | Từ | Sang | Actor |
|---|---|---|---|
| `SAVE_DRAFT` | (none) | `DRAFT` | LANDLORD |
| `SUBMIT` | `DRAFT`, `REJECTED` | `PENDING` | LANDLORD |
| `APPROVE` | `PENDING` | `ACTIVE` | MODERATOR/ADMIN |
| `REJECT` | `PENDING` | `REJECTED` (bắt buộc có lý do) | MODERATOR/ADMIN |
| `HIDE_BY_OWNER` | `ACTIVE` | `HIDDEN` | LANDLORD |
| `UNHIDE_BY_OWNER` | `HIDDEN` | `ACTIVE` (nếu chưa hết hạn **và** `auto_hidden_at IS NULL`) | LANDLORD |
| `AUTO_HIDE_BY_SYSTEM` | `ACTIVE`, `NEED_REVIEW` | `HIDDEN` (ghi `auto_hidden_at` + `auto_hide_reason`) | SYSTEM |
| `UNHIDE_BY_MODERATOR` | `HIDDEN` (kể cả bị tự động ẩn) | `ACTIVE` (xóa `auto_hidden_at`) | MODERATOR/ADMIN |
| `CLOSE` | `ACTIVE`, `HIDDEN` | `CLOSED` | LANDLORD |
| `EXPIRE` | `ACTIVE`, `NEED_REVIEW` | `EXPIRED` | SYSTEM (job) |
| `FLAG_NEED_REVIEW` | `ACTIVE` | `NEED_REVIEW` | SYSTEM/MODERATOR |
| `CLEAR_NEED_REVIEW` | `NEED_REVIEW` | `ACTIVE` | MODERATOR/ADMIN |
| `LOCK` | `ACTIVE`, `NEED_REVIEW`, `HIDDEN`, `PENDING` | `LOCKED` (bắt buộc lý do + severity) | ADMIN |
| `UNLOCK` | `LOCKED` | `HIDDEN` | ADMIN |
| `RENEW` | `ACTIVE`, `EXPIRED` | `ACTIVE` | LANDLORD |
| `SOFT_DELETE` | mọi trạng thái trừ `LOCKED` | `DELETED` | LANDLORD/ADMIN |
| `RESUBMIT_AFTER_EDIT` | `ACTIVE` (khi sửa nhạy cảm) | `PENDING` | LANDLORD |

**Ràng buộc bổ sung bắt buộc:**
- `LOCKED` → **không** cho `RENEW`, **không** cho `SUBMIT`, **không** cho `SOFT_DELETE` `[§3.5][§5.1]`.
- `REJECTED` → phải sửa và duyệt lại trước khi gia hạn `[§3.5]`.
- `UNLOCK` trả về `HIDDEN` (không phải `ACTIVE`) để chủ trọ chủ động bật lại sau khi đã sửa.
- **Ẩn bởi hệ thống ≠ ẩn bởi chủ trọ.** Cả hai cùng cho ra `HIDDEN`, phân biệt bằng
  `auto_hidden_at`. Nếu không phân biệt, chủ trọ chỉ cần bấm "Hiện lại" là vô hiệu hóa
  toàn bộ chế tài của `[§5.3]` — nên `UNHIDE_BY_OWNER` bị chặn khi `auto_hidden_at IS NOT NULL`,
  chỉ Moderator/Admin mới gỡ được.
- **`AUTO_HIDE_BY_SYSTEM` không ghi `violation_warnings` và không ghi `audit_logs`.**
  Cảnh báo vi phạm là chế tài **do người ban hành** `[§5.4]` và nó là một số hạng trong công thức
  uy tín `[§5.8]` — nếu hệ thống tự ghi thì cùng một sự kiện sentiment bị phạt điểm **hai lần**
  (một lần qua `NegativeCommentCount`, một lần qua `ViolationWarningCount`). Còn `audit_logs` ghi
  *ai* làm gì, mà ở đây không có actor người. Sự kiện này ghi vào `moderation_actions` với
  `actor_type = SYSTEM`.

### 5.2. Khả năng hiển thị công khai — **quy tắc tối quan trọng**

`[§5.1]` nói `NEED_REVIEW`: *"Có thể vẫn hiển thị hoặc tạm ẩn tùy cấu hình"*.
Do đó **không được** viết cứng `status = 'ACTIVE'` trong truy vấn tìm kiếm.

```
publiclyVisible(listing) :=
    listing.status == ACTIVE
 OR (listing.status == NEED_REVIEW
     AND SystemConfig.getBoolean("listing.need_review.publicly_visible"))
```

Tập trạng thái public được cung cấp bởi **một** method duy nhất
`ListingVisibilityService.publicStatuses()` và **mọi** truy vấn công khai (search, chi tiết,
gợi ý, chatbot, sitemap, tin liên quan) đều phải dùng nó.
Mặc định `listing.need_review.publicly_visible = true` (đúng tinh thần "report không tự động
khóa tin ngay" `[§3.13]`).

---

## 6. Danh sách Entity chốt (46 bảng)

`[§6.1]` liệt kê 38 entity. Bổ sung 8 entity **bắt buộc** để thỏa các mục nghiệp vụ khác mà
`[§6.1]` không liệt kê tường minh:

| Entity bổ sung | Bắt buộc vì |
|---|---|
| `Permission`, `RolePermission` | `[§11.2]` RBAC + đề bài yêu cầu "Permission" |
| `RefreshToken` | đề bài yêu cầu refresh token |
| `PasswordResetToken` | `[§2.1]` AUTH-04 |
| `Follow` | `[§2.5]` FOLLOW-01/02 (`[§6.1]` thiếu) |
| `ListingEditHistory` | `[§3.4]` *"lưu lịch sử chỉnh sửa"*, `[§10.4]` *"Xem lịch sử chỉnh sửa"* |
| `BannedKeyword` | `[§3.3]`, `[§5.3]`, `[§11.10]` *"Chặn từ khóa cấm"* |
| `ViolationWarning` | `[§5.4]` *"3 lần cảnh báo trong 30 ngày"* — phải đếm được |
| `Coupon` | `[§10.6]` *"Cấu hình khuyến mãi nếu cần"*, `[§2.9]` PROMO |
| `NotificationPreference` | `[§11.12]` *"Có thể tắt một số loại thông báo không quan trọng"* — không có bảng này thì câu đó không có chỗ thực thi (**bảng thứ 46, thêm ở v2**) |

Danh sách đầy đủ theo module:

**auth/user (11)** — `users`, `roles`, `user_roles`, `permissions`, `role_permissions`,
`user_profiles`, `landlord_profiles`, `verifications`, `refresh_tokens`,
`password_reset_tokens`, `follows`

**catalog (5)** — `categories`, `provinces`, `districts`, `wards`, `amenities`

**listing (4)** — `listings`, `listing_images`, `listing_amenities`, `listing_edit_histories`

**interaction (8)** — `favorites`, `view_histories`, `search_histories`, `contact_logs`,
`conversations`, `messages`, `comments`, `reviews`

**moderation (4)** — `reports`, `moderation_actions`, `violation_warnings`, `banned_keywords`

**payment (4)** — `promotion_packages`, `payments`, `promotion_subscriptions`, `coupons`

**notification (2)** — `notifications`, `notification_preferences`

**ai (5)** — `sentiment_results`, `recommendation_logs`, `prediction_histories`,
`chatbot_conversations`, `chatbot_messages`

**admin (3)** — `audit_logs`, `system_configs`, `ai_configs`

> **Tổng: 46 bảng.** (v1 chốt 45; v2 thêm `notification_preferences`.)
>
> Đặc tả cột/index/FK đầy đủ của từng bảng nằm ở `02_THIET_KE_DATABASE.md`. Khi file đó và file
> này mâu thuẫn về **danh sách bảng, tên enum, tên config key** thì file này thắng; còn về **chi
> tiết cột và index** thì `02` là bản đặc tả thi hành.

### 6.1. Quy ước cột chung

Mọi bảng nghiệp vụ kế thừa `AuditableEntity`:
`id`, `created_at`, `updated_at`, `created_by`, `updated_by`, `deleted_at` (nullable).
Bảng tra cứu (`provinces`, `districts`, `wards`, `amenities`, `categories`) chỉ cần
`id`, `created_at`, `updated_at`.

**Soft delete toàn hệ thống** `[§3.6][§10.2][§11.5]`: không có `DELETE` vật lý cho dữ liệu
nghiệp vụ. Dùng `deleted_at IS NULL` trong repository query (không dùng `@Where` của
Hibernate vì nó chặn cả Admin xem dữ liệu đã xóa — mà `[§3.6]` yêu cầu *"Admin vẫn xem được
tin đã xóa mềm"*).

**Unique có điều kiện qua cột sinh (ADR-02).** `[§3.1]` yêu cầu email/phone là duy nhất, nhưng
với soft delete thì một email của tài khoản đã xóa vẫn chiếm chỗ — người dùng không đăng ký lại
được. MySQL **không có** partial index (`WHERE deleted_at IS NULL`) như Postgres. Giải pháp chốt:
thêm **cột sinh** (generated column) và đặt unique trên nó:

```sql
email_uk VARCHAR(320) AS (IF(deleted_at IS NULL, email, NULL)) STORED,
UNIQUE KEY uk_users_email (email_uk)
```

MySQL bỏ qua giá trị `NULL` trong ràng buộc unique, nên nhiều bản ghi đã xóa (email_uk = NULL)
cùng tồn tại được, còn các bản ghi sống thì email vẫn duy nhất. Áp dụng cho: `users.email_uk`,
`users.phone_uk`, và `sentiment_results.latest_uk` (ép "đúng 1 phiên bản sentiment hiện hành"
cho mỗi comment `[§6.2]`).

---

## 7. Chuẩn API

### 7.1. Envelope thống nhất (bắt buộc mọi endpoint)

Thành công:
```json
{ "success": true, "message": "...", "data": { }, "timestamp": "2026-07-17T10:00:00Z" }
```
Thất bại:
```json
{
  "success": false,
  "message": "...",
  "data": null,
  "errorCode": "LISTING_NOT_FOUND",
  "errors": [ { "field": "price", "message": "Giá phải lớn hơn 0" } ],
  "timestamp": "2026-07-17T10:00:00Z",
  "path": "/api/listings",
  "traceId": "..."
}
```
Phân trang (`data` chứa):
```json
{ "items": [], "page": 0, "size": 20, "totalElements": 0, "totalPages": 0,
  "first": true, "last": true }
```

### 7.2. HTTP status

| Tình huống | Status |
|---|---|
| GET/PUT/PATCH thành công | 200 |
| POST tạo mới | 201 + header `Location` |
| DELETE / thao tác không trả nội dung | 204 |
| Sai validation | 400 `VALIDATION_FAILED` |
| Chưa đăng nhập | 401 `UNAUTHORIZED` |
| Không đủ quyền | 403 `FORBIDDEN` |
| Không tìm thấy | 404 `<X>_NOT_FOUND` |
| Xung đột (trùng email, đã lưu tin, đã đánh giá) | 409 `<X>_CONFLICT` |
| Vi phạm quy tắc nghiệp vụ (sai state machine) | 422 `BUSINESS_RULE_VIOLATED` |
| Vượt rate limit | 429 `RATE_LIMIT_EXCEEDED` + header `Retry-After` |
| Lỗi hệ thống | 500 `INTERNAL_ERROR` |
| AI timeout/không khả dụng | 503 `AI_SERVICE_UNAVAILABLE` |

### 7.3. Quy ước chung

- Prefix `/api`. Version qua header `X-Api-Version` (mặc định `1`), không nhúng `/v1` vào path.
- Phân trang: `?page=0&size=20&sort=createdAt,desc`. `size` tối đa 100 (ép ở `PageableConfig`).
- Filter dùng query param; **không** dùng RSQL.
- Endpoint quản trị luôn nằm dưới `/api/admin/**`.
- Thời gian trả về ISO-8601 UTC (`Instant`).
- Tiền: `BigDecimal(15,2)`, VND, trả về dạng number.
- Mọi endpoint có `@Operation` + `@ApiResponses` cho Swagger.

---

## 8. Bảo mật

- Access token JWT: **15 phút**, chứa `sub` (userId), `email`, `roles[]`, `permissions[]`, `jti`.
- Refresh token: **7 ngày**, opaque UUID, **lưu DB** (`refresh_tokens`, hash SHA-256), **xoay
  vòng (rotation)** mỗi lần refresh + phát hiện tái sử dụng (reuse detection) → thu hồi cả họ token.
- **"Họ token" (`family_id`)**: mỗi lần login sinh một `family_id` mới; mọi refresh token xoay vòng
  từ nó kế thừa cùng `family_id`. Reuse detection → thu hồi **toàn bộ** token cùng `family_id`.
  Bảng `refresh_tokens` vì vậy phải có: `family_id`, `revoked_at`, `replaced_by_id`, `user_agent`,
  `ip_address`.
- **Nơi lưu token ở client (chốt v2):** access token **giữ trong memory** (biến module + mirror
  vào Redux, **không** `localStorage`); refresh token nằm trong cookie
  `httpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=604800`.
  Lý do: nếu để refresh token trong `localStorage` thì toàn bộ đầu tư vào rotation + reuse
  detection + hash SHA-256 trở nên vô nghĩa — một lỗ XSS bất kỳ đọc được token 7 ngày. Với
  phương án này, thiệt hại tệ nhất của XSS bị chặn trần ở một access token sống ≤15 phút và
  **không gia hạn được**.
- **`csrf().disable()` vẫn đúng, và đây là lý do:** cookie bị giới hạn `Path=/api/auth` +
  `SameSite=Strict`, nên mọi endpoint nghiệp vụ (`/api/listings`, `/api/payments`…) **không hề
  nhận cookie** — chúng chỉ chấp nhận `Authorization: Bearer`. Không có endpoint nào vừa gây
  side-effect vừa được xác thực bằng cookie ⇒ không có bề mặt CSRF.
  *Ràng buộc kéo theo: nếu sau này có ai đặt endpoint gây side-effect dưới `/api/auth` mà xác
  thực bằng cookie, luận điểm này gãy và phải bật lại CSRF cho nhóm path đó.*
- Logout: xóa refresh token + đưa `jti` của access token vào **blacklist Redis** với TTL = hạn còn lại.
- **Chính sách khi Redis chết — bất đối xứng có chủ đích:**
  - Rate limit → **fail-open** (cho request đi qua, log WARN). Redis chết không được làm sập
    toàn site.
  - JWT blacklist → **fail-closed** (từ chối request). Nếu fail-open, token đã logout/thu hồi sẽ
    được chấp nhận trở lại — đây là lỗ hổng xác thực, không phải sự cố hiệu năng.
  - Cache dữ liệu → **fail-open**, đọc thẳng DB (`CacheErrorHandler` log WARN).
- Mật khẩu: BCrypt cost 12. Tối thiểu 8 ký tự, có chữ và số `[§3.1]`.
- Rate limit `[§11.10]` (Redis, key theo user hoặc IP):

| Hành động | Giới hạn | Config key |
|---|---|---|
| Đăng nhập sai | 5 lần / 15 phút / IP+email → khóa tạm 15 phút `[§3.2]` | `security.login.*` |
| Đăng ký | 3 / giờ / IP | `security.register.rate` |
| Đăng tin (tài khoản mới <7 ngày) | 3 / ngày | `spam.listing.new_account_daily` |
| Đăng tin (thường) | 10 / ngày | `spam.listing.daily` |
| Bình luận | 5 / phút | `spam.comment.per_minute` |
| Report | 10 / ngày | `spam.report.daily` |
| Tin nhắn | 30 / phút | `spam.message.per_minute` |
| Chatbot | 30 / phút | `spam.chatbot.per_minute` |

- XSS `[§11.1]`: sanitize input bằng `HtmlSanitizer` (allowlist rỗng cho mô tả — strip toàn bộ
  HTML), escape output ở React (mặc định), **không** dùng `dangerouslySetInnerHTML` ở bất kỳ đâu.
- SQL Injection: 100% JPA/Criteria/`@Query` có tham số. Không nối chuỗi SQL.
- CSRF: API stateless dùng Bearer token → `csrf().disable()` là **đúng** (không có cookie
  session). Ghi rõ lý do trong `SecurityConfig`.
- Upload `[§11.9]`: kiểm tra **magic bytes** (không tin `Content-Type`), whitelist JPG/PNG/WEBP,
  ≤5MB/ảnh, ≤10 ảnh/tin, đổi tên thành UUID, lưu ngoài webroot, **không** phục vụ file thực thi.
- **Chặn decompression bomb**: đọc kích thước pixel từ header **trước khi** decode; vượt
  `upload.max_pixels` (mặc định 50 triệu pixel) → từ chối. Một file PNG 100KB có thể giải nén ra
  ảnh 50000×50000 và giết JVM bằng OOM — giới hạn 5MB dung lượng **không** chặn được việc này.
- **Ảnh sinh 3 kích thước** `[§11.9]` *"Nén ảnh và tạo thumbnail"*: `thumb` 200px (`ListingCard`),
  `medium` 800px (slider), `original` ≤1920px. Lưu `width`/`height` vào `listing_images` để
  frontend đặt `aspect-ratio` giữ chỗ, chống layout nhảy (CLS).
- Che số điện thoại `[§3.8]`: khách chưa đăng nhập chỉ thấy `0901***456` (`MaskUtil`).
- **Làm tròn tọa độ cho khách chưa đăng nhập**: trả `latitude/longitude` làm tròn **3 chữ số
  thập phân** (~110m) — đủ để hiển thị vị trí tương đối trên bản đồ, nhưng không lộ tọa độ chính
  xác của nhà riêng. Cùng tinh thần với việc che số điện thoại `[§3.8]`.

---

## 9. Config key chuẩn (`system_configs`) — **không hardcode** `[§5.x]`

| Key | Mặc định | Nguồn |
|---|---|---|
| `listing.display_days` | 30 | `[§3.3][§5.2]` |
| `listing.image.min` | 1 | `[§3.3][§11.9]` |
| `listing.image.max` | 10 | `[§3.3][§11.9]` |
| `listing.image.max_size_mb` | 5 | `[§11.9]` |
| `listing.title.min` | 10 | `[§3.3]` |
| `listing.title.max` | 150 | `[§3.3]` |
| `listing.description.min` | 30 | `[§3.3]` |
| `listing.description.max` | 3000 | `[§3.3]` |
| `listing.expiry.reminder_days` | `3,1` | `[§5.2]` |
| `listing.renew.free_per_month` | 2 | `[§3.5]` |
| `listing.need_review.publicly_visible` | `true` | `[§5.1]` |
| `listing.auto_approve.trusted_landlord` | `false` | `[§3.3]` |
| `moderation.autohide.report_count` | 5 | `[§5.3]` |
| `moderation.autohide.distinct_reporters` | 5 | `[§5.3]` |
| `moderation.autohide.window_hours` | 24 | `[§5.3]` |
| `moderation.threshold.warning_count` | 3 | `[§5.4]` |
| `moderation.threshold.warning_window_days` | 30 | `[§5.4]` |
| `moderation.threshold.locked_listing_count` | 5 | `[§5.4]` |
| `moderation.threshold.locked_listing_window_days` | 60 | `[§5.4]` |
| `moderation.threshold.spam_comment_count` | 10 | `[§5.4]` |
| `moderation.threshold.spam_comment_window_hours` | 1 | `[§5.4]` |
| `trust.base_score` | 100 | `[§5.8]` |
| `trust.weight.positive_comment` | 1 | `[§5.8]` |
| `trust.weight.negative_comment` | 2 | `[§5.8]` |
| `trust.weight.average_rating` | 5 | `[§5.8]` |
| `trust.weight.valid_report` | 10 | `[§5.8]` |
| `trust.weight.violation_warning` | 15 | `[§5.8]` |
| `trust.min` | 0 | `[§5.8]` |
| `trust.max` | 100 | `[§5.8]` |
| `trust.threshold.risky` | 40 | `[§5.8]` |
| `trust.threshold.need_review` | 25 | `[§5.8]` |
| `ai.sentiment.enabled` | `true` | `[§10.10]` |
| `ai.sentiment.min_comments_l1` | 5 | `[§9.1]` |
| `ai.sentiment.negative_ratio_l1` | 0.40 | `[§9.1]` |
| `ai.sentiment.min_comments_l2` | 10 | `[§9.1]` |
| `ai.sentiment.negative_ratio_l2` | 0.50 | `[§9.1]` |
| `ai.sentiment.need_review_count_for_lock` | 3 | `[§9.1]` |
| `ai.sentiment.need_review_window_days` | 30 | `[§9.1]` |
| `ai.sentiment.landlord_alert_listing_count` | 3 | `[§9.1]` |
| `ai.sentiment.min_length` | 10 | `[§9.1]` (bình luận quá ngắn → NEUTRAL) |
| `ai.sentiment.new_account_days` | 7 | `[§9.1]` (trọng số thấp hơn) |
| `ai.sentiment.new_account_weight` | 0.5 | `[§9.1]` |
| `ai.recommendation.enabled` | `true` | `[§10.10]` |
| `ai.recommendation.size` | 12 | |
| `ai.recommendation.cache_ttl_minutes` | 15 | `[§11.11]` |
| `ai.recommendation.promoted_boost` | 1.15 | `[§9.2]` (trần, tránh phá tính liên quan) |
| `ai.price.enabled` | `true` | `[§10.10]` |
| `ai.price.min_samples` | 8 | `[§9.4]` (thiếu → `INSUFFICIENT_DATA`) |
| `ai.price.deviation_flag_ratio` | 0.35 | `[§3.3][§9.4]` |
| `ai.chatbot.enabled` | `true` | `[§10.10]` |
| `ai.chatbot.max_clarify_turns` | 3 | `[§9.3]` |
| `contact.dedup_minutes` | 60 | `[§3.10]` |
| `view.dedup_minutes` | 30 | `[§3.8]` |
| `comment.edit_window_minutes` | 30 | `[§3.11]` |
| `review.edit_window_hours` | 24 | `[§3.12]` |
| `review.require_contact` | `true` | `[§3.12]` |
| `promotion.max_priority` | 100 | `[§10.6]` |

**Bổ sung v2 — rate limit (10 key).** Mục 8 chốt *giá trị* nhưng bảng trên chỉ ghi tên rút gọn
(`security.login.*`); phải liệt kê thành key riêng vì mục 13.4 cấm hardcode ngưỡng:

| Key | Mặc định | Nguồn |
|---|---|---|
| `security.login.max_attempts` | 5 | `[§3.2]` |
| `security.login.window_minutes` | 15 | `[§3.2]` |
| `security.login.lock_minutes` | 15 | `[§3.2]` |
| `security.register.rate` | 3 | `[§11.10]` |
| `spam.listing.new_account_daily` | 3 | `[§11.10]` |
| `spam.listing.daily` | 10 | `[§11.10]` |
| `spam.comment.per_minute` | 5 | `[§11.10]` |
| `spam.report.daily` | 10 | `[§11.10]` |
| `spam.message.per_minute` | 30 | `[§11.10]` |
| `spam.chatbot.per_minute` | 30 | `[§11.10]` |

**Bổ sung v2 — timeout & retry AI (6 key).** `[§9.1]` yêu cầu xử lý *"AI lỗi hoặc timeout"*
nhưng không cho con số:

| Key | Mặc định | Nguồn |
|---|---|---|
| `ai.sentiment.timeout_ms` | 2000 | `[§9.1]` |
| `ai.sentiment.max_retry` | 5 | `[§9.1]` |
| `ai.recommendation.timeout_ms` | 1500 | `[§9.2]` |
| `ai.chatbot.timeout_ms` | 3000 | `[§9.3]` |
| `ai.price.timeout_ms` | 2000 | `[§9.4]` |
| `upload.max_pixels` | 50000000 | `[§11.9]` (chặn decompression bomb) |

**Bổ sung v2 — tỷ lệ phản hồi chủ trọ (5 key).** `[§5.7]` liệt kê *"Chủ trọ phản hồi người thuê
nhanh và đầy đủ nếu có module chat"* là 1 trong 5 sự kiện cập nhật điểm uy tín chủ trọ, nhưng v1
**không có số hạng nào** cho nó:

| Key | Mặc định | Nguồn |
|---|---|---|
| `trust.weight.landlord_response_rate` | 10 | `[§5.7]` |
| `trust.response_rate.window_days` | 30 | `[§5.7]` |
| `trust.response_rate.sla_hours` | 24 | `[§5.7]` (định nghĩa "nhanh") |
| `trust.response_rate.min_conversations` | 3 | `[§5.7]` (dưới ngưỡng → không đủ mẫu, term = 0) |
| `trust.response_rate.neutral_percent` | 70 | `[§5.7]` (mốc trung tính) |

**Bổ sung v2 — thông báo tin mới phù hợp (5 key).** `[§9.2]` *"Trong email/in-app notification
nếu có tin mới phù hợp"*:

| Key | Mặc định | Nguồn |
|---|---|---|
| `ai.recommendation.notify_enabled` | `true` | `[§9.2]` |
| `ai.recommendation.notify_min_score` | 0.65 | `[§9.2]` |
| `ai.recommendation.notify_max_per_user` | 3 | `[§9.2]` (chống spam thông báo) |
| `ai.recommendation.notify_lookback_hours` | 24 | `[§9.2]` |
| `ai.recommendation.notify_active_user_days` | 30 | `[§9.2]` |

**Bổ sung v2 — khác (2 key):**

| Key | Mặc định | Nguồn |
|---|---|---|
| `security.refresh.grace_seconds` | 10 | Rotation nhiều tab: 2 tab cùng refresh trong tích tắc sẽ bị coi nhầm là reuse attack và đá người dùng ra. Cho phép token vừa bị xoay vòng còn dùng được thêm 10 giây. |
| `moderation.autohide.sentiment_requires_prior_warning` | `true` | `[§5.3]` điều kiện thứ ba: *"AI sentiment phát hiện tỷ lệ bình luận tiêu cực cao **và tin đã từng bị cảnh báo trước đó**"* — vế sau là bắt buộc, không phải tùy chọn. |

**Bổ sung v2.1 — hệ số & ngưỡng còn hardcode trong đặc tả (20 key).** Khi đặc tả chi tiết `02`/`03`,
phát hiện các hệ số/ngưỡng vẫn nằm cứng trong công thức; đưa hết vào config theo mục 13.4:

| Key | Mặc định | Nguồn |
|---|---|---|
| `ai.price.hedonic.furniture_full` | 0.12 | mục 10.4 bước 4 (nội thất đầy đủ) |
| `ai.price.hedonic.toilet_private` | 0.08 | mục 10.4 (toilet riêng) |
| `ai.price.hedonic.elevator` | 0.07 | mục 10.4 (thang máy) |
| `ai.price.hedonic.parking` | 0.05 | mục 10.4 (chỗ để xe) |
| `ai.price.hedonic.curfew_free` | 0.03 | mục 10.4 (giờ tự do) |
| `ai.price.hedonic.street_front` | 0.15 | mục 10.4 (mặt tiền) |
| `ai.price.comparable_days` | 180 | mục 10.4 bước 1 (cửa sổ tin so sánh) |
| `ai.price.comparable_area_tolerance` | 0.30 | mục 10.4 bước 1 (diện tích ±30%) |
| `ai.sentiment.low_confidence_threshold` | 0.5 | mục 10.1 (confidence thấp → không xử lý nặng) |
| `chat.message.max_length` | 2000 | `[§3.10]` (tin nhắn chat nội bộ) |
| `chatbot.message.max_length` | 500 | `[§9.3]` (câu hỏi chatbot) |
| `report.abuse.rejected_count` | 5 | `[§3.13]` *"report sai nhiều lần → hạn chế"* |
| `report.abuse.window_days` | 30 | `[§3.13]` |
| `payment.order.expiry_minutes` | 30 | `[§3.14]` (đơn PENDING quá hạn → FAILED) |
| `payment.callback.max_skew_seconds` | 300 | `[§8.2]` (chống replay callback) |
| `security.login.captcha_after_attempts` | 3 | `[§3.2]` *"đăng nhập sai nhiều lần → captcha"* |
| `search.keyword.max_length` | 100 | `[§3.7]` (chống query quá dài) |
| `search.amenity_filter.max_count` | 20 | `[§3.7]` (giới hạn số tiện ích lọc) |
| `page.about` | *(HTML tĩnh)* | `[§10.14]` nội dung trang Giới thiệu (Admin sửa) |
| `page.terms` | *(HTML tĩnh)* | `[§10.14]` nội dung trang Điều khoản (Admin sửa) |

> **Tổng: 57 (v1) + 28 (v2) + 20 (v2.1) = 105 config key** — đã đếm lại và dedup bằng script,
> không trùng lặp. `V5__seed_system_configs.sql` phải seed **đúng 105 dòng**, kiểm chứng máy móc:
>
> ```sql
> -- Phải trả về 105
> SELECT COUNT(*) FROM system_configs;
> ```
>
> `page.about` / `page.terms` là `value_type = JSON` chứa HTML tĩnh, không phải ngưỡng số. Chúng
> vẫn nằm trong `system_configs` để Admin sửa nội dung tĩnh qua giao diện `[§10.14]` thay vì phải
> sửa code.
>
> Mỗi key trong bảng ở mục 9 là **một dòng riêng** — không gộp `x.min` / `x.max` vào một dòng nữa,
> vì gộp làm số đếm sai lệch và không đối chiếu máy móc được với migration.

Đọc qua `SystemConfigService` có cache Redis, invalidate khi Admin cập nhật.

---

## 10. Đặc tả 4 module AI

Mọi module nằm sau interface + chạy **async qua queue** `[§11.6]`, có `@Async` executor riêng.
AI **không bao giờ** tự khóa tài khoản `[§10.10]`; chỉ đề xuất `NEED_REVIEW` + cảnh báo.

**Cơ chế async chốt (bắt buộc, tránh 2 lỗi kinh điển):** service nghiệp vụ (ví dụ
`CommentServiceImpl`) sau khi lưu **publish một `ApplicationEvent` chỉ mang id** (không mang cả
entity). Listener nhận bằng `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async("aiTaskExecutor")`,
và mở transaction mới `@Transactional(REQUIRES_NEW)`.

- **Vì sao `AFTER_COMMIT`:** nếu chạy async ngay trong transaction gốc, thread AI đọc DB **trước
  khi** transaction commit → "không tìm thấy bình luận" ngẫu nhiên (race). Chỉ mang id (không mang
  entity) vì entity detached qua thread khác sẽ lỗi lazy-loading.
- **Vì sao `REQUIRES_NEW` + gọi async, không gọi trực tiếp:** nếu gọi sentiment đồng bộ trong
  transaction tạo bình luận, AI lỗi/timeout sẽ **rollback cả bình luận** — vi phạm `[§9.1]`
  (*"AI lỗi hoặc timeout: bình luận vẫn được lưu, sentiment ở trạng thái PendingAnalysis"*).
- Đây chính là hiện thực của luật phụ thuộc số 7 (mục 3): `interaction → ai` đi qua event, không
  `@Autowired` chéo.

### 10.1. Sentiment `[§9.1]`

`SentimentAnalyzer` (interface) → `VietnameseLexiconSentimentAnalyzer` (impl).
Thuật toán: chuẩn hóa (bỏ dấu tùy chọn, teencode) → tách token → so khớp từ điển có trọng số
→ xử lý **phủ định** ("không", "chẳng", "chưa" đảo dấu trong cửa sổ 3 từ) → **từ tăng cường**
("rất", "cực kỳ", "quá" ×1.5) → emoji → cụm n-gram ("không đáng tiền", "chủ dễ tính").
Kết quả: `score ∈ [-1,1]`, `label`, `confidence ∈ [0,1]`.

Xử lý ngoại lệ **bắt buộc** `[§9.1]`:
- Độ dài < `ai.sentiment.min_length` → `NEUTRAL`, **không** tính vào điểm uy tín.
- Vừa khen vừa chê (có cả cụm dương và âm mạnh) → `MIXED`.
- Confidence thấp (< 0.5) → không kích hoạt hành động nặng.
- AI lỗi/timeout → bình luận **vẫn được lưu**, sentiment = `PENDING_ANALYSIS`, job retry.
- Bình luận bị Moderator đánh dấu spam → **loại khỏi thống kê** điểm uy tín.
- Bình luận từ tài khoản mới (<7 ngày) → trọng số 0.5.

### 10.2. Recommendation `[§9.2]`

`RecommendationEngine` → `ContentBasedRecommendationEngine`. Rule-based có trọng số `[§13.2]`.
Xây `UserPreferenceProfile` từ `ViewHistory` (w=1) + `SearchHistory` (w=2) + `Favorite` (w=3)
+ `ContactLog` (w=5).

**`UserPreferenceProfile` phải có đủ 7 chiều** — phủ 1-1 với 11 mục "Dữ liệu đầu vào" `[§9.2]`:

| Chiều | Suy ra từ | Mục `[§9.2]` |
|---|---|---|
| `preferredProvinces/Districts/Wards` | lịch sử xem + tìm kiếm | "Khu vực thường xem" |
| `preferredPriceRange` | percentile 10–90 giá đã xem | "Khoảng giá thường xem" |
| `preferredCategories` | loại tin đã xem/lưu | "Loại phòng thường xem" |
| `preferredAreaRange` | percentile 10–90 diện tích đã xem | **"Diện tích quan tâm"** |
| `preferredOccupants` | mode(số người ở) + `user_profiles.preferred_occupants` | **"Số người ở"** |
| `preferredGenderRequirement` | `user_profiles.preferred_gender_requirement` | **"Giới tính nếu là ở ghép"** |
| `preferredAmenities` | tiện ích của tin đã lưu/liên hệ | "Tiện ích quan tâm" |

> 3 chiều in đậm là thứ **v1 bỏ sót**. Hai mục còn lại của `[§9.2]` ("Lịch sử xem", "Lịch sử tìm
> kiếm") là *nguồn* chứ không phải chiều; mục "Tin đang Active" là *tập ứng viên*.

**Công thức 9 số hạng (Σ trọng số = 1.00):**

```
score = 0.22·locationMatch   + 0.12·areaSizeMatch + 0.20·priceMatch
      + 0.12·categoryMatch   + 0.08·amenityMatch  + 0.08·occupantMatch
      + 0.06·genderMatch     + 0.06·trustScoreNorm + 0.06·freshness
score = score × promotedBoost        // trần 1.15, không được phá tính liên quan [§9.2]
```

Mọi số hạng trả giá trị trong `[0,1]`.

**Quy tắc chuẩn hóa lại trọng số (bắt buộc):** `genderMatch` chỉ có nghĩa với tin `ROOMMATE`.
Với tin khác, **không** gán mặc định `1.0` — làm vậy thì mọi tin non-`ROOMMATE` được cộng không
0.06 và bị thiên vị có hệ thống so với tin `ROOMMATE`. Thay vào đó bỏ số hạng đó ra và chia lại
cho tổng trọng số thực áp dụng `W = 0.94`:

```
score = (Σ áp dụng được) / W
```

`recommendation_logs` phải lưu **điểm thành phần từng số hạng** + `applied_weight_sum` để tái dựng
và giải thích được điểm tổng `[§9.2]` (*"cần lưu RecommendationLog để giải thích và đánh giá hiệu
quả"*). `gender_score = NULL` khi không áp dụng — `NULL` khác `0`.

Bắt buộc `[§9.2]`: loại tin `HIDDEN/EXPIRED/LOCKED/CLOSED/DELETED`; loại tin user đã xem gần
đây (chống lặp); loại tin của chính user; ghi `RecommendationLog` để giải thích được.
**Cold start**: tin mới nhất + phổ biến trong khu vực đang xem + theo filter hiện tại +
danh mục phổ biến.

### 10.3. Chatbot `[§9.3][§3.15]`

`ChatbotEngine` → `RuleBasedChatbotEngine`. Intent classification (từ khóa + regex có trọng số)
→ slot filling (giá "dưới 4 triệu"/"3-5tr", khu vực "Quận 1", diện tích, số người, giới tính,
thú cưng, giờ giấc, chỗ để xe, nội thất) → `ListingSearchService`.

Ràng buộc cứng: **chỉ trả tin public**, **không bịa thông tin ngoài DB**, không cam kết còn
phòng, không tư vấn pháp lý, không đặt cọc/thương lượng thay người dùng. Hỏi lại tối đa 3 lượt.
Không kết quả → đề xuất nới giá/khu vực/diện tích. Nội dung nhạy cảm → từ chối lịch sự và
hướng về chức năng tìm trọ. Ghi log câu hỏi phổ biến để cải thiện FAQ.

### 10.4. Price Prediction `[§9.4]`

`PriceEstimator` → `ComparableHedonicPriceEstimator`.
Bước 1: lấy comparable — cùng `ward` (nới dần lên `district` → `province` nếu thiếu mẫu),
cùng `category`, diện tích ±30%, tin `ACTIVE`/`CLOSED` trong 180 ngày.
Bước 2: nếu `n < ai.price.min_samples` → `INSUFFICIENT_DATA`, **không dự đoán** `[§9.4]`.
Bước 3: giá cơ sở = median(price/m²) × diện tích.
Bước 4: điều chỉnh hedonic theo hệ số cấu hình được: nội thất đầy đủ (+12%), toilet riêng (+8%),
thang máy (+7%), chỗ để xe (+5%), giờ tự do (+3%), mặt tiền (+15%), khoảng cách trung tâm.
Bước 5: khoảng = percentile 25 / 50 / 75. Confidence theo `n` và độ phân tán (IQR/median).
Bước 6: `|giá nhập − giá đề xuất| / giá đề xuất > 0.35` → **ghi flag**, cảnh báo mềm,
**tuyệt đối không chặn đăng tin** `[§3.3][§9.4]`. Lưu `PredictionHistory` mọi lần.

---

### 10.5. Điểm uy tín (AI-02, AI-03) `[§5.7][§5.8]`

**Điểm uy tín tin đăng** — giữ **đúng** công thức `[§5.8]`, không tự ý đổi:

```
ListingTrustScore = trust.base_score                              (100)
  + PositiveCommentCount   × trust.weight.positive_comment        (1)
  - NegativeCommentCount   × trust.weight.negative_comment        (2)
  + AverageRating          × trust.weight.average_rating          (5)
  - ValidReportCount       × trust.weight.valid_report            (10)
  - ViolationWarningCount  × trust.weight.violation_warning       (15)
kẹp vào [trust.min, trust.max] = [0, 100]
```

> **Ghi chú trung thực về công thức này.** Nó có mâu thuẫn nội tại: bắt đầu từ 100 và trần cũng là
> 100, nên hai số hạng cộng (`PositiveCommentCount`, `AverageRating`) gần như **không có tác dụng
> đẩy điểm lên** — chúng chỉ bù trừ được phần đã bị trừ. Tài liệu nghiệp vụ `[§5.8]` nói *"Không
> bắt buộc dùng đúng công thức này"*, nhưng đây là đồ án và công thức là yêu cầu, nên **giữ nguyên**
> và đưa toàn bộ hệ số vào config để Admin chỉnh `[§10.10]`. Không âm thầm "sửa cho hợp lý".

Bình luận **không** được tính vào điểm khi: quá ngắn (`< ai.sentiment.min_length`), bị Moderator
đánh dấu spam, hoặc `sentiment = PENDING_ANALYSIS` `[§9.1]`. Bình luận từ tài khoản mới
(`< ai.sentiment.new_account_days`) nhân trọng số `ai.sentiment.new_account_weight` = 0.5.

**Điểm uy tín chủ trọ** — `[§5.7]` liệt kê 5 sự kiện, mỗi sự kiện phải có chỗ thực thi:

```
LandlordTrustScore = AVG(trust_score của các tin còn hiệu lực)
  - ViolationWarningCount × trust.weight.violation_warning
  + ResponseTerm
kẹp vào [0, 100]
```

`ResponseTerm` hiện thực hóa `[§5.7]` *"Chủ trọ phản hồi người thuê nhanh và đầy đủ nếu có module
chat"* — v1 **không có** số hạng này:

```
responseRate = (số hội thoại có phản hồi trong SLA) / (tổng hội thoại trong cửa sổ)   [0..1]
ResponseTerm = trust.weight.landlord_response_rate
             × (responseRate×100 − trust.response_rate.neutral_percent) / 100
```

Ba quyết định trong đó, ghi rõ để không bị hiểu nhầm là tùy tiện:
1. **Vế "nhanh" nằm trong định nghĩa tỷ lệ**, không tách thành số hạng riêng: một hội thoại chỉ
   tính là "đạt" khi phản hồi trong `trust.response_rate.sla_hours` (24h). Vế "đầy đủ" = có phản
   hồi (`first_response_at IS NOT NULL`).
2. **Chuẩn hóa quanh mốc trung tính 70%**, không cộng thẳng. Nếu cộng thẳng, chủ trọ tắt chat —
   quyền mà `[§3.10]` **cho phép** (*"Nếu chủ trọ tắt chat, hệ thống chỉ hiển thị số điện thoại"*)
   — sẽ bị phạt điểm gián tiếp vì không bao giờ có phản hồi. Với mốc trung tính, phản hồi tốt
   (>70%) được cộng, kém (<70%) bị trừ, và `allow_chat = FALSE` ⇒ `ResponseTerm = 0`, đúng mệnh
   đề điều kiện *"nếu có module chat"*.
3. **Dưới `trust.response_rate.min_conversations` (3) hội thoại ⇒ `ResponseTerm = 0`** — không đủ
   mẫu thì không kết luận, tránh phạt oan chủ trọ mới.

Đo được nhờ `conversations.first_response_at` (ghi idempotent:
`UPDATE ... SET first_response_at = ? WHERE id = ? AND first_response_at IS NULL`), rollup vào
`landlord_profiles.response_rate_percent` bởi `TrustScoreRecalcJob`.

---

## 11. Job nền `[§11.3][§5.2]`

| Job | Lịch | Việc |
|---|---|---|
| `ListingExpiryJob` | mỗi giờ | `ACTIVE/NEED_REVIEW` quá `expired_at` → `EXPIRED` `[§5.2]` |
| `ListingExpiryReminderJob` | 08:00 hằng ngày | nhắc trước 3 ngày và 1 ngày `[§5.2]` |
| `TrustScoreRecalcJob` | 02:00 hằng ngày | tính lại điểm uy tín tin + chủ trọ `[§5.7]` |
| `SentimentRetryJob` | mỗi 10 phút | xử lý lại `PENDING_ANALYSIS` `[§9.1]` |
| `RecommendationPrecomputeJob` | mỗi 6 giờ | tính trước gợi ý cho user hoạt động `[§5.5]` |
| `PromotionExpiryJob` | mỗi giờ | `PromotionSubscription` hết hạn → `EXPIRED` |
| `TokenCleanupJob` | 03:00 hằng ngày | xóa refresh/reset token hết hạn |
| `PaymentReconcileJob` | mỗi 15 phút | `PENDING` quá 30 phút → `FAILED` `[§3.14]` |
| `NewMatchingListingNotifyJob` | 07:30 hằng ngày | `[§9.2]` *"Trong email/in-app notification nếu có tin mới phù hợp"* — quét tin mới trong `notify_lookback_hours`, chấm điểm với user hoạt động trong `notify_active_user_days`, gửi tối đa `notify_max_per_user` tin có `score ≥ notify_min_score`. **Đây là tác nhân duy nhất sinh `RecommendationSource.NOTIFICATION` và `NotificationType.NEW_MATCHING_LISTING`.** |
| `DataRetentionJob` | 03:30 hằng ngày | **(job thứ 10, thêm ở v2.1)** Dọn log hành vi theo tuổi: `view_histories`, `search_histories`, `notifications` đã đọc, `chatbot_messages` cũ, `audit_logs` quá hạn lưu. Bắt buộc vì `[§11.5]`: các bảng này tăng ~16 GB/năm không giới hạn làm backup/restore (RPO/RTO mục 13) bất khả thi. Ngưỡng tuổi đọc từ config `retention.*`. **Không** xóa dữ liệu nghiệp vụ (tin, thanh toán, báo cáo) — chỉ log hành vi. |

> **v1: 8 job → v2: 9 → v2.1: 10.** `NotificationDigestJob` của v1 bị **xóa** (chưa từng đặc tả,
> không có căn cứ nghiệp vụ), thay bằng `NewMatchingListingNotifyJob` `[§9.2]`. v2.1 thêm
> `DataRetentionJob` `[§11.5]`. Nếu không có `NewMatchingListingNotifyJob` thì
> `RecommendationSource.NOTIFICATION` là **code chết**.

**Luật viết job (bắt buộc, cả 9 job):**
1. **Idempotent** — chạy lại 2 lần không được nhân đôi tác dụng (dùng cột dấu như
   `listings.expiry_reminder_sent_at`, không dựa vào "job chỉ chạy 1 lần").
2. **`@Scheduled(zone = "UTC")` bắt buộc** — không để phụ thuộc timezone của máy chủ.
3. **Không nuốt lỗi im lặng** — bắt exception từng phần tử, log ERROR kèm id, tiếp tục phần còn
   lại; kết thúc job log INFO kèm số liệu (đã xử lý / bỏ qua / lỗi). Job im lặng là job không ai
   biết đã chết.
4. Chạy **một instance** (đề án không dựng nhiều node); nếu scale ngang sau này thì thêm khóa
   phân tán — ghi rõ ở `01` mục 12.

---

## 12. Sitemap frontend

**Public**: `/`, `/tim-kiem`, `/tin/:slug-:id`, `/chu-tro/:id`, `/dang-nhap`, `/dang-ky`,
`/quen-mat-khau`, `/dat-lai-mat-khau`, `/xac-thuc-email`, `/gioi-thieu`, `/dieu-khoan`, `/404`.

**Tenant** (`/tai-khoan/*`): `ho-so`, `tin-da-luu`, `lich-su-xem`, `tin-nhan`,
`thong-bao`, `bao-cao-cua-toi`, `danh-gia-cua-toi`, `dang-theo-doi`, `doi-mat-khau`.

**Landlord** (`/quan-ly/*`): `tong-quan`, `tin-dang`, `tin-dang/tao`, `tin-dang/:id/sua`,
`tin-dang/:id/thong-ke`, `nguoi-lien-he`, `tin-nhan`, `goi-dich-vu`, `thanh-toan`, `ho-so-chu-tro`.

**Admin/Moderator** (`/admin/*`): `dashboard`, `nguoi-dung`, `chu-tro`, `tin-dang`,
`kiem-duyet`, `bao-cao`, `binh-luan`, `danh-gia`, `danh-muc`, `khu-vuc`, `tien-ich`,
`goi-dich-vu`, `thanh-toan`, `ai/log`, `ai/cau-hinh`, `thong-ke`, `cau-hinh`, `audit-log`.

Route guard: `<ProtectedRoute>` (đăng nhập) + `<RoleRoute roles=[...]>` +
`<PermissionRoute permissions=[...]>`. Admin và Moderator **dùng chung** layout `/admin` nhưng
menu render theo permission — Moderator không thấy mục tài chính/cấu hình `[§1.2]`.

**Luật phân lớp frontend F1–F6** (đối xứng với 8 luật backend ở mục 3):

| # | Luật |
|---|---|
| F1 | `pages/` **không** gọi `axios` trực tiếp — chỉ gọi qua `api/`. |
| F2 | `api/` **không** chứa logic nghiệp vụ — chỉ gọi HTTP và trả `response.data.data`. |
| F3 | `components/` **không** gọi API. Nhận dữ liệu qua props. Ngoại lệ duy nhất: component tự chứa như `ChatbotWidget`, `NotificationBell` — và chúng gọi qua hook, không gọi `axios`. |
| F4 | Chỉ `redux/` giữ state dùng chung nhiều trang (auth, notification count, favorite ids). State của một trang thì để `useState` trong trang đó — **không** nhét mọi thứ vào Redux. |
| F5 | **Không** `dangerouslySetInnerHTML` ở bất kỳ đâu `[§11.1]` — đã bật rule ESLint `react/no-danger: error` để ép ở CI. |
| F6 | Guard chỉ để **điều hướng**, không phải để bảo mật. Backend luôn kiểm tra quyền lại `[§11.2]` — ẩn nút không phải là phân quyền. |

---

## 13. Backup & khôi phục `[§11.5]`

`[§11.5]` yêu cầu *"có kế hoạch khôi phục dữ liệu"* nhưng không cho số. Chốt mục tiêu:

| Chỉ tiêu | Giá trị | Nghĩa là |
|---|---|---|
| **RPO** (mất tối đa bao nhiêu dữ liệu) | ≤ 24 giờ | backup toàn phần hằng ngày |
| **RTO** (khôi phục xong trong bao lâu) | ≤ 2 giờ | restore dump + khởi động lại stack |

- `mysqldump --single-transaction` hằng ngày (không khóa bảng, an toàn với InnoDB).
- Ảnh trong volume `webtro_storage` backup riêng — **dữ liệu ảnh không nằm trong DB dump**.
- Redis **không cần** backup: toàn bộ nội dung là cache + rate-limit counter + blacklist, tái tạo
  được. Mất Redis không mất dữ liệu nghiệp vụ.
- **Không xóa cứng dữ liệu nghiệp vụ** `[§11.5]` — soft delete toàn hệ thống (mục 6.1).

---

## 14. Định nghĩa "Hoàn thành" (Definition of Done)

Một phần được coi là xong khi:
1. Không còn `TODO`, `FIXME`, "demo", "giả sử", code rỗng, hay method ném `UnsupportedOperationException`.
2. Mọi request DTO có validation; mọi business rule trong tài liệu có chỗ thực thi tương ứng.
3. Mọi endpoint có Swagger annotation và trả đúng envelope `[§7.1]`.
4. Không hardcode ngưỡng — đọc từ `SystemConfig`.
5. `docker compose up --build` chạy được toàn hệ thống.
6. Flyway migration khớp 100% với entity (không dùng `ddl-auto=update` ở prod; dùng `validate`).
7. Frontend: có loading, toast, error handling, route guard, responsive.

---

## 15. Tổng hợp thay đổi v1 → v2

23 đề xuất từ `01` mục 15.1 + Phụ lục A của `02` đã được **chấp nhận toàn bộ**. Tóm tắt nơi chúng
được hợp nhất:

| Nhóm | Thay đổi | Mục canonical |
|---|---|---|
| **Sửa lỗi thực chất** | Recommendation 6 → **9 số hạng** + quy tắc chuẩn hóa lại trọng số | 10.2 |
| | Thêm `ResponseTerm` vào công thức uy tín chủ trọ `[§5.7]` | 10.5 |
| | Thêm `AUTO_HIDE_BY_SYSTEM` + `UNHIDE_BY_MODERATOR` `[§5.3]` | 5.1 |
| | Thêm bảng thứ 46 `notification_preferences` `[§11.12]` | 6 |
| | Xóa `NotificationDigestJob` (chưa từng đặc tả), thay bằng `NewMatchingListingNotifyJob` | 11 |
| **Bảo mật** | Chốt nơi lưu token ở client + lý do `csrf().disable()` vẫn đúng | 8 |
| | `family_id` cho refresh token | 8 |
| | Redis fail-open (rate limit) / fail-closed (blacklist) | 8 |
| | Chặn decompression bomb, làm tròn tọa độ, 3 kích thước ảnh | 8 |
| **Kiến trúc** | Luật phụ thuộc 7 (ApplicationEvent) và 8 (không `@ManyToOne` chéo module) | 3 |
| | Package `statemachine/`, `engine/`, `gateway/`, `specification/`, `listener/`, `common/event/`, `storage/` | 3 |
| | Luật phân lớp frontend F1–F6 | 12 |
| **Enum** | +11 enum (`AmenityGroup`, `ContactType`, `AutoHideReason`…) + `NEW_MATCHING_LISTING` | 5 |
| **Config** | 57 → **85** key (+10 rate limit, +6 AI timeout, +5 response rate, +5 notify, +2 khác) | 9 |
| **Vận hành** | Luật viết job (idempotent, zone UTC, không nuốt lỗi) | 11 |
| | RPO ≤ 24h / RTO ≤ 2h | 13 |
| | Chốt cổng host 80 / 8080 / 3307 / 6380 | 1.3 |

**Một điểm không nhận nguyên văn:** `01` mục 15.1 đề xuất cổng backend **8081**; canonical chốt
**8080** cho khớp `docker-compose.yml`, `.env.example` và `README.md` đã dựng. `01` phải sửa theo.
