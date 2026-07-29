package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ảnh của tin trả về cho client.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingImageResponse {

    private Long id;
    private String url;
    private String thumbnailUrl;
    private Boolean primary;
    private Integer displayOrder;
    private Integer width;
    private Integer height;
}
