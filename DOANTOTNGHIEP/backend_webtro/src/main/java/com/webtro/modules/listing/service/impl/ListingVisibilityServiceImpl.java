package com.webtro.modules.listing.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.service.ListingVisibilityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Set;

/**
 * Cài đặt quy tắc hiển thị công khai (canonical §5.2).
 *
 * <p>{@code ACTIVE} luôn công khai. {@code NEED_REVIEW} công khai khi
 * {@code listing.need_review.publicly_visible = true} (mặc định) — đúng tinh thần "report không
 * tự động gỡ tin, chỉ đánh dấu để soi".
 */
@Service
@RequiredArgsConstructor
public class ListingVisibilityServiceImpl implements ListingVisibilityService {

    private final SystemConfigService systemConfigService;

    @Override
    public Set<ListingStatus> publicStatuses() {
        EnumSet<ListingStatus> statuses = EnumSet.of(ListingStatus.ACTIVE);
        if (systemConfigService.getBoolean(ConfigKey.LISTING_NEED_REVIEW_PUBLICLY_VISIBLE)) {
            statuses.add(ListingStatus.NEED_REVIEW);
        }
        return statuses;
    }

    @Override
    public boolean publiclyVisible(Listing listing) {
        return listing != null
                && listing.getDeletedAt() == null
                && isPublicStatus(listing.getStatus());
    }

    @Override
    public boolean isPublicStatus(ListingStatus status) {
        return status != null && publicStatuses().contains(status);
    }
}
