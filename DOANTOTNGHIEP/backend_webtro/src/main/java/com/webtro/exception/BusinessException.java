package com.webtro.exception;

import com.webtro.constant.ErrorCode;
import lombok.Getter;

/**
 * Ngoại lệ nghiệp vụ gốc. Mọi lỗi nghiệp vụ có thể dự đoán được đều ném ra từ lớp này (hoặc lớp
 * con của nó), mang theo một {@link ErrorCode} — {@code GlobalExceptionHandler} sẽ suy ra HTTP
 * status và thông điệp từ đó, trả về đúng envelope canonical mục 7.1.
 *
 * <p>Không dùng để bọc lỗi hệ thống ngoài dự đoán (NPE, lỗi kết nối...) — những cái đó để
 * {@code GlobalExceptionHandler} bắt ở nhánh {@code Exception} và trả {@code INTERNAL_ERROR}.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final transient ErrorCode errorCode;

    /** Tham số format cho thông điệp, nếu thông điệp mặc định có chỗ giữ chỗ. */
    private final transient Object[] args;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.args = new Object[0];
    }

    protected BusinessException(ErrorCode errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }
}
