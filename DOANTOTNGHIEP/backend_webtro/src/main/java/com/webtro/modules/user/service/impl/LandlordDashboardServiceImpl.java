package com.webtro.modules.user.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.TrustLabel;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.interaction.repository.ContactLogRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.mapper.ListingMapper;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.user.dto.response.LandlordDashboardResponse;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.service.LandlordDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Live aggregate for the landlord dashboard. The endpoint is scoped only by the authenticated user id.
 */
@Service
@RequiredArgsConstructor
public class LandlordDashboardServiceImpl implements LandlordDashboardService {

    private static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    private static final ZoneId DASHBOARD_ZONE = ZoneId.systemDefault();
    private static final Collection<ListingStatus> TOP_LISTING_STATUSES =
            List.of(ListingStatus.ACTIVE, ListingStatus.HIDDEN);

    private final ListingRepository listingRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final ContactLogRepository contactLogRepository;
    private final ReportRepository reportRepository;
    private final ListingMapper listingMapper;

    /** {@code @Lazy} avoids user <-> listing dependency cycles. */
    @Lazy
    @Autowired
    private TrustScoreService trustScoreService;

    @Override
    @Transactional(readOnly = true)
    public LandlordDashboardResponse getDashboard(Long userId, int days) {
        if (!ALLOWED_DAYS.contains(days)) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "days chỉ nhận 7, 30 hoặc 90");
        }

        Instant generatedAt = Instant.now();
        LocalDate today = LocalDate.now(DASHBOARD_ZONE);
        LocalDate fromDate = today.minusDays(days - 1L);
        Instant from = fromDate.atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(DASHBOARD_ZONE).toInstant();
        Instant previousFrom = from.minus(Duration.ofDays(days));

        Map<ListingStatus, Long> statusCounts = statusCounts(userId);
        long activeCount = count(statusCounts, ListingStatus.ACTIVE);
        long pendingCount = count(statusCounts, ListingStatus.PENDING);
        long totalListings = listingRepository.countByOwner(userId);
        long viewCountInWindow = viewHistoryRepository.countCountedViewsForOwnerBetween(userId, from, to);
        long contactCountInWindow = contactLogRepository
                .countByOwnerIdAndIsCountedTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedAtIsNull(
                        userId, from, to);

        long previousViewCount = viewHistoryRepository.countCountedViewsForOwnerBetween(userId, previousFrom, from);
        long previousContactCount = contactLogRepository
                .countByOwnerIdAndIsCountedTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedAtIsNull(
                        userId, previousFrom, from);

        LandlordDashboardResponse.LandlordDashboardResponseBuilder builder = LandlordDashboardResponse.builder()
                .activeCount(activeCount)
                .pendingCount(pendingCount)
                .viewCount30d(viewCountInWindow)
                .contactCount30d(contactCountInWindow)
                .deltas(LandlordDashboardResponse.Deltas.builder()
                        .viewCountPercent(percentDelta(viewCountInWindow, previousViewCount))
                        .contactCountPercent(percentDelta(contactCountInWindow, previousContactCount))
                        .build())
                .chart(chart(userId, fromDate, days, from, to))
                .topListings(topListings(userId))
                .actionItems(actionItems(userId, generatedAt, statusCounts))
                .generatedAt(generatedAt)
                .totalListings(totalListings)
                .listingsByStatus(legacyStatusMap(statusCounts))
                .totalViews(listingRepository.sumViewCountForOwner(userId))
                .totalFavorites(listingRepository.sumFavoriteCountForOwner(userId))
                .totalContacts(listingRepository.sumContactCountForOwner(userId));

        LandlordProfile lp = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId).orElse(null);
        if (lp != null) {
            Integer trustScore = toInt(lp.getTrustScore());
            TrustLabel trustLabel = trustScore != null ? trustScoreService.labelOf(trustScore) : null;
            builder.landlordVerificationStatus(lp.getVerificationStatus() == null
                            ? null : lp.getVerificationStatus().name())
                    .trustScore(trustScore)
                    .trustLabel(trustLabel != null ? trustLabel.getLabel() : null)
                    .responseRatePercent(lp.getResponseRatePercent())
                    .averageRating(lp.getAverageRating())
                    .reviewCount(lp.getReviewCount())
                    .validReportCount(lp.getValidReportCount())
                    .warningCount(lp.getWarningCount());
        }
        return builder.build();
    }

    private Map<ListingStatus, Long> statusCounts(Long userId) {
        Map<ListingStatus, Long> counts = new EnumMap<>(ListingStatus.class);
        for (Object[] row : listingRepository.countByStatusForOwner(userId)) {
            if (row[0] instanceof ListingStatus status) {
                counts.put(status, ((Number) row[1]).longValue());
            }
        }
        return counts;
    }

    private Map<String, Long> legacyStatusMap(Map<ListingStatus, Long> statusCounts) {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ListingStatus status : ListingStatus.values()) {
            long value = count(statusCounts, status);
            if (value > 0) {
                byStatus.put(status.name(), value);
            }
        }
        return byStatus;
    }

    private List<LandlordDashboardResponse.ChartPoint> chart(Long userId, LocalDate fromDate, int days,
                                                             Instant from, Instant to) {
        Map<LocalDate, Long> viewsByDate = dailyMap(
                viewHistoryRepository.countDailyViewsForOwnerBetween(userId, from, to));
        Map<LocalDate, Long> contactsByDate = dailyMap(
                contactLogRepository.countDailyContactsForOwnerBetween(userId, from, to));

        List<LandlordDashboardResponse.ChartPoint> points = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate date = fromDate.plusDays(i);
            points.add(LandlordDashboardResponse.ChartPoint.builder()
                    .date(date)
                    .views(viewsByDate.getOrDefault(date, 0L))
                    .contacts(contactsByDate.getOrDefault(date, 0L))
                    .build());
        }
        return points;
    }

    private Map<LocalDate, Long> dailyMap(List<Object[]> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                result.put(date, ((Number) row[1]).longValue());
            }
        }
        return result;
    }

    private List<ListingSummaryResponse> topListings(Long userId) {
        return listingRepository.findTopForLandlordDashboard(userId, TOP_LISTING_STATUSES, PageRequest.of(0, 5))
                .stream()
                .map((Listing listing) -> listingMapper.toSummary(listing, true))
                .toList();
    }

    private List<LandlordDashboardResponse.ActionItem> actionItems(
            Long userId, Instant now, Map<ListingStatus, Long> statusCounts) {
        long expiringSoon = listingRepository.countExpiringSoonForOwner(
                userId, now, now.plus(Duration.ofDays(3)));
        long rejected = count(statusCounts, ListingStatus.REJECTED);
        long needReview = count(statusCounts, ListingStatus.NEED_REVIEW);
        long reported = reportRepository.countPendingReportedListingsForOwner(userId);
        long locked = count(statusCounts, ListingStatus.LOCKED);

        return List.of(
                item("EXPIRING_SOON", "WARNING", expiringSoon,
                        "%d tin sẽ hết hạn trong 3 ngày tới".formatted(expiringSoon),
                        "/quan-ly/tin-dang?expiringWithinDays=3"),
                item("REJECTED", "ERROR", rejected,
                        "%d tin bị từ chối, cần chỉnh sửa".formatted(rejected),
                        "/quan-ly/tin-dang?status=REJECTED"),
                item("AI_NEGATIVE_ALERT", "WARNING", needReview,
                        "%d tin cần kiểm tra do tín hiệu AI/bình luận".formatted(needReview),
                        "/quan-ly/tin-dang?status=NEED_REVIEW"),
                item("REPORTED", "WARNING", reported,
                        "%d tin đang có báo cáo chờ xử lý".formatted(reported),
                        "/quan-ly/tin-dang?status=NEED_REVIEW"),
                item("LOCKED", "ERROR", locked,
                        "%d tin bị khóa".formatted(locked),
                        "/quan-ly/tin-dang?status=LOCKED")
        );
    }

    private LandlordDashboardResponse.ActionItem item(String type, String severity, long count,
                                                      String message, String actionUrl) {
        return LandlordDashboardResponse.ActionItem.builder()
                .type(type)
                .severity(severity)
                .count(count)
                .message(message)
                .actionUrl(actionUrl)
                .build();
    }

    private long count(Map<ListingStatus, Long> counts, ListingStatus status) {
        return counts.getOrDefault(status, 0L);
    }

    private BigDecimal percentDelta(long current, long previous) {
        if (previous == 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 2, RoundingMode.HALF_UP);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return value == null ? null : LocalDate.parse(value.toString());
    }

    private Integer toInt(BigDecimal value) {
        return value == null ? null : value.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
