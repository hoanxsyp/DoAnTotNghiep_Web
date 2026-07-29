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
 * Kết quả gia hạn tin (docs/03 mục 4.4.14). Bao trùm cả hai luồng: gia hạn miễn phí thành công
 * và trường hợp cần thanh toán ({@code paymentRequired = true}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewResponse {

    private Long id;
    private String status;
    private String previousStatus;
    private Instant previousExpiredAt;
    private Instant expiredAt;
    private Integer displayDays;
    private Boolean free;
    private Integer freeRenewUsed;
    private Integer freeRenewLimit;
    private Integer freeRenewRemaining;
    private Boolean paymentRequired;
    private PaymentHint paymentHint;

    /** Gợi ý thanh toán khi hết lượt miễn phí hoặc chọn gói trả phí. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentHint {
        private String endpoint;
        private String purpose;
        private List<PackageOption> availablePackages;
    }

    /** Một gói gia hạn khả dụng. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageOption {
        private Long id;
        private String code;
        private String name;
        private BigDecimal price;
        private Integer durationDays;
    }
}
