package com.webtro.storage;

import lombok.Builder;
import lombok.Getter;

/**
 * Kết quả lưu một ảnh: khóa tham chiếu + URL các kích thước + kích thước pixel gốc.
 *
 * <p>{@code width}/{@code height} được lưu để frontend đặt {@code aspect-ratio}, giữ chỗ trước khi
 * ảnh tải về → chống layout nhảy (CLS) (canonical mục 8, §11.3).
 */
@Getter
@Builder
public class StoredImage {

    /** Khóa gốc (không đuôi kích thước), dùng để xóa và tra cứu. */
    private final String key;

    /** URL bản gốc (đã giới hạn ≤1920px). */
    private final String originalUrl;

    /** URL bản trung (800px) cho slider. */
    private final String mediumUrl;

    /** URL thumbnail (200px) cho ListingCard. */
    private final String thumbnailUrl;

    private final int width;
    private final int height;
    private final long sizeBytes;
}
