package com.webtro.modules.admin.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.PaymentStatus;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.common.enums.UserStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.dto.response.DashboardResponse;
import com.webtro.modules.admin.dto.response.RevenueStatisticsResponse;
import com.webtro.modules.admin.dto.response.StatisticsResponse;
import com.webtro.modules.admin.repository.AdminPaymentMetricsRepository;
import com.webtro.modules.admin.repository.AdminUserRoleMetricsRepository;
import com.webtro.modules.admin.service.AdminDashboardService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.payment.entity.PromotionPackage;
import com.webtro.modules.payment.repository.PromotionPackageRepository;
import com.webtro.modules.ai.entity.SentimentResult;
import com.webtro.modules.ai.repository.SentimentResultRepository;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.moderation.entity.Report;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cài đặt {@link AdminDashboardService}. Tổng hợp số liệu bằng {@code count(Specification)} trên các
 * repository chỉ-đọc (User/Listing/Report) và các query tổng hợp admin-owned (Payment/UserRole).
 * Top khu vực/danh mục dùng bộ đếm denormalized {@code listing_count} trên bảng tra cứu.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private static final int MAX_SERIES_DAYS = 92;

    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final ReportRepository reportRepository;
    private final AdminPaymentMetricsRepository paymentMetricsRepository;
    private final PromotionPackageRepository promotionPackageRepository;
    private final AdminUserRoleMetricsRepository userRoleMetricsRepository;
    private final ProvinceRepository provinceRepository;
    private final CategoryRepository categoryRepository;
    private final SentimentResultRepository sentimentResultRepository;
    private final SystemConfigService systemConfigService;

    // ============================ Dashboard ============================

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant now = Instant.now();
        Instant todayStart = startOf(today);
        Instant weekStart = startOf(today.minusDays(6));
        Instant monthStart = startOf(today.withDayOfMonth(1));
        Instant lastMonthStart = startOf(today.withDayOfMonth(1).minusMonths(1));

        return DashboardResponse.builder()
                .users(buildUsers(todayStart, weekStart, monthStart))
                .listings(buildListings(todayStart, weekStart, monthStart))
                .reports(buildReports(monthStart))
                .revenue(buildRevenue(todayStart, weekStart, monthStart, lastMonthStart, now))
                .payments(buildPayments())
                .aiAlerts(buildAiAlerts())
                .topProvinces(buildTopProvinces())
                .topCategories(buildTopCategories())
                .generatedAt(now)
                .build();
    }

    private DashboardResponse.Users buildUsers(Instant todayStart, Instant weekStart, Instant monthStart) {
        return DashboardResponse.Users.builder()
                .total(userRepository.count(userAlive()))
                .landlords(userRoleMetricsRepository.countUsersByRole(RoleCode.LANDLORD))
                .tenants(userRoleMetricsRepository.countUsersByRole(RoleCode.TENANT))
                .moderators(userRoleMetricsRepository.countUsersByRole(RoleCode.MODERATOR))
                .admins(userRoleMetricsRepository.countUsersByRole(RoleCode.ADMIN))
                .newToday(userRepository.count(userAlive().and(userCreatedFrom(todayStart))))
                .newThisWeek(userRepository.count(userAlive().and(userCreatedFrom(weekStart))))
                .newThisMonth(userRepository.count(userAlive().and(userCreatedFrom(monthStart))))
                .activeCount(userRepository.count(userAlive().and(userStatus(UserStatus.ACTIVE))))
                .pendingVerifyCount(userRepository.count(userAlive().and(userStatus(UserStatus.PENDING_VERIFY))))
                .lockedCount(userRepository.count(userAlive().and(userStatus(UserStatus.LOCKED))))
                .build();
    }

    private DashboardResponse.Listings buildListings(Instant todayStart, Instant weekStart, Instant monthStart) {
        return DashboardResponse.Listings.builder()
                .active(listingRepository.count(listingStatus(ListingStatus.ACTIVE)))
                .pending(listingRepository.count(listingStatus(ListingStatus.PENDING)))
                .expired(listingRepository.count(listingStatus(ListingStatus.EXPIRED)))
                .locked(listingRepository.count(listingStatus(ListingStatus.LOCKED)))
                .draft(listingRepository.count(listingStatus(ListingStatus.DRAFT)))
                .rejected(listingRepository.count(listingStatus(ListingStatus.REJECTED)))
                .hidden(listingRepository.count(listingStatus(ListingStatus.HIDDEN)))
                .closed(listingRepository.count(listingStatus(ListingStatus.CLOSED)))
                .needReview(listingRepository.count(listingStatus(ListingStatus.NEED_REVIEW)))
                .newToday(listingRepository.count(listingAlive().and(listingCreatedFrom(todayStart))))
                .newThisWeek(listingRepository.count(listingAlive().and(listingCreatedFrom(weekStart))))
                .newThisMonth(listingRepository.count(listingAlive().and(listingCreatedFrom(monthStart))))
                .build();
    }

    private DashboardResponse.Reports buildReports(Instant monthStart) {
        return DashboardResponse.Reports.builder()
                .pending(reportRepository.count(reportStatus(ReportStatus.PENDING)))
                .reviewing(reportRepository.count(reportStatus(ReportStatus.REVIEWING)))
                .resolvedThisMonth(reportRepository.count(
                        reportStatus(ReportStatus.RESOLVED).and(reportResolvedFrom(monthStart))))
                .rejectedThisMonth(reportRepository.count(
                        reportStatus(ReportStatus.REJECTED).and(reportResolvedFrom(monthStart))))
                .criticalPending(reportRepository.count(
                        reportStatus(ReportStatus.PENDING).and(reportSeverity(ReportSeverity.CRITICAL))))
                .build();
    }

    private DashboardResponse.Revenue buildRevenue(Instant todayStart, Instant weekStart,
                                                   Instant monthStart, Instant lastMonthStart, Instant now) {
        BigDecimal thisMonth = paymentMetricsRepository.sumRevenueBetween(monthStart, now);
        BigDecimal lastMonth = paymentMetricsRepository.sumRevenueBetween(lastMonthStart, monthStart);
        BigDecimal growth = BigDecimal.ZERO;
        if (lastMonth != null && lastMonth.signum() > 0) {
            growth = thisMonth.subtract(lastMonth)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(lastMonth, 2, RoundingMode.HALF_UP);
        }
        return DashboardResponse.Revenue.builder()
                .today(paymentMetricsRepository.sumRevenueBetween(todayStart, now))
                .thisWeek(paymentMetricsRepository.sumRevenueBetween(weekStart, now))
                .thisMonth(thisMonth)
                .lastMonth(lastMonth)
                .growthPercent(growth)
                .allTime(paymentMetricsRepository.sumRevenueAllTime())
                .build();
    }

    private DashboardResponse.Payments buildPayments() {
        long success = paymentMetricsRepository.countByStatus(PaymentStatus.SUCCESS);
        long failed = paymentMetricsRepository.countByStatus(PaymentStatus.FAILED);
        long pending = paymentMetricsRepository.countByStatus(PaymentStatus.PENDING);
        long refunded = paymentMetricsRepository.countByStatus(PaymentStatus.REFUNDED);
        long attempted = success + failed;
        BigDecimal successRate = percent(success, attempted);
        BigDecimal failureRate = percent(failed, attempted);
        return DashboardResponse.Payments.builder()
                .successCount(success).failedCount(failed).pendingCount(pending).refundedCount(refunded)
                .successRatePercent(successRate).failureRatePercent(failureRate)
                .build();
    }

    private DashboardResponse.AiAlerts buildAiAlerts() {
        long flaggedBySentiment = listingRepository.count(listingStatus(ListingStatus.NEED_REVIEW));
        long priceDeviation = listingRepository.count(listingAlive().and(listingPriceDeviation()));
        long pendingSentiment = sentimentResultRepository.count(sentimentPending());
        return DashboardResponse.AiAlerts.builder()
                .listingsFlaggedBySentiment(flaggedBySentiment)
                .listingsWithPriceDeviation(priceDeviation)
                .pendingSentimentAnalysis(pendingSentiment)
                .sentimentModuleEnabled(systemConfigService.getBoolean(ConfigKey.AI_SENTIMENT_ENABLED))
                .recommendationModuleEnabled(systemConfigService.getBoolean(ConfigKey.AI_RECOMMENDATION_ENABLED))
                .chatbotModuleEnabled(systemConfigService.getBoolean(ConfigKey.AI_CHATBOT_ENABLED))
                .priceModuleEnabled(systemConfigService.getBoolean(ConfigKey.AI_PRICE_ENABLED))
                .build();
    }

    private List<DashboardResponse.TopProvince> buildTopProvinces() {
        return provinceRepository.findAll().stream()
                .sorted(Comparator.comparingInt((Province p) -> nz(p.getListingCount())).reversed())
                .limit(5)
                .map(p -> DashboardResponse.TopProvince.builder()
                        .provinceId(p.getId()).name(p.getName())
                        .listingCount(nz(p.getListingCount())).build())
                .toList();
    }

    private List<DashboardResponse.TopCategory> buildTopCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingInt((Category c) -> nz(c.getListingCount())).reversed())
                .limit(5)
                .map(c -> DashboardResponse.TopCategory.builder()
                        .categoryId(c.getId())
                        .code(c.getCode() != null ? c.getCode().name() : null)
                        .name(c.getName())
                        .listingCount(nz(c.getListingCount())).build())
                .toList();
    }

    // ============================ Thống kê ============================

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics(LocalDate from, LocalDate to, String granularity) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (effectiveFrom.plusDays(365).isBefore(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Khoảng thống kê tối đa 365 ngày");
        }

        Instant rangeStart = startOf(effectiveFrom);
        Instant rangeEnd = startOf(effectiveTo.plusDays(1));

        long newUsers = userRepository.count(userAlive().and(userCreatedBetween(rangeStart, rangeEnd)));
        long newListings = listingRepository.count(listingAlive().and(listingCreatedBetween(rangeStart, rangeEnd)));
        long approved = listingRepository.count(listingPublishedBetween(rangeStart, rangeEnd));
        long rejected = listingRepository.count(
                listingStatus(ListingStatus.REJECTED).and(listingUpdatedBetween(rangeStart, rangeEnd)));
        long closed = listingRepository.count(
                listingStatus(ListingStatus.CLOSED).and(listingUpdatedBetween(rangeStart, rangeEnd)));
        long newReports = reportRepository.count(reportCreatedBetween(rangeStart, rangeEnd));
        BigDecimal revenue = paymentMetricsRepository.sumRevenueBetween(rangeStart, rangeEnd);

        StatisticsResponse.Totals totals = StatisticsResponse.Totals.builder()
                .newUsers(newUsers).newListings(newListings).approvedListings(approved)
                .rejectedListings(rejected).closedListings(closed).revenue(revenue).newReports(newReports)
                .build();

        long decided = approved + rejected;
        StatisticsResponse.Rates rates = StatisticsResponse.Rates.builder()
                .approvalRatePercent(percent(approved, decided))
                .rejectionRatePercent(percent(rejected, decided))
                .successfulRentalRatePercent(percent(closed, newListings))
                .build();

        return StatisticsResponse.builder()
                .from(effectiveFrom).to(effectiveTo)
                .granularity(granularity != null ? granularity : "DAY")
                .series(buildSeries(effectiveFrom, effectiveTo))
                .totals(totals).rates(rates)
                .build();
    }

    /** Chuỗi theo ngày; chỉ dựng khi khoảng ≤ 92 ngày để tránh bùng nổ số query. */
    private List<StatisticsResponse.DailyPoint> buildSeries(LocalDate from, LocalDate to) {
        List<StatisticsResponse.DailyPoint> series = new ArrayList<>();
        if (from.plusDays(MAX_SERIES_DAYS).isBefore(to)) {
            return series; // khoảng quá dài → chỉ trả tổng, không trả chuỗi ngày
        }
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Instant dayStart = startOf(d);
            Instant dayEnd = startOf(d.plusDays(1));
            series.add(StatisticsResponse.DailyPoint.builder()
                    .date(d)
                    .newUsers(userRepository.count(userAlive().and(userCreatedBetween(dayStart, dayEnd))))
                    .newListings(listingRepository.count(listingAlive().and(listingCreatedBetween(dayStart, dayEnd))))
                    .approvedListings(listingRepository.count(listingPublishedBetween(dayStart, dayEnd)))
                    .rejectedListings(listingRepository.count(
                            listingStatus(ListingStatus.REJECTED).and(listingUpdatedBetween(dayStart, dayEnd))))
                    .closedListings(listingRepository.count(
                            listingStatus(ListingStatus.CLOSED).and(listingUpdatedBetween(dayStart, dayEnd))))
                    .revenue(paymentMetricsRepository.sumRevenueBetween(dayStart, dayEnd))
                    .newReports(reportRepository.count(reportCreatedBetween(dayStart, dayEnd)))
                    .build());
        }
        return series;
    }

    // ============================ Thống kê doanh thu ============================

    @Override
    @Transactional(readOnly = true)
    public RevenueStatisticsResponse getRevenueStatistics(LocalDate from, LocalDate to, String granularity) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveTo = to != null ? to : today;
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(30);
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Ngày bắt đầu phải trước ngày kết thúc");
        }
        if (effectiveFrom.plusDays(366).isBefore(effectiveTo)) {
            throw new BusinessException(ErrorCode.STATISTIC_RANGE_INVALID,
                    "Khoảng thống kê doanh thu tối đa 366 ngày");
        }
        boolean monthly = "MONTH".equalsIgnoreCase(granularity);
        String normalizedGranularity = monthly ? "MONTH" : "DAY";

        Instant rangeStart = startOf(effectiveFrom);
        Instant rangeEnd = startOf(effectiveTo.plusDays(1));

        BigDecimal totalRevenue = nzMoney(paymentMetricsRepository.sumRevenueBetween(rangeStart, rangeEnd));
        long transactionCount = paymentMetricsRepository.countSuccessBetween(rangeStart, rangeEnd);

        List<RevenueStatisticsResponse.RevenuePoint> series = monthly
                ? buildMonthlyRevenueSeries(effectiveFrom, effectiveTo)
                : buildDailyRevenueSeries(effectiveFrom, effectiveTo);

        return RevenueStatisticsResponse.builder()
                .from(effectiveFrom)
                .to(effectiveTo)
                .granularity(normalizedGranularity)
                .totalRevenue(totalRevenue)
                .transactionCount(transactionCount)
                .series(series)
                .byPackage(buildRevenueByPackage(rangeStart, rangeEnd))
                .build();
    }

    private List<RevenueStatisticsResponse.RevenuePoint> buildDailyRevenueSeries(LocalDate from, LocalDate to) {
        List<RevenueStatisticsResponse.RevenuePoint> series = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Instant dayStart = startOf(d);
            Instant dayEnd = startOf(d.plusDays(1));
            series.add(RevenueStatisticsResponse.RevenuePoint.builder()
                    .bucket(d.toString())
                    .revenue(nzMoney(paymentMetricsRepository.sumRevenueBetween(dayStart, dayEnd)))
                    .transactionCount(paymentMetricsRepository.countSuccessBetween(dayStart, dayEnd))
                    .build());
        }
        return series;
    }

    private List<RevenueStatisticsResponse.RevenuePoint> buildMonthlyRevenueSeries(LocalDate from, LocalDate to) {
        List<RevenueStatisticsResponse.RevenuePoint> series = new ArrayList<>();
        YearMonth start = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        for (YearMonth ym = start; !ym.isAfter(end); ym = ym.plusMonths(1)) {
            Instant monthStart = startOf(ym.atDay(1));
            Instant monthEnd = startOf(ym.plusMonths(1).atDay(1));
            series.add(RevenueStatisticsResponse.RevenuePoint.builder()
                    .bucket(ym.toString())
                    .revenue(nzMoney(paymentMetricsRepository.sumRevenueBetween(monthStart, monthEnd)))
                    .transactionCount(paymentMetricsRepository.countSuccessBetween(monthStart, monthEnd))
                    .build());
        }
        return series;
    }

    private List<RevenueStatisticsResponse.PackageRevenue> buildRevenueByPackage(Instant from, Instant to) {
        List<Object[]> rows = paymentMetricsRepository.sumRevenueByPackageBetween(from, to);
        // Nạp tên/mã gói một lần để tránh N+1.
        Set<Long> packageIds = new LinkedHashSet<>();
        for (Object[] row : rows) {
            if (row[0] != null) {
                packageIds.add(((Number) row[0]).longValue());
            }
        }
        Map<Long, PromotionPackage> packages = new HashMap<>();
        if (!packageIds.isEmpty()) {
            for (PromotionPackage pkg : promotionPackageRepository.findAllById(packageIds)) {
                packages.put(pkg.getId(), pkg);
            }
        }
        List<RevenueStatisticsResponse.PackageRevenue> result = new ArrayList<>();
        for (Object[] row : rows) {
            Long packageId = row[0] != null ? ((Number) row[0]).longValue() : null;
            PromotionPackage pkg = packageId != null ? packages.get(packageId) : null;
            result.add(RevenueStatisticsResponse.PackageRevenue.builder()
                    .packageId(packageId)
                    .packageCode(pkg != null ? pkg.getCode() : null)
                    .packageName(pkg != null ? pkg.getName() : (packageId == null ? "Không gắn gói" : null))
                    .revenue(nzMoney((BigDecimal) row[1]))
                    .transactionCount(((Number) row[2]).longValue())
                    .build());
        }
        // revenue luôn khác null (đã qua nzMoney) nên sắp giảm dần an toàn.
        result.sort(Comparator.comparing(RevenueStatisticsResponse.PackageRevenue::getRevenue).reversed());
        return result;
    }

    private BigDecimal nzMoney(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    // ============================ Specification helpers ============================

    private Specification<User> userAlive() {
        return (r, q, cb) -> cb.isNull(r.get("deletedAt"));
    }

    private Specification<User> userStatus(UserStatus s) {
        return (r, q, cb) -> cb.equal(r.get("status"), s);
    }

    private Specification<User> userCreatedFrom(Instant from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from);
    }

    private Specification<User> userCreatedBetween(Instant from, Instant to) {
        return (r, q, cb) -> cb.and(
                cb.greaterThanOrEqualTo(r.get("createdAt"), from),
                cb.lessThan(r.get("createdAt"), to));
    }

    private Specification<Listing> listingAlive() {
        return (r, q, cb) -> cb.isNull(r.get("deletedAt"));
    }

    private Specification<Listing> listingStatus(ListingStatus s) {
        return (r, q, cb) -> cb.equal(r.get("status"), s);
    }

    private Specification<Listing> listingPriceDeviation() {
        return (r, q, cb) -> cb.isTrue(r.get("priceDeviationFlag"));
    }

    private Specification<Listing> listingCreatedFrom(Instant from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("createdAt"), from);
    }

    private Specification<Listing> listingCreatedBetween(Instant from, Instant to) {
        return (r, q, cb) -> cb.and(
                cb.greaterThanOrEqualTo(r.get("createdAt"), from),
                cb.lessThan(r.get("createdAt"), to));
    }

    private Specification<Listing> listingPublishedBetween(Instant from, Instant to) {
        return (r, q, cb) -> cb.and(
                cb.isNotNull(r.get("publishedAt")),
                cb.greaterThanOrEqualTo(r.get("publishedAt"), from),
                cb.lessThan(r.get("publishedAt"), to));
    }

    private Specification<Listing> listingUpdatedBetween(Instant from, Instant to) {
        return (r, q, cb) -> cb.and(
                cb.greaterThanOrEqualTo(r.get("updatedAt"), from),
                cb.lessThan(r.get("updatedAt"), to));
    }

    private Specification<Report> reportStatus(ReportStatus s) {
        return (r, q, cb) -> cb.and(cb.equal(r.get("status"), s), cb.isNull(r.get("deletedAt")));
    }

    private Specification<Report> reportSeverity(ReportSeverity s) {
        return (r, q, cb) -> cb.equal(r.get("severity"), s);
    }

    private Specification<Report> reportResolvedFrom(Instant from) {
        return (r, q, cb) -> cb.greaterThanOrEqualTo(r.get("resolvedAt"), from);
    }

    private Specification<Report> reportCreatedBetween(Instant from, Instant to) {
        return (r, q, cb) -> cb.and(
                cb.isNull(r.get("deletedAt")),
                cb.greaterThanOrEqualTo(r.get("createdAt"), from),
                cb.lessThan(r.get("createdAt"), to));
    }

    private Specification<SentimentResult> sentimentPending() {
        return (r, q, cb) -> cb.and(
                cb.equal(r.get("label"), SentimentLabel.PENDING_ANALYSIS),
                cb.isTrue(r.get("isLatest")));
    }

    // ============================ Tiện ích ============================

    private Instant startOf(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private int nz(Integer v) {
        return v != null ? v : 0;
    }

    private BigDecimal percent(long part, long whole) {
        if (whole <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(whole), 2, RoundingMode.HALF_UP);
    }
}
