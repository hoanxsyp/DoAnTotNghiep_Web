package com.webtro.constant;

/**
 * Hằng kỹ thuật dùng chung. KHÔNG chứa ngưỡng nghiệp vụ (những cái đó nằm ở
 * {@link ConfigKey} + bảng {@code system_configs}).
 */
public final class AppConstant {

    private AppConstant() {
    }

    /** Header mang mã truy vết để nối log giữa request và response. */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    /** Key của traceId trong MDC (log context). */
    public static final String TRACE_ID_MDC_KEY = "traceId";
    /** Header phiên bản API (canonical mục 7.3). */
    public static final String API_VERSION_HEADER = "X-Api-Version";
    public static final String DEFAULT_API_VERSION = "1";

    /** Prefix chuẩn của mọi endpoint. */
    public static final String API_PREFIX = "/api";

    /**
     * Claim tùy biến trong JWT.
     *
     * <p>{@code role} là CHUỖI đơn (không phải mảng): mỗi người dùng chỉ có một vai trò.
     */
    public static final String JWT_CLAIM_ROLE = "role";
    public static final String JWT_CLAIM_EMAIL = "email";

    /** Prefix key Redis. */
    public static final String REDIS_RATE_LIMIT_PREFIX = "rl:";
    public static final String REDIS_JWT_BLACKLIST_PREFIX = "bl:";
    public static final String REDIS_LOGIN_ATTEMPT_PREFIX = "login:";

    /** Phân trang: chặn size để tránh truy vấn trả về quá nhiều (canonical mục 7.3). */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 20;

    /** Định dạng ảnh được phép upload {@code [§3.3][§11.9]}. */
    public static final String[] ALLOWED_IMAGE_EXTENSIONS = {"jpg", "jpeg", "png", "webp"};

    /** Số chữ số thập phân làm tròn tọa độ cho khách chưa đăng nhập (canonical mục 8, ~110m). */
    public static final int PUBLIC_GEO_SCALE = 3;
}
