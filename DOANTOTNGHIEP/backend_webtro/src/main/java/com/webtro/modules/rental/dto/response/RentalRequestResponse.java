package com.webtro.modules.rental.dto.response;

import com.webtro.common.enums.RentalRequestStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class RentalRequestResponse {
    private Long id;
    private Long listingId;
    private String listingTitle;
    private String listingSlug;
    private String listingThumbnailUrl;
    private BigDecimal listingPrice;
    private Long tenantId;
    private String tenantName;
    private String tenantAvatarUrl;
    private String tenantPhone;
    private LocalDate desiredViewingDate;
    private RentalRequestStatus status;
    private String landlordNote;
    private Instant approvedAt;
    private Instant rejectedAt;
    private Instant cancelledAt;
    private Instant rentedAt;
    private Instant checkedOutAt;
    private Instant createdAt;
}
