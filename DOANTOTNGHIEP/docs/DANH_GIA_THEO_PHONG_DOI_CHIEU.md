# Đánh giá theo Phòng: đối chiếu lại với code mới nhất

> Trạng thái: **ĐÃ TRIỂN KHAI (2026-10-22)** trên nhánh `ner-real-data`, backend + frontend. Các quyết định ở mục 9, cách giữ điểm phòng và điểm tin khớp nhau ở mục 10, kết quả triển khai và những chỗ khác với bản chốt ở mục 11.
> Ngày: 2026-10-21, cập nhật 2026-10-22. Đối chiếu trên nhánh `ner-real-data` (đã merge `origin/main` tới commit `61d09fb`).
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

Migration mới phải đánh số từ **V29** (hiện tại đến `V28__listing_location_source.sql`).

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

Bổ sung sau khi đối chiếu lại code (2026-10-22):

7. **Đổi địa chỉ tin có review mà không cần duyệt lại.** Sửa tin chỉ bị gửi duyệt lại khi tin đang `ACTIVE` (`ListingServiceImpl.java:301`). Chủ trọ có thể ẩn tin (`HIDDEN`), sửa địa chỉ, rồi bỏ ẩn: tin về thẳng `ACTIVE` ở địa chỉ mới, kèm review cũ. RU-4 (khoá địa chỉ ở phòng) phải áp dụng ở **mọi** trạng thái của tin.
8. **Review trên tin đã xoá mềm không thao tác được.** `ListingGatewayAdapter.getBrief` ném 404 với tin đã xoá mềm, nên tác giả không xoá được review, moderator không ẩn/hiện được. Truy vấn review theo chủ trọ lại không lọc tin đã xoá, nên review vẫn hiện trên hồ sơ và vẫn tính vào điểm chủ trọ. Khi làm theo phòng thì các thao tác review lấy thông tin từ phòng, không qua `getBrief(listingId)`.
9. **Điểm cộng trừ tăng dần, không khoá dòng, không có job đối soát.** Ghi chú trong `ReviewServiceImpl` nhắc tới "nightly reconcile" nhưng job này không tồn tại. 2 review ghi cùng lúc có thể làm lệch điểm chủ trọ. Xem mục 10.
10. **Đóng hoặc xoá tin không huỷ các yêu cầu thuê `PENDING`/`APPROVED`**, nên các yêu cầu này bị treo. `closeReason` cũng không được lưu vào DB (chỉ trả về trong response).

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
| RU-2 | Mỗi phòng tối đa 1 tin "sống": mọi trạng thái trừ `CLOSED` và `DELETED` (gồm cả `REJECTED` vì tin bị từ chối vẫn sửa rồi gửi duyệt lại được). Tin `CLOSED` có nút "Đăng lại phòng này": tạo tin **nháp** mới sao chép nội dung, tiện ích và ảnh của tin gần nhất. Tin `EXPIRED` thì gia hạn (`RENEW`) thay vì tạo tin mới. Tạo tin cho phòng khoá dòng phòng (`FOR UPDATE`) để 2 yêu cầu cùng lúc không tạo được 2 tin sống. |
| RU-3 | Địa chỉ của tin lấy từ phòng. Tạm giữ bản sao `listings.ward_id/address_detail/latitude/longitude/location_source` cho search và chatbot (phi chuẩn hoá có chủ đích). Sửa địa chỉ phòng (khi chưa khoá) thì chép sang tin sống của phòng; tin đang `ACTIVE` thì gửi duyệt lại. |
| RU-4 | Phòng đã có ít nhất 1 review VISIBLE thì khoá `ward_id`, `address_detail`, `unit_label`, **bất kể tin đang ở trạng thái nào** (kể cả `HIDDEN`, xem lỗ hổng 7). Khoá **không tự mở lại** khi review bị ẩn/xoá (tránh lách: ẩn review → đổi địa chỉ). Chỉ ADMIN sửa được địa chỉ phòng đã khoá. Toạ độ không bị khoá. |
| RU-5 | Phòng có review hoặc lịch sử thuê thì không xoá được, chỉ lưu trữ (`ARCHIVED`). |
| RU-6 | Phát hiện trùng trong cùng phường theo `normalize(address_detail) + normalize(unit_label)` (`AddressNormalizer`: bỏ dấu, đ→d, chữ thường, bỏ dấu câu và từ đệm như "số", "phòng", "P."; đổi chữ viết tắt không gây nhầm về từ đầy đủ: "ng" → ngõ, "ngh" → ngách, "đ"/"đg" → đường; tách từ chỉ loại địa chỉ dính số như "ngõ62". Giữ nguyên thứ tự và các từ ngõ/ngách/đường nên "ngõ 12 số 62" vẫn khác "số 12 ngõ 62". Cố ý không đổi "p", "h", "k" vì dễ nhầm). So khớp làm trong Java trên các phòng cùng phường, không lưu cột `address_key`. Cùng chủ trọ thì **chặn** và gợi ý phòng có sẵn (riêng người thuê đăng tin ở ghép: tự dùng lại phòng cũ của chính họ, vì họ không có màn chọn phòng); khác chủ trọ thì cho tạo nhưng gắn cờ `duplicateAddress` lên tin để hàng duyệt hiện nhãn "trùng địa chỉ với phòng của chủ trọ khác" khi tin đang `PENDING`. Không dùng `NEED_REVIEW`, vì máy trạng thái chỉ cho vào `NEED_REVIEW` từ `ACTIVE`. Dò trùng chỉ so khớp chuỗi sau chuẩn hoá nên không chặn tuyệt đối (ghi vào hạn chế của báo cáo). |
| RE-1 | Điều kiện đánh giá: từng thuê (`RENTED`/`CHECKED_OUT`) **bất kỳ tin nào của phòng**. |
| RE-2 | Review hiện trên mọi tin của phòng, kèm ghi chú "thuê qua tin tháng MM/YYYY". Tin `EXPIRED`/`CLOSED` không làm ẩn review. |
| RE-3 | Nguồn điểm gốc là `rental_units` (điểm phòng) và `landlord_profiles` (điểm chủ trọ). `listings.average_rating/review_count` được **giữ lại làm bản sao chỉ đọc** của điểm phòng. Tính lại bằng aggregate query (`AVG`, `COUNT` theo `rental_unit_id`) mỗi khi review thay đổi, thay cho 6 hàm `apply*Aggregate*` cộng trừ tăng dần trong `ReviewServiceImpl`. Cách đảm bảo phòng và tin luôn khớp: mục 10. |
| RE-4 | Giữ nguyên các quy tắc cũ: không tự đánh giá, 1–2 sao bắt buộc nội dung, lọc từ cấm, tối đa 10 review/ngày. (Bản trước ghi "sửa/xoá trong 24 giờ" nhưng code **không có** quy tắc này; không làm thêm.) |
| RE-5 | Mỗi người thuê có 1 review cho mỗi phòng, kể cả khi thuê phòng đó nhiều lần; muốn đổi ý kiến thì sửa review cũ. |
| RR-1 | Trạng thái `RENTED` vẫn nằm trên tin; `rental_requests` vẫn tạo theo tin. Lưu thêm `rental_unit_id` để kiểm tra điều kiện đánh giá theo phòng. |

### 3.2. Migration

| File | Nội dung |
|---|---|
| `V29__create_rental_units.sql` | Tạo `rental_units`; backfill 1 tin = 1 phòng (không tự gộp, vì gộp sai còn tệ hơn không gộp); thêm `listings.rental_unit_id` (NOT NULL, FK, index `(rental_unit_id, status)`); thêm `listings.duplicate_address` (BOOLEAN, mặc định FALSE) cho cờ trùng địa chỉ ở RU-6 |
| `V30__link_reviews_rentals_to_units.sql` | Thêm `rental_unit_id` cho `reviews` và `rental_requests` và backfill từ `listings`; đổi unique review thành `(user_id, rental_unit_id, active_marker)`; đổi `fk_reviews_listings` từ `CASCADE` sang `SET NULL` (cho `reviews.listing_id` được `NULL`); tính điểm phòng và điểm chủ trọ từ review hiện có; **chép điểm phòng sang mọi tin của phòng**; đặt `address_locked = TRUE` cho phòng có review |
| ~~`V31__drop_listing_rating_columns.sql`~~ | **Không làm** (quyết định A, mục 9): giữ 2 cột trên `listings` làm bản sao điểm phòng |

Kiểm tra sau migration:
- `SELECT COUNT(*) FROM listings WHERE rental_unit_id IS NULL` = 0.
- Tổng số review và điểm trung bình khớp trước/sau.
- Câu truy vấn kiểm tra lệch điểm ở mục 10.4 trả về 0 dòng.

---

## 4. API thay đổi

| Loại | Endpoint | Role |
|---|---|---|
| **Mới** | `GET/POST /api/landlord/rental-units`, `GET/PUT /api/landlord/rental-units/{id}`, `POST /{id}/archive`, `POST /{id}/unarchive`, `POST /{id}/relist`, `GET /duplicate-check` | LANDLORD, ADMIN |
| Đổi body | `POST /api/listings`: thêm `rentalUnitId`, `unitLabel`, `unitName`. Có `rentalUnitId` thì địa chỉ lấy từ phòng; không có thì tạo phòng mới từ địa chỉ gửi kèm (giữ tương thích với form cũ). `PUT /api/listings/{id}`: thêm `unitLabel`; địa chỉ gửi kèm sẽ đổi địa chỉ **phòng** (bị chặn nếu phòng đã khoá) | TENANT, LANDLORD, ADMIN |
| Đổi response | `GET /api/listings/{id}`: thêm khối `rentalUnit{id, name, unitLabel, averageRating, reviewCount, addressLocked}`; `GET /api/listings/my`: thêm `rentalUnitId`, `rentalUnitName`, hành động `RELIST`; `GET /api/admin/listings`, `/moderation-queue`: thêm `rentalUnitId`, `rentalUnitName`, `duplicateAddress` | Tất cả |
| Đổi logic | `GET /api/listings/{id}/reviews` → trả review của phòng; `GET .../reviews/eligibility` và `POST .../reviews` → kiểm tra theo phòng | Tất cả / TENANT |
| Đổi logic | `GET /api/users/{id}/reviews`: truy vấn theo `rental_units.owner_id`. **Mở công khai** trong `SecurityConfig` (trước đây bắt đăng nhập dù tài liệu ghi Public) vì hồ sơ chủ trọ là nơi khách xem review của các tin đã đóng | Public |
| Đổi response | `GET /api/rental-requests/my`, `GET /api/landlord/rental-requests`, `GET /api/reviews/my`, `GET /api/admin/reviews`: thêm tên phòng; admin lọc được theo phòng | Theo role hiện tại |
| Không đổi hình dạng | Thẻ tin trong search, chatbot (`ListingDataGatewayAdapter`), recommendation: vẫn trả `averageRating`/`reviewCount`, chỉ đổi nguồn trong mapper/adapter | — |

**Mã lỗi mới (`ErrorCode`):** `RENTAL_UNIT_NOT_FOUND`, `RENTAL_UNIT_FORBIDDEN`, `RENTAL_UNIT_DUPLICATE`, `RENTAL_UNIT_ADDRESS_LOCKED`, `RENTAL_UNIT_HAS_LIVE_LISTING`, `RENTAL_UNIT_ARCHIVED`, `RENTAL_UNIT_NO_LISTING_TO_RELIST`, `RENTAL_UNIT_ADDRESS_REQUIRED`.

### 4.1. Backend bị động tới

| Khu vực | File | Mức |
|---|---|---|
| Migration | `V29`–`V30` (mới) | Lớn |
| Listing | `entity/RentalUnit`, `RentalUnitRepository`, `RentalUnitService(+Impl)`, `LandlordRentalUnitController`, DTO, mapper (**mới**) | Lớn |
| | `Listing.java` (thêm `rentalUnitId`; `averageRating`/`reviewCount` đổi thành `@Column(insertable = false, updatable = false)`, bỏ setter, xem mục 10.3), `ListingServiceImpl` (tạo/sửa tin theo phòng, RU-2/RU-3, prefill, huỷ yêu cầu thuê treo khi đóng/xoá tin), `ListingMapper` (khối `rentalUnit`) | Vừa |
| | `ListingGatewayAdapter` / `interaction/spi/ListingGateway` (bỏ `updateReviewAggregate`, thêm thông tin phòng). `ListingDataGatewayAdapter` **không đổi** vì vẫn đọc cột bản sao | Nhỏ |
| Interaction | `Review.java`, `ReviewRepository` (finder và aggregate theo phòng; review theo chủ trọ join `rental_units.owner_id`), `ReviewSpecifications` (`landlordIdEq` theo phòng, thêm `rentalUnitIdEq`), `ReviewServiceImpl` (eligibility/create theo phòng, bỏ 6 hàm `apply*Aggregate*`, gọi `ReviewRatingRecalculator`), `ReviewRatingRecalculator` (**mới**, mục 10), `ReviewMapper`, các DTO review | Vừa–lớn |
| Rental | `RentalRequest.java` (+`rentalUnitId`), `RentalEligibilityGateway(+Adapter)` (`hasRentalHistory(unitId, tenantId)`), `RentalRequestResponse` (+tên phòng) | Nhỏ |
| User | `UserGatewayAdapter`, `LandlordDashboardServiceImpl`, các mapper đang đọc điểm chủ trọ | Nhỏ |
| Khác | `AdminUserServiceImpl` (lọc theo phòng), `ErrorCode`, `DemoDataInitializer`, `scheduler/RatingReconcileJob` (**mới**, mục 10.4) | Nhỏ |
| Chatbot | Logic **không đổi** (thẻ tin đọc `averageRating` qua `ListingMapper`, vẫn là cột bản sao). Chỉ sửa câu mẫu `HOW_TO_POST` trong `RuleBasedChatbotEngine.java:186` thêm bước "chọn hoặc tạo phòng", và cập nhật `TEST_CASES_CHATBOT.md` | Nhỏ |

---

## 5. Màn hình thay đổi

| Màn hình | Thay đổi | Role |
|---|---|---|
| **Mới: "Phòng của tôi"** `/quan-ly/phong` | Danh sách phòng, điểm, tin đang sống, các nút Đăng tin / Đăng lại / Lưu trữ / Sửa | LANDLORD, ADMIN |
| `CreateListingPage` / `EditListingPage` (wizard) | Bước đầu tiên là "Phòng & địa chỉ" (`RentalUnitStep`): chọn phòng có sẵn hoặc tạo phòng mới, dò trùng trước khi sang bước sau, sửa tin của phòng đã khoá thì địa chỉ chỉ đọc. Mở từ "Phòng của tôi" thì chọn sẵn phòng (`?rentalUnitId=`) | TENANT, LANDLORD, ADMIN |
| `MyListingsPage` | Hiện tên phòng; tin `CLOSED` có nút "Đăng lại phòng này" | LANDLORD |
| `ListingDetailPage` | Hiện review của cả phòng, ghi chú "thuê qua tin tháng…"; tin `EXPIRED`/`CLOSED` vẫn xem được review (hoặc dẫn sang hồ sơ chủ trọ) | Mọi người |
| `LandlordProfilePage` | **Thêm** danh sách review (trước đây chỉ có điểm trung bình), mỗi review kèm tên phòng | Mọi người |
| `RentalRequestsPage` (tenant và landlord) | Hiện tên phòng | TENANT, LANDLORD |
| `MyReviewsPage` | Hiện phòng thay cho tin | TENANT |
| Admin `ReviewsPage`, `ModerationQueuePage`, `AdminListingsPage` | Cột / lọc theo phòng; nhãn "trùng địa chỉ với phòng của chủ trọ khác" khi tin đang `PENDING` | MODERATOR, ADMIN |
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

1. **Đăng tin:** chọn / tạo phòng → kiểm tra trùng → (khác chủ trọ thì gắn cờ trùng địa chỉ) → duyệt.
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
| G1 | V29–V30, entity/service/API `RentalUnit`, tạo/sửa tin theo phòng, review và điểm theo phòng (`ReviewRatingRecalculator`, `RatingReconcileJob`) | L |
| G2 | Frontend: wizard, trang "Phòng của tôi", trang chi tiết tin, phần admin | M–L |
| G3 | Demo data (`DemoDataInitializer`), test, cập nhật docs `00`/`02`/`03`/`04`/`09` | M |

### 8.1. Phương án nhẹ (đã cân nhắc, **không chọn**; giữ lại để ghi vào báo cáo)

Không tạo bảng mới, coi **tin = phòng** (luồng `RENTED ↔ ACTIVE` đã gần như vậy). Chỉ vá 4 chỗ:

1. Thêm sự kiện mở lại tin `CLOSED` (ví dụ `REOPEN: CLOSED → PENDING`), giữ `listing_id`.
2. Chặn xoá tin đã có review.
3. Khoá địa chỉ tin khi đã có review.
4. Cho xem review của tin `EXPIRED`/`CLOSED` (trang chi tiết ở chế độ "không còn cho thuê", hoặc chỉ khối review).

Khoảng 1/3 công sức của phương án đầy đủ. Nhược điểm: không chặn được việc chủ trọ cố tình đăng **tin mới** cho cùng phòng để né review; không có nền để sau này làm khu trọ nhiều phòng.

---

## 9. Quyết định đã chốt (2026-10-22)

| # | Câu hỏi | Quyết định |
|---|---|---|
| 1 | Khu trọ nhiều phòng giống nhau | Giữ **1 tin = 1 phòng**. Ràng buộc 1 người thuê mỗi tin và luồng `RENTED ↔ ACTIVE` đều dựa trên giả định này |
| 2 | Phương án đầy đủ hay nhẹ | **Phương án đầy đủ** `rental_units` (mục 3) |
| 3 | Chủ trọ không xác nhận "đã thuê" | **Chấp nhận là hạn chế**, ghi vào báo cáo. Không làm cơ chế khiếu nại |
| A | Bỏ cột điểm trên `listings` (V31)? | **Không bỏ.** Giữ làm bản sao chỉ đọc của điểm phòng, **bắt buộc luôn khớp** với `rental_units` (mục 10). Search, chatbot, gợi ý phòng không phải sửa |
| B | Phòng trùng địa chỉ với chủ trọ khác | Cho tạo, gắn cờ trùng địa chỉ trong hàng duyệt khi tin `PENDING`; không dùng `NEED_REVIEW` (RU-6) |
| C | Thuê cùng phòng nhiều lần | 1 review cho mỗi phòng, sửa được (RE-5) |

---

## 10. Giữ điểm phòng và điểm tin luôn thống nhất (quyết định A)

### 10.1. Nguyên tắc

- **Nguồn gốc duy nhất** là `rental_units.average_rating/review_count`. `listings.average_rating/review_count` chỉ là bản sao, không có code nào được tự tính hay ghi riêng vào đó.
- **Chỉ một đường ghi:** mọi thay đổi điểm đi qua `ReviewRatingRecalculator`, chạy **trong cùng transaction** với thay đổi review. Review lỗi thì điểm cũng rollback theo.

### 10.2. `ReviewRatingRecalculator.recalc(rentalUnitId)`

Được gọi sau mỗi thao tác làm thay đổi tập review VISIBLE: tạo, sửa số sao, tác giả xoá, moderator ẩn, admin ẩn, moderator hiện lại.

1. Khoá dòng hồ sơ chủ trọ rồi khoá dòng phòng (`SELECT … FOR UPDATE`), **trước** khi thay đổi review. Các thay đổi review của cùng một chủ trọ chạy lần lượt, không tính trên số liệu cũ.
2. Tính `COUNT(*)`, `SUM(rating)` của review `VISIBLE`, chưa xoá, theo `rental_unit_id`, bằng **đọc khoá** (`LOCK IN SHARE MODE`). Ở mức REPEATABLE READ, đọc thường dùng ảnh chụp từ đầu transaction nên có thể bỏ sót review vừa được transaction khác commit trong lúc mình chờ khoá; đọc khoá luôn thấy bản mới nhất. Trung bình làm tròn 2 chữ số, HALF_UP.
3. Ghi vào `rental_units`.
4. Chép sang **mọi tin của phòng** bằng một câu lệnh, kể cả tin `CLOSED`/`EXPIRED`/đã xoá mềm:
   `UPDATE listings SET average_rating = ?, review_count = ? WHERE rental_unit_id = ?`
5. Tính lại điểm chủ trọ từ toàn bộ review VISIBLE của các phòng thuộc chủ trọ đó (đọc khoá). Chủ phòng không có hồ sơ chủ trọ (người thuê đăng tin ở ghép) thì bỏ qua bước này.

Thứ tự khoá là **chủ trọ trước, phòng sau** (bản chốt ghi ngược lại, đã sửa khi triển khai). Bước 5 đọc khoá review của *mọi* phòng thuộc chủ trọ; nếu khoá phòng trước thì 2 review ghi cùng lúc vào 2 phòng của cùng chủ trọ sẽ chờ nhau (deadlock). Khoá chủ trọ trước loại bỏ vòng chờ này.

### 10.3. Chặn ghi lệch từ chỗ khác

- `Listing.java`, `RentalUnit.java` và `LandlordProfile.java`: các trường điểm (và `rental_units.address_locked`) đặt `@Column(insertable = false, updatable = false)` và bỏ setter; chỉ ghi qua câu `UPDATE` riêng (`RentalUnitRepository.applyRating`, `ListingRepository.copyRatingFromUnit`, `LandlordProfileRepository.applyRating`). Hibernate sẽ không bao giờ ghi đè 2 cột này khi lưu tin vì lý do khác (sửa tin, đổi trạng thái, tăng lượt xem…). Đây cũng là điều kiện để câu `UPDATE` hàng loạt ở bước 4 không bị entity cũ trong bộ nhớ ghi đè.
- Bỏ `ListingGateway.updateReviewAggregate` và `ListingGatewayAdapter.updateReviewAggregate`.
- **Tin mới của một phòng** (đăng lại phòng, hoặc tin đầu tiên của phòng đã có review): ngay sau khi lưu tin, gọi `ListingRepository.copyRatingFromUnit(rentalUnitId)` (chính là bước 4) để tin mới mang điểm hiện tại của phòng, không bắt đầu từ 0.

### 10.4. Lưới an toàn

Câu truy vấn phát hiện lệch:

```sql
SELECT l.id, l.average_rating, u.average_rating, l.review_count, u.review_count
FROM listings l
JOIN rental_units u ON u.id = l.rental_unit_id
WHERE l.average_rating <> u.average_rating
   OR l.review_count   <> u.review_count;
```

- Dùng ngay sau migration V30 (phải trả về 0 dòng).
- `RatingReconcileJob` chạy hằng đêm: tính lại điểm mọi phòng có review, chép sang tin, ghi log cảnh báo nếu phát hiện lệch. Job này cũng thay cho "nightly reconcile" mà ghi chú cũ trong `ReviewServiceImpl` nhắc tới nhưng chưa từng có.
- Test tích hợp: sau mỗi thao tác review (tạo, sửa, xoá, ẩn, hiện, đăng lại phòng), câu truy vấn trên trả về 0 dòng.

---

## 11. Kết quả triển khai (2026-10-22)

### 11.1. Đã kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| `mvn test` | 32 test qua (7 test cũ sửa cho khớp + 25 test mới: `ReviewServiceImplTest`, `ReviewRatingRecalculatorTest`, `RentalUnitSupportTest`, `AddressNormalizerTest`, `ListingWithdrawnListenerTest`) |
| V1→V30 trên MySQL 8.4 với dữ liệu thử (tin đang đăng, tin đã đóng, tin đã xoá mềm, review ẩn/xoá, yêu cầu thuê) | Điểm phòng, bản sao điểm trên tin, điểm chủ trọ, cờ khoá địa chỉ, phòng lưu trữ của tin đã xoá đều đúng; `updated_at` của tin giữ nguyên; 0 tin lệch điểm |
| Khởi động backend trên DB trống | Flyway chạy 29 migration, Hibernate `validate` khớp schema, dữ liệu demo gieo qua `ReviewRatingRecalculator` (52 tin ↔ 52 phòng, 0 lệch) |
| Kịch bản API đầu-cuối (34 bước) | Thuê → trả phòng → đánh giá → đóng tin (yêu cầu thuê đang chờ tự kết thúc, lưu `close_reason`) → đăng lại → review cũ hiện trên tin mới kèm "thuê qua tin…" → chặn review lần 2 / đăng lại khi còn tin sống / đổi địa chỉ phòng đã khoá / tạo phòng trùng → moderator ẩn, hiện review → điểm phòng, tin, chủ trọ khớp |
| Frontend | `vite build` và `eslint` các file sửa đều sạch. **Chưa** bấm thử trên trình duyệt |

### 11.2. Khác với bản chốt

- Thứ tự khoá đổi thành chủ trọ → phòng (mục 10.2).
- Không có cột `address_key`; so khớp trong Java (RU-6).
- `GET /{id}/listing-draft` thay bằng `POST /{id}/relist` tạo luôn tin nháp có ảnh, tiện ích.
- Không có `newRentalUnit{…}` trong body tạo tin; dùng lại các trường địa chỉ sẵn có + `unitLabel`/`unitName`, nên form cũ và người thuê đăng tin ở ghép vẫn chạy.
- Người thuê đăng lại tin ở ghép cùng địa chỉ thì tự dùng lại phòng cũ.

### 11.3. Lỗi có sẵn phát hiện và đã sửa trong lúc làm

1. Nút "Đóng tin" ở `MyListingsPage` gửi `closeReason` trong khi backend nhận `reason` (bắt buộc), nên đóng tin từ giao diện luôn bị báo thiếu lý do.
2. Tạo slug chỉ kiểm tra trùng với tin chưa xoá, trong khi `uk_listings_slug` áp cho cả tin đã xoá mềm. Đăng tin trùng tiêu đề với tin đã xoá gây lỗi 500.
3. `GET /api/users/{id}/reviews` không nằm trong danh sách API công khai.
4. `LandlordProfilePage` không có danh sách review.
5. Ghi chú trong `ChatbotServiceImpl` còn nhắc PhoBERT (đã đổi sang ViSoBERT).

### 11.4. Còn lại

- Bấm thử giao diện trên trình duyệt (wizard đăng tin, "Phòng của tôi", đăng lại, trang admin).
- Cập nhật `00`/`02`/`03`/`04`/`09` theo mục 3–5 của tài liệu này.
- Hạn chế cần ghi vào báo cáo: chủ trọ không xác nhận "đã thuê" thì người thuê không đánh giá được (câu hỏi 3); dò trùng địa chỉ chỉ so khớp chuỗi.
