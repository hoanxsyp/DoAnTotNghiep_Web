package com.webtro.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Envelope thống nhất cho MỌI response của hệ thống — quy định tại
 * {@code docs/00_CANONICAL_DECISIONS.md} mục 7.1.
 *
 * <p>Thành công:
 * <pre>{@code { "success": true, "message": "...", "data": {...}, "timestamp": "..." } }</pre>
 *
 * <p>Thất bại:
 * <pre>{@code
 * { "success": false, "message": "...", "data": null, "errorCode": "LISTING_NOT_FOUND",
 *   "errors": [ { "field": "price", "message": "Giá phải lớn hơn 0" } ],
 *   "timestamp": "...", "path": "/api/listings", "traceId": "..." }
 * }</pre>
 *
 * <p>Các field {@code null} bị loại khỏi JSON ({@code NON_NULL}), nên response thành công không
 * mang theo {@code errorCode}/{@code errors}/{@code path} rỗng.
 *
 * @param <T> kiểu dữ liệu nghiệp vụ trả về
 */
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "ApiResponse", description = "Envelope thống nhất cho mọi response")
public class ApiResponse<T> {

    @Schema(description = "Request có thành công hay không", example = "true")
    private boolean success;

    @Schema(description = "Thông điệp cho người dùng cuối (tiếng Việt)", example = "Thành công")
    private String message;

    @Schema(description = "Dữ liệu nghiệp vụ. null khi thất bại")
    private T data;

    @Schema(description = "Mã lỗi ổn định để frontend map ra thông điệp", example = "LISTING_NOT_FOUND")
    private String errorCode;

    @Schema(description = "Danh sách lỗi theo từng field, dùng cho lỗi validation")
    private List<FieldError> errors;

    @Schema(description = "Thời điểm tạo response (ISO-8601, UTC)")
    @Builder.Default
    private Instant timestamp = Instant.now();

    @Schema(description = "Đường dẫn gây lỗi", example = "/api/listings")
    private String path;

    @Schema(description = "Mã truy vết, dùng để tra log", example = "b7f2c1a4e9d3")
    private String traceId;

    // ------------------------------------------------------------------
    //  Factory - thành công
    // ------------------------------------------------------------------

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Thành công");
    }

    /** Dùng cho thao tác không trả dữ liệu (ví dụ: bỏ lưu tin, đánh dấu đã đọc). */
    public static ApiResponse<Void> success(String message) {
        return ApiResponse.<Void>builder()
                .success(true)
                .message(message)
                .build();
    }

    // ------------------------------------------------------------------
    //  Factory - thất bại
    // ------------------------------------------------------------------

    public static <T> ApiResponse<T> error(String errorCode, String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    /**
     * Một lỗi gắn với field cụ thể của request body — phục vụ hiển thị lỗi ngay dưới ô nhập
     * ở frontend.
     */
    @Getter
    @Builder
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    @Schema(name = "FieldError", description = "Lỗi validation của một field")
    public static class FieldError {

        @Schema(description = "Tên field trong request", example = "price")
        private String field;

        @Schema(description = "Mô tả lỗi (tiếng Việt)", example = "Giá phải lớn hơn 0")
        private String message;

        @Schema(description = "Giá trị bị từ chối. Luôn null với field nhạy cảm (mật khẩu, token)")
        private Object rejectedValue;
    }
}
