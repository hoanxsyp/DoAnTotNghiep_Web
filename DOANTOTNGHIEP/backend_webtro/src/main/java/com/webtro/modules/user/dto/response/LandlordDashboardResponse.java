package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Landlord overview for {@code GET /api/landlord/dashboard}.
 *
 * <p>The first group of fields matches the frontend contract in docs/03 section 4.4.23. The legacy
 * aggregate fields are kept for backward compatibility with any caller that already consumed this endpoint.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LandlordDashboardResponse", description = "Tong quan chu tro")
public class LandlordDashboardResponse {

    private long activeCount;
    private long pendingCount;
    private long viewCount30d;
    private long contactCount30d;
    private Deltas deltas;
    private List<ChartPoint> chart;
    private List<ListingSummaryResponse> topListings;
    private List<ActionItem> actionItems;
    private String landlordVerificationStatus;
    private Instant generatedAt;

    private long totalListings;
    private Map<String, Long> listingsByStatus;
    private long totalViews;
    private long totalFavorites;
    private long totalContacts;
    private Integer trustScore;
    private String trustLabel;
    private Integer responseRatePercent;
    private BigDecimal averageRating;
    private Integer reviewCount;
    private Integer validReportCount;
    private Integer warningCount;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Deltas {
        private Integer activeCount;
        private Integer pendingCount;
        private BigDecimal viewCountPercent;
        private BigDecimal contactCountPercent;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChartPoint {
        private LocalDate date;
        private long views;
        private long contacts;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionItem {
        private String type;
        private String severity;
        private long count;
        private String message;
        private String actionUrl;
    }
}
