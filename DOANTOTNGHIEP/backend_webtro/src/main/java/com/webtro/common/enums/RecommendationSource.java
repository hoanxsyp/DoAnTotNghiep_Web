package com.webtro.common.enums;

/**
 * Nguồn hiển thị gợi ý [§9.2].
 *
 * <p>Lưu DB dạng chuỗi (@Enumerated(EnumType.STRING)); không dùng ORDINAL.
 */
public enum RecommendationSource {

    /** Trang chủ */
    HOMEPAGE("Trang chủ"),

    /** Tin tương tự */
    SIMILAR_LISTING("Tin tương tự"),

    /** Sau khi lưu tin */
    AFTER_FAVORITE("Sau khi lưu tin"),

    /** Tìm kiếm ít kết quả */
    LOW_RESULT_SEARCH("Tìm kiếm ít kết quả"),

    /** Chatbot */
    CHATBOT("Chatbot"),

    /** Thông báo */
    NOTIFICATION("Thông báo");

    private final String label;

    RecommendationSource(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để hiển thị cho người dùng cuối. */
    public String getLabel() {
        return label;
    }
}
