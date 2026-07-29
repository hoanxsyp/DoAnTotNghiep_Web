package com.webtro.constant;

/**
 * Tên các cache (Redis) — canonical mục 8 (§11.11).
 *
 * <p>KHÔNG cache dữ liệu cá nhân nhạy cảm {@code [§11.11]}. Chỉ cache dữ liệu tra cứu ít đổi
 * (danh mục, khu vực, tiện ích, cấu hình) và kết quả gợi ý có TTL.
 */
public final class CacheName {

    /** Danh mục loại tin — đổi rất hiếm. */
    public static final String CATEGORIES = "categories";
    /** Cây tỉnh/huyện/xã. */
    public static final String ADMINISTRATIVE_UNITS = "administrativeUnits";
    /** Danh sách tiện ích. */
    public static final String AMENITIES = "amenities";
    /** Giá trị cấu hình hệ thống — invalidate khi Admin cập nhật. */
    public static final String SYSTEM_CONFIG = "systemConfig";
    /** Gói dịch vụ đang bật. */
    public static final String PROMOTION_PACKAGES = "promotionPackages";
    /** Kết quả gợi ý theo user, có TTL ngắn {@code ai.recommendation.cache_ttl_minutes}. */
    public static final String RECOMMENDATIONS = "recommendations";
    /** Từ khóa cấm — nạp một lần, invalidate khi Admin sửa. */
    public static final String BANNED_KEYWORDS = "bannedKeywords";

    private CacheName() {
    }
}
