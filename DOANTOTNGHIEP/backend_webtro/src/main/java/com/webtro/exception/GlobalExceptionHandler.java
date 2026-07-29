package com.webtro.exception;

import com.webtro.common.ApiResponse;
import com.webtro.constant.AppConstant;
import com.webtro.constant.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import java.util.ArrayList;
import java.util.List;

/**
 * Bắt MỌI ngoại lệ và trả về đúng envelope thống nhất (canonical mục 7.1 + 7.2).
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>Lỗi nghiệp vụ ({@link BusinessException} và lớp con): dùng {@link ErrorCode} của nó để
 *       suy ra HTTP status + thông điệp. Log mức WARN (lỗi dự đoán được, không phải sự cố).</li>
 *   <li>Lỗi validation của Spring: gom thành danh sách {@code errors[]} theo field.</li>
 *   <li>Lỗi bảo mật của Spring: 401/403.</li>
 *   <li>Mọi thứ còn lại: 500 {@code INTERNAL_ERROR}, log mức ERROR kèm traceId, KHÔNG lộ chi tiết
 *       ra ngoài {@code [§11.1]}.</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ==================================================================
    //  Lỗi nghiệp vụ có chủ đích
    // ==================================================================

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex, HttpServletRequest req) {
        log.warn("[{}] Rate limit: {} - {}", traceId(), ex.getErrorCode(), ex.getMessage());
        ApiResponse<Void> body = buildError(ex.getErrorCode(), ex.getMessage(), null, req);
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        // 5xx nghiệp vụ (hiếm, ví dụ AI_SERVICE_UNAVAILABLE) log ERROR; còn lại WARN.
        if (code.getHttpStatus().is5xxServerError()) {
            log.error("[{}] Business 5xx: {} - {}", traceId(), code, ex.getMessage(), ex);
        } else {
            log.warn("[{}] Business: {} - {}", traceId(), code, ex.getMessage());
        }
        return ResponseEntity.status(code.getHttpStatus())
                .body(buildError(code, ex.getMessage(), null, req));
    }

    // ==================================================================
    //  Validation
    // ==================================================================

    /** Lỗi @Valid trên request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex,
                                                                  HttpServletRequest req) {
        List<ApiResponse.FieldError> fieldErrors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.add(ApiResponse.FieldError.builder()
                    .field(fe.getField())
                    .message(fe.getDefaultMessage())
                    .rejectedValue(maskSensitive(fe.getField(), fe.getRejectedValue()))
                    .build());
        }
        log.warn("[{}] Validation body: {} lỗi field", traceId(), fieldErrors.size());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ", fieldErrors, req));
    }

    /** Lỗi @Validated trên tham số path/query. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex,
                                                                       HttpServletRequest req) {
        List<ApiResponse.FieldError> fieldErrors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            String field = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
            fieldErrors.add(ApiResponse.FieldError.builder()
                    .field(field)
                    .message(v.getMessage())
                    .build());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Dữ liệu không hợp lệ", fieldErrors, req));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParam(MissingServletRequestParameterException ex,
                                                                HttpServletRequest req) {
        List<ApiResponse.FieldError> errors = List.of(ApiResponse.FieldError.builder()
                .field(ex.getParameterName())
                .message("Thiếu tham số bắt buộc")
                .build());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Thiếu tham số bắt buộc", errors, req));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                HttpServletRequest req) {
        List<ApiResponse.FieldError> errors = List.of(ApiResponse.FieldError.builder()
                .field(ex.getName())
                .message("Giá trị không đúng kiểu dữ liệu")
                .build());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Tham số không hợp lệ", errors, req));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadable(HttpMessageNotReadableException ex,
                                                              HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Nội dung request không đọc được", null, req));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex,
                                                                  HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(buildError(ErrorCode.VALIDATION_FAILED, "Tệp tải lên vượt quá dung lượng cho phép", null, req));
    }

    // ==================================================================
    //  Bảo mật
    // ==================================================================

    @ExceptionHandler({AuthenticationException.class, BadCredentialsException.class})
    public ResponseEntity<ApiResponse<Void>> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("[{}] Auth: {}", traceId(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildError(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục", null, req));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("[{}] Access denied: {}", traceId(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildError(ErrorCode.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này", null, req));
    }

    // ==================================================================
    //  Điều hướng / method
    // ==================================================================

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoHandlerFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildError(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy tài nguyên yêu cầu", null, req));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                    HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(buildError(ErrorCode.METHOD_NOT_ALLOWED, "Phương thức không được hỗ trợ", null, req));
    }

    // ==================================================================
    //  Bắt hết - lỗi ngoài dự đoán
    // ==================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Log đầy đủ stacktrace vào server, nhưng KHÔNG lộ ra client [§11.1].
        log.error("[{}] Lỗi ngoài dự đoán tại {}: {}", traceId(), req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildError(ErrorCode.INTERNAL_ERROR, "Đã có lỗi xảy ra, vui lòng thử lại sau", null, req));
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private ApiResponse<Void> buildError(ErrorCode code, String message,
                                         List<ApiResponse.FieldError> errors, HttpServletRequest req) {
        return ApiResponse.<Void>builder()
                .success(false)
                .message(message != null ? message : code.getDefaultMessage())
                .errorCode(code.name())
                .errors(errors)
                .path(req.getRequestURI())
                .traceId(traceId())
                .build();
    }

    private String traceId() {
        String id = MDC.get(AppConstant.TRACE_ID_MDC_KEY);
        return id != null ? id : "-";
    }

    /**
     * Không bao giờ trả lại giá trị của field nhạy cảm (mật khẩu, token) trong thông báo lỗi.
     */
    private Object maskSensitive(String field, Object value) {
        if (field == null) {
            return value;
        }
        String lower = field.toLowerCase();
        if (lower.contains("password") || lower.contains("token") || lower.contains("secret")) {
            return null;
        }
        return value;
    }
}
