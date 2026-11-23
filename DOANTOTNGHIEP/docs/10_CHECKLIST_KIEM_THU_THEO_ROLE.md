# Checklist kiểm tra chức năng theo vai trò

Điền cột **Trạng thái** bằng một trong ba giá trị: **Chưa check**, **Đang check**, **Đã check**. Mặc định là **Chưa check**; chỉ ghi **Đã check** khi đã kiểm tra và chức năng hoạt động đúng. Mục đang kiểm tra hoặc còn lỗi cần sửa để **Đang check**.

## 1. Khách chưa đăng nhập

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Xem trang chủ và danh sách tin đăng | Chưa check |
| Tìm kiếm theo từ khóa, khu vực, giá, diện tích, loại tin và tiện ích | Chưa check |
| Kết hợp bộ lọc, sắp xếp, phân trang và xóa bộ lọc | Chưa check |
| Xem chi tiết tin, ảnh, thông số, tiện ích và tin liên quan | Chưa check |
| Xem hồ sơ công khai và tin đăng của chủ trọ | Chưa check |
| Không xem được tin chưa duyệt, bị ẩn, khóa hoặc đã xóa | Chưa check |
| Số điện thoại chủ trọ hiển thị trực tiếp dạng text trên chi tiết tin | Chưa check |
| Được yêu cầu đăng nhập khi lưu tin, nhắn tin hoặc gửi yêu cầu thuê | Chưa check |
| Đăng ký tài khoản Tenant/Landlord và xác thực email | Chưa check |
| Gửi lại mã xác thực và tiếp tục xác thực sau khi tải lại trang | Chưa check |
| Đăng nhập, quên mật khẩu và đặt lại mật khẩu | Chưa check |
| Xem Giới thiệu, Điều khoản và trang báo lỗi | Chưa check |

## 2. Chức năng tài khoản chung

Các vai trò đăng nhập kiểm tra nhóm này trong phạm vi quyền của mình.

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Đăng nhập đúng vai trò, hiển thị đúng menu và trang đích | Chưa check |
| Khi truy cập `/` (localhost), mở tab mới hoặc tải lại trang: kiểm tra phiên đăng nhập còn hợp lệ và tự điều hướng theo role — ADMIN → `/admin/dashboard`, MODERATOR → `/admin/kiem-duyet`, LANDLORD → `/quan-ly/tong-quan`, TENANT/khách → trang tìm phòng `/` | Chưa check |
| Chờ khôi phục/xác thực phiên trước khi điều hướng; không dựa riêng vào role lưu cũ, không chuyển nhầm trang hoặc lặp điều hướng khi token hết hạn, bị thu hồi hay role đã thay đổi | Chưa check |
| Mở link cụ thể thì giữ đúng trang được phép; sau đăng nhập quay lại trang yêu cầu nếu đúng role, nếu không về trang mặc định theo role | Chưa check |
| Từ dashboard chọn “Xem website” mở `/trang-chu`, tải lại vẫn xem được trang công khai; menu “Trang quản trị” đưa Admin/Moderator về đúng trang của mình | Chưa check |
| Xem, chỉnh sửa hồ sơ và thay/xóa ảnh đại diện | Chưa check |
| Cập nhật thông tin liên hệ và xác thực số điện thoại theo cơ chế hiện có | Chưa check |
| Đổi mật khẩu | Chưa check |
| Giữ phiên, làm mới token và xử lý khi phiên hết hạn | Chưa check |
| Đăng xuất; phiên cũ không tiếp tục thao tác được | Chưa check |
| Xem và xóa lịch sử xem/tìm kiếm theo chức năng hiện có | Chưa check |
| Nhận, xem, đánh dấu đã đọc và xóa thông báo | Chưa check |
| Cập nhật cài đặt nhận thông báo | Chưa check |
| Không xem/sửa dữ liệu riêng của người khác bằng cách đổi ID | Chưa check |
| Không truy cập chức năng ngoài quyền qua URL hoặc gọi API trực tiếp | Chưa check |

## 3. Người thuê — TENANT

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Lưu/bỏ lưu tin và xem danh sách tin đã lưu | Chưa check |
| Theo dõi/bỏ theo dõi chủ trọ | Chưa check |
| Xem trực tiếp số điện thoại và nhắn tin cho chủ trọ | Chưa check |
| Tạo cuộc trò chuyện, gửi/nhận tin nhắn và cập nhật số chưa đọc | Chưa check |
| Tạo, trả lời, sửa và xóa bình luận trong phạm vi cho phép | Chưa check |
| Gửi báo cáo vi phạm và xem báo cáo của mình | Chưa check |
| Tạo tin ở ghép ROOMMATE, lưu nháp và gửi duyệt | Chưa check |
| Sửa tin ở ghép, quản lý ảnh/tiện ích và gửi lại sau khi bị từ chối | Chưa check |
| Ẩn/hiện, đóng, gia hạn và xóa tin ở ghép theo trạng thái cho phép | Chưa check |
| Không tạo/sửa tin sang loại chỉ dành cho chủ trọ | Chưa check |
| Gửi yêu cầu thuê phòng với ngày xem hợp lệ | Chưa check |
| Không gửi yêu cầu trùng đang mở hoặc yêu cầu vào tin không khả dụng | Chưa check |
| Xem trạng thái yêu cầu thuê, đổi ngày khi còn chờ và hủy đúng thời hạn | Chưa check |
| Nhận thông báo khi yêu cầu được duyệt, từ chối, xác nhận thuê hoặc trả phòng | Chưa check |
| Chỉ đánh giá khi có lịch sử thuê hợp lệ; không tự đánh giá tin mình | Chưa check |
| Sửa/xóa đánh giá của mình, tạo lại sau xóa nếu còn đủ điều kiện | Chưa check |
| Xem danh sách đánh giá của mình và số sao cập nhật đúng | Chưa check |
| Không truy cập mua gói, thanh toán hoặc chức năng quản trị | Chưa check |

## 4. Chủ trọ — LANDLORD

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Xem tổng quan và số liệu tin đăng của mình | Đã check |
| Cập nhật hồ sơ chủ trọ, thông tin liên hệ và bật/tắt chat | Đã check |
| Gửi yêu cầu xác thực chủ trọ và xem kết quả | Đã check |
| Tạo tin theo các loại được phép, lưu nháp và gửi duyệt | Đã check |
| Kiểm tra các trường bắt buộc, giá, diện tích và địa chỉ trong phạm vi Hà Nội | Đã check |
| Thêm/xóa/sắp xếp ảnh và đổi ảnh đại diện tin | Đã check |
| Thêm/xóa tiện ích của tin | Đã check |
| Sửa tin; thay đổi nội dung nhạy cảm được đưa duyệt lại | Đã check |
| Sửa tin bị từ chối và gửi duyệt lại | Đã check |
| Ẩn/hiện, đóng, gia hạn và xóa tin đúng trạng thái | Đã check |
| Không sửa/xóa hoặc lách khóa đối với tin đang bị khóa/đang cho thuê | Đã check |
| Lọc danh sách tin và xem thống kê từng tin | Đã check |
| Xem, duyệt và từ chối yêu cầu thuê trên tin của mình | Đã check |
| Xác nhận thuê; trạng thái tin và các yêu cầu cạnh tranh cập nhật đúng | Đã check |
| Không xác nhận hai người thuê cùng phòng hoặc một người đang thuê hai phòng | Đã check |
| Xác nhận trả phòng; tin và lịch sử thuê cập nhật phù hợp | Đã check |
| Không xử lý yêu cầu thuê hoặc quản lý tin của chủ trọ khác | Đã check |
| Xem gói đẩy tin, áp dụng mã giảm giá và tạo thanh toán | Đã check |
| Kiểm tra thanh toán thành công, thất bại, hủy và lịch sử giao dịch | Đã check |
| Gói đẩy tin chỉ kích hoạt sau thanh toán hợp lệ, không kích hoạt trùng | Đã check |
| Gói hết hạn thì tin không còn hưởng ưu tiên sai | Đã check |
| Nhận thông báo kiểm duyệt, sắp hết hạn và đánh giá | Đã check |
| Tài khoản bị hạn chế đăng tin không vượt được hạn chế | Đã check |

## 5. Kiểm duyệt viên — MODERATOR

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Xem/lọc danh sách tin và hàng đợi kiểm duyệt | Chưa check |
| Xem chi tiết tin cần duyệt, kể cả tin chưa công khai | Chưa check |
| Duyệt hoặc từ chối tin, ghi lý do và gửi kết quả cho chủ tin | Chưa check |
| Ẩn/bỏ ẩn, đánh dấu/bỏ cần kiểm tra và yêu cầu chỉnh sửa tin | Chưa check |
| Kiểm duyệt tin hàng loạt và xử lý rõ các mục không hợp lệ | Chưa check |
| Xem lịch sử hành động kiểm duyệt | Chưa check |
| Xác thực, từ chối, hủy xác thực và hạn chế đăng tin chủ trọ | Chưa check |
| Xem, nhận xử lý và kết luận báo cáo vi phạm | Chưa check |
| Xử lý nhóm báo cáo cùng đối tượng và gửi cảnh báo vi phạm | Chưa check |
| Ẩn/khôi phục/đánh dấu spam bình luận và xử lý hàng loạt | Chưa check |
| Ẩn/khôi phục đánh giá; tổng số và trung bình sao cập nhật đúng | Chưa check |
| Không sửa nội dung bình luận/đánh giá thay người dùng | Chưa check |
| Xem log AI và danh sách tin lệch giá | Chưa check |
| Không khóa/mở khóa tin bằng quyền chỉ dành Admin | Chưa check |
| Không quản lý user/role, tài chính, danh mục, cấu hình hoặc audit log | Chưa check |

## 6. Quản trị viên — ADMIN

Kiểm tra thêm toàn bộ chức năng kiểm duyệt ở mục 5. Quyền thao tác trên tin/yêu cầu thuê vẫn phải tuân theo điều kiện sở hữu của từng chức năng.

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Xem dashboard và đối chiếu số liệu tổng quan | Chưa check |
| Tìm/lọc người dùng và xem chi tiết tài khoản | Chưa check |
| Khóa/mở khóa tài khoản; phiên đang dùng bị thu hồi đúng | Chưa check |
| Đổi vai trò; mỗi tài khoản chỉ có một role và quyền cũ không còn hiệu lực | Chưa check |
| Kiểm tra bảo vệ thao tác với chính mình/Admin cuối cùng theo quy tắc nhóm chốt | Chưa check |
| Khóa/mở khóa tin, ghi lý do và kiểm tra trạng thái sau mở khóa | Chưa check |
| Quản lý loại tin và tiện ích: tạo, sửa, ẩn/bật và sắp xếp | Chưa check |
| Quản lý/import khu vực; dữ liệu tỉnh/quận/phường liên kết đúng | Chưa check |
| Quản lý từ khóa cấm và kiểm tra hiệu lực | Chưa check |
| Tạo/sửa/bật/tắt gói dịch vụ và mã khuyến mãi | Chưa check |
| Xem/lọc giao dịch, đối soát và xử lý hoàn tiền theo cơ chế hiện có | Chưa check |
| Kiểm tra callback sai chữ ký, lặp và đến muộn không gây sai tiền/quyền lợi | Chưa check |
| Xem thống kê, doanh thu và lọc theo thời gian | Chưa check |
| Cập nhật cấu hình hệ thống và nội dung trang công khai | Chưa check |
| Bật/tắt, chỉnh cấu hình AI và kiểm tra hiệu lực | Chưa check |
| Xem audit log của các thao tác quản trị | Chưa check |
| Thao tác quản trị không làm mất dữ liệu hoặc thay đổi lịch sử trái phép | Chưa check |

## 7. AI và các luồng cả nhóm cần kiểm tra

Phân công cho người phụ trách module tương ứng. Kiểm tra demo AI độc lập không thay cho kiểm tra tích hợp với website.

| Chức năng cần kiểm tra | Trạng thái |
|---|:---:|
| Website gợi ý tin cho người mới và người có lịch sử tương tác | Chưa check |
| Gợi ý không trùng và không chứa tin bị ẩn/khóa/xóa hoặc không còn khả dụng | Chưa check |
| Module recommendation FAISS + LightGBM chạy được và chọn đúng luồng theo lịch sử | Chưa check |
| View/favorite/unfavorite cập nhật tín hiệu gợi ý đúng | Chưa check |
| Fallback recommendation không trả phòng hết trống hoặc sai điều kiện | Chưa check |
| Rebuild index/retrain model và kiểm tra chất lượng với baseline | Chưa check |
| Chatbot cho khách/người đăng nhập, hội thoại nhiều lượt và tách đúng phiên | Chưa check |
| RoomFinder tìm/lọc/so sánh/reset và không bịa phòng/giá | Chưa check |
| Dự đoán giá có đầu vào, đơn vị, kết quả và lịch sử đúng | Đã check |
| AI tắt/lỗi/timeout không làm hỏng luồng chính của website | Đã check (dự đoán giá) |
| Xác nhận module AI nào đã nối dữ liệu thật của website, module nào còn độc lập | Đã check (dự đoán giá) |
| Đăng ký → đăng tin → duyệt → khách tìm thấy tin | Chưa check |
| Tìm phòng → kiểm tra số điện thoại hiển thị trực tiếp/chat → yêu cầu thuê → xác nhận thuê → đánh giá | Chưa check |
| Trả phòng → cập nhật tin → người thuê có thể thuê tiếp | Chưa check |
| Báo cáo vi phạm → kiểm duyệt xử lý → chủ tin/người báo nhận kết quả | Chưa check |
| Mua gói → xác nhận thanh toán → đẩy tin → hết hạn gói | Chưa check |
| Các job hết hạn tin/gói, nhắc hạn và thông báo chạy đúng, không xử lý trùng | Chưa check |
| Reload, mất mạng, bấm nhiều lần và thao tác đồng thời không làm sai dữ liệu | Chưa check |
| Giao diện dùng được trên máy tính/điện thoại, lỗi nhập liệu có thông báo rõ | Chưa check |
