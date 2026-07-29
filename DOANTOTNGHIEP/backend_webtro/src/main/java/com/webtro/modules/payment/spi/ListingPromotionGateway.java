package com.webtro.modules.payment.spi;

import com.webtro.common.enums.ListingStatus;

import java.time.Instant;

/**
 * Cổng (SPI) mà module {@code payment} cần từ module {@code listing} — canonical luật 4
 * (gọi module khác qua interface, không đụng repository của nó).
 *
 * <p><b>Quyết định kiến trúc:</b> tại thời điểm viết module payment, module listing mới chỉ có
 * entity + repository + {@code ListingStateMachine} + {@code TrustScoreService} +
 * {@code ListingVisibilityService}, chưa có service ghi. Theo đúng quy ước đã áp dụng ở
 * {@code interaction.spi.ListingGateway} và {@code moderation.spi.ListingModerationGateway}, hợp
 * đồng cross-module được khai báo dạng SPI <i>do bên tiêu dùng sở hữu</i> (đặt tại
 * {@code payment.spi}). Module {@code listing} phải cung cấp một {@code @Component} hiện thực
 * interface này, bọc {@code ListingRepository}.
 *
 * <p>Dùng cho luồng mua gói đẩy tin (§8.2): kiểm tra chủ sở hữu + trạng thái tin trước khi tạo đơn,
 * và khi thanh toán thành công thì bật cờ đẩy tin trên {@code listings}
 * ({@code is_promoted}, {@code promoted_until}, {@code promotion_priority}).
 */
public interface ListingPromotionGateway {

    /**
     * Thông tin tối thiểu của tin phục vụ kiểm tra nghiệp vụ khi mua gói đẩy.
     *
     * @param id      id tin
     * @param ownerId chủ tin (users.id) — dùng kiểm tra quyền sở hữu
     * @param title   tiêu đề (snapshot hiển thị trong response/thông báo)
     * @param status  trạng thái hiện tại của tin
     */
    record PromotableListing(Long id, Long ownerId, String title, ListingStatus status) {
    }

    /**
     * Lấy tin còn sống ({@code deleted_at IS NULL}) phục vụ mua gói đẩy;
     * ném {@code ResourceNotFoundException(LISTING_NOT_FOUND)} nếu không tồn tại.
     */
    PromotableListing getForPromotion(Long listingId);

    /**
     * Bật cờ đẩy tin sau khi thanh toán thành công: đặt {@code is_promoted = true},
     * {@code promotion_priority = priority}, {@code promoted_until = promotedUntil}.
     * Nếu tin đang có mức ưu tiên cao hơn thì bên hiện thực giữ mức cao hơn (không hạ cấp).
     *
     * @param listingId     id tin
     * @param priority      mức ưu tiên (đã chặn trần {@code promotion.max_priority} ở service)
     * @param promotedUntil thời điểm hết hiệu lực đẩy (UTC)
     */
    void applyPromotion(Long listingId, int priority, Instant promotedUntil);

    /**
     * Gỡ cờ đẩy tin khi gói hết hạn/hủy: đặt {@code is_promoted = false},
     * {@code promotion_priority = 0}, {@code promoted_until = null}.
     */
    void clearPromotion(Long listingId);
}
