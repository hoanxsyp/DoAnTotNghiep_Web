package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tiện ích của tin trả về cho client.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingAmenityResponse {

    private Long id;
    private String code;
    private String name;
    private String group;
    private String iconUrl;
}
