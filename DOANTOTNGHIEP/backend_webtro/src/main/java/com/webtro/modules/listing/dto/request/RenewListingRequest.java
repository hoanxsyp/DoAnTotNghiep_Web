package com.webtro.modules.listing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu gia hạn tin (LIST-09, docs/03 mục 4.4.14).
 *
 * <p>Bỏ trống {@code packageId} → dùng lượt gia hạn miễn phí. Có {@code packageId} → tạo giao dịch
 * thanh toán (gia hạn kích hoạt ở callback thành công).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewListingRequest {

    /** Gói gia hạn trả phí (tùy chọn). */
    private Long packageId;
}
