package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Thông tin chủ trọ hiển thị trong chi tiết tin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandlordSummaryResponse {

    private Long id;
    private String fullName;
    private String avatarUrl;
    private Boolean verified;
    private BigDecimal averageRating;
    private Integer totalActiveListings;
    private Boolean chatEnabled;
    /** Người đăng có nhận yêu cầu thuê không (false khi tenant đăng tin ở ghép). */
    private Boolean rentalRequestEnabled;
    private Instant memberSince;
    private Boolean followedByMe;
}
