package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Tin đăng rút gọn cho danh sách/thẻ tin (search, tin của tôi, tin liên quan).
 *
 * <p>Các trường mở rộng ({@code statusLabel}, {@code rejectReason}, {@code availableActions},
 * {@code daysRemaining}...) chỉ có ý nghĩa trong màn "tin của tôi"; ở luồng công khai chúng để
 * {@code null} và bị loại khỏi JSON ({@code NON_NULL} ở envelope).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingSummaryResponse {

    private Long id;
    private String slug;
    private String title;
    private String categoryCode;
    private String categoryName;
    private BigDecimal price;
    private BigDecimal area;
    private String shortAddress;
    private String thumbnailUrl;
    private Integer trustScore;
    private BigDecimal averageRating;
    private Boolean promoted;

    // ----- Mở rộng cho màn "tin của tôi" -----
    private String status;
    private String statusLabel;
    private String rejectReason;
    private Integer viewCount;
    private Integer favoriteCount;
    private Integer contactCount;
    private Instant publishedAt;
    private Instant expiredAt;
    private Long daysRemaining;
    private Boolean expiringSoon;
    private List<String> availableActions;
}
