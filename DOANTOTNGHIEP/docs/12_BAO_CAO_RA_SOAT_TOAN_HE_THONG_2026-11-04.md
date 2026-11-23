# Báo cáo rà soát hệ thống Webtro trên Docker

## 1. Thông tin đợt kiểm tra

- Thời gian: 04/11/2026, múi giờ Asia/Bangkok.
- Mã nguồn: commit `d26e8123f46b38de4a831e820f96f5cd2534daa4`, nhánh `main`.
- Môi trường: Docker 29.9.2, Docker Compose 5.6.1.
- Trình duyệt thực tế: Google Chrome 156.0.8078.80 và Microsoft Edge 156.0.4314.42, mỗi vai trò dùng browser context riêng tương đương cửa sổ ẩn danh/InPrivate.
- Kích thước kiểm tra responsive: desktop 1366×768 và mobile 390×844.
- Cách chạy: build toàn bộ image bằng Docker Compose, khởi tạo lại container nhưng giữ volume dữ liệu, sau đó kiểm tra UI, request/response API, WebSocket, health endpoint, log container, dữ liệu MySQL và mã nguồn liên quan.

Hệ thống vẫn đang chạy sau kiểm tra tại `http://localhost`.

## 2. Kết luận nhanh

Đã xác nhận **10 lỗi**:

| Mức độ | Số lượng |
|---|---:|
| Cao | 4 |
| Trung bình | 5 |
| Thấp | 1 |

Các luồng công khai, đăng nhập và phân quyền cơ bản hoạt động. Những lỗi nghiêm trọng nhất nằm ở đặt lại mật khẩu, hàng đợi kiểm duyệt, trang chi tiết tin dành cho kiểm duyệt viên và trang quản lý thanh toán. Hai vấn đề vận hành đáng chú ý là NLU chatbot đang chạy ở trạng thái suy giảm nhưng Docker vẫn báo healthy, và cache Redis liên tục không giải tuần tự hóa được dữ liệu cũ.

Không sửa mã nguồn trong đợt này; thay đổi duy nhất là file báo cáo này.

### 2.1. Trạng thái khắc phục sau rà soát — cập nhật 04/11/2026

Phần 3–5 bên dưới được giữ nguyên làm bằng chứng tại thời điểm rà soát. Bảng này ghi trạng thái
sau đợt sửa tiếp theo, không thay thế kết quả tái hiện ban đầu.

| Mã | Trạng thái sau sửa | Nội dung đã xử lý |
|---|---|---|
| BG-01 | Đã sửa | Frontend gửi đủ `token`, `newPassword`, `confirmPassword`; luồng reset thành công được kiểm tra lại. |
| BG-02 | Đã sửa | API hàng đợi nhận `status`, chỉ cho phép `PENDING`/`NEED_REVIEW`; hai tab lấy dữ liệu riêng và hiển thị hành động theo status thật của row. |
| BG-03 | Đã sửa | Link quản trị mở route chi tiết công khai hợp lệ của đúng tin; kiểm tra lại response 200, không còn rơi vào 404. |
| BG-04 | Đã sửa | Thống nhất `paymentMethod`; response có tên/email người mua; `keyword` tìm theo mã giao dịch hoặc tên/email và kết hợp được với status/phương thức. |
| BG-05 | Đã sửa | Frontend/backend dùng `categoryCode`; bộ lọc loại tin được truyền xuyên suốt tới truy vấn. |
| BG-06 | Đã sửa | `AdminListingResponse` có `categoryCode`, `categoryName`, `ownerName`, `provinceName`; mapper lấy metadata từ quan hệ đã tải, không tạo giá trị giả. |
| BG-07 | Đã sửa | `module` là tùy chọn; khi bỏ trống, backend hợp nhất 3 nguồn log rồi sort/phân trang toàn cục và trả `module=ALL`. |
| BG-08 | Đã sửa phần monitoring; còn thiếu artifact | Có `/live`, `/health`, `/ready`; Compose kiểm `/ready`, chatbot vẫn giữ fallback. Hai thư mục model local vẫn có 0 file nên NLU local chưa sẵn sàng; không tạo hoặc giả lập model. |
| BG-09 | Đã sửa | Cache dùng serializer thống nhất; lỗi đọc cache chỉ xóa đúng key hỏng rồi fallback DB, không xóa JWT blacklist/rate-limit. |
| BG-10 | Đã sửa | Nội dung public và tài liệu UI dùng phạm vi Hà Nội; migration `V31__align_about_page_scope.sql` chỉ thay giá trị seed cũ, giữ nguyên nội dung Admin đã tùy chỉnh. |

Xác minh tự động sau sửa: backend 252 test, không fail/error; các dòng và nhánh Java thay đổi đạt
100% theo JaCoCo. Frontend có 4 contract test đạt 100% line/branch/function và build thành công.
NLU có 3 test health/readiness đạt. Regression runtime cho các luồng đã sửa đạt; giới hạn model
artifact của BG-08 vẫn được ghi rõ, không đánh dấu NLU local đã hoạt động. Flyway đã áp dụng V31
thành công trên Docker và `GET /api/content/about` trả nội dung có phạm vi “tại Hà Nội”.

## 3. Phạm vi đã kiểm tra thực tế

### 3.1. Docker và dịch vụ

- Frontend, backend, MySQL, Redis, MailHog, recommendation API, rental-price API, AI portal, chatbot API, chatbot MySQL/Redis và chatbot NLU đều khởi động.
- Frontend trả HTTP 200; backend readiness trả HTTP 200.
- Recommendation API trả `status=ok`, có 12.958 phòng trong index.
- Rental-price API trả `status=ok`, mô hình LightGBM có 5.175 mẫu.
- Chatbot NLU trả HTTP 200 nhưng nội dung là `status=degraded`; đây là lỗi BG-08 bên dưới.
- Frontend và backend đều build thành công trong image Docker. Lần build backend đầu tiên gặp lỗi BuildKit tạm thời `frontend grpc server closed unexpectedly`; chạy lại cùng source đã thành công, không có bằng chứng đây là lỗi ứng dụng.

### 3.2. Bao phủ trang và vai trò

Đã mở và kiểm tra lỗi console, request thất bại, HTTP lỗi, ảnh hỏng và trạng thái hiển thị trên tổng cộng **70 lượt trang**:

| Phiên | Số trang | Trang đích sau đăng nhập | Kết quả chính |
|---|---:|---|---|
| Khách | 9 | Không áp dụng | Không có lỗi chung ngoài các lỗi riêng đã nêu |
| Tenant | 14 | `/` | Đúng |
| Landlord | 13 | `/quan-ly/tong-quan` | Đúng |
| Moderator | 11 | `/admin/kiem-duyet` | Đúng; phát hiện lỗi log AI/kiểm duyệt |
| Admin | 23 | `/admin/dashboard` | Đúng; phát hiện lỗi quản trị |

Kiểm tra chặn quyền trực tiếp bằng URL:

- Tenant bị đưa tới `/403` khi vào `/admin/dashboard` và `/quan-ly/thanh-toan`.
- Landlord bị đưa tới `/403` khi vào `/admin/dashboard` và `/admin/nguoi-dung`.
- Moderator bị đưa tới `/403` khi vào `/admin/dashboard`, `/admin/nguoi-dung` và `/admin/cau-hinh`.

### 3.3. Các luồng đã thao tác và có kết quả đúng

- Khách: trang chủ, tìm kiếm “Cầu Giấy”, 38 kết quả, mở chi tiết tin, ảnh/nội dung, số điện thoại dạng text, yêu cầu đăng nhập khi lưu tin.
- Responsive: trang chủ, tìm kiếm, hồ sơ và bước đầu tạo tin không tràn ngang, không có ảnh hỏng ở 390×844.
- Đăng ký tài khoản mới, nhận email xác minh qua MailHog, xác minh và đăng nhập lần đầu.
- Quên mật khẩu gửi được email và link reset; bước đặt mật khẩu mới lỗi như BG-01.
- Tenant: lưu/bỏ lưu tin (201/204), bình luận rồi xóa (201/204), theo dõi/bỏ theo dõi chủ trọ (201/204); trạng thái đều trở về ban đầu.
- Yêu cầu thuê: Tenant tạo yêu cầu (201), Landlord tương ứng nhìn thấy và từ chối (200), Tenant nhìn thấy trạng thái “Đã từ chối”.
- Chat hai chiều thời gian thực: Landlord gửi và Tenant nhận; Tenant gửi và Landlord nhận; cả hai lần tạo message đều HTTP 201.
- Chatbot: câu “Tìm phòng dưới 4 triệu ở Cầu Giấy” trả 5 tin giá 1,8–2,5 triệu. Đối chiếu chi tiết tin đầu tiên xác nhận giá 1,8 triệu và địa chỉ Quận Cầu Giấy. Câu hỏi tiếp theo giữ cùng conversation ID.
- Validation tạo tin: để trống bước địa chỉ hiển thị đúng lỗi tỉnh/quận/phường/địa chỉ bắt buộc.

### 3.4. Dữ liệu phát sinh trong môi trường local

- Có tài khoản QA đăng ký mới phục vụ luồng xác minh/quên mật khẩu; một lần thử xác minh không hoàn tất và một tài khoản đã xác minh.
- Có hai yêu cầu thuê QA ở trạng thái đã từ chối và một số message QA trong cuộc trò chuyện demo do chạy lại để loại trừ lỗi thời điểm/WebSocket.
- Favorite, comment và follow đã được hoàn tác.
- Không có dữ liệu nào được gửi tới hệ thống ngoài máy local.

### 3.5. Giới hạn và phần chưa kết luận

Không đánh dấu “đạt” cho các trường hợp chưa thực hiện. Đợt này không thực hiện các thao tác có nguy cơ phá hủy hoặc làm biến đổi rộng dữ liệu demo như xóa vĩnh viễn user/tin, đổi role Admin, khóa Admin cuối cùng, refund tiền, callback cổng thanh toán thật, chạy tua thời gian các scheduler, retrain/rebuild model và kiểm thử tải/đồng thời quy mô lớn. Các biến thể kết hợp của mọi bộ lọc cũng không thể xem là kiểm thử tổ hợp đầy đủ chỉ từ một vòng rà soát UI.

## 4. Danh sách lỗi xác nhận

### BG-01 — Không thể hoàn tất đặt lại mật khẩu

- **Mức độ:** Cao.
- **Vai trò/luồng:** Khách hoặc người dùng quên mật khẩu.
- **Cách tái hiện:** đăng ký và xác minh tài khoản → Quên mật khẩu → mở link trong email → nhập hai trường mật khẩu mới giống nhau → gửi form.
- **Kết quả thực tế:** `POST /api/auth/reset-password` trả HTTP 400, `VALIDATION_FAILED`, trường thiếu là `confirmPassword`.
- **Kết quả mong đợi:** mật khẩu được thay đổi; token reset bị vô hiệu hóa; người dùng đăng nhập được bằng mật khẩu mới và không đăng nhập được bằng mật khẩu cũ.
- **Nguyên nhân đã đối chiếu:** frontend có validate `confirmPassword` nhưng chỉ gửi `token` và `newPassword` tại `frontend_webtro/src/pages/auth/ResetPasswordPage.jsx:50`. Backend bắt buộc `confirmPassword` tại `backend_webtro/src/main/java/com/webtro/modules/auth/dto/request/ResetPasswordRequest.java:36`.
- **Phương án sửa không degrade:** frontend gửi thêm `confirmPassword: values.confirmPassword`. Giữ nguyên validation khớp mật khẩu ở cả frontend và backend; không bỏ ràng buộc backend để “làm cho request qua”.
- **Kiểm thử hồi quy bắt buộc:** reset thành công; hai mật khẩu không khớp bị chặn; token thiếu/hết hạn/đã dùng bị từ chối; mật khẩu cũ không còn hiệu lực; mọi phiên cũ bị xử lý theo chính sách hiện tại.

### BG-02 — Hai tab hàng đợi kiểm duyệt trả cùng dữ liệu và hiện thao tác sai trạng thái

- **Mức độ:** Cao.
- **Vai trò/luồng:** Moderator, Admin.
- **Dữ liệu đối chiếu:** DB có 5 tin `PENDING` và 2 tin `NEED_REVIEW`.
- **Kết quả thực tế:** tab “Chờ duyệt” hiện cả 7 tin với nút Duyệt/Từ chối; tab “Cần kiểm tra” vẫn hiện cùng 7 tin nhưng đổi thành Gỡ cờ/Ẩn. Như vậy 5 tin `PENDING` bị trình bày như `NEED_REVIEW`, và 2 tin `NEED_REVIEW` bị trình bày như `PENDING` tùy tab đang chọn.
- **Bằng chứng request:** frontend gửi `status=PENDING` hoặc `status=NEED_REVIEW`, nhưng kết quả ở hai tab không đổi.
- **Nguyên nhân đã đối chiếu:** `AdminListingController` không nhận tham số `status` tại `backend_webtro/src/main/java/com/webtro/modules/admin/controller/AdminListingController.java:87`; service luôn ghép cứng cả hai trạng thái tại `backend_webtro/src/main/java/com/webtro/modules/admin/service/impl/AdminListingServiceImpl.java:111`. Frontend lại giả định response đã được lọc và chọn thao tác theo tab trong `frontend_webtro/src/pages/admin/ModerationQueuePage.jsx`.
- **Phương án sửa không degrade:** thêm tham số trạng thái được whitelist chỉ `PENDING`/`NEED_REVIEW`, truyền xuyên suốt controller → service → repository và trả `totalElements` đúng theo tab. Vẫn giữ state machine backend để chặn chuyển trạng thái trái phép; không chỉ ẩn nút phía UI.
- **Kiểm thử hồi quy bắt buộc:** ID ở hai tab không giao nhau; số lượng lần lượt 5 và 2 với dữ liệu hiện tại; từng row chỉ có hành động hợp lệ; request trạng thái khác bị từ chối rõ ràng; phân trang/tổng số đúng sau khi xử lý một tin.

### BG-03 — Link chi tiết tin trong trang quản trị luôn dẫn tới 404

- **Mức độ:** Cao.
- **Vai trò/luồng:** Moderator, Admin; đặc biệt ảnh hưởng việc xem nội dung trước khi duyệt.
- **Cách tái hiện:** vào “Tin đăng” hoặc “Kiểm duyệt” → bấm tiêu đề một tin.
- **Kết quả thực tế:** link chuyển tới `/admin/tin-dang/{id}` rồi hiển thị trang 404 công khai.
- **Nguyên nhân đã đối chiếu:** link được tạo tại `frontend_webtro/src/pages/admin/ListingsPage.jsx:84` và `frontend_webtro/src/pages/admin/ModerationQueuePage.jsx:67`, nhưng router chỉ khai báo route danh sách `/admin/tin-dang` tại `frontend_webtro/src/routes/router.jsx:244`, không có route chi tiết.
- **Phương án sửa không degrade:** tạo trang/route chi tiết được bảo vệ cho Admin và Moderator, dùng API chi tiết quản trị để đọc được cả tin chưa công khai và hiển thị đúng hành động theo status/quyền. Nếu dùng drawer/dialog thay route thì cả hai link hiện tại phải mở cùng một chi tiết đầy đủ và có deep-link tương đương; không thay bằng việc bỏ khả năng xem chi tiết.
- **Kiểm thử hồi quy bắt buộc:** bấm link từ cả hai trang mở đúng ID; Moderator/Admin xem được tin chưa công khai; Tenant/khách không truy cập được endpoint quản trị; refresh/deep-link vẫn hoạt động.

### BG-04 — Trang quản lý thanh toán sai hợp đồng dữ liệu và các bộ lọc không hoạt động

- **Mức độ:** Cao.
- **Vai trò/luồng:** Admin.
- **Kết quả thực tế:** toàn bộ người mua hiển thị `—`, phương thức thanh toán trống. Nhập đúng mã giao dịch vào ô “Tìm mã giao dịch, người mua” vẫn giữ nguyên 9 dòng; chọn phương thức `SANDBOX` cũng không thay đổi kết quả.
- **Nguyên nhân đã đối chiếu:** frontend gửi `keyword` và `method` tại `frontend_webtro/src/pages/admin/PaymentsPage.jsx:42-44`; backend chỉ nhận `transactionCode`, `userId` và `paymentMethod` tại `backend_webtro/src/main/java/com/webtro/modules/payment/controller/AdminPaymentController.java:51-63`. Frontend đọc `r.method` tại dòng 87 trong khi DTO trả `paymentMethod` tại `backend_webtro/src/main/java/com/webtro/modules/payment/dto/response/PaymentResponse.java:68`. Frontend đọc `userName`/`buyerName` tại dòng 81 nhưng DTO chỉ có `userId`.
- **Phương án sửa không degrade:** thống nhất contract bằng một admin payment DTO có `userId`, tên/email người mua và `paymentMethod`; frontend đọc đúng field. Backend triển khai đúng chức năng đã quảng bá của ô tìm kiếm: keyword tìm trên transaction code và tên/email người mua, đồng thời nhận `paymentMethod`. Không đổi nhãn UI thành chức năng hẹp hơn để né việc sửa.
- **Kiểm thử hồi quy bắt buộc:** lọc mã giao dịch chính xác; tìm theo tên/email; lọc từng phương thức; kết hợp status + method + keyword + thời gian; phân trang và tổng tiền không bị sai; query dùng join/projection tránh N+1.

### BG-05 — Bộ lọc loại tin ở trang quản trị tin đăng bị backend bỏ qua

- **Mức độ:** Trung bình.
- **Vai trò/luồng:** Moderator, Admin.
- **Cách tái hiện:** vào `/admin/tin-dang` → chọn “Phòng trọ”.
- **Kết quả thực tế:** danh sách không đổi và vẫn chứa “Căn hộ”.
- **Nguyên nhân đã đối chiếu:** frontend gửi `category=BOARDING_HOUSE` tại `frontend_webtro/src/pages/admin/ListingsPage.jsx:48`; backend chỉ nhận `categoryId` kiểu Long tại `backend_webtro/src/main/java/com/webtro/modules/admin/controller/AdminListingController.java:69`.
- **Phương án sửa không degrade:** hoặc frontend nạp catalog và gửi đúng `categoryId`, hoặc backend hỗ trợ `categoryCode` chuẩn hóa. Nên dùng một tên tham số duy nhất được mô tả trong OpenAPI; không bỏ bộ lọc khỏi UI.
- **Kiểm thử hồi quy bắt buộc:** mỗi loại tin chỉ trả đúng category; reset filter trả toàn bộ; kết hợp category với status/keyword/khu vực và phân trang vẫn đúng.

### BG-06 — Danh sách tin quản trị thiếu tên loại tin, khu vực và chủ trọ

- **Mức độ:** Trung bình.
- **Vai trò/luồng:** Moderator, Admin.
- **Kết quả thực tế:** mô tả phụ ở mọi row chỉ hiện dấu `·`; cột Chủ trọ hiện `—`.
- **Nguyên nhân đã đối chiếu:** frontend yêu cầu `categoryName`/`categoryCode`, `provinceName`, `ownerName` tại `frontend_webtro/src/pages/admin/ListingsPage.jsx:88-101`. `AdminListingResponse` chỉ có các ID tương ứng tại `backend_webtro/src/main/java/com/webtro/modules/admin/dto/response/AdminListingResponse.java:29-41`; mapper cũng chỉ gán ID tại `backend_webtro/src/main/java/com/webtro/modules/admin/mapper/AdminListingMapper.java:33-45`.
- **Phương án sửa không degrade:** bổ sung các trường tên/code vào DTO quản trị và truy vấn theo batch/projection để tránh N+1. Giữ ID để phục vụ điều hướng/filter; không thay thông tin bằng giá trị giả hoặc bỏ cột.
- **Kiểm thử hồi quy bắt buộc:** tên/chủ/khu vực khớp catalog và user thật; dữ liệu đã xóa mềm có nhãn an toàn; số query không tăng tuyến tính theo số row.

### BG-07 — Trang Log AI lỗi ngay trạng thái mặc định và khi chọn “Tất cả module”

- **Mức độ:** Trung bình.
- **Vai trò/luồng:** Moderator, Admin.
- **Kết quả thực tế:** request mặc định `GET /api/admin/ai/logs?page=0&size=20&sort=createdAt,desc` trả HTTP 400 với lỗi “Thiếu tham số bắt buộc” cho field `module`. Chọn module “Gợi ý” trả 200; chọn lại “Tất cả module” lại trả 400.
- **Nguyên nhân đã đối chiếu:** frontend có option “Tất cả module” nhưng bỏ tham số khi giá trị rỗng tại `frontend_webtro/src/pages/admin/AiLogsPage.jsx:31,80-81`. Backend khai báo `module` bắt buộc tại `backend_webtro/src/main/java/com/webtro/modules/admin/controller/AdminAiController.java:68` và service chỉ switch theo một module tại `backend_webtro/src/main/java/com/webtro/modules/admin/service/impl/AdminAiServiceImpl.java:166`.
- **Phương án sửa không degrade:** hỗ trợ thật sự truy vấn tất cả module ở backend, hợp nhất kết quả với sort toàn cục, phân trang và tổng số chính xác. Giữ lựa chọn “Tất cả module”; không loại bỏ option hoặc ép mặc định một module để che lỗi.
- **Kiểm thử hồi quy bắt buộc:** mặc định/all/specific module đều 200; sort theo thời gian đúng sau hợp nhất; chuyển tab “Tin lệch giá” không dùng sai tham số; Moderator vẫn chỉ có quyền đọc.

### BG-08 — NLU chatbot không nạp được model nhưng Docker healthcheck báo healthy

- **Mức độ:** Trung bình.
- **Phạm vi:** AI/chatbot và vận hành.
- **Kết quả thực tế:** `GET http://localhost:8003/health` trả `status=degraded`, `startup_error` báo không nhận dạng model trong `/app/ml/out-intent`. Hai thư mục host `ml/out-intent` và `ml/out-ner` đều có 0 file. Tuy vậy container `webtro-chatbot-nlu` được Docker đánh dấu healthy.
- **Tác động đã xác nhận:** chatbot vẫn trả kết quả nhờ fallback; model intent/NER cục bộ không hoạt động. Vì vậy chất lượng NLU không đúng cấu hình mong muốn, còn monitoring không phát hiện trạng thái suy giảm.
- **Nguyên nhân đã đối chiếu:** model artifacts bị gitignore tại `AI_Module/Module-AI-CHATBOT/chatbot/ml/.gitignore:3-4`, Compose mount trực tiếp hai thư mục này tại `docker-compose.yml:290`; health endpoint luôn trả HTTP 200 và chỉ đổi field `status` tại `AI_Module/Module-AI-CHATBOT/chatbot/nlu-service/app.py:132-139`. Healthcheck Compose chỉ kiểm tra mở URL thành công tại `docker-compose.yml:294`. Fallback được kích hoạt khi gọi local NLU lỗi tại `AI_Module/Module-AI-CHATBOT/chatbot/src/main/java/com/roomfinder/chat/service/LocalNluServiceImpl.java:93-99`.
- **Phương án sửa không degrade:** cung cấp model artifact đã version hóa qua artifact store/volume có checksum và kiểm tra đủ file trước khi load. Tách `/live` (process sống) và `/ready` (model đã sẵn sàng); readiness trả non-2xx khi degraded và Compose kiểm tra `/ready`. Đồng thời để chatbot API khởi động độc lập với readiness NLU hoặc chỉ phụ thuộc `service_started`, nhằm tiếp tục dùng fallback khi NLU hỏng. Không được tắt fallback.
- **Kiểm thử hồi quy bắt buộc:** model load thành công và `/ready` 200; thiếu/corrupt model làm `/ready` non-2xx; chatbot chính vẫn trả response qua fallback; metric/log cho biết rõ tầng NLU thực tế được dùng.

### BG-09 — Cache Redis không đọc được dữ liệu sau khi container được khởi tạo lại

- **Mức độ:** Trung bình.
- **Phạm vi:** Hiệu năng và vận hành backend.
- **Kết quả thực tế:** trong khoảng 40 phút kiểm tra có 99 cảnh báo `Redis cache GET lỗi`. Lỗi giải tuần tự hóa lặp lại với thông báo `Unexpected token (START_ARRAY)... expected ... type id`. Request vẫn thành công vì hệ thống fail-open và đọc DB.
- **Nguyên nhân đã đối chiếu:** payload đang tồn tại trong Redis không tương thích với serializer hiện tại `GenericJackson2JsonRedisSerializer` tại `backend_webtro/src/main/java/com/webtro/config/RedisConfig.java:57-73`. Handler tại dòng 88 chỉ log và fallback, không loại key không thể đọc, nên lỗi lặp lại. Nguồn/version cụ thể đã tạo payload cũ chưa đủ bằng chứng để kết luận.
- **Phương án sửa không degrade:** version hóa namespace cache khi đổi schema/serializer; khi lỗi deserialize, chỉ evict key cache hỏng và nạp lại từ DB một lần. Không `FLUSHALL` vì cùng Redis còn dùng cho rate-limit và JWT blacklist. Có thể lưu cache schema version riêng và chỉ xóa các prefix cache đã biết khi nâng version.
- **Kiểm thử hồi quy bắt buộc:** khởi động phiên bản mới với payload cache cũ; request đầu fallback và sửa key; request sau cache hit không còn warning; JWT blacklist/rate-limit không bị xóa hay vô hiệu hóa.

### BG-10 — Nội dung UI nói “toàn quốc” trái với phạm vi dữ liệu Hà Nội

- **Mức độ:** Thấp.
- **Phạm vi:** UI/nội dung công khai.
- **Kết quả thực tế:** trang chủ ghi “Hàng nghìn tin đăng đã kiểm duyệt trên toàn quốc”; trang Giới thiệu mô tả nền tảng kết nối trên toàn quốc. Hệ thống và dữ liệu hiện tại giới hạn khu vực Hà Nội.
- **Vị trí:** `frontend_webtro/src/pages/public/HomePage.jsx:163` và `frontend_webtro/src/pages/public/AboutPage.jsx:17`.
- **Phương án sửa không degrade:** đổi nội dung thành “tại Hà Nội”/“khu vực Hà Nội”. Chỉ dùng lại tuyên bố “toàn quốc” sau khi catalog, validation, tìm kiếm và dữ liệu vận hành thực sự hỗ trợ phạm vi đó.
- **Kiểm thử hồi quy bắt buộc:** rà soát các trang public/SEO metadata cùng một nguồn cấu hình phạm vi; không còn thông điệp mâu thuẫn.

## 5. Thứ tự khuyến nghị xử lý

1. BG-01, BG-02, BG-03, BG-04: khôi phục các luồng nghiệp vụ đang không thể dùng hoặc có nguy cơ thao tác sai đối tượng/trạng thái.
2. BG-05, BG-06, BG-07: thống nhất contract frontend/backend cho các trang quản trị.
3. BG-08, BG-09: làm health/readiness và cache migration phản ánh đúng trạng thái thật, vẫn giữ fallback để không giảm availability.
4. BG-10: đồng bộ nội dung sau khi xác nhận phạm vi sản phẩm.

Sau khi sửa, nên chạy lại đúng các bước tái hiện trong từng mục, cộng thêm regression toàn bộ 70 lượt trang và các luồng tương tác đã nêu. Không nên coi việc response trả 200 là đủ: cần đối chiếu trạng thái UI, dữ liệu DB, quyền, tổng số phân trang và log sau thao tác.
