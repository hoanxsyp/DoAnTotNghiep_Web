package com.webtro.exception;

import com.webtro.constant.ErrorCode;

/**
 * Không tìm thấy tài nguyên → HTTP 404. Dùng {@link ErrorCode} cụ thể của từng loại
 * (LISTING_NOT_FOUND, USER_NOT_FOUND...) để frontend phân biệt được.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * Tiện dụng: "Không tìm thấy &lt;tên&gt; với id = &lt;id&gt;".
     */
    public ResourceNotFoundException(ErrorCode errorCode, String resourceName, Object id) {
        super(errorCode, "Không tìm thấy " + resourceName + " với id = " + id);
    }
}
