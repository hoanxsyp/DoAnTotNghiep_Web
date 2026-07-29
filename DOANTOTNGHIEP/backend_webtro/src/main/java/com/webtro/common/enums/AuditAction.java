package com.webtro.common.enums;

/**
 * Hành động cần ghi audit [§11.4].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum AuditAction {

    /** Khóa tài khoản */
    USER_LOCK("Khóa tài khoản"),

    /** Mở khóa tài khoản */
    USER_UNLOCK("Mở khóa tài khoản"),

    /** Đổi vai trò */
    ROLE_CHANGE("Đổi vai trò"),

    /** Xác thực chủ trọ */
    LANDLORD_VERIFY("Xác thực chủ trọ"),

    /** Hủy xác thực chủ trọ */
    LANDLORD_UNVERIFY("Hủy xác thực chủ trọ"),

    /** Duyệt tin */
    LISTING_APPROVE("Duyệt tin"),

    /** Từ chối tin */
    LISTING_REJECT("Từ chối tin"),

    /** Khóa tin */
    LISTING_LOCK("Khóa tin"),

    /** Mở khóa tin */
    LISTING_UNLOCK("Mở khóa tin"),

    /** Ẩn tin (kiểm duyệt) */
    LISTING_HIDE("Ẩn tin"),

    /** Bỏ ẩn tin (kiểm duyệt) */
    LISTING_UNHIDE("Bỏ ẩn tin"),

    /** Sửa tin */
    LISTING_EDIT("Sửa tin"),

    /** Ẩn bình luận (kiểm duyệt) */
    COMMENT_HIDE("Ẩn bình luận"),

    /** Bỏ ẩn bình luận (kiểm duyệt) */
    COMMENT_UNHIDE("Bỏ ẩn bình luận"),

    /** Đánh dấu bình luận spam */
    COMMENT_SPAM("Đánh dấu bình luận spam"),

    /** Ẩn đánh giá (kiểm duyệt) */
    REVIEW_HIDE("Ẩn đánh giá"),

    /** Bỏ ẩn đánh giá (kiểm duyệt) */
    REVIEW_UNHIDE("Bỏ ẩn đánh giá"),

    /** Đổi cấu hình AI */
    AI_CONFIG_CHANGE("Đổi cấu hình AI"),

    /** Đổi gói dịch vụ */
    PACKAGE_CHANGE("Đổi gói dịch vụ"),

    /** Đổi cấu hình hệ thống */
    SYSTEM_CONFIG_CHANGE("Đổi cấu hình hệ thống"),

    /** Hoàn tiền */
    PAYMENT_REFUND("Hoàn tiền");

    private final String label;

    AuditAction(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
