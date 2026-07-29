package com.webtro.exception;

import com.webtro.constant.ErrorCode;
import lombok.Getter;

/**
 * Vượt giới hạn tần suất → HTTP 429 {@code [§11.10]}. Mang theo số giây người dùng cần chờ để
 * {@code GlobalExceptionHandler} đặt header {@code Retry-After}.
 */
@Getter
public class RateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public RateLimitException(ErrorCode errorCode, long retryAfterSeconds) {
        super(errorCode);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public RateLimitException(ErrorCode errorCode, String message, long retryAfterSeconds) {
        super(errorCode, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
