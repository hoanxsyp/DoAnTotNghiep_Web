package com.webtro.constant;

import org.springframework.http.HttpStatus;

/**
 * Toàn bộ mã lỗi nghiệp vụ của hệ thống, trích từ docs/03_THIET_KE_API.md.
 * Mỗi mã gắn với một HTTP status cố định và một thông điệp mặc định tiếng Việt.
 *
 * <p>GlobalExceptionHandler dùng enum này để trả về envelope lỗi thống nhất (canonical mục 7.1).
 */
public enum ErrorCode {

    // ===== Nhóm: Chung / Hệ thống =====
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    MALFORMED_JSON(HttpStatus.BAD_REQUEST, "Nội dung gửi lên không phải JSON hợp lệ"),
    UNKNOWN_FIELD(HttpStatus.BAD_REQUEST, "Trường {field} không được hỗ trợ"),
    INVALID_SORT_FIELD(HttpStatus.BAD_REQUEST, "Không thể sắp xếp theo trường {field}"),
    UNSUPPORTED_API_VERSION(HttpStatus.BAD_REQUEST, "Phiên bản API không được hỗ trợ"),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST, "Thiếu tham số bắt buộc {name}"),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST, "Tham số {name} sai kiểu dữ liệu"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để tiếp tục"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu yêu cầu"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ cho tài nguyên này"),
    BUSINESS_RULE_VIOLATED(HttpStatus.UNPROCESSABLE_ENTITY, "Thao tác không hợp lệ với trạng thái hiện tại"),
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn thao tác quá nhanh, vui lòng thử lại sau {n} giây"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Hệ thống đang gặp sự cố, vui lòng thử lại sau"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ tạm thời không khả dụng"),

    // ===== Nhóm: Auth - Đăng ký & xác thực tài khoản =====
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "Email này đã được đăng ký"),
    PHONE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Số điện thoại này đã được sử dụng"),
    WEAK_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu phải có tối thiểu 8 ký tự, gồm cả chữ và số"),
    INVALID_EMAIL_FORMAT(HttpStatus.BAD_REQUEST, "Email không đúng định dạng"),
    INVALID_PHONE_FORMAT(HttpStatus.BAD_REQUEST, "Số điện thoại không đúng định dạng Việt Nam"),
    INVALID_FULL_NAME(HttpStatus.BAD_REQUEST, "Họ tên không hợp lệ hoặc chứa ký tự không cho phép"),
    OTP_EXPIRED(HttpStatus.GONE, "Mã xác thực đã hết hạn, vui lòng yêu cầu mã mới"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã xác thực không đúng"),
    OTP_ALREADY_USED(HttpStatus.CONFLICT, "Mã xác thực đã được sử dụng"),
    OTP_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã nhập sai mã quá nhiều lần, vui lòng yêu cầu mã mới"),
    VERIFICATION_ALREADY_DONE(HttpStatus.CONFLICT, "Tài khoản đã được xác thực"),
    LANDLORD_CONTACT_REQUIRED(HttpStatus.BAD_REQUEST, "Đăng ký vai trò chủ trọ cần bổ sung thông tin liên hệ"),
    REGISTER_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã đăng ký quá nhiều lần, vui lòng thử lại sau"),

    // ===== Nhóm: Auth - Đăng nhập & phiên =====
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email/số điện thoại hoặc mật khẩu không đúng"),
    ACCOUNT_LOCKED(HttpStatus.FORBIDDEN, "Tài khoản của bạn đã bị khóa. Lý do: {reason}"),
    ACCOUNT_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chưa được xác thực. Vui lòng kiểm tra email của bạn"),
    ACCOUNT_DELETED(HttpStatus.FORBIDDEN, "Tài khoản không còn tồn tại"),
    LOGIN_ATTEMPT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã đăng nhập sai quá 5 lần. Vui lòng thử lại sau 15 phút"),
    CAPTCHA_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng xác nhận captcha"),
    CAPTCHA_INVALID(HttpStatus.BAD_REQUEST, "Xác nhận captcha không đúng"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã hết hạn"),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ"),
    TOKEN_REVOKED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã bị thu hồi"),
    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Phiên làm việc không hợp lệ, vui lòng đăng nhập lại"),
    REFRESH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Phiên làm việc đã hết hạn, vui lòng đăng nhập lại"),
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Phát hiện bất thường bảo mật. Toàn bộ phiên đã bị thu hồi"),
    PASSWORD_RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Liên kết đặt lại mật khẩu không hợp lệ"),
    PASSWORD_RESET_TOKEN_EXPIRED(HttpStatus.GONE, "Liên kết đặt lại mật khẩu đã hết hạn"),
    OLD_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    NEW_PASSWORD_SAME_AS_OLD(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại"),
    PASSWORD_CONFIRM_MISMATCH(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu không khớp"),

    // ===== Nhóm: User - Người dùng & hồ sơ =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy người dùng"),
    USER_ALREADY_LOCKED(HttpStatus.CONFLICT, "Tài khoản đã ở trạng thái khóa"),
    USER_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Tài khoản đang hoạt động bình thường"),
    LOCK_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do khóa tài khoản"),
    CANNOT_LOCK_SELF(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không thể tự khóa tài khoản của mình"),
    CANNOT_MODIFY_ADMIN(HttpStatus.FORBIDDEN, "Không thể thay đổi tài khoản quản trị viên khác"),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy vai trò"),
    ROLE_ASSIGN_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể gán vai trò này"),
    CANNOT_FOLLOW_SELF(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không thể tự theo dõi chính mình"),
    ALREADY_FOLLOWING(HttpStatus.CONFLICT, "Bạn đã theo dõi chủ trọ này"),
    NOT_FOLLOWING(HttpStatus.CONFLICT, "Bạn chưa theo dõi chủ trọ này"),
    TARGET_NOT_LANDLORD(HttpStatus.UNPROCESSABLE_ENTITY, "Người dùng này không phải chủ trọ"),
    LANDLORD_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ chủ trọ"),
    LANDLORD_ALREADY_VERIFIED(HttpStatus.CONFLICT, "Chủ trọ đã được xác thực"),
    AVATAR_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Ảnh đại diện chỉ chấp nhận JPG, PNG hoặc WEBP"),
    AVATAR_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh đại diện vượt quá dung lượng cho phép"),
    AVATAR_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh đại diện"),
    INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Mật khẩu không đúng"),
    ACCOUNT_DELETE_BLOCKED(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể xóa tài khoản khi vẫn còn tin hoặc giao dịch ràng buộc"),

    // ===== Nhóm: Listing - Tạo/sửa tin =====
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tin đăng"),
    LISTING_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thao tác trên tin đăng này"),
    REQUIRED_FIELD_MISSING(HttpStatus.BAD_REQUEST, "Vui lòng nhập đầy đủ các trường bắt buộc"),
    INVALID_PRICE(HttpStatus.BAD_REQUEST, "Giá thuê phải lớn hơn 0"),
    INVALID_AREA(HttpStatus.BAD_REQUEST, "Diện tích phải lớn hơn 0"),
    INVALID_TITLE_LENGTH(HttpStatus.BAD_REQUEST, "Tiêu đề phải từ 10 đến 150 ký tự"),
    INVALID_DESCRIPTION_LENGTH(HttpStatus.BAD_REQUEST, "Mô tả phải từ 30 đến 3000 ký tự"),
    INVALID_DEPOSIT(HttpStatus.BAD_REQUEST, "Tiền cọc không được nhỏ hơn 0"),
    INVALID_UTILITY_PRICE(HttpStatus.BAD_REQUEST, "Giá điện/nước không hợp lệ"),
    INVALID_OCCUPANTS(HttpStatus.BAD_REQUEST, "Số người ở không hợp lệ"),
    INVALID_AVAILABLE_FROM(HttpStatus.BAD_REQUEST, "Ngày có thể vào ở không được ở quá khứ"),
    ROOMMATE_INFO_REQUIRED(HttpStatus.BAD_REQUEST, "Tin ở ghép phải có giới tính chấp nhận và số người"),
    IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Ảnh vượt quá 5MB"),
    IMAGE_INVALID_FORMAT(HttpStatus.BAD_REQUEST, "Ảnh chỉ chấp nhận định dạng JPG, PNG hoặc WEBP"),
    IMAGE_COUNT_MIN(HttpStatus.BAD_REQUEST, "Tin đăng phải có tối thiểu 1 ảnh"),
    IMAGE_COUNT_MAX(HttpStatus.BAD_REQUEST, "Tin đăng chỉ được tối đa 10 ảnh"),
    IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy ảnh"),
    IMAGE_EXECUTABLE_REJECTED(HttpStatus.BAD_REQUEST, "Tệp tải lên không được chấp nhận"),
    PRIMARY_IMAGE_REQUIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đăng phải có một ảnh đại diện"),
    AREA_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_ENTITY, "Địa chỉ không thuộc khu vực hệ thống đang hỗ trợ"),
    ADDRESS_HIERARCHY_MISMATCH(HttpStatus.BAD_REQUEST, "Phường/xã không thuộc quận/huyện đã chọn"),
    BANNED_KEYWORD_DETECTED(HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung chứa từ ngữ không được phép: {keywords}"),
    DANGEROUS_HTML_DETECTED(HttpStatus.BAD_REQUEST, "Mô tả không được chứa mã HTML hoặc script"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy loại tin đăng"),
    AMENITY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tiện ích"),
    PROVINCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tỉnh/thành phố"),
    DISTRICT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy quận/huyện"),
    WARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phường/xã"),
    LISTING_QUOTA_NEW_ACCOUNT(HttpStatus.TOO_MANY_REQUESTS, "Tài khoản mới chỉ được đăng tối đa 3 tin mỗi ngày"),
    LISTING_QUOTA_DAILY(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã đạt giới hạn 10 tin mỗi ngày"),
    LANDLORD_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Tài khoản chủ trọ chưa được xác thực nên chưa thể đăng tin công khai"), // LƯU Ý: doc có 2 status, chọn theo bảng tổng
    LISTING_POSTING_SUSPENDED(HttpStatus.FORBIDDEN, "Chức năng đăng tin của bạn đang bị tạm khóa do vi phạm"),

    // ===== Nhóm: Listing - Vòng đời & state machine =====
    LISTING_INVALID_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể chuyển tin từ trạng thái {from} sang {to}"),
    LISTING_LOCKED_CANNOT_RENEW(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đang bị khóa nên không thể gia hạn"),
    LISTING_LOCKED_CANNOT_EDIT(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đang bị khóa nên không thể chỉnh sửa"),
    LISTING_LOCKED_CANNOT_SUBMIT(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đang bị khóa nên không thể gửi duyệt"),
    LISTING_LOCKED_CANNOT_DELETE(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đang bị khóa nên không thể xóa"),
    LISTING_LOCKED_CANNOT_PROMOTE(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đang bị khóa nên không thể mua gói đẩy tin"),
    LISTING_REJECTED_MUST_EDIT(HttpStatus.UNPROCESSABLE_ENTITY, "Tin bị từ chối cần chỉnh sửa và duyệt lại trước khi gia hạn"),
    LISTING_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đăng hiện không hiển thị công khai"),
    LISTING_ALREADY_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Tin đã hết hạn hiển thị"),
    LISTING_ALREADY_HIDDEN(HttpStatus.CONFLICT, "Tin đã ở trạng thái ẩn"),
    LISTING_ALREADY_CLOSED(HttpStatus.CONFLICT, "Tin đã được đóng"),
    LISTING_ALREADY_APPROVED(HttpStatus.CONFLICT, "Tin đã được duyệt"),
    RENEW_FREE_QUOTA_EXCEEDED(HttpStatus.PAYMENT_REQUIRED, "Bạn đã dùng hết {n} lượt gia hạn miễn phí trong tháng. Vui lòng mua gói gia hạn"),
    REJECT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối tin"),
    LOCK_LISTING_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do và mức độ vi phạm khi khóa tin"),
    LISTING_NO_PENDING_REVIEW(HttpStatus.UNPROCESSABLE_ENTITY, "Tin không ở trạng thái cần kiểm tra"),
    LISTING_DELETED(HttpStatus.GONE, "Tin đăng đã bị xóa"),
    LISTING_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể chuyển trạng thái tin đăng theo yêu cầu"),

    // ===== Nhóm: Search - Tìm kiếm =====
    PRICE_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Giá từ không được lớn hơn giá đến"),
    AREA_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Diện tích từ không được lớn hơn diện tích đến"),
    KEYWORD_TOO_LONG(HttpStatus.BAD_REQUEST, "Từ khóa tìm kiếm quá dài (tối đa 100 ký tự)"),
    KEYWORD_INVALID_CHARACTER(HttpStatus.BAD_REQUEST, "Từ khóa chứa ký tự không được phép"),
    FILTER_COMBINATION_INVALID(HttpStatus.BAD_REQUEST, "Không thể lọc theo quận/huyện khi chưa chọn tỉnh/thành"),
    TOO_MANY_AMENITY_FILTERS(HttpStatus.BAD_REQUEST, "Chỉ được lọc tối đa 20 tiện ích cùng lúc"),

    // ===== Nhóm: Interaction - Lưu tin, lịch sử, liên hệ, chat =====
    FAVORITE_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã lưu tin đăng này"),
    FAVORITE_NOT_FOUND(HttpStatus.NOT_FOUND, "Tin đăng chưa có trong danh sách đã lưu"),
    CONTACT_FORBIDDEN_SELF(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không thể liên hệ tin đăng của chính mình"),
    CONTACT_RESTRICTED_SPAM(HttpStatus.FORBIDDEN, "Chức năng liên hệ của bạn đang bị hạn chế do bị báo cáo spam"),
    CONTACT_INFO_LOGIN_REQUIRED(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để xem đầy đủ số điện thoại"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cuộc trò chuyện"),
    CONVERSATION_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không thuộc cuộc trò chuyện này"),
    CHAT_DISABLED_BY_LANDLORD(HttpStatus.UNPROCESSABLE_ENTITY, "Chủ trọ đã tắt chat. Vui lòng liên hệ qua số điện thoại"),
    MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "Nội dung tin nhắn không được để trống"),
    MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Tin nhắn tối đa 2000 ký tự"),
    MESSAGE_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi tin nhắn quá nhanh, vui lòng chậm lại"),

    // ===== Nhóm: Interaction - Bình luận & đánh giá =====
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận"),
    COMMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn chỉ có thể sửa hoặc xóa bình luận của mình"),
    COMMENT_CONTENT_INVALID(HttpStatus.BAD_REQUEST, "Nội dung bình luận phải từ 3 đến 1000 ký tự"),
    COMMENT_EDIT_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá 30 phút, bạn không thể sửa bình luận này"),
    COMMENT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn bình luận quá nhanh, vui lòng chậm lại"),
    COMMENT_SUSPENDED(HttpStatus.FORBIDDEN, "Chức năng bình luận của bạn đang bị tạm khóa"),
    COMMENT_PARENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận gốc để trả lời"),
    COMMENT_NESTING_TOO_DEEP(HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ hỗ trợ trả lời một cấp"),
    COMMENT_PARENT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Bình luận gốc không thuộc tin đăng này"),
    COMMENT_NOT_VISIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "Bình luận đang chờ kiểm duyệt hoặc đã bị ẩn"),
    COMMENT_ALREADY_HIDDEN(HttpStatus.CONFLICT, "Bình luận đã bị ẩn"),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đánh giá"),
    REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn chỉ có thể sửa đánh giá của mình"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bạn đã đánh giá tin đăng này rồi"),
    REVIEW_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn cần liên hệ chủ trọ trước khi đánh giá tin này"),
    REVIEW_SELF_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không thể đánh giá tin đăng của chính mình"),
    REVIEW_RATING_INVALID(HttpStatus.BAD_REQUEST, "Số sao phải từ 1 đến 5"),
    REVIEW_CONTENT_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập nội dung đánh giá khi chấm từ 2 sao trở xuống"),
    REVIEW_EDIT_WINDOW_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Đã quá 24 giờ, bạn không thể sửa đánh giá này"),
    REVIEW_CONTENT_IMMUTABLE_BY_ADMIN(HttpStatus.FORBIDDEN, "Quản trị viên chỉ được ẩn hoặc khôi phục, không được sửa nội dung đánh giá"),
    MODERATION_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do kiểm duyệt"),
    COMMENT_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Bình luận đã ở đúng trạng thái đích"),
    REVIEW_ALREADY_HIDDEN(HttpStatus.CONFLICT, "Đánh giá đã bị ẩn"),
    REVIEW_INVALID_TRANSITION(HttpStatus.UNPROCESSABLE_ENTITY, "Đánh giá đã ở đúng trạng thái đích"),

    // ===== Nhóm: Moderation - Báo cáo & kiểm duyệt =====
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy báo cáo"),
    REPORT_DUPLICATE(HttpStatus.CONFLICT, "Bạn đã báo cáo đối tượng này với cùng lý do"),
    REPORT_TARGET_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đối tượng bị báo cáo"),
    REPORT_SELF_FORBIDDEN(HttpStatus.UNPROCESSABLE_ENTITY, "Bạn không thể báo cáo nội dung của chính mình"),
    REPORT_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng chọn lý do báo cáo"),
    REPORT_DESCRIPTION_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng mô tả chi tiết khi chọn lý do \"Khác\""),
    REPORT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã gửi quá 10 báo cáo hôm nay"),
    REPORT_RESTRICTED_ABUSE(HttpStatus.FORBIDDEN, "Chức năng báo cáo của bạn bị hạn chế do nhiều báo cáo sai"),
    REPORT_ALREADY_RESOLVED(HttpStatus.CONFLICT, "Báo cáo đã được xử lý"),
    REPORT_RESOLUTION_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng chọn kết quả xử lý"),
    EVIDENCE_IMAGE_INVALID(HttpStatus.BAD_REQUEST, "Ảnh bằng chứng chỉ chấp nhận JPG, PNG hoặc WEBP"),
    WARNING_REASON_REQUIRED(HttpStatus.BAD_REQUEST, "Vui lòng nhập nội dung cảnh báo"),
    MODERATION_ACTION_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Hành động kiểm duyệt không hợp lệ với đối tượng này"),
    BULK_ACTION_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác hàng loạt này"),
    REPORT_GROUP_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "Không có báo cáo nào khớp để xử lý"),
    REPORT_RESULT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền chọn kết quả xử lý này"),

    // ===== Nhóm: Payment - Thanh toán & gói dịch vụ =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"),
    PAYMENT_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem giao dịch này"),
    PAYMENT_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Thanh toán thất bại. Vui lòng thử lại hoặc chọn phương thức khác"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "Giao dịch đã được xử lý"),
    PAYMENT_ALREADY_CANCELLED(HttpStatus.CONFLICT, "Giao dịch đã bị hủy"),
    PAYMENT_NOT_PENDING(HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ có thể hủy giao dịch đang chờ thanh toán"),
    PAYMENT_AMOUNT_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Số tiền thanh toán không khớp với đơn hàng"),
    PAYMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "Chữ ký callback không hợp lệ"),
    PAYMENT_CALLBACK_REPLAY(HttpStatus.CONFLICT, "Callback đã được xử lý trước đó"),
    PAYMENT_CALLBACK_EXPIRED(HttpStatus.BAD_REQUEST, "Callback đã quá hạn xử lý"),
    PAYMENT_METHOD_UNSUPPORTED(HttpStatus.BAD_REQUEST, "Phương thức thanh toán không được hỗ trợ"),
    PAYMENT_REFUND_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ có thể hoàn tiền giao dịch đã thanh toán thành công"),
    PAYMENT_ALREADY_REFUNDED(HttpStatus.CONFLICT, "Giao dịch đã được hoàn tiền"),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Thiếu header Idempotency-Key"),
    IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key phải là UUID hợp lệ"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "Idempotency-Key đã dùng cho yêu cầu khác"),
    IDEMPOTENCY_KEY_IN_PROGRESS(HttpStatus.CONFLICT, "Yêu cầu đang được xử lý, vui lòng chờ"),
    PACKAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy gói dịch vụ"),
    PACKAGE_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Gói dịch vụ hiện không còn được bán"),
    PACKAGE_IN_USE_CANNOT_DELETE(HttpStatus.UNPROCESSABLE_ENTITY, "Gói đang có người dùng nên không thể xóa"),
    PACKAGE_PRIORITY_EXCEEDED(HttpStatus.BAD_REQUEST, "Mức ưu tiên tối đa là {max}"),
    PACKAGE_DURATION_INVALID(HttpStatus.BAD_REQUEST, "Thời hạn gói phải lớn hơn 0 ngày"),
    PACKAGE_CODE_DUPLICATE(HttpStatus.CONFLICT, "Mã gói dịch vụ đã tồn tại"),
    COUPON_CODE_DUPLICATE(HttpStatus.CONFLICT, "Mã khuyến mãi đã tồn tại"),
    LISTING_NOT_PROMOTABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Chỉ có thể đẩy tin đang hiển thị hoặc chờ duyệt"),
    SUBSCRIPTION_ALREADY_ACTIVE(HttpStatus.CONFLICT, "Tin đăng đang có gói đẩy còn hiệu lực"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy gói đã mua"),
    COUPON_NOT_FOUND(HttpStatus.NOT_FOUND, "Mã khuyến mãi không tồn tại"),
    COUPON_EXPIRED(HttpStatus.UNPROCESSABLE_ENTITY, "Mã khuyến mãi đã hết hạn"),
    COUPON_NOT_STARTED(HttpStatus.UNPROCESSABLE_ENTITY, "Mã khuyến mãi chưa đến thời gian áp dụng"),
    COUPON_USAGE_EXCEEDED(HttpStatus.UNPROCESSABLE_ENTITY, "Mã khuyến mãi đã hết lượt sử dụng"),
    COUPON_ALREADY_USED_BY_USER(HttpStatus.CONFLICT, "Bạn đã sử dụng mã khuyến mãi này"),
    COUPON_NOT_APPLICABLE(HttpStatus.UNPROCESSABLE_ENTITY, "Mã khuyến mãi không áp dụng cho gói này"),
    COUPON_INACTIVE(HttpStatus.UNPROCESSABLE_ENTITY, "Mã khuyến mãi đã bị vô hiệu hóa"),

    // ===== Nhóm: Notification - Thông báo =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"),
    NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "Bạn không có quyền xem thông báo này"),
    NOTIFICATION_TYPE_NOT_OPTIONAL(HttpStatus.UNPROCESSABLE_ENTITY, "Không thể tắt loại thông báo quan trọng này"),

    // ===== Nhóm: AI =====
    AI_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dịch vụ AI tạm thời không khả dụng, vui lòng thử lại sau"),
    AI_MODULE_DISABLED(HttpStatus.SERVICE_UNAVAILABLE, "Tính năng AI này đang được bảo trì"),
    AI_INSUFFICIENT_DATA(HttpStatus.UNPROCESSABLE_ENTITY, "Chưa đủ dữ liệu thị trường để dự đoán giá cho khu vực này"),
    AI_MISSING_INPUT_FIELD(HttpStatus.BAD_REQUEST, "Cần nhập thêm: {fields} để dự đoán giá"),
    AI_PRICE_INPUT_INVALID(HttpStatus.BAD_REQUEST, "Thông tin đầu vào dự đoán giá không hợp lệ"),
    CHATBOT_MESSAGE_EMPTY(HttpStatus.BAD_REQUEST, "Vui lòng nhập câu hỏi"),
    CHATBOT_MESSAGE_TOO_LONG(HttpStatus.BAD_REQUEST, "Câu hỏi tối đa 500 ký tự"),
    CHATBOT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn gửi câu hỏi quá nhanh, vui lòng chậm lại"),
    CHATBOT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiên trò chuyện"),
    CHATBOT_OUT_OF_SCOPE(HttpStatus.OK, "Câu hỏi nằm ngoài phạm vi hỗ trợ"),
    SENTIMENT_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bình luận cần phân tích"),
    AI_CONFIG_KEY_UNKNOWN(HttpStatus.BAD_REQUEST, "Khóa cấu hình AI không hợp lệ: {key}"),
    AI_CONFIG_VALUE_INVALID(HttpStatus.BAD_REQUEST, "Giá trị cấu hình không hợp lệ cho khóa {key}"),

    // ===== Nhóm: Admin - Quản trị, Catalog & cấu hình hệ thống =====
    CONFIG_KEY_UNKNOWN(HttpStatus.BAD_REQUEST, "Khóa cấu hình không tồn tại: {key}"),
    CONFIG_VALUE_INVALID(HttpStatus.BAD_REQUEST, "Giá trị cấu hình không hợp lệ cho khóa {key}"),
    CATEGORY_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "Danh mục đang có tin đăng nên không thể xóa"),
    CATEGORY_CODE_DUPLICATE(HttpStatus.CONFLICT, "Mã danh mục đã tồn tại"),
    AMENITY_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "Tiện ích đang được sử dụng nên không thể xóa"),
    AMENITY_CODE_DUPLICATE(HttpStatus.CONFLICT, "Mã tiện ích đã tồn tại"),
    PROVINCE_CODE_DUPLICATE(HttpStatus.CONFLICT, "Mã tỉnh/thành đã tồn tại"),
    DISTRICT_IN_USE(HttpStatus.UNPROCESSABLE_ENTITY, "Quận/huyện đang có tin đăng nên không thể xóa"),
    BANNED_KEYWORD_DUPLICATE(HttpStatus.CONFLICT, "Từ khóa cấm đã tồn tại"),
    BANNED_KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy từ khóa cấm"),
    AUDIT_LOG_RANGE_TOO_LARGE(HttpStatus.BAD_REQUEST, "Khoảng thời gian tra cứu tối đa 90 ngày"),
    STATISTIC_RANGE_INVALID(HttpStatus.BAD_REQUEST, "Ngày bắt đầu phải trước ngày kết thúc"),
    DISPLAY_ORDER_DUPLICATE(HttpStatus.UNPROCESSABLE_ENTITY, "Thứ tự hiển thị hoặc id bị trùng trong danh sách"),

    // ===== Nhóm: File / Upload (export & import) =====
    EXPORT_EMPTY(HttpStatus.UNPROCESSABLE_ENTITY, "Không có dòng nào khớp bộ lọc để xuất tệp"),
    EXPORT_RATE_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "Bạn đã vượt giới hạn 5 lần xuất tệp mỗi giờ"),
    IMPORT_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Tệp nhập vượt quá dung lượng cho phép (5MB)"),
    IMPORT_FILE_INVALID(HttpStatus.UNPROCESSABLE_ENTITY, "Tệp nhập sai định dạng hoặc thiếu cột bắt buộc"),
    IMPORT_TOO_MANY_ROWS(HttpStatus.UNPROCESSABLE_ENTITY, "Tệp nhập vượt quá 20.000 dòng"),
    ;

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

// Tổng: 236 mã lỗi
