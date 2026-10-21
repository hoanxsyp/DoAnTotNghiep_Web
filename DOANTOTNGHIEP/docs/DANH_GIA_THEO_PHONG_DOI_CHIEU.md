# Đánh giá theo Phòng: đối chiếu lại với code mới nhất

> Trạng thái: **Bản nháp, CHƯA CHỐT.** Cần quyết định 3 câu hỏi ở mục 9 trước khi code.
> Ngày: 2026-10-21. Đối chiếu trên nhánh `ner-real-data` (đã merge `origin/main` tới commit `61d09fb`).
> Thay thế cho bản nháp `TEMP_THIET_KE_DANH_GIA_THEO_PHONG.md` (2026-10-13, nhánh `fix-user`).

---

## 0. Bối cảnh

Bản nháp ngày 13/10 viết **trước** khi main merge 3 thay đổi làm đổi bài toán khá nhiều:

| Thay đổi trên main | Migration | Ảnh hưởng tới thiết kế cũ |
|---|---|---|
| Luồng thuê phòng `rental_requests` (`PENDING → APPROVED → RENTED → CHECKED_OUT`), tin có trạng thái `RENTED` và quay về `ACTIVE` khi trả phòng | `V23__add_rental_requests.sql`, `V25` | Điều kiện đánh giá chuyển sang "đã thuê thật" |
| Bỏ trust score | `V22__drop_trust_score.sql` | Phần phạt uy tín chủ trọ (RV-12) không còn ý nghĩa |
| Cho phép đánh giá lại sau khi xoá mềm review | `V26__allow_review_recreation_after_soft_delete.sql` | Unique review đổi thành `(user_id, listing_id, active_marker)` |

Kết luận: **khoảng một nửa thiết kế cũ không cần làm nữa**. Phần còn đáng làm là thực thể Phòng (G1 cũ), nhưng phải sửa lại cho khớp với luồng thuê.

Migration mới phải đánh số từ **V28** (hiện tại đến `V27__listing_location_source.sql`).

---

## 1. Các vấn đề cũ, giờ ra sao

| # | Vấn đề trong bản 13/10 | Hiện tại |
|---|---|---|
| P1–P3 | Chỉ cần bấm "Xem SĐT" là được đánh giá; `is_verified_contact` gần như luôn `true` | ✅ **Đã giải quyết.** Muốn đánh giá phải có `rental_request` ở trạng thái `RENTED` hoặc `CHECKED_OUT` (`ReviewServiceImpl.java:159`, `RentalEligibilityGatewayAdapter`). Điều kiện này còn chặt hơn mức VIEWED trong thiết kế cũ |
| P4 | Phòng trống lại thì phải đăng tin mới, mất review | 🟡 **Giải quyết một phần.** Trả phòng thì tin quay `RENTED → ACTIVE` với cùng `listing_id`, nên review vẫn còn (`ListingGatewayAdapter.java:142`, sự kiện `CHECK_OUT`) |
| P5 | Đóng tin rồi đăng lại để né review xấu | 🔴 **Vẫn còn.** `CLOSE` vẫn là trạng thái cuối, xoá mềm vẫn được phép (`ListingStateMachine`) |
| P6 | Tin `EXPIRED` trả 404, không xem được review | 🔴 **Vẫn còn.** Chỉ chủ tin, moderator/admin và người từng thuê được ngoại lệ (`ListingServiceImpl.java:549`) |
| P7 | Frontend không hiện nút "Viết đánh giá" | ✅ Frontend đã gọi `eligibility` (`ListingDetailPage.jsx:468`). Backend vẫn gán cứng `canReview=false` nhưng frontend ghi đè |
| P8 | `GET /api/reviews/my` luôn trả rỗng | ✅ Đã có `findByUserIdAndDeletedAtIsNull` |

### Phần nên bỏ khỏi thiết kế cũ

- Bảng `rental_verifications` và toàn bộ luồng xác minh VIEWED/RENTED, khiếu nại, auto-accept: `rental_requests` đã làm việc này.
- `trust_level` và trọng số 0.3 / 0.6 / 1.0: mọi review giờ đều đến từ người thuê thật nên trọng số như nhau.
- Phạt trust score (RV-12) và bước đối soát trong `TrustScoreRecalcJob`: trust score đã bị bỏ.

---

## 2. Lỗ hổng còn lại (lý do vẫn nên tách Phòng)

1. **Né review bằng đóng/xoá tin rồi đăng lại.** Tin mới có `listing_id` mới, review cũ chỉ còn trên hồ sơ chủ trọ.
2. **Chuyển review tốt sang phòng khác.** Chủ trọ sửa `wardId`/`addressDetail` của tin đã có review. Tin chỉ bị gửi duyệt lại (`RESUBMIT_AFTER_EDIT`), review vẫn đi theo tin (`ListingServiceImpl.java:258`).
3. **Review bị ẩn khi tin `EXPIRED` hoặc `CLOSED`**, vì trang chi tiết trả 404 cho người ngoài.
4. **Chủ trọ quyết định ai được đánh giá.** Chủ trọ không bấm "xác nhận đã thuê", hoặc đóng tin với lý do `RENTED_OUT` mà không qua luồng thuê, thì người thuê không bao giờ đánh giá được. Phương án ở mục 3 **không** giải quyết điểm này (xem câu hỏi mở 3).
5. **Một tin chỉ có tối đa 1 người thuê** (`uk_rental_requests_active_listing`). Nên "khu trọ còn 5 phòng giống nhau" không đăng chung một tin được (xem câu hỏi mở 1).
6. FK `fk_reviews_listings ... ON DELETE CASCADE` (V1): nếu xoá cứng tin thì mất luôn review. Hiện chưa có job nào xoá cứng tin, nhưng nên đổi thành `SET NULL` khi làm migration.

---

## 3. Phương án đầy đủ: tách thực thể Phòng (`rental_units`)

Đặt tên `rental_units` thay vì `rooms`, vì danh mục có cả nhà nguyên căn, căn hộ, ở ghép; ngoài ra `listings.room_count` đã mang nghĩa "số phòng ngủ".

```
landlord ──1:N── rental_units (địa chỉ gốc, số phòng, điểm phòng, address_locked, status ACTIVE/ARCHIVED)
                   ├──1:N── listings          (tối đa 1 tin "sống"; RENTED ↔ ACTIVE giữ như hiện tại)
                   │          └──1:N── rental_requests  (giữ listing_id, thêm rental_unit_id)
                   └──1:N── reviews           (rental_unit_id bắt buộc, listing_id để biết thuê qua tin nào)
```

### 3.1. Quy tắc

| Mã | Quy tắc |
|---|---|
| RU-1 | Mỗi tin bắt buộc thuộc một phòng. Trong wizard đăng tin, chủ trọ chọn phòng có sẵn hoặc tạo phòng mới. |
| RU-2 | Mỗi phòng tối đa 1 tin "sống" (`DRAFT, PENDING, ACTIVE, HIDDEN, NEED_REVIEW, EXPIRED, RENTED, LOCKED`). Tin `CLOSED` có nút "Đăng lại phòng này", tin mới được điền sẵn dữ liệu từ tin gần nhất. Tin `EXPIRED` thì gia hạn (`RENEW`) thay vì tạo tin mới. |
| RU-3 | Địa chỉ của tin lấy từ phòng. Tạm giữ bản sao `listings.ward_id/address_detail/latitude/longitude/location_source` cho search và chatbot (phi chuẩn hoá có chủ đích). |
| RU-4 | Phòng đã có ít nhất 1 review VISIBLE thì khoá `ward_id`, `address_detail`, `unit_label`. |
| RU-5 | Phòng có review hoặc lịch sử thuê thì không xoá được, chỉ lưu trữ (`ARCHIVED`). |
| RU-6 | Phát hiện trùng theo `address_key = ward_id + normalize(address_detail) + normalize(unit_label)`: cùng chủ trọ thì **chặn** và gợi ý phòng có sẵn; khác chủ trọ thì cho tạo nhưng tin đầu tiên vào `NEED_REVIEW` để moderator kiểm tra. |
| RE-1 | Điều kiện đánh giá: từng thuê (`RENTED`/`CHECKED_OUT`) **bất kỳ tin nào của phòng**. |
| RE-2 | Review hiện trên mọi tin của phòng, kèm ghi chú "thuê qua tin tháng MM/YYYY". Tin `EXPIRED`/`CLOSED` không làm ẩn review. |
| RE-3 | Điểm chỉ lưu ở `rental_units` (điểm phòng) và `landlord_profiles` (điểm chủ trọ). Tính lại bằng aggregate query (`AVG`, `COUNT` theo `rental_unit_id`) mỗi khi review thay đổi, thay cho 6 hàm `apply*Aggregate*` cộng trừ tăng dần trong `ReviewServiceImpl`. |
| RE-4 | Giữ nguyên các quy tắc cũ: không tự đánh giá, 1–2 sao bắt buộc nội dung, lọc từ cấm, tối đa 10 review/ngày, sửa/xoá trong 24 giờ. |
| RR-1 | Trạng thái `RENTED` vẫn nằm trên tin; `rental_requests` vẫn tạo theo tin. Lưu thêm `rental_unit_id` để kiểm tra điều kiện đánh giá theo phòng. |

### 3.2. Migration

| File | Nội dung |
|---|---|
| `V28__create_rental_units.sql` | Tạo `rental_units`; backfill 1 tin = 1 phòng (không tự gộp, vì gộp sai còn tệ hơn không gộp); thêm `listings.rental_unit_id` (NOT NULL, FK, index `(rental_unit_id, status)`) |
| `V29__link_reviews_rentals_to_units.sql` | Thêm `rental_unit_id` cho `reviews` và `rental_requests` và backfill từ `listings`; đổi unique review thành `(user_id, rental_unit_id, active_marker)`; đổi `fk_reviews_listings` từ `CASCADE` sang `SET NULL`; tính điểm phòng và điểm chủ trọ từ review hiện có; đặt `address_locked = TRUE` cho phòng có review |
| `V30__drop_listing_rating_columns.sql` | Bỏ `listings.average_rating`, `review_count` (gỡ/viết lại CHECK constraint liên quan trước). Tách riêng để dễ rollback |

Kiểm tra sau migration: `SELECT COUNT(*) FROM listings WHERE rental_unit_id IS NULL` = 0; tổng số review và điểm trung bình khớp trước/sau.

---

## 4. API thay đổi

| Loại | Endpoint | Role |
|---|---|---|
| **Mới** | `GET/POST /api/landlord/rental-units`, `GET/PUT /api/landlord/rental-units/{id}`, `POST /{id}/archive`, `GET /duplicate-check`, `GET /{id}/listing-draft` | LANDLORD, ADMIN |
| **Mới** (tuỳ chọn) | `GET /api/rental-units/{id}/reviews` | Public |
| Đổi body | `POST/PUT /api/listings`: nhận `rentalUnitId` **hoặc** `newRentalUnit{…}`; các trường địa chỉ gửi trong tin bị bỏ qua | LANDLORD, ADMIN |
| Đổi response | `GET /api/listings/{id}`: thêm khối `rentalUnit{id, name, averageRating, reviewCount}` | Tất cả |
| Đổi logic | `GET /api/listings/{id}/reviews` → trả review của phòng; `GET .../reviews/eligibility` và `POST .../reviews` → kiểm tra theo phòng | Tất cả / TENANT |
| Đổi logic | `GET /api/users/{id}/reviews`: truy vấn theo `rental_units.owner_id` | Public |
| Đổi response | `GET /api/rental-requests/my`, `GET /api/landlord/rental-requests`, `GET /api/reviews/my`, `GET /api/admin/reviews`: thêm tên phòng; admin lọc được theo phòng | Theo role hiện tại |
| Không đổi hình dạng | Thẻ tin trong search, chatbot (`ListingDataGatewayAdapter`), recommendation: vẫn trả `averageRating`/`reviewCount`, chỉ đổi nguồn trong mapper/adapter | — |

**Mã lỗi mới (`ErrorCode`):** `RENTAL_UNIT_NOT_FOUND`, `RENTAL_UNIT_DUPLICATE`, `RENTAL_UNIT_ADDRESS_LOCKED`, `RENTAL_UNIT_HAS_LIVE_LISTING`, `RENTAL_UNIT_ARCHIVED`.

### 4.1. Backend bị động tới

| Khu vực | File | Mức |
|---|---|---|
| Migration | `V28`–`V30` (mới) | Lớn |
| Listing | `entity/RentalUnit`, `RentalUnitRepository`, `RentalUnitService(+Impl)`, `LandlordRentalUnitController`, DTO, mapper (**mới**) | Lớn |
| | `Listing.java` (thêm `rentalUnitId`, bỏ `averageRating`/`reviewCount`), `ListingServiceImpl` (tạo/sửa tin theo phòng, RU-2/RU-3, prefill), `ListingMapper` (khối `rentalUnit`, đọc điểm phòng theo lô, tránh N+1) | Vừa |
| | `ListingGatewayAdapter` / `interaction/spi/ListingGateway` (bỏ `updateReviewAggregate`, thêm thông tin phòng), `ListingDataGatewayAdapter` (đọc điểm phòng) | Nhỏ–vừa |
| Interaction | `Review.java`, `ReviewRepository` (finder và aggregate theo phòng), `ReviewServiceImpl` (eligibility/create theo phòng, bỏ 6 hàm `apply*Aggregate*`), `ReviewMapper`, các DTO review | Vừa–lớn |
| Rental | `RentalRequest.java` (+`rentalUnitId`), `RentalEligibilityGateway(+Adapter)` (`hasRentalHistory(unitId, tenantId)`), `RentalRequestResponse` (+tên phòng) | Nhỏ |
| User | `UserGatewayAdapter`, `LandlordDashboardServiceImpl`, các mapper đang đọc điểm chủ trọ | Nhỏ |
| Khác | `ChatbotServiceImpl`, `AdminUserServiceImpl` (đang đọc `averageRating`/`reviewCount`), `ErrorCode`, `DemoDataInitializer` | Nhỏ |

Trước khi chạy `V30`, dùng `grep -rn "average_rating\|review_count\|getAverageRating\|getReviewCount"` trên toàn repo (cả `AI_Module` và native query) để chắc chắn không còn chỗ nào đọc cột cũ.

---

## 5. Màn hình thay đổi

| Màn hình | Thay đổi | Role |
|---|---|---|
| **Mới: "Phòng của tôi"** `/quan-ly/phong` | Danh sách phòng, điểm, tin đang sống, các nút Đăng tin / Đăng lại / Lưu trữ / Sửa | LANDLORD, ADMIN |
| `CreateListingPage` / `EditListingPage` (wizard) | Thêm bước đầu "Chọn / tạo phòng" có cảnh báo trùng địa chỉ; các bước sau hiện địa chỉ chỉ đọc | LANDLORD, ADMIN |
| `MyListingsPage` | Hiện tên phòng; tin `CLOSED` có nút "Đăng lại phòng này" | LANDLORD |
| `ListingDetailPage` | Hiện review của cả phòng, ghi chú "thuê qua tin tháng…"; tin `EXPIRED`/`CLOSED` vẫn xem được review (hoặc dẫn sang hồ sơ chủ trọ) | Mọi người |
| `LandlordProfilePage` | Review kèm tên phòng | Mọi người |
| `RentalRequestsPage` (tenant và landlord) | Hiện tên phòng | TENANT, LANDLORD |
| `MyReviewsPage` | Hiện phòng thay cho tin | TENANT |
| Admin `ReviewsPage`, `ModerationQueuePage`, `AdminListingsPage` | Cột / lọc theo phòng; nhãn "trùng địa chỉ với phòng của chủ trọ khác" khi tin vào `NEED_REVIEW` | MODERATOR, ADMIN |
| `router.jsx`, `menus.js`, `constants` | Thêm route, menu, enum mới | — |
| `api/rentalUnitApi.js` (**mới**), `api/reviewApi.js`, `api/listingApi.js` | Endpoint phòng; body tạo/sửa tin | — |

---

## 6. Mức ảnh hưởng theo role

| Role | Ảnh hưởng |
|---|---|
| **LANDLORD** (nặng nhất) | Đăng tin phải gắn với phòng, có thêm trang quản lý phòng, không còn sửa địa chỉ hay xoá phòng có review được |
| **ADMIN** | Giống LANDLORD (route đang cho ADMIN đăng tin), cộng thêm phần của MODERATOR |
| **MODERATOR** | Thêm việc duyệt tin trùng địa chỉ khác chủ. Ẩn/hiện review thì tính lại điểm cho phòng |
| **TENANT** | Luồng thuê và đánh giá giữ nguyên thao tác, chỉ đổi phạm vi (theo phòng) và cách hiển thị |
| **GUEST** | Chỉ thấy review đầy đủ và ổn định hơn |

---

## 7. Các flow chính bị động tới

1. **Đăng tin:** chọn / tạo phòng → kiểm tra trùng → (khác chủ trọ thì `NEED_REVIEW`) → duyệt.
2. **Sửa tin / sửa phòng:** phòng đã có review thì khoá địa chỉ.
3. **Thuê phòng:** yêu cầu → duyệt → xác nhận thuê → trả phòng. Thao tác giữ nguyên, chỉ lưu thêm `rental_unit_id`.
4. **Đánh giá:** kiểm tra điều kiện theo phòng → tạo review → tính lại điểm phòng và điểm chủ trọ.
5. **Đóng tin rồi đăng lại:** tin cũ `CLOSED` → tin mới cùng phòng tự kế thừa review.
6. **Lưu trữ phòng** thay cho xoá.
7. **Moderator ẩn/hiện review** → tính lại điểm.

---

## 8. Thứ tự làm đề xuất

| Bước | Nội dung | Cỡ |
|---|---|---|
| G0 | Sửa độc lập: `canComment` đang bằng `viewerAuthenticated` nên cho cả MODERATOR (`ListingMapper.java:174`) trong khi backend chặn MODERATOR bình luận | S |
| G1 | V28–V30, entity/service/API `RentalUnit`, tạo/sửa tin theo phòng, review và điểm theo phòng, sửa khoảng 15 chỗ đang đọc `averageRating`/`reviewCount` | L |
| G2 | Frontend: wizard, trang "Phòng của tôi", trang chi tiết tin, phần admin | M–L |
| G3 | Demo data (`DemoDataInitializer`), test, cập nhật docs `00`/`02`/`03`/`04`/`09` | M |

### 8.1. Phương án nhẹ (nếu thiếu thời gian)

Không tạo bảng mới, coi **tin = phòng** (luồng `RENTED ↔ ACTIVE` đã gần như vậy). Chỉ vá 4 chỗ:

1. Thêm sự kiện mở lại tin `CLOSED` (ví dụ `REOPEN: CLOSED → PENDING`), giữ `listing_id`.
2. Chặn xoá tin đã có review.
3. Khoá địa chỉ tin khi đã có review.
4. Cho xem review của tin `EXPIRED`/`CLOSED` (trang chi tiết ở chế độ "không còn cho thuê", hoặc chỉ khối review).

Khoảng 1/3 công sức của phương án đầy đủ. Nhược điểm: không chặn được việc chủ trọ cố tình đăng **tin mới** cho cùng phòng để né review; không có nền để sau này làm khu trọ nhiều phòng.

---

## 9. Câu hỏi cần chốt

1. **Khu trọ nhiều phòng giống nhau:** giữ "1 phòng = 1 unit = 1 tin" (đơn giản, khớp ràng buộc 1 người thuê/tin hiện tại), hay cho một tin đại diện nhiều phòng? Cách sau thì phải dời trạng thái "đang thuê" từ tin sang unit, việc lớn hơn hẳn.
2. **Làm phương án đầy đủ (mục 3) hay phương án nhẹ (mục 8.1)?**
3. **Người thuê bị chủ trọ không xác nhận "đã thuê":** chấp nhận là hạn chế và ghi vào báo cáo, hay vẫn cần một cơ chế khiếu nại tối thiểu (ví dụ người thuê báo cáo qua `reports`, moderator xác nhận thay)?
