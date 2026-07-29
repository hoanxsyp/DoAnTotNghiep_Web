package com.webtro.modules.listing.adapter;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.payment.spi.ListingPromotionGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Adapter hiện thực {@link ListingPromotionGateway} (SPI do module {@code payment} sở hữu) —
 * canonical luật 4. Bọc {@code ListingRepository}; đặt/gỡ cờ đẩy tin khi mua gói/hết hạn (§8.2).
 */
@Component
@RequiredArgsConstructor
public class ListingPromotionGatewayAdapter implements ListingPromotionGateway {

    private final ListingRepository listingRepository;

    @Override
    @Transactional(readOnly = true)
    public PromotableListing getForPromotion(Long listingId) {
        Listing l = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
        return new PromotableListing(l.getId(), l.getOwnerId(), l.getTitle(), l.getStatus());
    }

    @Override
    @Transactional
    public void applyPromotion(Long listingId, int priority, Instant promotedUntil) {
        Listing l = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
        l.setIsPromoted(true);
        // Không hạ cấp: giữ mức ưu tiên cao hơn nếu tin đang có mức cao hơn.
        l.setPromotionPriority(Math.max(nz(l.getPromotionPriority()), priority));
        // Không rút ngắn thời hạn đẩy đang có: giữ mốc hết hiệu lực xa hơn.
        Instant current = l.getPromotedUntil();
        l.setPromotedUntil(current != null && current.isAfter(promotedUntil) ? current : promotedUntil);
        listingRepository.save(l);
    }

    @Override
    @Transactional
    public void clearPromotion(Long listingId) {
        listingRepository.findAliveById(listingId).ifPresent(l -> {
            l.setIsPromoted(false);
            l.setPromotionPriority(0);
            l.setPromotedUntil(null);
            listingRepository.save(l);
        });
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
