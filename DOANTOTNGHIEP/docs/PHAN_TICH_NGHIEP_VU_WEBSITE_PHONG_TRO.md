# Tài liệu phân tích nghiệp vụ Website quảng cáo và tìm kiếm phòng trọ

## 0. Tổng quan hệ thống

### 0.1. Mục tiêu

Hệ thống là website trung gian giúp kết nối giữa:

- Chủ trọ, người đăng tin cho thuê.
- Người cần thuê phòng, căn hộ, nhà nguyên căn, homestay.
- Người cần tìm ở ghép.
- Người muốn cho người khác ở ghép.
- Admin/Moderator quản lý và kiểm duyệt hệ thống.

Website hỗ trợ người dùng đăng tin, tìm kiếm, lọc, lưu tin, liên hệ, đánh giá, bình luận và báo cáo vi phạm. Hệ thống có thêm 4 module AI ở mức ứng dụng thực tế:

1. Phân tích cảm xúc bình luận.
2. Gợi ý tin đăng phù hợp.
3. Chatbot hỗ trợ tìm trọ.
4. AI dự đoán giá thuê.

### 0.2. Phạm vi phù hợp đồ án

Hệ thống cần đủ lớn để thể hiện năng lực phân tích, thiết kế và triển khai nhưng vẫn thực tế. Do đó:

- Tập trung vào nghiệp vụ đăng tin, tìm kiếm, liên hệ và quản trị.
- Thanh toán có thể mô phỏng hoặc tích hợp cổng thanh toán sandbox.
- AI ở mức hỗ trợ quyết định, không thay thế hoàn toàn người kiểm duyệt.
- Không triển khai đặt cọc, ký hợp đồng điện tử phức tạp hoặc xác minh giấy tờ nhà đất bằng AI.

### 0.3. Loại tin đăng

| Loại tin | Mô tả | Ghi chú nghiệp vụ |
|---|---|---|
| Phòng trọ | Phòng thuê riêng trong dãy trọ hoặc nhà cho thuê | Loại chính của hệ thống |
| Chung cư mini | Căn nhỏ trong tòa chung cư mini | Có thể có thang máy, bảo vệ, nội thất |
| Căn hộ | Căn hộ chung cư hoặc dịch vụ | Giá thường cao hơn phòng trọ |
| Nhà nguyên căn | Cho thuê cả căn nhà | Có số phòng, số tầng |
| Homestay cho thuê | Thuê theo tháng hoặc dài hạn | Không tập trung thuê theo ngày |
| Ở ghép | Người cần tìm phòng để ghép hoặc tìm người ghép | Có thêm giới tính, số người, quy định sinh hoạt |
| Mặt bằng nhỏ | Mặt bằng kinh doanh nhỏ | Có thể đưa vào nếu hệ thống muốn mở rộng nhẹ |

### 0.4. Trạng thái tổng quát của tin đăng

| Trạng thái | Ý nghĩa |
|---|---|
| Draft | Chủ trọ lưu nháp, chưa gửi đăng |
| Pending | Chờ duyệt |
| Active | Đang hiển thị công khai |
| Rejected | Bị từ chối duyệt |
| Hidden | Chủ trọ tự ẩn |
| Expired | Hết hạn hiển thị |
| Closed | Đã cho thuê hoặc không còn nhu cầu |
| Locked | Bị khóa do vi phạm |
| NeedReview | Cần Admin/Moderator kiểm tra |

---

## 1. Phân tích Actor

### 1.1. Danh sách Actor

| Actor | Mô tả | Vai trò chính |
|---|---|---|
| Khách chưa đăng nhập | Người truy cập website chưa có tài khoản hoặc chưa đăng nhập | Xem, tìm kiếm, lọc tin công khai |
| Người thuê | Người có tài khoản, cần tìm phòng hoặc tìm ở ghép | Tìm kiếm, lưu tin, liên hệ, bình luận, đánh giá, báo cáo |
| Chủ trọ | Người có tài khoản, đăng tin cho thuê | Đăng tin, quản lý tin, nhận liên hệ, mua gói đẩy tin |
| Người cho ở ghép | Người có phòng/căn hộ và muốn tìm người ở chung | Đăng tin ở ghép, quản lý người liên hệ |
| Người cần ở ghép | Người tìm phòng hoặc người cùng thuê | Tìm kiếm tin ở ghép, liên hệ, lưu tin |
| Moderator | Nhân sự kiểm duyệt nội dung | Duyệt tin, xử lý báo cáo, kiểm tra bình luận |
| Admin | Quản trị toàn bộ hệ thống | Quản lý người dùng, tin, thanh toán, cấu hình, thống kê, AI |
| Hệ thống AI | Tác nhân xử lý tự động | Phân tích bình luận, gợi ý, chatbot, dự đoán giá |
| Payment Gateway | Cổng thanh toán bên thứ ba hoặc module mô phỏng | Xử lý thanh toán gói dịch vụ |
| Email/SMS/Push Service | Dịch vụ gửi thông báo | Gửi email, OTP, thông báo hệ thống |

### 1.2. Quyền của từng Actor

#### Khách chưa đăng nhập

- Xem trang chủ.
- Tìm kiếm và lọc tin công khai.
- Xem chi tiết tin đăng đang Active.
- Xem thông tin cơ bản của chủ trọ.
- Sử dụng chatbot ở mức cơ bản.
- Đăng ký, đăng nhập.
- Không được lưu tin, bình luận, đánh giá, báo cáo hoặc xem đầy đủ thông tin liên hệ nếu hệ thống yêu cầu đăng nhập.

#### Người thuê

- Cập nhật hồ sơ cá nhân.
- Tìm kiếm, lọc, xem chi tiết tin đăng.
- Lưu hoặc bỏ lưu tin.
- Xem lịch sử đã xem.
- Liên hệ chủ trọ qua số điện thoại, chat nội bộ hoặc form liên hệ.
- Bình luận dưới tin đăng.
- Đánh giá tin hoặc chủ trọ sau khi có tương tác hợp lệ.
- Báo cáo tin vi phạm.
- Theo dõi chủ trọ.
- Sử dụng chatbot đầy đủ hơn dựa trên lịch sử và nhu cầu.
- Nhận gợi ý tin đăng cá nhân hóa.

#### Chủ trọ

- Có toàn bộ quyền cơ bản của người thuê nếu hệ thống dùng chung tài khoản.
- Tạo, sửa, gửi duyệt, ẩn, đóng, xóa mềm tin đăng.
- Gia hạn tin.
- Mua gói đẩy tin.
- Xem danh sách người đã liên hệ.
- Phản hồi bình luận.
- Xem thống kê tin đăng: lượt xem, lượt lưu, lượt liên hệ.
- Xem gợi ý giá thuê khi đăng hoặc sửa tin.
- Nhận cảnh báo khi tin có nhiều bình luận tiêu cực hoặc bị báo cáo.

#### Moderator

- Xem danh sách tin chờ duyệt.
- Duyệt hoặc từ chối tin đăng.
- Xem danh sách tin bị báo cáo.
- Xử lý bình luận vi phạm.
- Đánh dấu tin cần chỉnh sửa.
- Tạm ẩn tin.
- Gửi cảnh báo cho chủ trọ.
- Không quản lý cấu hình hệ thống, gói dịch vụ, doanh thu hoặc phân quyền Admin.

#### Admin

- Quản lý toàn bộ người dùng và vai trò.
- Quản lý chủ trọ, tin đăng, danh mục, khu vực, tiện ích.
- Quản lý gói dịch vụ, thanh toán, khuyến mãi.
- Quản lý báo cáo vi phạm, khiếu nại, bình luận, đánh giá.
- Quản lý cấu hình AI: ngưỡng cảnh báo, trọng số điểm uy tín, log xử lý AI.
- Xem dashboard thống kê.
- Khóa hoặc mở khóa tài khoản.
- Khóa hoặc mở khóa tin đăng.
- Cấu hình SEO, banner, nội dung tĩnh nếu cần.

---

## 2. Danh sách toàn bộ chức năng theo module

### 2.1. Authentication & Authorization

| Mã | Chức năng | Actor |
|---|---|---|
| AUTH-01 | Đăng ký tài khoản | Khách |
| AUTH-02 | Đăng nhập | Khách |
| AUTH-03 | Đăng xuất | Người dùng |
| AUTH-04 | Quên mật khẩu | Người dùng |
| AUTH-05 | Đổi mật khẩu | Người dùng |
| AUTH-06 | Xác thực email/số điện thoại | Người dùng |
| AUTH-07 | Phân quyền theo vai trò | Admin |
| AUTH-08 | Khóa/mở khóa tài khoản | Admin |

### 2.2. User & Profile

| Mã | Chức năng | Actor |
|---|---|---|
| USER-01 | Xem hồ sơ cá nhân | Người dùng |
| USER-02 | Cập nhật hồ sơ cá nhân | Người dùng |
| USER-03 | Quản lý thông tin liên hệ | Người dùng |
| USER-04 | Xem hồ sơ chủ trọ | Người thuê |
| USER-05 | Theo dõi/bỏ theo dõi chủ trọ | Người thuê |
| USER-06 | Quản lý trạng thái xác thực chủ trọ | Admin/Moderator |

### 2.3. Listing

| Mã | Chức năng | Actor |
|---|---|---|
| LIST-01 | Tạo tin nháp | Chủ trọ |
| LIST-02 | Đăng tin | Chủ trọ |
| LIST-03 | Sửa tin | Chủ trọ |
| LIST-04 | Gửi duyệt tin | Chủ trọ |
| LIST-05 | Duyệt/từ chối tin | Moderator/Admin |
| LIST-06 | Ẩn tin | Chủ trọ |
| LIST-07 | Đóng tin | Chủ trọ |
| LIST-08 | Xóa mềm tin | Chủ trọ/Admin |
| LIST-09 | Gia hạn tin | Chủ trọ |
| LIST-10 | Xem thống kê tin | Chủ trọ |
| LIST-11 | Quản lý ảnh tin đăng | Chủ trọ |
| LIST-12 | Quản lý tiện ích của tin | Chủ trọ |

### 2.4. Search & Discovery

| Mã | Chức năng | Actor |
|---|---|---|
| SRCH-01 | Tìm kiếm theo từ khóa | Tất cả |
| SRCH-02 | Lọc theo khu vực | Tất cả |
| SRCH-03 | Lọc theo giá | Tất cả |
| SRCH-04 | Lọc theo diện tích | Tất cả |
| SRCH-05 | Lọc theo loại tin | Tất cả |
| SRCH-06 | Lọc theo tiện ích | Tất cả |
| SRCH-07 | Lọc ở ghép theo giới tính/số người | Tất cả |
| SRCH-08 | Sắp xếp kết quả | Tất cả |
| SRCH-09 | Xem tin liên quan | Tất cả |

### 2.5. Favorite, History & Follow

| Mã | Chức năng | Actor |
|---|---|---|
| FAV-01 | Lưu tin | Người thuê |
| FAV-02 | Bỏ lưu tin | Người thuê |
| FAV-03 | Xem danh sách tin đã lưu | Người thuê |
| HIST-01 | Ghi nhận lịch sử xem | Hệ thống |
| HIST-02 | Xem lịch sử xem | Người thuê |
| FOLLOW-01 | Theo dõi chủ trọ | Người thuê |
| FOLLOW-02 | Nhận thông báo từ chủ trọ đã theo dõi | Người thuê |

### 2.6. Contact & Conversation

| Mã | Chức năng | Actor |
|---|---|---|
| CONT-01 | Hiển thị thông tin liên hệ | Người thuê |
| CONT-02 | Gửi yêu cầu liên hệ | Người thuê |
| CONT-03 | Chat nội bộ giữa người thuê và chủ trọ | Người dùng |
| CONT-04 | Chủ trọ quản lý người liên hệ | Chủ trọ |
| CONT-05 | Ghi nhận lượt liên hệ | Hệ thống |

### 2.7. Comment & Review

| Mã | Chức năng | Actor |
|---|---|---|
| CMT-01 | Bình luận tin đăng | Người thuê |
| CMT-02 | Sửa/xóa bình luận của mình | Người thuê |
| CMT-03 | Chủ trọ phản hồi bình luận | Chủ trọ |
| CMT-04 | Kiểm duyệt bình luận | Moderator/Admin |
| REV-01 | Đánh giá tin/chủ trọ | Người thuê |
| REV-02 | Sửa đánh giá | Người thuê |
| REV-03 | Ẩn đánh giá vi phạm | Moderator/Admin |

### 2.8. Report & Moderation

| Mã | Chức năng | Actor |
|---|---|---|
| RPT-01 | Báo cáo tin vi phạm | Người thuê |
| RPT-02 | Báo cáo bình luận | Người dùng |
| RPT-03 | Báo cáo người dùng | Người dùng |
| RPT-04 | Xử lý báo cáo | Moderator/Admin |
| RPT-05 | Gửi cảnh báo vi phạm | Moderator/Admin/Hệ thống |
| RPT-06 | Khóa tin/tài khoản | Admin |

### 2.9. Payment & Promotion

| Mã | Chức năng | Actor |
|---|---|---|
| PAY-01 | Xem gói dịch vụ | Chủ trọ |
| PAY-02 | Mua gói đẩy tin | Chủ trọ |
| PAY-03 | Tạo giao dịch thanh toán | Hệ thống |
| PAY-04 | Xác nhận thanh toán | Payment Gateway/Hệ thống |
| PAY-05 | Kích hoạt gói | Hệ thống |
| PAY-06 | Quản lý lịch sử thanh toán | Chủ trọ/Admin |
| PROMO-01 | Đẩy tin lên đầu | Hệ thống |
| PROMO-02 | Gắn nhãn tin nổi bật | Hệ thống |

### 2.10. Notification

| Mã | Chức năng | Actor |
|---|---|---|
| NOTI-01 | Thông báo trong hệ thống | Hệ thống |
| NOTI-02 | Gửi email xác thực | Hệ thống |
| NOTI-03 | Gửi email cảnh báo | Hệ thống |
| NOTI-04 | Thông báo có người liên hệ | Hệ thống |
| NOTI-05 | Thông báo tin sắp hết hạn | Hệ thống |
| NOTI-06 | Thông báo kết quả duyệt tin | Hệ thống |

### 2.11. AI

| Mã | Chức năng | Actor |
|---|---|---|
| AI-01 | Phân tích cảm xúc bình luận | Hệ thống AI |
| AI-02 | Cập nhật điểm uy tín tin đăng | Hệ thống AI |
| AI-03 | Cập nhật điểm uy tín chủ trọ | Hệ thống AI |
| AI-04 | Gợi ý tin đăng cá nhân hóa | Hệ thống AI |
| AI-05 | Chatbot tư vấn tìm trọ | Người dùng/Hệ thống AI |
| AI-06 | Dự đoán giá thuê | Chủ trọ/Hệ thống AI |
| AI-07 | Quản lý log AI | Admin |
| AI-08 | Cấu hình ngưỡng AI | Admin |

### 2.12. Admin

| Mã | Chức năng |
|---|---|
| ADM-01 | Dashboard tổng quan |
| ADM-02 | Quản lý người dùng |
| ADM-03 | Quản lý chủ trọ |
| ADM-04 | Quản lý tin đăng |
| ADM-05 | Quản lý danh mục |
| ADM-06 | Quản lý khu vực |
| ADM-07 | Quản lý tiện ích |
| ADM-08 | Quản lý gói dịch vụ |
| ADM-09 | Quản lý thanh toán |
| ADM-10 | Quản lý báo cáo/khiếu nại |
| ADM-11 | Quản lý bình luận/đánh giá |
| ADM-12 | Quản lý AI |
| ADM-13 | Thống kê và báo cáo |
| ADM-14 | Quản lý cấu hình hệ thống |

---

## 3. Phân tích chi tiết chức năng

### 3.1. Đăng ký tài khoản

| Nội dung | Mô tả |
|---|---|
| Mục đích | Tạo tài khoản để sử dụng các chức năng cá nhân hóa |
| Người sử dụng | Khách chưa đăng nhập |
| Điều kiện sử dụng | Chưa đăng nhập, email/số điện thoại chưa tồn tại |
| Dữ liệu vào | Họ tên, email, số điện thoại, mật khẩu, vai trò mong muốn |
| Dữ liệu ra | Tài khoản mới ở trạng thái Active hoặc PendingVerify |

Luồng chính:

1. Người dùng mở form đăng ký.
2. Nhập thông tin cá nhân.
3. Hệ thống kiểm tra định dạng và trùng lặp.
4. Hệ thống tạo tài khoản.
5. Hệ thống gửi email/OTP xác thực nếu có cấu hình.
6. Người dùng xác thực và có thể đăng nhập.

Luồng phụ:

- Người dùng đăng ký bằng tài khoản mạng xã hội nếu hệ thống có hỗ trợ.
- Người dùng chọn vai trò chủ trọ, hệ thống yêu cầu bổ sung thông tin liên hệ.

Điều kiện lỗi:

- Email đã tồn tại.
- Số điện thoại đã tồn tại.
- Mật khẩu không đạt yêu cầu.
- OTP hết hạn.

Quy tắc nghiệp vụ:

- Một email chỉ thuộc một tài khoản.
- Một số điện thoại nên chỉ thuộc một tài khoản đang hoạt động.
- Tài khoản chủ trọ chưa xác thực vẫn có thể tạo nháp nhưng không nên được đăng tin công khai nếu hệ thống yêu cầu xác thực.

Validation:

- Email đúng định dạng.
- Số điện thoại Việt Nam hợp lệ.
- Mật khẩu tối thiểu 8 ký tự, có chữ và số.
- Họ tên không rỗng, không chứa ký tự nguy hiểm.

### 3.2. Đăng nhập

| Nội dung | Mô tả |
|---|---|
| Mục đích | Xác thực người dùng |
| Người sử dụng | Khách đã có tài khoản |
| Điều kiện sử dụng | Tài khoản tồn tại, không bị khóa |
| Dữ liệu vào | Email/số điện thoại, mật khẩu |
| Dữ liệu ra | Phiên đăng nhập, token, thông tin vai trò |

Luồng chính:

1. Người dùng nhập tài khoản và mật khẩu.
2. Hệ thống xác thực thông tin.
3. Hệ thống kiểm tra trạng thái tài khoản.
4. Hệ thống tạo phiên đăng nhập.
5. Người dùng được chuyển về trang phù hợp.

Luồng phụ:

- Nếu tài khoản chưa xác thực, hệ thống cho phép gửi lại mã xác thực.
- Nếu đăng nhập sai nhiều lần, hệ thống yêu cầu captcha hoặc tạm khóa đăng nhập.

Quy tắc nghiệp vụ:

- Tài khoản Locked không được đăng nhập.
- Mỗi lần đăng nhập thành công ghi nhận thời gian đăng nhập cuối.
- Đăng nhập sai quá ngưỡng trong thời gian ngắn bị rate limit.

### 3.3. Tạo và đăng tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cho phép chủ trọ đưa thông tin cho thuê lên hệ thống |
| Người sử dụng | Chủ trọ, người cho ở ghép |
| Điều kiện sử dụng | Đã đăng nhập, tài khoản không bị khóa, đủ quyền đăng tin |
| Dữ liệu vào | Loại tin, tiêu đề, mô tả, giá, diện tích, địa chỉ, tiện ích, ảnh, thông tin liên hệ |
| Dữ liệu ra | Tin nháp hoặc tin chờ duyệt |

Luồng chính:

1. Chủ trọ chọn tạo tin mới.
2. Chọn loại tin: phòng trọ, căn hộ, ở ghép...
3. Nhập thông tin cơ bản.
4. Nhập địa chỉ và khu vực.
5. Nhập giá, diện tích, số người ở, quy định.
6. Chọn tiện ích và nội thất.
7. Upload ảnh.
8. Hệ thống gợi ý giá thuê tham khảo bằng AI nếu đủ dữ liệu.
9. Chủ trọ lưu nháp hoặc gửi duyệt.
10. Hệ thống chuyển tin sang Pending.
11. Moderator/Admin duyệt tin.
12. Tin được chuyển sang Active nếu hợp lệ.

Luồng phụ:

- Chủ trọ chỉ lưu nháp, tin ở trạng thái Draft.
- Tin bị từ chối, chủ trọ nhận lý do và chỉnh sửa lại.
- Nếu chủ trọ đã được xác thực uy tín, hệ thống có thể tự động duyệt tin ít rủi ro.

Điều kiện lỗi:

- Thiếu trường bắt buộc.
- Giá hoặc diện tích không hợp lệ.
- Ảnh quá dung lượng, sai định dạng.
- Địa chỉ không thuộc khu vực hỗ trợ.
- Nội dung chứa từ khóa cấm.

Dữ liệu vào chính:

- CategoryId.
- Title.
- Description.
- Price.
- Area.
- Province/District/Ward.
- AddressDetail.
- Latitude/Longitude nếu có bản đồ.
- Amenities.
- Images.
- ContactName.
- ContactPhone.
- GenderRequirement nếu là ở ghép.
- MaxOccupants.
- DepositAmount.
- ElectricityPrice, WaterPrice.
- AvailableFrom.

Dữ liệu ra:

- ListingId.
- ListingStatus.
- CreatedAt.
- ExpiredAt dự kiến.
- PricePrediction nếu có.

Quy tắc nghiệp vụ:

- Tin mới mặc định có thời hạn hiển thị, ví dụ 30 ngày.
- Tin chưa duyệt không hiển thị công khai.
- Tin phải có tối thiểu 1 ảnh và tối đa số ảnh theo cấu hình, ví dụ 10 ảnh.
- Tin ở ghép phải có thông tin giới tính chấp nhận, số người hiện tại hoặc số người cần tìm.
- Tin có giá quá bất thường so với AI đề xuất không bị chặn tự động, nhưng có thể bị đánh dấu cần kiểm tra.
- Tin bị báo cáo nhiều lần có thể chuyển sang NeedReview hoặc Hidden.

Validation:

- Tiêu đề từ 10 đến 150 ký tự.
- Mô tả từ 30 đến 3000 ký tự.
- Giá > 0.
- Diện tích > 0.
- Số điện thoại hợp lệ.
- Ảnh định dạng JPG, PNG, WEBP.
- Không cho phép script, HTML nguy hiểm trong mô tả.

### 3.4. Sửa tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cập nhật thông tin tin đăng |
| Người sử dụng | Chủ trọ sở hữu tin, Admin |
| Điều kiện sử dụng | Tin chưa bị khóa vĩnh viễn, người dùng có quyền |
| Dữ liệu vào | Các trường cần cập nhật |
| Dữ liệu ra | Tin đã cập nhật, có thể quay về Pending |

Luồng chính:

1. Chủ trọ mở màn hình quản lý tin.
2. Chọn tin cần sửa.
3. Cập nhật thông tin.
4. Hệ thống validate dữ liệu.
5. Nếu thay đổi nhạy cảm, tin quay về Pending.
6. Nếu thay đổi nhỏ, tin tiếp tục Active.

Luồng phụ:

- Admin sửa trực tiếp nội dung vi phạm nhẹ.
- Chủ trọ thay ảnh hoặc giá, hệ thống ghi audit.

Quy tắc nghiệp vụ:

- Thay đổi tiêu đề, mô tả, giá, địa chỉ hoặc ảnh chính cần kiểm duyệt lại.
- Thay đổi trạng thái còn phòng/hết phòng không cần kiểm duyệt.
- Mọi thay đổi quan trọng cần lưu lịch sử chỉnh sửa.

### 3.5. Gia hạn tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Kéo dài thời gian hiển thị tin |
| Người sử dụng | Chủ trọ |
| Điều kiện sử dụng | Tin thuộc chủ trọ, không bị khóa, không bị xóa |
| Dữ liệu vào | ListingId, gói gia hạn nếu có |
| Dữ liệu ra | Ngày hết hạn mới |

Luồng chính:

1. Chủ trọ chọn gia hạn tin.
2. Hệ thống hiển thị thời hạn hiện tại và tùy chọn gia hạn.
3. Nếu gia hạn miễn phí trong giới hạn, hệ thống cập nhật ExpiredAt.
4. Nếu cần thanh toán, hệ thống tạo giao dịch.
5. Thanh toán thành công, hệ thống gia hạn tin.

Quy tắc nghiệp vụ:

- Tin hết hạn có thể được gia hạn và chuyển lại Active nếu không vi phạm.
- Tin Locked không được gia hạn.
- Tin Rejected cần chỉnh sửa và duyệt lại trước khi gia hạn.
- Có thể giới hạn số lần gia hạn miễn phí trong tháng.

### 3.6. Ẩn, đóng và xóa tin

| Chức năng | Mục đích | Quy tắc chính |
|---|---|---|
| Ẩn tin | Tạm thời không hiển thị tin | Chủ trọ có thể mở lại nếu tin chưa hết hạn và không bị khóa |
| Đóng tin | Đánh dấu đã cho thuê hoặc không còn nhu cầu | Tin không xuất hiện trong tìm kiếm mặc định |
| Xóa mềm | Loại bỏ khỏi quản lý thông thường | Dữ liệu vẫn giữ để audit và báo cáo |

Luồng chính:

1. Chủ trọ vào quản lý tin.
2. Chọn hành động ẩn/đóng/xóa.
3. Hệ thống yêu cầu xác nhận.
4. Hệ thống cập nhật trạng thái.
5. Hệ thống ghi log.

Quy tắc nghiệp vụ:

- Không xóa cứng tin nếu có thanh toán, báo cáo hoặc bình luận liên quan.
- Admin vẫn xem được tin đã xóa mềm.
- Tin Closed có thể dùng để thống kê tỷ lệ thành công.

### 3.7. Tìm kiếm và lọc tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Giúp người thuê tìm tin phù hợp |
| Người sử dụng | Tất cả người truy cập |
| Điều kiện sử dụng | Tin đang Active |
| Dữ liệu vào | Từ khóa, khu vực, giá, diện tích, loại tin, tiện ích |
| Dữ liệu ra | Danh sách tin phù hợp |

Luồng chính:

1. Người dùng nhập từ khóa hoặc chọn bộ lọc.
2. Hệ thống truy vấn danh sách tin Active.
3. Hệ thống áp dụng bộ lọc.
4. Kết quả được sắp xếp theo mức ưu tiên.
5. Người dùng mở chi tiết tin.

Luồng phụ:

- Không có kết quả, hệ thống gợi ý mở rộng khu vực hoặc khoảng giá.
- Người dùng đăng nhập, hệ thống lưu lịch sử tìm kiếm.
- Hệ thống có thể xen kẽ tin được đẩy nhưng phải đảm bảo không làm mất tính liên quan.

Dữ liệu lọc:

- Tỉnh/thành, quận/huyện, phường/xã.
- Khoảng giá.
- Khoảng diện tích.
- Loại nhà/phòng.
- Số người ở.
- Giới tính nếu ở ghép.
- Có nội thất.
- Cho nuôi thú cưng.
- Có chỗ để xe.
- Giờ giấc tự do.
- Nhà vệ sinh riêng/chung.
- Ban công, máy lạnh, máy giặt, thang máy.

Quy tắc nghiệp vụ:

- Chỉ hiển thị tin Active.
- Tin Locked, Hidden, Expired, Deleted không xuất hiện.
- Tin trả phí có thể được ưu tiên trong phạm vi kết quả phù hợp.
- Tìm kiếm của người đăng nhập được lưu để phục vụ gợi ý.

Validation:

- Giá từ không lớn hơn giá đến.
- Diện tích từ không lớn hơn diện tích đến.
- Không cho phép query quá dài hoặc chứa ký tự nguy hiểm.

### 3.8. Xem chi tiết tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cung cấp đầy đủ thông tin để người thuê quyết định liên hệ |
| Người sử dụng | Tất cả |
| Điều kiện sử dụng | Tin Active hoặc người xem có quyền quản trị |
| Dữ liệu vào | ListingId |
| Dữ liệu ra | Chi tiết tin, ảnh, tiện ích, chủ trọ, bình luận, đánh giá |

Luồng chính:

1. Người dùng mở tin.
2. Hệ thống kiểm tra trạng thái tin.
3. Hệ thống tăng lượt xem hợp lệ.
4. Hệ thống hiển thị thông tin chi tiết.
5. Nếu người dùng đăng nhập, hệ thống ghi HistoryView.
6. Hệ thống hiển thị tin tương tự hoặc gợi ý.

Quy tắc nghiệp vụ:

- Không tính nhiều lượt xem liên tục từ cùng người dùng/IP trong thời gian ngắn.
- Thông tin liên hệ có thể bị che một phần nếu người dùng chưa đăng nhập.
- Tin có cảnh báo uy tín thấp có thể hiển thị nhãn cảnh báo nhẹ.

### 3.9. Lưu tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Giúp người thuê lưu lại tin quan tâm |
| Người sử dụng | Người thuê |
| Điều kiện sử dụng | Đã đăng nhập, tin Active |
| Dữ liệu vào | ListingId |
| Dữ liệu ra | Trạng thái đã lưu |

Luồng chính:

1. Người thuê bấm lưu tin.
2. Hệ thống kiểm tra tin có tồn tại và Active.
3. Hệ thống tạo Favorite nếu chưa có.
4. Hệ thống cập nhật số lượt lưu.

Luồng phụ:

- Nếu đã lưu, bấm lần nữa để bỏ lưu.
- Nếu tin hết hạn sau khi lưu, hệ thống vẫn lưu trong danh sách nhưng gắn nhãn không còn hiển thị.

Quy tắc nghiệp vụ:

- Một người dùng chỉ lưu một tin một lần.
- Dữ liệu Favorite dùng cho Recommendation System.

### 3.10. Liên hệ chủ trọ

| Nội dung | Mô tả |
|---|---|
| Mục đích | Kết nối người thuê với chủ trọ |
| Người sử dụng | Người thuê |
| Điều kiện sử dụng | Tin Active, người dùng không bị khóa |
| Dữ liệu vào | ListingId, hình thức liên hệ, nội dung nếu gửi form |
| Dữ liệu ra | ContactLog, thông báo cho chủ trọ |

Luồng chính:

1. Người thuê bấm xem số điện thoại hoặc gửi tin nhắn.
2. Hệ thống kiểm tra quyền.
3. Hệ thống ghi nhận lượt liên hệ.
4. Hệ thống hiển thị số điện thoại hoặc tạo cuộc trò chuyện.
5. Chủ trọ nhận thông báo.

Luồng phụ:

- Khách chưa đăng nhập được yêu cầu đăng nhập trước khi xem số đầy đủ.
- Nếu chủ trọ tắt chat, hệ thống chỉ hiển thị số điện thoại.

Quy tắc nghiệp vụ:

- Không ghi quá nhiều lượt liên hệ trùng từ cùng người dùng trong thời gian ngắn.
- Chủ trọ có thể xem danh sách người đã liên hệ tin của mình.
- Người dùng bị report spam có thể bị hạn chế liên hệ.

### 3.11. Bình luận

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cho phép trao đổi công khai và tạo dữ liệu đánh giá chất lượng tin |
| Người sử dụng | Người thuê, chủ trọ phản hồi |
| Điều kiện sử dụng | Đã đăng nhập, tin cho phép bình luận |
| Dữ liệu vào | ListingId, nội dung bình luận, ParentCommentId nếu trả lời |
| Dữ liệu ra | Bình luận mới, kết quả phân tích cảm xúc |

Luồng chính:

1. Người dùng nhập bình luận.
2. Hệ thống validate nội dung.
3. Hệ thống lưu bình luận ở trạng thái Visible hoặc Pending tùy cấu hình.
4. Hệ thống kích hoạt AI phân tích cảm xúc.
5. Hệ thống cập nhật nhãn sentiment và điểm uy tín.
6. Nếu bình luận tiêu cực vượt ngưỡng, hệ thống cảnh báo Admin.

Luồng phụ:

- Bình luận chứa từ cấm chuyển sang Pending hoặc Hidden.
- Chủ trọ phản hồi bình luận.
- Người dùng sửa/xóa bình luận của mình trong giới hạn thời gian.

Quy tắc nghiệp vụ:

- Người dùng không được spam bình luận liên tục.
- Bình luận bị xóa mềm để giữ dữ liệu kiểm duyệt.
- Chủ trọ không được xóa bình luận của người thuê, chỉ được báo cáo hoặc phản hồi.

Validation:

- Nội dung từ 3 đến 1000 ký tự.
- Không chứa script.
- Không chứa thông tin nhạy cảm trái quy định.

### 3.12. Đánh giá

| Nội dung | Mô tả |
|---|---|
| Mục đích | Ghi nhận trải nghiệm người thuê về tin hoặc chủ trọ |
| Người sử dụng | Người thuê |
| Điều kiện sử dụng | Đã đăng nhập, có tương tác hợp lệ với tin |
| Dữ liệu vào | ListingId, số sao, nội dung đánh giá |
| Dữ liệu ra | Review, điểm trung bình |

Luồng chính:

1. Người thuê mở form đánh giá.
2. Chọn số sao và nhập nội dung.
3. Hệ thống kiểm tra điều kiện đánh giá.
4. Hệ thống lưu đánh giá.
5. Hệ thống cập nhật điểm trung bình của tin và chủ trọ.

Luồng phụ:

- Người thuê sửa đánh giá trong thời gian cho phép.
- Moderator ẩn đánh giá nếu vi phạm.

Quy tắc nghiệp vụ:

- Một người dùng chỉ đánh giá một tin một lần.
- Nên yêu cầu người dùng đã từng liên hệ tin để giảm đánh giá ảo.
- Đánh giá quá tiêu cực vẫn được hiển thị nếu không vi phạm nội dung, nhưng được AI và Admin theo dõi.

Validation:

- Rating từ 1 đến 5.
- Nội dung đánh giá có thể bắt buộc nếu rating <= 2.

### 3.13. Báo cáo vi phạm

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cho phép cộng đồng báo cáo tin sai, lừa đảo, nội dung xấu |
| Người sử dụng | Người dùng đăng nhập |
| Điều kiện sử dụng | Tin/bình luận/người dùng tồn tại |
| Dữ liệu vào | Loại đối tượng, lý do, mô tả, ảnh bằng chứng nếu có |
| Dữ liệu ra | Report ở trạng thái Pending |

Luồng chính:

1. Người dùng chọn báo cáo.
2. Chọn lý do: sai thông tin, đã cho thuê, lừa đảo, ảnh không thật, giá sai, nội dung phản cảm, spam...
3. Nhập mô tả bổ sung.
4. Hệ thống lưu report.
5. Hệ thống thông báo cho Moderator/Admin nếu vượt ngưỡng.
6. Moderator/Admin xử lý.

Luồng phụ:

- Nếu nhiều người báo cáo cùng một tin, hệ thống gom nhóm để xử lý.
- Nếu report sai nhiều lần, tài khoản báo cáo có thể bị hạn chế.

Quy tắc nghiệp vụ:

- Một người dùng không được báo cáo cùng một đối tượng cùng một lý do nhiều lần liên tục.
- Report không tự động khóa tin ngay, trừ khi số lượng và mức độ nghiêm trọng vượt ngưỡng.
- Tất cả thao tác xử lý report cần có log.

### 3.14. Thanh toán đẩy tin

| Nội dung | Mô tả |
|---|---|
| Mục đích | Cho phép chủ trọ trả phí để tăng độ hiển thị |
| Người sử dụng | Chủ trọ |
| Điều kiện sử dụng | Tin Active hoặc Pending được phép mua trước |
| Dữ liệu vào | ListingId, PromotionPackageId, phương thức thanh toán |
| Dữ liệu ra | Payment, PromotionSubscription |

Luồng chính:

1. Chủ trọ chọn tin cần đẩy.
2. Chọn gói dịch vụ.
3. Hệ thống tạo đơn thanh toán.
4. Người dùng thanh toán qua cổng thanh toán hoặc mô phỏng.
5. Hệ thống nhận kết quả thanh toán.
6. Nếu thành công, kích hoạt gói.
7. Tin được ưu tiên hiển thị theo thời gian gói.

Luồng phụ:

- Thanh toán thất bại, đơn ở trạng thái Failed.
- Thanh toán pending, hệ thống chờ callback hoặc cho phép kiểm tra lại.
- Nếu tin bị khóa trong thời gian gói, Admin có thể xử lý hoàn tiền thủ công hoặc không hoàn theo chính sách.

Quy tắc nghiệp vụ:

- Gói đẩy tin có ngày bắt đầu và ngày kết thúc.
- Tin được đẩy vẫn phải phù hợp với kết quả tìm kiếm.
- Không cho phép tin vi phạm dùng tiền để vượt kiểm duyệt.
- Giao dịch cần mã duy nhất.

### 3.15. Chatbot hỗ trợ tìm trọ

| Nội dung | Mô tả |
|---|---|
| Mục đích | Hỗ trợ người dùng tìm tin bằng hội thoại có cấu trúc |
| Người sử dụng | Khách, người thuê |
| Điều kiện sử dụng | Website hoạt động, dữ liệu tin có sẵn |
| Dữ liệu vào | Câu hỏi, nhu cầu, bộ lọc do chatbot thu thập |
| Dữ liệu ra | Danh sách tin phù hợp, câu trả lời FAQ |

Luồng chính:

1. Người dùng mở chatbot.
2. Chatbot hỏi nhu cầu: khu vực, giá, loại phòng, số người ở.
3. Chatbot hỏi thêm các ràng buộc: nội thất, thú cưng, giờ giấc, chỗ để xe.
4. Hệ thống chuyển nhu cầu thành bộ lọc.
5. Hệ thống tìm tin Active phù hợp.
6. Chatbot trả về danh sách tin.
7. Người dùng có thể mở chi tiết, lưu tin hoặc hỏi tiếp.

Luồng phụ:

- Người dùng hỏi cách đăng tin, chatbot trả lời hướng dẫn.
- Người dùng hỏi thuật ngữ như "chung cư mini", "cọc", "giờ giấc tự do".
- Nếu thiếu thông tin quan trọng, chatbot hỏi lại.
- Nếu không có kết quả, chatbot đề xuất mở rộng giá/khu vực/diện tích.

Quy tắc nghiệp vụ:

- Chatbot không tự tạo thông tin không có trong dữ liệu tin đăng.
- Chatbot chỉ trả về tin Active.
- Chatbot cần ghi log câu hỏi phổ biến để cải thiện FAQ.
- Chatbot không thay người dùng đặt cọc hay cam kết chất lượng phòng.

### 3.16. AI dự đoán giá thuê

| Nội dung | Mô tả |
|---|---|
| Mục đích | Hỗ trợ chủ trọ đặt giá hợp lý, giúp người thuê tham khảo thị trường |
| Người sử dụng | Chủ trọ, Admin, Hệ thống |
| Điều kiện sử dụng | Có đủ dữ liệu đầu vào |
| Dữ liệu vào | Khu vực, diện tích, loại nhà, số phòng, toilet, nội thất, vị trí, tiện ích |
| Dữ liệu ra | Giá đề xuất hoặc khoảng giá tham khảo |

Luồng chính:

1. Chủ trọ nhập thông tin tin đăng.
2. Hệ thống kiểm tra đủ dữ liệu dự đoán.
3. AI trả về giá đề xuất hoặc khoảng giá.
4. Hệ thống hiển thị cho chủ trọ.
5. Chủ trọ có thể áp dụng hoặc bỏ qua.
6. Hệ thống lưu PredictionHistory.

Luồng phụ:

- Nếu thiếu dữ liệu, hệ thống thông báo cần nhập thêm trường.
- Nếu giá chủ trọ nhập lệch quá lớn, hệ thống cảnh báo mềm.

Quy tắc nghiệp vụ:

- Giá AI chỉ là tham khảo, không bắt buộc.
- Không chặn đăng tin chỉ vì giá khác dự đoán.
- Nếu giá thấp bất thường, có thể đánh dấu cần kiểm duyệt để tránh tin giả.
- Kết quả dự đoán cần lưu để phục vụ báo cáo và đánh giá chất lượng AI.

---

## 4. Quy trình nghiệp vụ

### 4.1. Quy trình người thuê

```text
Truy cập website
↓
Tìm kiếm hoặc dùng chatbot
↓
Lọc theo khu vực, giá, diện tích, tiện ích
↓
Xem danh sách tin
↓
Xem chi tiết tin
↓
Đăng nhập nếu muốn lưu/liên hệ/bình luận
↓
Lưu tin quan tâm
↓
Liên hệ chủ trọ
↓
Đi xem phòng ngoài thực tế
↓
Đánh giá hoặc bình luận
↓
Báo cáo nếu phát hiện vi phạm
```

### 4.2. Quy trình chủ trọ

```text
Đăng ký tài khoản
↓
Đăng nhập
↓
Cập nhật hồ sơ và thông tin liên hệ
↓
Tạo tin nháp
↓
Nhập thông tin phòng
↓
Nhận gợi ý giá thuê từ AI
↓
Gửi duyệt tin
↓
Tin được duyệt và hiển thị
↓
Nhận liên hệ từ người thuê
↓
Theo dõi lượt xem, lượt lưu, lượt liên hệ
↓
Mua gói đẩy tin nếu cần
↓
Gia hạn, ẩn, đóng hoặc cập nhật tin
```

### 4.3. Quy trình Admin/Moderator

```text
Đăng nhập trang quản trị
↓
Xem dashboard
↓
Duyệt tin chờ kiểm duyệt
↓
Xử lý báo cáo vi phạm
↓
Kiểm tra cảnh báo từ AI
↓
Ẩn/khóa tin hoặc cảnh báo chủ trọ
↓
Quản lý người dùng và chủ trọ
↓
Quản lý gói dịch vụ và thanh toán
↓
Xem thống kê hệ thống
```

### 4.4. Quy trình xử lý tin vi phạm

```text
Tin bị người dùng báo cáo hoặc AI cảnh báo
↓
Hệ thống chuyển tin sang NeedReview nếu vượt ngưỡng
↓
Moderator xem nội dung, bình luận, lịch sử report
↓
Ra quyết định: bỏ qua / cảnh báo / yêu cầu sửa / ẩn / khóa
↓
Hệ thống gửi thông báo cho chủ trọ
↓
Ghi audit log
```

---

## 5. Logic nghiệp vụ

### 5.1. Logic vòng đời tin đăng

| Sự kiện | Trạng thái trước | Trạng thái sau | Ghi chú |
|---|---|---|---|
| Lưu nháp | Không có | Draft | Chưa công khai |
| Gửi duyệt | Draft/Rejected | Pending | Chờ Moderator/Admin |
| Duyệt | Pending | Active | Có ngày hết hạn |
| Từ chối | Pending | Rejected | Có lý do |
| Chủ trọ ẩn | Active | Hidden | Có thể mở lại |
| Chủ trọ đóng | Active/Hidden | Closed | Đã cho thuê hoặc ngừng cho thuê |
| Hết hạn | Active | Expired | Job chạy tự động |
| Bị báo cáo nhiều | Active | NeedReview | Có thể vẫn hiển thị hoặc tạm ẩn tùy cấu hình |
| Vi phạm nghiêm trọng | Active/NeedReview | Locked | Không cho sửa hoặc gia hạn nếu chưa xử lý |
| Xóa mềm | Bất kỳ trừ Locked tùy quyền | Deleted | Admin vẫn xem được |

### 5.2. Khi nào tin hết hạn

- Mỗi tin Active có trường ExpiredAt.
- Mặc định ExpiredAt = ngày duyệt + số ngày hiển thị, ví dụ 30 ngày.
- Job tự động chạy hằng ngày hoặc hằng giờ để chuyển tin quá hạn sang Expired.
- Tin Expired không xuất hiện ở trang tìm kiếm công khai.
- Trước khi hết hạn 3 ngày và 1 ngày, hệ thống gửi thông báo cho chủ trọ.

### 5.3. Khi nào tự động ẩn tin

Tin có thể bị tự động ẩn khi:

- Bị Admin/Moderator xác nhận vi phạm.
- Số report hợp lệ vượt ngưỡng nghiêm trọng, ví dụ từ 5 report từ 5 tài khoản khác nhau trong 24 giờ.
- AI sentiment phát hiện tỷ lệ bình luận tiêu cực cao và tin đã từng bị cảnh báo trước đó.
- Nội dung chứa từ khóa cấm nghiêm trọng.

### 5.4. Khi nào khóa tin hoặc tài khoản

Khóa tin:

- Tin đăng thông tin lừa đảo đã được xác minh.
- Tin dùng ảnh giả hoặc thông tin sai nghiêm trọng.
- Tin đăng nội dung cấm, phản cảm, spam.
- Tin đã bị cảnh báo nhiều lần nhưng không chỉnh sửa.

Khóa tài khoản:

- Chủ trọ có nhiều tin vi phạm nghiêm trọng.
- Người dùng spam bình luận, spam báo cáo hoặc lừa đảo.
- Tài khoản cố tình né kiểm duyệt.
- Tài khoản có hành vi tấn công hệ thống.

Gợi ý ngưỡng cho đồ án:

- 3 lần cảnh báo trong 30 ngày: khóa đăng tin tạm thời.
- 5 tin bị khóa trong 60 ngày: khóa tài khoản chủ trọ.
- 10 bình luận spam trong 1 giờ: tạm khóa chức năng bình luận.

### 5.5. Khi nào AI chạy

| Module AI | Điều kiện kích hoạt |
|---|---|
| Sentiment Analysis | Khi có bình luận mới hoặc bình luận được sửa |
| Recommendation System | Khi người dùng xem trang chủ, trang chi tiết, danh sách gợi ý; hoặc job định kỳ tính trước |
| Chatbot | Khi người dùng gửi câu hỏi hoặc chọn nhu cầu trong chatbot |
| Price Prediction | Khi chủ trọ nhập đủ thông tin tin đăng; khi sửa các trường ảnh hưởng giá |

### 5.6. Khi nào gửi thông báo

| Sự kiện | Người nhận | Kênh |
|---|---|---|
| Tài khoản đăng ký thành công | Người dùng | Email/In-app |
| Tin được duyệt | Chủ trọ | In-app/Email |
| Tin bị từ chối | Chủ trọ | In-app/Email |
| Có người liên hệ | Chủ trọ | In-app/Email |
| Có bình luận mới | Chủ trọ | In-app |
| Tin sắp hết hạn | Chủ trọ | In-app/Email |
| Thanh toán thành công | Chủ trọ/Admin | In-app/Email |
| Tin bị báo cáo nhiều | Admin/Moderator | Dashboard/In-app |
| AI phát hiện tiêu cực vượt ngưỡng | Admin/Moderator | Dashboard/In-app |
| Tài khoản bị khóa | Người dùng | Email/In-app |

### 5.7. Khi nào cập nhật điểm uy tín

Điểm uy tín tin đăng cập nhật khi:

- Có bình luận mới và AI đã phân tích cảm xúc.
- Có đánh giá mới.
- Có report được xác nhận đúng.
- Tin bị khóa, ẩn hoặc cảnh báo.
- Tin có lịch sử phản hồi tốt từ chủ trọ.

Điểm uy tín chủ trọ cập nhật khi:

- Tin của chủ trọ nhận đánh giá.
- Tin của chủ trọ có bình luận tích cực/tiêu cực.
- Chủ trọ có report được xác nhận.
- Chủ trọ phản hồi người thuê nhanh và đầy đủ nếu có module chat.
- Chủ trọ có nhiều tin hết hạn/đóng thành công mà không vi phạm.

### 5.8. Công thức điểm uy tín gợi ý

Không bắt buộc dùng đúng công thức này, nhưng có thể dùng cho đồ án:

```text
ListingTrustScore = 100
  + PositiveCommentCount * 1
  - NegativeCommentCount * 2
  + AverageRating * 5
  - ValidReportCount * 10
  - ViolationWarningCount * 15
```

Giới hạn:

- Điểm tối thiểu: 0.
- Điểm tối đa: 100.
- Tin dưới 40 điểm: đánh dấu rủi ro.
- Tin dưới 25 điểm: cần kiểm duyệt.

### 5.9. Khi nào tính lại giá dự đoán

AI dự đoán giá thuê được tính lại khi:

- Chủ trọ thay đổi khu vực.
- Chủ trọ thay đổi diện tích.
- Chủ trọ thay đổi loại nhà.
- Chủ trọ thay đổi số phòng, số toilet.
- Chủ trọ thay đổi nội thất hoặc tiện ích quan trọng.
- Admin cập nhật mô hình hoặc cấu hình vùng giá.

---

## 6. Thiết kế dữ liệu mức nghiệp vụ

### 6.1. Entity chính

| Entity | Mô tả |
|---|---|
| User | Thông tin tài khoản người dùng |
| Role | Vai trò: tenant, landlord, moderator, admin |
| UserRole | Bảng liên kết user và role nếu một user có nhiều role |
| UserProfile | Hồ sơ cá nhân, ảnh đại diện, giới tính |
| LandlordProfile | Thông tin mở rộng cho chủ trọ |
| Verification | Thông tin xác thực email, số điện thoại, chủ trọ |
| Listing | Tin đăng chính |
| ListingImage | Ảnh của tin đăng |
| Category | Loại tin đăng |
| Province | Tỉnh/thành |
| District | Quận/huyện |
| Ward | Phường/xã |
| Amenity | Tiện ích |
| ListingAmenity | Liên kết tin và tiện ích |
| Favorite | Tin đã lưu |
| ViewHistory | Lịch sử xem tin |
| SearchHistory | Lịch sử tìm kiếm |
| ContactLog | Lượt liên hệ |
| Conversation | Cuộc trò chuyện |
| Message | Tin nhắn |
| Comment | Bình luận |
| Review | Đánh giá |
| Report | Báo cáo vi phạm |
| ModerationAction | Hành động kiểm duyệt |
| Notification | Thông báo |
| PromotionPackage | Gói dịch vụ |
| Payment | Giao dịch thanh toán |
| PromotionSubscription | Gói đẩy tin đã mua |
| RecommendationLog | Log gợi ý tin đăng |
| SentimentResult | Kết quả phân tích cảm xúc |
| PredictionHistory | Lịch sử dự đoán giá |
| ChatbotConversation | Phiên trò chuyện chatbot |
| ChatbotMessage | Tin nhắn chatbot |
| AuditLog | Nhật ký thay đổi quan trọng |
| SystemConfig | Cấu hình hệ thống |

### 6.2. Mô tả quan hệ Entity

| Quan hệ | Mô tả |
|---|---|
| User - Role | Một user có thể có một hoặc nhiều role |
| User - Listing | Một chủ trọ có nhiều tin đăng |
| Category - Listing | Một danh mục có nhiều tin |
| Province/District/Ward - Listing | Một tin thuộc một địa chỉ hành chính |
| Listing - ListingImage | Một tin có nhiều ảnh |
| Listing - Amenity | Nhiều-nhiều qua ListingAmenity |
| User - Favorite - Listing | Người dùng lưu nhiều tin, một tin được nhiều người lưu |
| User - ViewHistory - Listing | Ghi lịch sử xem tin |
| User - SearchHistory | Ghi lịch sử tìm kiếm |
| Listing - ContactLog - User | Ghi người thuê đã liên hệ tin |
| Conversation - Message | Một cuộc trò chuyện có nhiều tin nhắn |
| Listing - Comment | Một tin có nhiều bình luận |
| Comment - SentimentResult | Một bình luận có một kết quả sentiment mới nhất hoặc nhiều phiên bản |
| Listing - Review | Một tin có nhiều đánh giá |
| User - Report | Một user tạo nhiều report |
| Report - ModerationAction | Một report có thể có nhiều hành động xử lý |
| Listing - Payment | Thanh toán có thể gắn với tin hoặc gói dịch vụ |
| PromotionPackage - PromotionSubscription | Một gói có nhiều lượt đăng ký |
| User - RecommendationLog | Lưu lịch sử gợi ý cho người dùng |
| Listing - PredictionHistory | Một tin có nhiều lần dự đoán giá |
| User - Notification | Một user nhận nhiều thông báo |
| User - AuditLog | Ghi người thực hiện thao tác quan trọng |

### 6.3. Thuộc tính nghiệp vụ gợi ý

#### User

- Id.
- FullName.
- Email.
- Phone.
- PasswordHash.
- AvatarUrl.
- Gender.
- Status: Active, PendingVerify, Locked, Deleted.
- CreatedAt.
- LastLoginAt.

#### Listing

- Id.
- OwnerId.
- CategoryId.
- Title.
- Description.
- Price.
- Area.
- DepositAmount.
- ProvinceId.
- DistrictId.
- WardId.
- AddressDetail.
- Latitude.
- Longitude.
- RoomCount.
- ToiletCount.
- MaxOccupants.
- CurrentOccupants.
- GenderRequirement.
- PetAllowed.
- ParkingAvailable.
- CurfewType.
- FurnitureStatus.
- Status.
- TrustScore.
- AverageRating.
- ViewCount.
- FavoriteCount.
- ContactCount.
- PublishedAt.
- ExpiredAt.
- CreatedAt.
- UpdatedAt.

#### Comment

- Id.
- ListingId.
- UserId.
- ParentCommentId.
- Content.
- Status.
- SentimentLabel.
- SentimentScore.
- CreatedAt.
- UpdatedAt.

#### Report

- Id.
- ReporterId.
- TargetType.
- TargetId.
- Reason.
- Description.
- EvidenceImageUrl.
- Status: Pending, Reviewing, Resolved, Rejected.
- Severity.
- CreatedAt.
- ResolvedAt.

#### Payment

- Id.
- UserId.
- ListingId.
- PackageId.
- Amount.
- PaymentMethod.
- TransactionCode.
- Status: Pending, Success, Failed, Cancelled, Refunded.
- CreatedAt.
- PaidAt.

---

## 7. Use Case theo Actor

### 7.1. Khách chưa đăng nhập

| Use Case | Mô tả |
|---|---|
| Xem trang chủ | Xem tin mới, tin nổi bật, khu vực phổ biến |
| Tìm kiếm tin | Tìm tin theo từ khóa và bộ lọc cơ bản |
| Xem chi tiết tin | Xem thông tin tin Active |
| Xem hồ sơ chủ trọ | Xem thông tin công khai |
| Dùng chatbot cơ bản | Hỏi cách tìm phòng, tìm tin theo nhu cầu |
| Đăng ký | Tạo tài khoản mới |
| Đăng nhập | Truy cập tài khoản |

### 7.2. Người thuê

| Use Case | Mô tả |
|---|---|
| Cập nhật hồ sơ | Cập nhật thông tin cá nhân |
| Tìm kiếm nâng cao | Dùng nhiều tiêu chí lọc |
| Lưu tin | Lưu tin quan tâm |
| Xem tin đã lưu | Quản lý danh sách yêu thích |
| Xem lịch sử | Xem lại tin đã xem |
| Liên hệ chủ trọ | Gọi điện, gửi form hoặc chat |
| Bình luận | Đặt câu hỏi hoặc chia sẻ trải nghiệm |
| Đánh giá | Chấm điểm tin/chủ trọ sau tương tác |
| Báo cáo vi phạm | Báo cáo tin, bình luận, người dùng |
| Theo dõi chủ trọ | Nhận thông báo tin mới |
| Nhận gợi ý | Xem danh sách tin được hệ thống đề xuất |
| Dùng chatbot | Nhận tư vấn tìm phòng |

### 7.3. Chủ trọ / Người cho ở ghép

| Use Case | Mô tả |
|---|---|
| Cập nhật hồ sơ chủ trọ | Cập nhật thông tin liên hệ, xác thực |
| Tạo tin nháp | Nhập thông tin tin đăng |
| Xem giá AI đề xuất | Tham khảo giá thuê |
| Gửi duyệt tin | Gửi tin cho Moderator/Admin |
| Sửa tin | Cập nhật thông tin |
| Quản lý ảnh | Thêm, xóa, sắp xếp ảnh |
| Ẩn tin | Tạm ngưng hiển thị |
| Đóng tin | Đánh dấu đã cho thuê |
| Gia hạn tin | Kéo dài thời gian hiển thị |
| Mua gói đẩy tin | Thanh toán để tăng hiển thị |
| Xem thống kê tin | Xem lượt xem, lưu, liên hệ |
| Quản lý người liên hệ | Xem danh sách người quan tâm |
| Phản hồi bình luận | Trả lời câu hỏi công khai |

### 7.4. Moderator

| Use Case | Mô tả |
|---|---|
| Duyệt tin | Kiểm tra tin Pending |
| Từ chối tin | Từ chối và nhập lý do |
| Xử lý report | Kiểm tra báo cáo vi phạm |
| Ẩn bình luận | Ẩn nội dung vi phạm |
| Tạm ẩn tin | Ẩn tin cần xử lý |
| Gửi cảnh báo | Gửi cảnh báo cho chủ trọ/người dùng |
| Xem cảnh báo AI | Xem danh sách tin bị AI đánh dấu |

### 7.5. Admin

| Use Case | Mô tả |
|---|---|
| Quản lý người dùng | Tìm kiếm, khóa, mở khóa, phân quyền |
| Quản lý chủ trọ | Xem hồ sơ, xác thực, xử lý vi phạm |
| Quản lý tin đăng | Xem, sửa trạng thái, khóa tin |
| Quản lý danh mục | Thêm/sửa/xóa loại tin |
| Quản lý khu vực | Quản lý tỉnh, huyện, xã |
| Quản lý tiện ích | Cấu hình tiện ích |
| Quản lý gói dịch vụ | Tạo và sửa gói đẩy tin |
| Quản lý thanh toán | Xem giao dịch, xử lý lỗi |
| Quản lý báo cáo | Xử lý khiếu nại và vi phạm |
| Quản lý AI | Cấu hình ngưỡng, xem log |
| Xem thống kê | Thống kê người dùng, tin, doanh thu |
| Quản lý cấu hình | Cấu hình thời hạn tin, upload, email |

---

## 8. Sequence nghiệp vụ quan trọng

### 8.1. Sequence đăng tin

```text
Chủ trọ -> Website: Mở form đăng tin
Website -> Chủ trọ: Hiển thị form theo loại tin
Chủ trọ -> Website: Nhập thông tin và upload ảnh
Website -> Website: Validate dữ liệu
Website -> AI Price Module: Gửi dữ liệu dự đoán giá
AI Price Module -> Website: Trả về khoảng giá đề xuất
Website -> Chủ trọ: Hiển thị giá tham khảo
Chủ trọ -> Website: Gửi duyệt
Website -> Database: Lưu tin trạng thái Pending
Website -> Moderator/Admin: Tạo thông báo tin chờ duyệt
Moderator/Admin -> Website: Duyệt tin
Website -> Database: Cập nhật Active, PublishedAt, ExpiredAt
Website -> Chủ trọ: Gửi thông báo tin đã được duyệt
```

### 8.2. Sequence thanh toán đẩy tin

```text
Chủ trọ -> Website: Chọn gói đẩy tin
Website -> Database: Kiểm tra tin và gói
Website -> Payment Gateway: Tạo yêu cầu thanh toán
Payment Gateway -> Chủ trọ: Hiển thị trang thanh toán
Chủ trọ -> Payment Gateway: Thanh toán
Payment Gateway -> Website: Callback kết quả
Website -> Database: Cập nhật Payment Success
Website -> Database: Tạo PromotionSubscription
Website -> Chủ trọ: Thông báo kích hoạt gói
```

### 8.3. Sequence AI phân tích bình luận

```text
Người thuê -> Website: Gửi bình luận
Website -> Database: Lưu bình luận
Website -> AI Sentiment Module: Gửi nội dung bình luận
AI Sentiment Module -> Website: Trả về label và score
Website -> Database: Lưu SentimentResult
Website -> Website: Tính lại TrustScore của tin và chủ trọ
Website -> Database: Cập nhật điểm uy tín
alt Tiêu cực vượt ngưỡng
  Website -> Database: Đánh dấu tin NeedReview
  Website -> Admin/Moderator: Gửi cảnh báo
end
Website -> Người thuê: Hiển thị bình luận
```

### 8.4. Sequence chatbot tìm trọ

```text
Người dùng -> Chatbot: "Tôi muốn tìm phòng gần Quận 1 dưới 4 triệu"
Chatbot -> Chatbot: Trích xuất nhu cầu
Chatbot -> Người dùng: Hỏi thêm diện tích/số người/tiện ích nếu thiếu
Người dùng -> Chatbot: Trả lời bổ sung
Chatbot -> Listing Search Service: Gửi bộ lọc
Listing Search Service -> Database: Truy vấn tin Active phù hợp
Database -> Listing Search Service: Trả danh sách tin
Listing Search Service -> Chatbot: Trả kết quả
Chatbot -> Người dùng: Hiển thị danh sách tin và gợi ý mở rộng
```

### 8.5. Sequence gợi ý bài đăng

```text
Người thuê -> Website: Mở trang chủ hoặc chi tiết tin
Website -> Recommendation Module: Yêu cầu danh sách gợi ý
Recommendation Module -> Database: Lấy lịch sử xem, tìm kiếm, lưu tin
Recommendation Module -> Database: Lấy tin Active phù hợp
Recommendation Module -> Recommendation Module: Chấm điểm phù hợp nghiệp vụ
Recommendation Module -> Database: Lưu RecommendationLog
Recommendation Module -> Website: Trả danh sách gợi ý
Website -> Người thuê: Hiển thị "Gợi ý cho bạn"
```

### 8.6. Sequence đánh giá

```text
Người thuê -> Website: Gửi đánh giá
Website -> Database: Kiểm tra đã liên hệ hoặc đủ điều kiện
Website -> Database: Kiểm tra chưa đánh giá trước đó
Website -> Database: Lưu Review
Website -> Database: Tính lại AverageRating của tin và chủ trọ
Website -> Chủ trọ: Thông báo có đánh giá mới
Website -> Người thuê: Thông báo gửi đánh giá thành công
```

### 8.7. Sequence báo cáo và khóa bài

```text
Người dùng -> Website: Gửi report tin
Website -> Database: Lưu Report Pending
Website -> Website: Kiểm tra số lượng report hợp lệ
alt Vượt ngưỡng
  Website -> Database: Cập nhật Listing NeedReview
  Website -> Moderator/Admin: Gửi cảnh báo
end
Moderator/Admin -> Website: Mở report
Website -> Database: Lấy tin, chủ trọ, lịch sử report, bình luận
Moderator/Admin -> Website: Chọn khóa tin
Website -> Database: Cập nhật Listing Locked
Website -> Chủ trọ: Gửi thông báo lý do khóa
Website -> AuditLog: Ghi thao tác
```

---

## 9. Phân tích 4 module AI

### 9.1. AI phân tích cảm xúc bình luận

#### Mục tiêu

- Phân loại bình luận thành tích cực, trung lập, tiêu cực.
- Cập nhật điểm uy tín của tin đăng.
- Cập nhật điểm uy tín của chủ trọ.
- Phát hiện sớm tin có dấu hiệu lừa đảo, sai thông tin hoặc chất lượng thấp.

#### Input

- CommentId.
- ListingId.
- UserId.
- Nội dung bình luận.
- Thời điểm bình luận.
- Thông tin tin đăng.
- Lịch sử sentiment của tin.

#### Output

- SentimentLabel: Positive, Neutral, Negative.
- SentimentScore: giá trị từ 0 đến 1 hoặc -1 đến 1 tùy thiết kế.
- ConfidenceScore.
- IsRiskComment.
- Gợi ý hành động: None, Watch, NeedReview.

#### Luồng xử lý

1. Người dùng gửi bình luận.
2. Hệ thống lưu bình luận.
3. Hệ thống gửi nội dung sang module sentiment.
4. Module sentiment trả kết quả.
5. Hệ thống lưu kết quả vào SentimentResult.
6. Hệ thống cập nhật thống kê sentiment của tin.
7. Hệ thống tính lại TrustScore của tin và chủ trọ.
8. Nếu số bình luận tiêu cực vượt ngưỡng, hệ thống đánh dấu NeedReview.
9. Hệ thống gửi cảnh báo cho Admin/Moderator.

#### Điều kiện kích hoạt

- Có bình luận mới.
- Bình luận được chỉnh sửa.
- Admin yêu cầu phân tích lại.
- Hệ thống chạy job tính lại khi thay đổi cấu hình ngưỡng.

#### Logic nghiệp vụ

- Một bình luận tiêu cực đơn lẻ không làm khóa tin.
- Tỷ lệ tiêu cực cần xét theo số lượng bình luận tối thiểu.
- Bình luận từ tài khoản mới tạo có thể có trọng số thấp hơn.
- Bình luận đã bị Moderator xác định spam không dùng để tính điểm uy tín.

Gợi ý ngưỡng:

| Điều kiện | Hành động |
|---|---|
| Có ít nhất 5 bình luận và tỷ lệ tiêu cực >= 40% | Đánh dấu NeedReview |
| Có ít nhất 10 bình luận và tỷ lệ tiêu cực >= 50% | Gửi cảnh báo mức cao |
| Tin đã NeedReview 3 lần trong 30 ngày | Đề xuất khóa tin |
| Chủ trọ có 3 tin bị cảnh báo sentiment trong 30 ngày | Đề xuất kiểm tra tài khoản |

#### Trường hợp ngoại lệ

- Bình luận quá ngắn: gắn Neutral hoặc bỏ qua tính điểm.
- Bình luận mỉa mai khó phân tích: lưu confidence thấp, không tự động xử lý nặng.
- Bình luận chứa cả khen và chê: có thể gắn Neutral hoặc Mixed nếu hệ thống hỗ trợ.
- AI lỗi hoặc timeout: bình luận vẫn được lưu, sentiment ở trạng thái PendingAnalysis.
- Người dùng spam bình luận tiêu cực: Moderator có thể loại khỏi thống kê.

### 9.2. Hệ thống gợi ý tin đăng

#### Mục tiêu

- Tăng khả năng người thuê tìm được tin phù hợp.
- Tăng thời gian sử dụng website.
- Hỗ trợ cá nhân hóa nhưng vẫn dễ triển khai.

#### Dữ liệu đầu vào

- Lịch sử xem tin.
- Lịch sử tìm kiếm.
- Loại phòng thường xem.
- Khoảng giá thường xem.
- Khu vực thường xem.
- Diện tích quan tâm.
- Số người ở.
- Giới tính nếu là ở ghép.
- Tin đã lưu.
- Tin đã liên hệ.
- Tin đang Active trong hệ thống.

#### Cách hoạt động ở mức nghiệp vụ

1. Hệ thống thu thập hành vi hợp lệ của người dùng.
2. Hệ thống xác định hồ sơ nhu cầu tạm thời:
   - Khu vực ưu tiên.
   - Khoảng giá ưu tiên.
   - Loại tin ưu tiên.
   - Tiện ích quan tâm.
3. Hệ thống lấy danh sách tin Active phù hợp.
4. Hệ thống loại bỏ tin đã đóng, hết hạn, bị khóa.
5. Hệ thống ưu tiên tin có:
   - Khu vực gần với lịch sử tìm kiếm.
   - Giá trong khoảng thường xem.
   - Loại tin tương tự tin đã lưu.
   - Điểm uy tín tốt.
   - Còn mới hoặc được đẩy hợp lệ.
6. Hệ thống hiển thị danh sách gợi ý.

#### Khi nào hiển thị gợi ý

- Trang chủ sau khi người dùng đăng nhập.
- Trang chi tiết tin: "Tin tương tự".
- Sau khi người dùng lưu một tin.
- Sau khi người dùng tìm kiếm nhưng ít kết quả.
- Trong chatbot khi đã xác định nhu cầu.
- Trong email/in-app notification nếu có tin mới phù hợp.

#### Cold start

Với người dùng mới hoặc khách chưa đăng nhập:

- Gợi ý tin mới nhất.
- Gợi ý tin phổ biến trong khu vực đang xem.
- Gợi ý theo bộ lọc hiện tại.
- Gợi ý theo vị trí nếu người dùng chọn tỉnh/quận.
- Gợi ý danh mục phổ biến: phòng trọ giá rẻ, ở ghép, căn hộ mini.

#### Quy tắc nghiệp vụ

- Không gợi ý tin Hidden, Expired, Locked.
- Không gợi ý lặp lại quá nhiều một tin.
- Tin trả phí có thể tăng thứ hạng nhưng vẫn cần phù hợp nhu cầu.
- Hệ thống cần lưu RecommendationLog để giải thích và đánh giá hiệu quả.

### 9.3. Chatbot hỗ trợ tìm trọ

#### Mục tiêu

- Giúp người dùng chưa quen bộ lọc vẫn tìm được phòng.
- Giảm thao tác nhập liệu.
- Trả lời câu hỏi thường gặp.
- Hỗ trợ điều hướng website.

#### Phạm vi năng lực

Chatbot nên hỗ trợ:

- Hỏi nhu cầu tìm trọ.
- Tư vấn loại phòng phù hợp.
- Lọc tin theo tiêu chí.
- Trả danh sách bài đăng.
- Giải thích thuật ngữ.
- Hướng dẫn sử dụng website.
- Trả lời FAQ.

Chatbot không nên:

- Tự cam kết phòng còn trống nếu chưa có xác nhận.
- Tư vấn pháp lý chuyên sâu.
- Thay người dùng thương lượng, đặt cọc, ký hợp đồng.
- Tạo tin đăng thay chủ trọ một cách tự động hoàn toàn.

#### Bộ lọc chatbot cần hỗ trợ

- Giá.
- Khu vực.
- Diện tích.
- Nội thất.
- Cho nuôi thú cưng.
- Giờ giấc.
- Chỗ để xe.
- Số người ở.
- Giới tính ở ghép.
- Loại nhà/phòng.
- Tiện ích.

#### Luồng nghiệp vụ

1. Người dùng nhập nhu cầu tự nhiên.
2. Chatbot xác định intent:
   - Tìm phòng.
   - Hỏi hướng dẫn.
   - Hỏi thuật ngữ.
   - Hỏi FAQ.
3. Nếu intent là tìm phòng, chatbot trích xuất filter.
4. Nếu thiếu thông tin quan trọng, chatbot hỏi thêm.
5. Chatbot gọi Search Service.
6. Chatbot hiển thị danh sách tin.
7. Người dùng chọn tin hoặc yêu cầu lọc tiếp.

#### Trường hợp ngoại lệ

- Người dùng nhập yêu cầu mơ hồ: hỏi lại tối đa 2-3 câu.
- Không có kết quả: đề xuất mở rộng giá, khu vực hoặc diện tích.
- Người dùng hỏi ngoài phạm vi: trả lời giới hạn hỗ trợ.
- Câu hỏi có nội dung nhạy cảm: từ chối lịch sự và hướng về chức năng tìm trọ.

### 9.4. AI dự đoán giá thuê

#### Mục tiêu

- Giúp chủ trọ đặt giá phù hợp thị trường.
- Giúp Admin phát hiện tin giá bất thường.
- Giúp người thuê có giá tham khảo.

#### Input

- Khu vực.
- Diện tích.
- Loại nhà.
- Số phòng.
- Số toilet.
- Nội thất.
- Mặt tiền/hẻm.
- Khoảng cách đến trung tâm.
- Tiện ích.
- Tình trạng phòng.

#### Output

- Giá thuê đề xuất.
- Khoảng giá tham khảo: thấp - trung bình - cao.
- Mức độ tin cậy của dự đoán.
- Gợi ý giải thích đơn giản: "Giá cao hơn do gần trung tâm và có nội thất".

#### Luồng nghiệp vụ

1. Chủ trọ nhập form đăng tin.
2. Hệ thống xác định đã đủ thông tin.
3. Hệ thống gọi module dự đoán.
4. Module trả khoảng giá.
5. Hệ thống hiển thị:
   - Giá bạn nhập.
   - Giá AI đề xuất.
   - Chênh lệch.
6. Chủ trọ tự quyết định dùng giá nào.
7. Nếu chênh lệch bất thường, hệ thống ghi flag.

#### Quy tắc nghiệp vụ

- Giá đề xuất không bắt buộc.
- Không hiển thị AI như nguồn đảm bảo chính xác tuyệt đối.
- Nếu dữ liệu đầu vào thiếu hoặc quá khác thường, hệ thống không dự đoán hoặc báo độ tin cậy thấp.
- Admin có thể dùng danh sách tin lệch giá lớn để kiểm duyệt.

---

## 10. Chức năng Admin

### 10.1. Dashboard

Admin xem:

- Tổng số người dùng.
- Tổng số chủ trọ.
- Tổng số tin Active, Pending, Expired, Locked.
- Số tin mới trong ngày/tuần/tháng.
- Số báo cáo đang chờ xử lý.
- Doanh thu từ gói dịch vụ.
- Tỷ lệ thanh toán thành công/thất bại.
- Cảnh báo AI.
- Top khu vực có nhiều tin.
- Top danh mục phổ biến.

### 10.2. Quản lý người dùng

Chức năng:

- Tìm kiếm theo tên, email, số điện thoại.
- Lọc theo vai trò, trạng thái.
- Xem chi tiết hồ sơ.
- Khóa/mở khóa tài khoản.
- Cấp hoặc thu hồi role.
- Xem lịch sử hoạt động.
- Xem report liên quan.

Quy tắc:

- Không xóa cứng user có giao dịch, tin đăng hoặc report.
- Khóa tài khoản phải có lý do.
- Thao tác phân quyền cần ghi audit log.

### 10.3. Quản lý chủ trọ

Chức năng:

- Xem danh sách chủ trọ.
- Xem số tin đã đăng.
- Xem điểm uy tín.
- Xem số report đã xác nhận.
- Xác thực hoặc hủy xác thực chủ trọ.
- Hạn chế đăng tin nếu vi phạm.

### 10.4. Quản lý tin đăng

Chức năng:

- Xem tất cả tin theo trạng thái.
- Duyệt hoặc từ chối tin.
- Sửa trạng thái tin.
- Gắn nhãn cần kiểm tra.
- Khóa/mở khóa tin.
- Xem lịch sử chỉnh sửa.
- Xem thống kê từng tin.

Quy tắc:

- Từ chối tin phải nhập lý do.
- Khóa tin phải nhập lý do và mức độ vi phạm.
- Mở khóa tin cần ghi nhận người thực hiện.

### 10.5. Quản lý danh mục, khu vực, tiện ích

Danh mục:

- Thêm/sửa/ẩn loại tin.
- Cấu hình trường bắt buộc theo loại tin.

Khu vực:

- Quản lý tỉnh/thành, quận/huyện, phường/xã.
- Có thể import dữ liệu hành chính.

Tiện ích:

- Thêm/sửa/ẩn tiện ích.
- Nhóm tiện ích: nội thất, an ninh, sinh hoạt, giao thông.

### 10.6. Quản lý gói dịch vụ

Chức năng:

- Tạo gói đẩy tin.
- Cấu hình giá, thời hạn, mức ưu tiên.
- Bật/tắt gói.
- Xem số lượt mua.
- Cấu hình khuyến mãi nếu cần.

Quy tắc:

- Gói đang có người dùng mua không nên xóa cứng.
- Thay đổi giá không ảnh hưởng giao dịch đã thanh toán.
- Mức ưu tiên cần có giới hạn để tránh làm sai kết quả tìm kiếm.

### 10.7. Quản lý thanh toán

Chức năng:

- Xem danh sách giao dịch.
- Lọc theo trạng thái, ngày, chủ trọ.
- Xem chi tiết giao dịch.
- Đối soát thanh toán.
- Đánh dấu hoàn tiền thủ công nếu đồ án có mô phỏng.

Trạng thái:

- Pending.
- Success.
- Failed.
- Cancelled.
- Refunded.

### 10.8. Quản lý khiếu nại, báo cáo

Chức năng:

- Xem report Pending.
- Gom nhóm report theo tin hoặc user.
- Xem bằng chứng.
- Cập nhật trạng thái xử lý.
- Gửi phản hồi cho người báo cáo.
- Gửi cảnh báo cho người bị báo cáo.

Kết quả xử lý:

- Không vi phạm.
- Vi phạm nhẹ: nhắc nhở.
- Vi phạm trung bình: ẩn nội dung.
- Vi phạm nặng: khóa tin/tài khoản.

### 10.9. Quản lý bình luận và đánh giá

Chức năng:

- Tìm kiếm bình luận theo từ khóa.
- Lọc bình luận tiêu cực.
- Ẩn/hiện bình luận.
- Xem kết quả sentiment.
- Xử lý report bình luận.
- Ẩn đánh giá vi phạm.

Quy tắc:

- Không sửa nội dung đánh giá của người dùng.
- Chỉ ẩn hoặc khôi phục.
- Cần lưu lý do kiểm duyệt.

### 10.10. Quản lý AI

Chức năng:

- Xem log phân tích sentiment.
- Xem danh sách tin bị AI cảnh báo.
- Cấu hình ngưỡng bình luận tiêu cực.
- Cấu hình trọng số điểm uy tín.
- Xem log gợi ý tin đăng.
- Xem lịch sử dự đoán giá.
- Bật/tắt từng module AI nếu cần bảo trì.

Quy tắc:

- AI không tự khóa tài khoản nếu chưa có cấu hình rõ.
- Các quyết định nặng cần Admin/Moderator xác nhận.
- Mọi thay đổi cấu hình AI cần audit log.

---

## 11. Tính năng phi chức năng

### 11.1. Bảo mật

- Mật khẩu lưu bằng hash an toàn.
- Dùng HTTPS khi triển khai thật.
- Chống SQL Injection bằng ORM hoặc prepared statement.
- Chống XSS bằng sanitize input và escape output.
- Chống CSRF cho form quan trọng nếu dùng cookie session.
- Kiểm soát upload ảnh, không cho upload file thực thi.
- Không lộ thông tin nhạy cảm trong API response.

### 11.2. Phân quyền

- Áp dụng Role-Based Access Control.
- Người dùng chỉ sửa dữ liệu thuộc sở hữu của mình.
- Moderator chỉ có quyền kiểm duyệt, không quản lý cấu hình tài chính.
- Admin có quyền cao nhất.
- API cần kiểm tra quyền ở backend, không chỉ ẩn nút ở frontend.

### 11.3. Hiệu năng

- Phân trang danh sách tin.
- Index các trường tìm kiếm: khu vực, giá, diện tích, category, status.
- Cache danh mục, khu vực, tiện ích.
- Lazy load ảnh.
- Tối ưu ảnh upload.
- Dùng job nền cho AI, email, hết hạn tin.

### 11.4. Logging và Audit

Logging:

- Lỗi hệ thống.
- Lỗi thanh toán.
- Lỗi AI.
- Request bất thường.

Audit:

- Khóa/mở khóa tài khoản.
- Khóa/mở khóa tin.
- Thay đổi role.
- Duyệt/từ chối tin.
- Thay đổi cấu hình AI.
- Thay đổi gói dịch vụ.

### 11.5. Backup và khôi phục

- Backup database định kỳ.
- Lưu ảnh ở thư mục/cloud riêng.
- Có kế hoạch khôi phục dữ liệu.
- Không xóa cứng dữ liệu nghiệp vụ quan trọng.

### 11.6. Khả năng mở rộng

- Tách module Listing, Search, Payment, AI theo service/layer rõ ràng.
- AI có thể chạy async bằng queue.
- Search có thể nâng cấp sang Elasticsearch/OpenSearch nếu dữ liệu lớn.
- Upload ảnh có thể chuyển sang cloud storage.

### 11.7. Responsive

- Hỗ trợ desktop, tablet, mobile.
- Mobile ưu tiên tìm kiếm nhanh, bộ lọc dễ dùng, nút liên hệ rõ.
- Form đăng tin cần chia bước để dễ nhập trên mobile.

### 11.8. SEO

- URL chi tiết tin thân thiện.
- Meta title, description theo tin.
- Sitemap cho tin Active.
- Robots.txt.
- Schema markup cơ bản cho listing nếu có thời gian.
- Không index tin hết hạn, tin bị khóa.

### 11.9. Upload ảnh

- Giới hạn dung lượng mỗi ảnh, ví dụ 5MB.
- Giới hạn số ảnh, ví dụ 10 ảnh/tin.
- Nén ảnh và tạo thumbnail.
- Kiểm tra định dạng.
- Có ảnh đại diện chính.
- Xóa ảnh khỏi hiển thị nhưng vẫn có thể lưu log nếu cần.

### 11.10. Chống spam và rate limiting

- Giới hạn số lần đăng tin/ngày với tài khoản mới.
- Giới hạn bình luận/phút.
- Giới hạn report/ngày.
- Giới hạn gửi tin nhắn liên tục.
- Captcha cho hành vi nghi ngờ.
- Chặn từ khóa cấm.

### 11.11. Cache

- Cache danh mục, tiện ích, khu vực.
- Cache trang chủ trong thời gian ngắn.
- Cache kết quả gợi ý có TTL.
- Không cache dữ liệu cá nhân nhạy cảm.

### 11.12. Notification

- Có bảng Notification trong hệ thống.
- Đánh dấu đã đọc/chưa đọc.
- Email cho sự kiện quan trọng.
- Có thể tắt một số loại thông báo không quan trọng.

---

## 12. API nghiệp vụ chính

### 12.1. Authentication

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | /api/auth/register | Đăng ký |
| POST | /api/auth/login | Đăng nhập |
| POST | /api/auth/logout | Đăng xuất |
| POST | /api/auth/forgot-password | Quên mật khẩu |
| POST | /api/auth/reset-password | Đặt lại mật khẩu |
| POST | /api/auth/verify-email | Xác thực email |
| POST | /api/auth/verify-phone | Xác thực số điện thoại |

### 12.2. User

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/users/me | Xem hồ sơ |
| PUT | /api/users/me | Cập nhật hồ sơ |
| GET | /api/users/{id} | Xem hồ sơ công khai |
| POST | /api/users/{id}/follow | Theo dõi |
| DELETE | /api/users/{id}/follow | Bỏ theo dõi |

### 12.3. Listing

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/listings | Danh sách/tìm kiếm tin |
| GET | /api/listings/{id} | Chi tiết tin |
| POST | /api/listings | Tạo tin |
| PUT | /api/listings/{id} | Sửa tin |
| DELETE | /api/listings/{id} | Xóa mềm tin |
| POST | /api/listings/{id}/submit | Gửi duyệt |
| POST | /api/listings/{id}/hide | Ẩn tin |
| POST | /api/listings/{id}/close | Đóng tin |
| POST | /api/listings/{id}/renew | Gia hạn tin |
| POST | /api/listings/{id}/images | Upload ảnh |
| DELETE | /api/listings/{id}/images/{imageId} | Xóa ảnh |
| GET | /api/listings/{id}/stats | Thống kê tin |

### 12.4. Search, Favorite, History

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/search/listings | Tìm kiếm tin |
| GET | /api/listings/suggested | Tin gợi ý |
| POST | /api/favorites | Lưu tin |
| DELETE | /api/favorites/{listingId} | Bỏ lưu |
| GET | /api/favorites | Danh sách đã lưu |
| GET | /api/history/views | Lịch sử xem |
| DELETE | /api/history/views | Xóa lịch sử xem |

### 12.5. Contact & Chat

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | /api/listings/{id}/contact | Ghi nhận liên hệ |
| GET | /api/landlord/contacts | Chủ trọ xem người liên hệ |
| GET | /api/conversations | Danh sách cuộc trò chuyện |
| POST | /api/conversations | Tạo cuộc trò chuyện |
| GET | /api/conversations/{id}/messages | Xem tin nhắn |
| POST | /api/conversations/{id}/messages | Gửi tin nhắn |

### 12.6. Comment & Review

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/listings/{id}/comments | Danh sách bình luận |
| POST | /api/listings/{id}/comments | Tạo bình luận |
| PUT | /api/comments/{id} | Sửa bình luận |
| DELETE | /api/comments/{id} | Xóa bình luận |
| POST | /api/comments/{id}/reply | Trả lời bình luận |
| GET | /api/listings/{id}/reviews | Danh sách đánh giá |
| POST | /api/listings/{id}/reviews | Tạo đánh giá |
| PUT | /api/reviews/{id} | Sửa đánh giá |
| DELETE | /api/reviews/{id} | Xóa/ẩn đánh giá |

### 12.7. Report

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | /api/reports | Tạo báo cáo |
| GET | /api/reports/my | Báo cáo của tôi |
| GET | /api/admin/reports | Admin xem báo cáo |
| PUT | /api/admin/reports/{id}/resolve | Xử lý báo cáo |

### 12.8. Payment & Promotion

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/promotion-packages | Danh sách gói |
| POST | /api/payments | Tạo thanh toán |
| GET | /api/payments/{id} | Chi tiết thanh toán |
| POST | /api/payments/callback | Callback thanh toán |
| GET | /api/payments/my | Lịch sử thanh toán |
| POST | /api/listings/{id}/promote | Mua gói đẩy tin |

### 12.9. AI

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | /api/ai/sentiment/analyze | Phân tích bình luận |
| POST | /api/ai/recommendations | Lấy gợi ý |
| POST | /api/ai/chatbot/message | Gửi tin nhắn chatbot |
| POST | /api/ai/price-prediction | Dự đoán giá |
| GET | /api/admin/ai/logs | Admin xem log AI |
| PUT | /api/admin/ai/config | Cập nhật cấu hình AI |

### 12.10. Admin

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/admin/dashboard | Dashboard |
| GET | /api/admin/users | Quản lý người dùng |
| PUT | /api/admin/users/{id}/lock | Khóa user |
| PUT | /api/admin/users/{id}/unlock | Mở khóa user |
| PUT | /api/admin/users/{id}/roles | Cập nhật role |
| GET | /api/admin/listings | Quản lý tin |
| PUT | /api/admin/listings/{id}/approve | Duyệt tin |
| PUT | /api/admin/listings/{id}/reject | Từ chối tin |
| PUT | /api/admin/listings/{id}/lock | Khóa tin |
| GET | /api/admin/categories | Danh mục |
| POST | /api/admin/categories | Tạo danh mục |
| PUT | /api/admin/categories/{id} | Sửa danh mục |
| GET | /api/admin/payments | Quản lý thanh toán |
| GET | /api/admin/statistics | Thống kê |

---

## 13. Những chức năng nên có và không nên có

### 13.1. Chức năng nên có trong đồ án

| Chức năng | Phù hợp đồ án | Dễ triển khai | Giá trị thực tế | Nhận xét |
|---|---|---|---|---|
| Đăng ký/đăng nhập/phân quyền | Cao | Cao | Cao | Bắt buộc |
| Đăng tin nhiều loại | Cao | Trung bình | Cao | Là lõi hệ thống |
| Duyệt tin | Cao | Trung bình | Cao | Tạo tính thực tế |
| Tìm kiếm/lọc nâng cao | Cao | Trung bình | Cao | Rất quan trọng |
| Lưu tin | Cao | Dễ | Cao | Dữ liệu cho gợi ý |
| Lịch sử xem/tìm kiếm | Cao | Dễ | Cao | Hỗ trợ AI |
| Liên hệ chủ trọ | Cao | Dễ | Cao | Cốt lõi chuyển đổi |
| Bình luận | Cao | Trung bình | Trung bình | Cần cho sentiment |
| Đánh giá | Cao | Trung bình | Cao | Tăng uy tín |
| Báo cáo vi phạm | Cao | Trung bình | Cao | Cần cho kiểm duyệt |
| Admin dashboard | Cao | Trung bình | Cao | Thể hiện quản trị |
| Thanh toán đẩy tin | Cao | Trung bình | Cao | Có giá trị thương mại |
| Sentiment Analysis | Cao | Trung bình | Cao | AI rõ nghiệp vụ |
| Recommendation | Cao | Trung bình | Cao | AI thực tế |
| Chatbot tìm trọ | Cao | Trung bình | Cao | Tăng trải nghiệm |
| Dự đoán giá thuê | Cao | Trung bình | Cao | AI phù hợp lĩnh vực |
| Notification | Cao | Trung bình | Cao | Hệ thống thực tế |
| Upload ảnh | Cao | Trung bình | Cao | Bắt buộc với tin trọ |

### 13.2. Chức năng nên làm ở mức đơn giản

| Chức năng | Lý do làm đơn giản |
|---|---|
| Thanh toán online | Có thể dùng sandbox hoặc mô phỏng để tránh phụ thuộc pháp lý, đối soát thật |
| Chat nội bộ | Chỉ cần nhắn tin cơ bản, không cần realtime phức tạp nếu thiếu thời gian |
| Xác thực chủ trọ | Chỉ cần trạng thái xác thực thủ công bởi Admin |
| Bản đồ | Có thể hiển thị vị trí và lọc khu vực, không cần tìm đường nâng cao |
| SEO | Làm meta, URL, sitemap cơ bản |
| Recommendation | Dùng rule-based kết hợp điểm hành vi, không cần thuật toán phức tạp |
| Price Prediction | Hiển thị khoảng giá tham khảo, không cần giải thích ML sâu |

### 13.3. Chức năng không nên có trong phạm vi đồ án

| Chức năng | Lý do nên loại bỏ |
|---|---|
| Ký hợp đồng điện tử | Phức tạp pháp lý, không cần cho website quảng cáo/tìm kiếm |
| Đặt cọc online giữ phòng | Rủi ro tranh chấp, cần quy trình hoàn tiền và xác minh mạnh |
| AI xác minh giấy tờ nhà đất | Khó triển khai, dữ liệu nhạy cảm, vượt phạm vi đồ án |
| AI nhận diện phòng thật/giả từ ảnh | Khó đảm bảo chính xác, cần dữ liệu lớn |
| Livestream xem phòng | Không cần thiết, tốn hạ tầng |
| Định giá pháp lý tài sản | Không liên quan thuê trọ phổ thông |
| Tự động gọi điện cho chủ trọ | Không phù hợp, rủi ro spam |
| Mạng xã hội đầy đủ | Làm loãng mục tiêu chính |
| Đấu giá phòng thuê | Không phổ biến, nghiệp vụ không thực tế |
| Blockchain lưu hợp đồng | Không cần thiết, gây phức tạp không tạo giá trị rõ |

---

## 14. Gợi ý phạm vi triển khai theo mức ưu tiên

### 14.1. MVP bắt buộc

- Đăng ký, đăng nhập, phân quyền.
- Quản lý hồ sơ.
- Đăng tin, sửa tin, ẩn, đóng, gia hạn.
- Upload ảnh.
- Tìm kiếm và lọc.
- Xem chi tiết tin.
- Lưu tin.
- Liên hệ chủ trọ.
- Bình luận.
- Báo cáo vi phạm.
- Admin duyệt tin và quản lý report.
- AI sentiment cơ bản.
- AI dự đoán giá cơ bản.

### 14.2. Nâng cao nên có

- Recommendation System.
- Chatbot tìm trọ.
- Đánh giá chủ trọ/tin.
- Thanh toán mô phỏng hoặc sandbox.
- Gói đẩy tin.
- Notification.
- Dashboard thống kê.

### 14.3. Có thể làm nếu còn thời gian

- Chat nội bộ realtime.
- Bản đồ tương tác.
- Theo dõi chủ trọ.
- Email tự động.
- SEO nâng cao.
- Audit log chi tiết.

---

## 15. Kết luận kiến trúc nghiệp vụ

Website quảng cáo và tìm kiếm phòng trọ nên được thiết kế xoay quanh ba trục chính:

1. Tin đăng chất lượng và dễ tìm.
2. Niềm tin giữa người thuê và chủ trọ.
3. AI hỗ trợ trải nghiệm nhưng không thay thế kiểm duyệt con người.

Về mặt kiến trúc phần mềm, hệ thống nên chia thành các module rõ ràng:

- Auth & User.
- Listing.
- Search.
- Interaction: favorite, contact, comment, review.
- Moderation & Report.
- Payment & Promotion.
- Notification.
- AI Services.
- Admin.

Cách chia này giúp đồ án đủ lớn, có nghiệp vụ thực tế, dễ thiết kế database, dễ vẽ use case diagram, sequence diagram, activity diagram và có thể triển khai từng phần theo mức ưu tiên.
