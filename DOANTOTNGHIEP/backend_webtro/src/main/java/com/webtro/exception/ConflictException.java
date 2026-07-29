package com.webtro.exception;

import com.webtro.constant.ErrorCode;

/**
 * Xung đột trạng thái tài nguyên → HTTP 409. Ví dụ: email đã tồn tại, đã lưu tin, đã đánh giá.
 */
public class ConflictException extends BusinessException {

    public ConflictException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
