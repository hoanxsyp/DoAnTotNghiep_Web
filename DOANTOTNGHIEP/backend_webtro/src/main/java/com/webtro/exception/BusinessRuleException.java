package com.webtro.exception;

import com.webtro.constant.ErrorCode;

/**
 * Vi phạm quy tắc nghiệp vụ → HTTP 422. Khác với validation (400): 400 là dữ liệu sai định dạng;
 * 422 là dữ liệu đúng định dạng nhưng thao tác không hợp lệ theo trạng thái nghiệp vụ hiện tại —
 * ví dụ: chuyển trạng thái tin sai theo state machine, gia hạn tin đang bị khóa {@code [§3.5]}.
 */
public class BusinessRuleException extends BusinessException {

    public BusinessRuleException(ErrorCode errorCode) {
        super(errorCode);
    }

    public BusinessRuleException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
