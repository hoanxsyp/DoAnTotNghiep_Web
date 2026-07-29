package com.webtro.modules.user.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.TrustLabel;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.user.dto.response.LandlordDashboardResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.service.LandlordDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cài đặt {@link LandlordDashboardService}. Số tin theo trạng thái + tổng lượt xem/lưu/liên hệ lấy
 * trực tiếp từ {@link ListingRepository} (số liệu sống); điểm uy tín/tỷ lệ phản hồi/đánh giá lấy từ
 * bộ đếm sẵn trên {@link LandlordProfile}.
 */
@Service
@RequiredArgsConstructor
public class LandlordDashboardServiceImpl implements LandlordDashboardService {

    private final ListingRepository listingRepository;
    private final LandlordProfileRepository landlordProfileRepository;

    /** {@code @Lazy} để tránh vòng phụ thuộc user ↔ listing. */
    @Lazy
    @Autowired
    private TrustScoreService trustScoreService;

    @Override
    @Transactional(readOnly = true)
    public LandlordDashboardResponse getDashboard(Long userId) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : listingRepository.countByStatusForOwner(userId)) {
            String status = row[0] != null ? ((ListingStatus) row[0]).name() : "UNKNOWN";
            long count = ((Number) row[1]).longValue();
            byStatus.put(status, count);
        }

        LandlordDashboardResponse.LandlordDashboardResponseBuilder builder = LandlordDashboardResponse.builder()
                .totalListings(listingRepository.countByOwner(userId))
                .listingsByStatus(byStatus)
                .totalViews(listingRepository.sumViewCountForOwner(userId))
                .totalFavorites(listingRepository.sumFavoriteCountForOwner(userId))
                .totalContacts(listingRepository.sumContactCountForOwner(userId));

        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        if (lp != null) {
            Integer trustScore = toInt(lp.getTrustScore());
            TrustLabel trustLabel = trustScore != null ? trustScoreService.labelOf(trustScore) : null;
            builder.trustScore(trustScore)
                    .trustLabel(trustLabel != null ? trustLabel.getLabel() : null)
                    .responseRatePercent(lp.getResponseRatePercent())
                    .averageRating(lp.getAverageRating())
                    .reviewCount(lp.getReviewCount())
                    .validReportCount(lp.getValidReportCount())
                    .warningCount(lp.getWarningCount());
        }
        return builder.build();
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
