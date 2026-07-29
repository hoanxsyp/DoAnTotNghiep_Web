package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả upload ảnh tin (docs/03 mục 4.4.15).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageUploadResponse {

    private Long listingId;
    private Integer totalImages;
    private Integer maxImages;
    private List<Item> uploaded;

    /** Một ảnh vừa upload. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private String url;
        private String thumbnailUrl;
        private Boolean primary;
        private Integer displayOrder;
        private Long sizeBytes;
        private Integer width;
        private Integer height;
    }
}
