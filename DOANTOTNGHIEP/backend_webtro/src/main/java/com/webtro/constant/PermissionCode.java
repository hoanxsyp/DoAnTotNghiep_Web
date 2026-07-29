package com.webtro.constant;

import java.util.Set;

/**
 * Toàn bộ permission code — canonical mục 4.2 (RBAC 2 tầng: Role → Permission).
 *
 * <p>Kiểm tra ở backend bằng {@code @PreAuthorize("hasAuthority('...')")}, KHÔNG chỉ ẩn nút ở
 * frontend {@code [§11.2]}. MODERATOR cố tình KHÔNG có bất kỳ permission nào về PAYMENT, PACKAGE,
 * SYSTEM_CONFIG, USER_ROLE_ASSIGN, STATISTIC — đúng {@code [§1.2]}.
 */
public final class PermissionCode {

    // -------- Listing --------
    public static final String LISTING_CREATE = "LISTING_CREATE";
    public static final String LISTING_UPDATE_OWN = "LISTING_UPDATE_OWN";
    public static final String LISTING_UPDATE_ANY = "LISTING_UPDATE_ANY";
    public static final String LISTING_MODERATE = "LISTING_MODERATE";
    public static final String LISTING_LOCK = "LISTING_LOCK";
    public static final String LISTING_VIEW_ANY = "LISTING_VIEW_ANY";

    // -------- Interaction --------
    public static final String FAVORITE_MANAGE = "FAVORITE_MANAGE";
    public static final String CONTACT_CREATE = "CONTACT_CREATE";
    public static final String COMMENT_CREATE = "COMMENT_CREATE";
    public static final String COMMENT_MODERATE = "COMMENT_MODERATE";
    public static final String REVIEW_CREATE = "REVIEW_CREATE";
    public static final String REVIEW_MODERATE = "REVIEW_MODERATE";

    // -------- Report / Moderation --------
    public static final String REPORT_CREATE = "REPORT_CREATE";
    public static final String REPORT_RESOLVE = "REPORT_RESOLVE";
    public static final String WARNING_SEND = "WARNING_SEND";

    // -------- User / Landlord --------
    public static final String USER_MANAGE = "USER_MANAGE";
    public static final String USER_ROLE_ASSIGN = "USER_ROLE_ASSIGN";
    public static final String LANDLORD_VERIFY = "LANDLORD_VERIFY";

    // -------- Payment / Package --------
    public static final String PAYMENT_VIEW_OWN = "PAYMENT_VIEW_OWN";
    public static final String PAYMENT_MANAGE = "PAYMENT_MANAGE";
    public static final String PACKAGE_MANAGE = "PACKAGE_MANAGE";

    // -------- Catalog / AI / System --------
    public static final String CATALOG_MANAGE = "CATALOG_MANAGE";
    public static final String AI_CONFIG_MANAGE = "AI_CONFIG_MANAGE";
    public static final String AI_LOG_VIEW = "AI_LOG_VIEW";
    public static final String SYSTEM_CONFIG_MANAGE = "SYSTEM_CONFIG_MANAGE";
    public static final String STATISTIC_VIEW = "STATISTIC_VIEW";
    public static final String AUDIT_LOG_VIEW = "AUDIT_LOG_VIEW";

    /**
     * Tập đầy đủ mọi permission — dùng khi seed và khi cấp toàn quyền cho ADMIN.
     * Giữ đồng bộ thủ công với danh sách hằng phía trên; nếu thêm permission mới nhớ thêm vào đây.
     */
    public static final Set<String> ALL = Set.of(
            LISTING_CREATE, LISTING_UPDATE_OWN, LISTING_UPDATE_ANY, LISTING_MODERATE, LISTING_LOCK,
            LISTING_VIEW_ANY, FAVORITE_MANAGE, CONTACT_CREATE, COMMENT_CREATE, COMMENT_MODERATE,
            REVIEW_CREATE, REVIEW_MODERATE, REPORT_CREATE, REPORT_RESOLVE, WARNING_SEND,
            USER_MANAGE, USER_ROLE_ASSIGN, LANDLORD_VERIFY, PAYMENT_VIEW_OWN, PAYMENT_MANAGE,
            PACKAGE_MANAGE, CATALOG_MANAGE, AI_CONFIG_MANAGE, AI_LOG_VIEW, SYSTEM_CONFIG_MANAGE,
            STATISTIC_VIEW, AUDIT_LOG_VIEW);

    private PermissionCode() {
    }
}
