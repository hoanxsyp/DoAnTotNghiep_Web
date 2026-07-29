package com.webtro.scheduler;

import com.webtro.common.enums.NotificationType;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.notification.repository.NotificationRepository;
import com.webtro.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Job 9 (canonical mục 11, §9.2) — <b>NewMatchingListingNotifyJob</b>, chạy 07:30 UTC hằng ngày.
 *
 * <p>Quét tin mới xuất bản trong {@link ConfigKey#AI_RECOMMENDATION_NOTIFY_LOOKBACK_HOURS} giờ, với
 * mỗi người dùng hoạt động trong {@link ConfigKey#AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS} ngày,
 * gửi tối đa {@link ConfigKey#AI_RECOMMENDATION_NOTIFY_MAX_PER_USER} tin có điểm khớp
 * {@code >= }{@link ConfigKey#AI_RECOMMENDATION_NOTIFY_MIN_SCORE} qua
 * {@link NotificationType#NEW_MATCHING_LISTING}. Đây là tác nhân duy nhất sinh loại thông báo này.
 *
 * <p><b>Phiên bản khớp hợp lý</b> (canonical cho phép nếu logic đầy đủ quá phức tạp): xây sở thích
 * từ lịch sử xem gần đây của user (khu vực quận/tỉnh hay xem + khoảng giá đã xem) rồi chấm điểm tin
 * mới theo khớp khu vực (0.6) và khớp giá (0.4), điểm ∈ [0,1]. Loại tin của chính user và tin user
 * đã xem. Chống gửi trùng bằng việc kiểm tra đã từng gửi cùng {@code link} tin cho user chưa
 * (idempotent — chạy lại không gửi lại tin đã gửi).
 *
 * <p>Lịch UTC, bọc try/catch từng user, kết thúc log INFO kèm số liệu.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NewMatchingListingNotifyJob {

    /** Trọng số khớp khu vực và khớp giá trong điểm khớp [0,1]. */
    private static final double W_LOCATION = 0.6;
    private static final double W_PRICE = 0.4;
    /** Biên nới khoảng giá đã xem khi so khớp (±20%). */
    private static final double PRICE_LOWER = 0.8;
    private static final double PRICE_UPPER = 1.2;

    private static final int MAX_NEW_LISTINGS = 500;
    private static final int MAX_USERS = 5000;
    private static final int RECENT_VIEW_LIMIT = 50;

    private final ListingRepository listingRepository;
    private final ViewHistoryRepository viewHistoryRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final SystemConfigService config;
    private final AppProperties appProperties;

    @Scheduled(cron = "0 30 7 * * *", zone = "UTC")
    public void run() {
        if (!appProperties.getScheduler().isEnabled()) {
            return;
        }
        if (!config.getBoolean(ConfigKey.AI_RECOMMENDATION_NOTIFY_ENABLED)) {
            log.debug("NewMatchingListingNotifyJob: thông báo tin mới phù hợp đang tắt, bỏ qua.");
            return;
        }

        int lookbackHours = config.getInt(ConfigKey.AI_RECOMMENDATION_NOTIFY_LOOKBACK_HOURS);
        int activeDays = config.getInt(ConfigKey.AI_RECOMMENDATION_NOTIFY_ACTIVE_USER_DAYS);
        int maxPerUser = config.getInt(ConfigKey.AI_RECOMMENDATION_NOTIFY_MAX_PER_USER);
        double minScore = config.getDouble(ConfigKey.AI_RECOMMENDATION_NOTIFY_MIN_SCORE);

        Instant now = Instant.now();
        Instant newSince = now.minus(Duration.ofHours(lookbackHours));
        Instant activeSince = now.minus(Duration.ofDays(activeDays));

        List<Listing> newListings = listingRepository.findNewActiveListingsSince(
                newSince, PageRequest.of(0, MAX_NEW_LISTINGS));
        if (newListings.isEmpty()) {
            log.info("NewMatchingListingNotifyJob hoàn tất: không có tin mới trong {} giờ qua.", lookbackHours);
            return;
        }

        List<Long> activeUserIds = viewHistoryRepository.findDistinctActiveUserIdsSince(
                activeSince, PageRequest.of(0, MAX_USERS));

        int usersMatched = 0;
        int notificationsSent = 0;
        int errors = 0;
        for (Long userId : activeUserIds) {
            try {
                int sent = processUser(userId, newListings, activeSince, maxPerUser, minScore);
                if (sent > 0) {
                    usersMatched++;
                    notificationsSent += sent;
                }
            } catch (RuntimeException e) {
                errors++;
                log.error("NewMatchingListingNotifyJob: lỗi xử lý user id={}: {}", userId, e.getMessage(), e);
            }
        }

        log.info("NewMatchingListingNotifyJob hoàn tất: tin mới={}, user hoạt động={}, "
                        + "user được gợi ý={}, thông báo đã gửi={}, lỗi={} (minScore={}, maxPerUser={})",
                newListings.size(), activeUserIds.size(), usersMatched, notificationsSent, errors,
                minScore, maxPerUser);
    }

    /** Xử lý một user; trả về số thông báo đã gửi. */
    private int processUser(Long userId, List<Listing> newListings, Instant activeSince,
                            int maxPerUser, double minScore) {
        Preference pref = buildPreference(userId, activeSince);
        if (pref == null) {
            return 0; // không đủ dữ liệu lịch sử để cá nhân hóa
        }
        Set<Long> alreadyViewed = new HashSet<>(
                viewHistoryRepository.findDistinctListingIdsByUserIdSince(userId, activeSince));

        // Chấm điểm các tin mới đủ điều kiện, giữ tin đạt ngưỡng.
        List<ScoredListing> matches = new ArrayList<>();
        for (Listing listing : newListings) {
            if (userId.equals(listing.getOwnerId())) {
                continue; // tin của chính user
            }
            if (alreadyViewed.contains(listing.getId())) {
                continue; // đã xem → không gợi ý lại
            }
            double score = score(pref, listing);
            if (score >= minScore) {
                matches.add(new ScoredListing(listing, score));
            }
        }
        if (matches.isEmpty()) {
            return 0;
        }
        matches.sort(Comparator.comparingDouble(ScoredListing::score).reversed());

        int sent = 0;
        for (ScoredListing sl : matches) {
            if (sent >= maxPerUser) {
                break;
            }
            String link = appProperties.getFrontend().getListingDetailPath() + "/" + sl.listing().getId();
            // Chống gửi trùng: đã từng gửi tin này cho user chưa.
            if (notificationRepository.existsByUserIdAndTypeAndLinkAndDeletedAtIsNull(
                    userId, NotificationType.NEW_MATCHING_LISTING, link)) {
                continue;
            }
            notificationService.notifyUser(userId, NotificationType.NEW_MATCHING_LISTING,
                    NotificationType.NEW_MATCHING_LISTING.getLabel(),
                    "Có tin mới phù hợp với khu vực/khoảng giá bạn hay xem: " + safeTitle(sl.listing()),
                    link, null);
            sent++;
        }
        return sent;
    }

    /**
     * Xây sở thích từ các tin user đã xem gần đây: tập quận, tập tỉnh, khoảng giá [min,max]. Trả về
     * {@code null} nếu không có tin xem nào để suy sở thích.
     */
    private Preference buildPreference(Long userId, Instant activeSince) {
        List<ViewHistory> recent = viewHistoryRepository.findByUserIdAndViewedAtAfterOrderByViewedAtDesc(
                userId, activeSince, PageRequest.of(0, RECENT_VIEW_LIMIT));
        if (recent.isEmpty()) {
            return null;
        }
        List<Long> listingIds = recent.stream().map(ViewHistory::getListingId).distinct().toList();
        List<Listing> viewed = listingRepository.findAllById(listingIds);
        if (viewed.isEmpty()) {
            return null;
        }
        Set<Long> districts = new HashSet<>();
        Set<Long> provinces = new HashSet<>();
        double priceMin = Double.MAX_VALUE;
        double priceMax = Double.MIN_VALUE;
        boolean hasPrice = false;
        for (Listing l : viewed) {
            if (l.getDistrictId() != null) {
                districts.add(l.getDistrictId());
            }
            if (l.getProvinceId() != null) {
                provinces.add(l.getProvinceId());
            }
            BigDecimal p = l.getPrice();
            if (p != null) {
                double pv = p.doubleValue();
                priceMin = Math.min(priceMin, pv);
                priceMax = Math.max(priceMax, pv);
                hasPrice = true;
            }
        }
        if (districts.isEmpty() && provinces.isEmpty()) {
            return null;
        }
        return new Preference(districts, provinces, hasPrice ? priceMin : 0, hasPrice ? priceMax : 0, hasPrice);
    }

    /** Điểm khớp ∈ [0,1] = W_LOCATION·khớpKhuVực + W_PRICE·khớpGiá. */
    private double score(Preference pref, Listing listing) {
        double locationScore;
        if (listing.getDistrictId() != null && pref.districtIds().contains(listing.getDistrictId())) {
            locationScore = 1.0;
        } else if (listing.getProvinceId() != null && pref.provinceIds().contains(listing.getProvinceId())) {
            locationScore = 0.5;
        } else {
            locationScore = 0.0;
        }

        double priceScore;
        if (!pref.hasPrice() || listing.getPrice() == null) {
            priceScore = 0.5; // thiếu dữ liệu giá → trung tính
        } else {
            double pv = listing.getPrice().doubleValue();
            priceScore = (pv >= pref.priceMin() * PRICE_LOWER && pv <= pref.priceMax() * PRICE_UPPER) ? 1.0 : 0.0;
        }
        return W_LOCATION * locationScore + W_PRICE * priceScore;
    }

    private String safeTitle(Listing listing) {
        String title = listing.getTitle();
        return title == null ? ("#" + listing.getId()) : title;
    }

    /** Sở thích suy ra từ lịch sử xem. */
    private record Preference(Set<Long> districtIds, Set<Long> provinceIds,
                              double priceMin, double priceMax, boolean hasPrice) {
    }

    /** Tin kèm điểm khớp để xếp hạng. */
    private record ScoredListing(Listing listing, double score) {
    }
}
