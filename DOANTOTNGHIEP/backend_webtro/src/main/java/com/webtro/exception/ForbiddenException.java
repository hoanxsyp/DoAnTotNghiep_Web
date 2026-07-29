package com.webtro.exception;

import com.webtro.constant.ErrorCode;

/**
 * Đã đăng nhập nhưng không đủ quyền cho thao tác → HTTP 403.
 * Dùng cho kiểm tra quyền sở hữu ở tầng service (ví dụ: sửa tin của người khác), bổ sung cho
 * kiểm tra permission ở tầng {@code @PreAuthorize}.
 */
public class ForbiddenException extends BusinessException {

    public ForbiddenException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ForbiddenException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
