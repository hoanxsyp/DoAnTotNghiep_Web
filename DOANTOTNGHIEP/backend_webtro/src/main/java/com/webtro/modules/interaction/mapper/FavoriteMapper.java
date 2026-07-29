package com.webtro.modules.interaction.mapper;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.interaction.dto.response.FavoriteResponse;
import com.webtro.modules.interaction.dto.response.FavoriteToggleResponse;
import com.webtro.modules.interaction.entity.Favorite;
import com.webtro.modules.interaction.spi.ListingGateway.ListingBrief;
import org.springframework.stereotype.Component;

/**
 * Ánh xạ Favorite ↔ DTO (thủ công, Builder — canonical luật 3). Nơi DUY NHẤT chuyển entity↔DTO.
 */
@Component
public class FavoriteMapper {

    /**
     * Ánh xạ một tin đã lưu kèm tóm tắt tin. {@code brief} có thể null nếu tin không còn nạp được
     * (rất hiếm) — khi đó chỉ trả các trường của favorite.
     */
    public FavoriteResponse toResponse(Favorite favorite, ListingBrief brief) {
        FavoriteResponse.FavoriteResponseBuilder b = FavoriteResponse.builder()
                .id(favorite.getId())
                .listingId(favorite.getListingId())
                .note(favorite.getNote())
                .favoritedAt(favorite.getCreatedAt());

        if (brief == null) {
            return b.notAvailable(true).notAvailableLabel("Tin đã bị gỡ").build();
        }

        boolean notAvailable = !brief.publiclyVisible();
        return b.title(brief.title())
                .slug(brief.slug())
                .thumbnailUrl(brief.thumbnailUrl())
                .price(brief.price())
                .area(brief.area())
                .shortAddress(brief.shortAddress())
                .status(brief.status() == null ? null : brief.status().name())
                .notAvailable(notAvailable)
                .notAvailableLabel(notAvailable ? notAvailableLabel(brief.status()) : null)
                .build();
    }

    /** Nhãn tiếng Việt cho tin không còn hiển thị {@code [§3.9]}. */
    public String notAvailableLabel(ListingStatus status) {
        if (status == null) {
            return "Tin đã bị gỡ";
        }
        return switch (status) {
            case DELETED -> "Tin đã bị gỡ";
            case EXPIRED -> "Tin đã hết hạn hiển thị";
            case CLOSED -> "Tin đã đóng";
            case LOCKED -> "Tin đã bị khóa";
            default -> "Tin hiện không hiển thị";
        };
    }

    public FavoriteToggleResponse toToggleResponse(Favorite favorite, int favoriteCount) {
        return FavoriteToggleResponse.builder()
                .id(favorite.getId())
                .listingId(favorite.getListingId())
                .favorited(true)
                .favoriteCount(favoriteCount)
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}
