package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.AuditActorType;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.dto.response.AdminReviewResponse;
import com.webtro.modules.interaction.dto.response.AuthorResponse;
import com.webtro.modules.interaction.dto.response.ReviewEligibilityResponse;
import com.webtro.modules.interaction.dto.response.ReviewPageResponse;
import com.webtro.modules.interaction.dto.response.ReviewResponse;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.mapper.ReviewMapper;
import com.webtro.modules.interaction.repository.ContactLogRepository;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.service.ReviewService;
import com.webtro.modules.interaction.specification.ReviewSpecifications;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.listing.service.TrustScoreService;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import com.webtro.security.SecurityUtils;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực nghiệp vụ đánh giá (REV) — canonical 4.7.6–4.7.12, {@code [§3.12]}.
 *
 * <p>Điểm trung bình của tin/chủ trọ được cập nhật <i>tăng dần</i> ngay trong transaction (dùng
 * số liệu denormalized đọc qua gateway); {@code TrustScoreRecalcJob} 02:00 và nightly reconcile
 * bù sai số làm tròn (canonical 4.7.8 quy tắc 5, 6).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int REVIEW_MAX_PER_DAY = 10;
    private static final int SUMMARY_SCAN_CAP = 1000;
    private static final int CONTENT_REQUIRED_BELOW = 3;

    private final ReviewRepository reviewRepository;
    private final ContactLogRepository contactLogRepository;
    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final ReviewMapper reviewMapper;
    private final NotificationService notificationService;
    private final TrustScoreService trustScoreService;
    private final RateLimitService rateLimitService;
    private final SystemConfigService systemConfig;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse listListingReviews(Long listingId, Integer rating, Pageable pageable) {
        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        Page<Review> page = reviewRepository
                .findByListingIdAndStatusAndDeletedAtIsNull(listingId, ReviewStatus.VISIBLE, pageable);
        PageResponse<ReviewResponse> mapped = mapReviewPage(page, rating, false);

        return ReviewPageResponse.builder()
                .page(mapped)
                .summary(ReviewPageResponse.ReviewSummary.builder()
                        .averageRating(listing.averageRating())
                        .totalReviews(listing.reviewCount())
                        .distribution(distributionForListing(listingId))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse listLandlordReviews(Long landlordId, Integer rating, Pageable pageable) {
        UserGateway.LandlordAggregate agg = userGateway.getLandlordAggregate(landlordId);
        Page<Review> page = reviewRepository
                .findByLandlordIdAndStatusAndDeletedAtIsNull(landlordId, ReviewStatus.VISIBLE, pageable);
        PageResponse<ReviewResponse> mapped = mapReviewPage(page, rating, true);

        return ReviewPageResponse.builder()
                .page(mapped)
                .summary(ReviewPageResponse.ReviewSummary.builder()
                        .averageRating(agg.averageRating())
                        .totalReviews(agg.reviewCount())
                        .distribution(distributionForLandlord(landlordId))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse checkEligibility(Long listingId, Long userId) {
        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        boolean contactRequired = systemConfig.getBoolean(ConfigKey.REVIEW_REQUIRE_CONTACT);
        Review existing = reviewRepository.findByUserIdAndListingIdAndDeletedAtIsNull(userId, listingId).orElse(null);
        boolean hasContacted = hasEverContacted(listingId, userId);

        String reason = null;
        boolean eligible = true;
        if (listing.ownerId().equals(userId)) {
            eligible = false;
            reason = "OWN_LISTING";
        } else if (existing != null) {
            eligible = false;
            reason = "ALREADY_REVIEWED";
        } else if (contactRequired && !hasContacted) {
            eligible = false;
            reason = "NOT_CONTACTED";
        }

        return ReviewEligibilityResponse.builder()
                .listingId(listingId)
                .eligible(eligible)
                .reason(reason)
                .reasonMessage(reasonMessage(reason))
                .alreadyReviewed(existing != null)
                .existingReviewId(existing == null ? null : existing.getId())
                .contactRequired(contactRequired)
                .hasContacted(hasContacted)
                .contentRequiredWhenRatingBelow(CONTENT_REQUIRED_BELOW)
                .build();
    }

    @Override
    @Transactional
    public ReviewResponse createReview(Long listingId, CreateReviewRequest request, Long userId) {
        rateLimitService.checkLimit("review", String.valueOf(userId), REVIEW_MAX_PER_DAY, Duration.ofDays(1));

        ListingGateway.ListingBrief listing = listingGateway.getBrief(listingId);
        if (!listing.publiclyVisible()) {
            throw new BusinessRuleException(ErrorCode.LISTING_NOT_ACTIVE);
        }
        if (listing.ownerId().equals(userId)) {
            throw new BusinessRuleException(ErrorCode.REVIEW_SELF_FORBIDDEN);
        }
        if (reviewRepository.existsByUserIdAndListingIdAndDeletedAtIsNull(userId, listingId)) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        boolean hasContacted = hasEverContacted(listingId, userId);
        if (systemConfig.getBoolean(ConfigKey.REVIEW_REQUIRE_CONTACT) && !hasContacted) {
            throw new BusinessRuleException(ErrorCode.REVIEW_NOT_ELIGIBLE);
        }
        String content = validateContent(request.getRating(), request.getContent());

        Review review = Review.builder()
                .listingId(listingId)
                .userId(userId)
                .rating(request.getRating())
                .content(content)
                .status(ReviewStatus.VISIBLE)
                .isVerifiedContact(hasContacted)
                .build();
        review = reviewRepository.save(review);

        // Cập nhật trung bình tin + chủ trọ.
        BigDecimal listingAvg = applyListingAggregateOnAdd(listing, request.getRating());
        BigDecimal landlordAvg = applyLandlordAggregateOnAdd(listing.ownerId(), request.getRating());
        int listingCount = (int) reviewRepository
                .countByListingIdAndStatusAndDeletedAtIsNull(listingId, ReviewStatus.VISIBLE);

        recalcTrust(listingId, listing.ownerId());

        if (!userId.equals(listing.ownerId())) {
            notificationService.notifyUser(listing.ownerId(), NotificationType.NEW_REVIEW,
                    "Đánh giá mới", "Tin \"" + listing.title() + "\" vừa nhận đánh giá " + request.getRating() + " sao.");
        }

        UserGateway.UserBrief author = userGateway.findBrief(userId).orElse(null);
        ReviewResponse resp = single(review, author, userId);
        resp.setListingAverageRating(listingAvg);
        resp.setListingReviewCount(listingCount);
        resp.setLandlordAverageRating(landlordAvg);
        return resp;
    }

    @Override
    @Transactional
    public ReviewResponse updateReview(Long reviewId, CreateReviewRequest request, Long userId) {
        Review r = aliveById(reviewId);
        if (!r.getUserId().equals(userId)) {
            if (SecurityUtils.hasAnyRole(RoleCode.MODERATOR, RoleCode.ADMIN)) {
                throw new ForbiddenException(ErrorCode.REVIEW_CONTENT_IMMUTABLE_BY_ADMIN);
            }
            throw new ForbiddenException(ErrorCode.REVIEW_FORBIDDEN);
        }
        if (Instant.now().isAfter(reviewEditableUntil(r))) {
            throw new BusinessRuleException(ErrorCode.REVIEW_EDIT_WINDOW_EXPIRED);
        }
        String content = validateContent(request.getRating(), request.getContent());

        int oldRating = r.getRating();
        r.setRating(request.getRating());
        r.setContent(content);
        r.setEditedAt(Instant.now());
        reviewRepository.save(r);

        ListingGateway.ListingBrief listing = listingGateway.getBrief(r.getListingId());
        BigDecimal listingAvg = applyListingAggregateOnChange(listing, oldRating, request.getRating());
        BigDecimal landlordAvg = applyLandlordAggregateOnChange(listing.ownerId(), oldRating, request.getRating());
        int listingCount = (int) reviewRepository
                .countByListingIdAndStatusAndDeletedAtIsNull(r.getListingId(), ReviewStatus.VISIBLE);

        recalcTrust(r.getListingId(), listing.ownerId());

        UserGateway.UserBrief author = userGateway.findBrief(userId).orElse(null);
        ReviewResponse resp = single(r, author, userId);
        resp.setListingAverageRating(listingAvg);
        resp.setListingReviewCount(listingCount);
        resp.setLandlordAverageRating(landlordAvg);
        return resp;
    }

    @Override
    @Transactional
    public void deleteReview(Long reviewId, Long userId, String moderationReason) {
        Review r = aliveById(reviewId);
        boolean author = r.getUserId().equals(userId);
        boolean moderator = SecurityUtils.hasAnyRole(RoleCode.MODERATOR, RoleCode.ADMIN);
        if (!author && !moderator) {
            throw new ForbiddenException(ErrorCode.REVIEW_FORBIDDEN);
        }

        if (!author && moderator) {
            // Kiểm duyệt viên ẩn (không xóa) + bắt buộc lý do — [§10.9], canonical 4.7.10.
            if (moderationReason == null || moderationReason.trim().length() < 10
                    || moderationReason.trim().length() > 255) {
                throw new BusinessException(ErrorCode.MODERATION_REASON_REQUIRED);
            }
            r.setStatus(ReviewStatus.HIDDEN);
            r.setHiddenReason(moderationReason.trim());
            r.setHiddenBy(userId);
            r.setHiddenAt(Instant.now());
            reviewRepository.save(r);
        } else {
            // Tác giả xóa mềm trong cửa sổ.
            if (Instant.now().isAfter(reviewEditableUntil(r))) {
                throw new BusinessRuleException(ErrorCode.REVIEW_EDIT_WINDOW_EXPIRED);
            }
            r.setStatus(ReviewStatus.DELETED);
            r.softDelete();
            reviewRepository.save(r);
        }

        // Sau khi bản ghi rời khỏi tập VISIBLE → tính lại trung bình.
        ListingGateway.ListingBrief listing = listingGateway.getBrief(r.getListingId());
        applyListingAggregateOnRemove(listing, r.getRating());
        applyLandlordAggregateOnRemove(listing.ownerId(), r.getRating());
        recalcTrust(r.getListingId(), listing.ownerId());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listMyReviews(Long userId, Pageable pageable) {
        // GIỚI HẠN: ReviewRepository (đã chốt) KHÔNG có finder theo userId (chỉ có theo
        // (userId, listingId)). Endpoint 4.7.11 cần bổ sung
        // Page<Review> findByUserIdAndDeletedAtIsNull(Long userId, Pageable). Trước khi có method
        // đó, trả trang rỗng để không dựng dữ liệu sai.
        log.warn("GET /api/reviews/my: ReviewRepository thiếu finder findByUserIdAndDeletedAtIsNull "
                + "-> trả rỗng. Cần bổ sung method này ở module listing/interaction repository.");
        return PageResponse.empty(pageable.getPageNumber(), pageable.getPageSize());
    }

    @Override
    @Transactional
    public void hideByModeration(Long reviewId, Long moderatorId, String reason) {
        Review r = aliveById(reviewId);
        boolean wasVisible = r.getStatus() == ReviewStatus.VISIBLE;
        r.setStatus(ReviewStatus.HIDDEN);
        r.setHiddenReason(reason == null ? null
                : (reason.trim().length() <= 500 ? reason.trim() : reason.trim().substring(0, 500)));
        r.setHiddenBy(moderatorId);
        r.setHiddenAt(Instant.now());
        reviewRepository.save(r);

        // Chỉ tính lại trung bình khi đánh giá vừa rời khỏi tập VISIBLE (tránh trừ trùng nếu đã ẩn).
        if (wasVisible) {
            ListingGateway.ListingBrief listing = listingGateway.getBrief(r.getListingId());
            applyListingAggregateOnRemove(listing, r.getRating());
            applyLandlordAggregateOnRemove(listing.ownerId(), r.getRating());
            recalcTrust(r.getListingId(), listing.ownerId());
        }
    }

    // ------------------ Kiểm duyệt (REVIEW_MODERATE) ------------------

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> adminListReviews(ReviewStatus status, Integer rating, Long listingId,
                                                              Long landlordId, String keyword, Pageable pageable) {
        Specification<Review> spec = ReviewSpecifications.notDeleted();
        if (status != null) {
            spec = spec.and(ReviewSpecifications.statusEq(status));
        }
        if (rating != null) {
            spec = spec.and(ReviewSpecifications.ratingEq(rating));
        }
        if (listingId != null) {
            spec = spec.and(ReviewSpecifications.listingIdEq(listingId));
        }
        if (landlordId != null) {
            spec = spec.and(ReviewSpecifications.landlordIdEq(landlordId));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(ReviewSpecifications.contentContains(keyword));
        }

        Page<Review> page = reviewRepository.findAll(spec, pageable);
        List<Long> userIds = page.getContent().stream().map(Review::getUserId).distinct().toList();
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(userIds);
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefs(
                page.getContent().stream().map(Review::getListingId).distinct().toList());

        return PageResponse.from(page, r -> reviewMapper.toAdminResponse(
                r,
                reviewMapper.toAuthor(authors.get(r.getUserId())),
                listings.get(r.getListingId()) == null ? null : listings.get(r.getListingId()).ownerId()));
    }

    @Override
    @Transactional
    public AdminReviewResponse adminHideReview(Long reviewId, Long moderatorId, String reason) {
        Review r = aliveById(reviewId);
        if (r.getStatus() == ReviewStatus.HIDDEN) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_HIDDEN);
        }
        // Tái dùng nghiệp vụ ẩn chuẩn (ghi hidden_* + tính lại trung bình/uy tín khi rời tập VISIBLE).
        hideByModeration(reviewId, moderatorId, reason);

        auditLogService.record(AuditAction.REVIEW_HIDE, AuditActorType.USER, moderatorId,
                "REVIEW", reviewId, reason == null ? null : reason.trim());
        return buildAdmin(aliveById(reviewId));
    }

    @Override
    @Transactional
    public AdminReviewResponse unhideByModeration(Long reviewId, Long moderatorId) {
        Review r = aliveById(reviewId);
        if (r.getStatus() != ReviewStatus.HIDDEN) {
            throw new BusinessRuleException(ErrorCode.REVIEW_INVALID_TRANSITION);
        }
        r.setStatus(ReviewStatus.VISIBLE);
        r.setHiddenReason(null);
        r.setHiddenBy(null);
        r.setHiddenAt(null);
        reviewRepository.save(r);

        // Đánh giá quay lại tập VISIBLE → cộng lại vào trung bình sao của tin/chủ trọ + uy tín.
        ListingGateway.ListingBrief listing = listingGateway.getBrief(r.getListingId());
        applyListingAggregateOnAdd(listing, r.getRating());
        applyLandlordAggregateOnAdd(listing.ownerId(), r.getRating());
        recalcTrust(r.getListingId(), listing.ownerId());

        auditLogService.record(AuditAction.REVIEW_UNHIDE, AuditActorType.USER, moderatorId,
                "REVIEW", reviewId, "Khôi phục đánh giá bị ẩn");
        return buildAdmin(r);
    }

    // ------------------------------------------------------------------

    private AdminReviewResponse buildAdmin(Review r) {
        AuthorResponse author = reviewMapper.toAuthor(userGateway.findBrief(r.getUserId()).orElse(null));
        ListingGateway.ListingBrief listing = listingGateway.getBrief(r.getListingId());
        return reviewMapper.toAdminResponse(r, author, listing.ownerId());
    }

    private Review aliveById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REVIEW_NOT_FOUND));
    }

    private boolean hasEverContacted(Long listingId, Long userId) {
        return contactLogRepository
                .existsByListingIdAndUserIdAndCreatedAtAfterAndDeletedAtIsNull(listingId, userId, Instant.EPOCH);
    }

    private String validateContent(int rating, String content) {
        boolean blank = content == null || content.isBlank();
        if (rating <= CONTENT_REQUIRED_BELOW - 1 && blank) {
            throw new BusinessException(ErrorCode.REVIEW_CONTENT_REQUIRED);
        }
        if (blank) {
            return null;
        }
        BannedKeywordGateway.ScanResult scan = bannedKeywordGateway.scan(content, BannedKeywordScope.COMMENT);
        if (!scan.isClean()) {
            throw new BusinessRuleException(ErrorCode.BANNED_KEYWORD_DETECTED,
                    "Nội dung chứa từ ngữ không được phép: " + String.join(", ", scan.matched()));
        }
        return HtmlSanitizer.stripAllHtml(content);
    }

    private Instant reviewEditableUntil(Review r) {
        int hours = systemConfig.getInt(ConfigKey.REVIEW_EDIT_WINDOW_HOURS);
        return r.getCreatedAt().plus(Duration.ofHours(hours));
    }

    private void recalcTrust(Long listingId, Long landlordId) {
        trustScoreService.recalculateAndSaveListing(listingId);
        trustScoreService.recalculateLandlord(landlordId);
    }

    // ----- Cập nhật trung bình tăng dần -----

    private BigDecimal applyListingAggregateOnAdd(ListingGateway.ListingBrief listing, int rating) {
        int newCount = listing.reviewCount() + 1;
        BigDecimal newSum = sum(listing.averageRating(), listing.reviewCount()).add(BigDecimal.valueOf(rating));
        BigDecimal newAvg = avg(newSum, newCount);
        listingGateway.updateReviewAggregate(listing.id(), newAvg, newCount);
        return newAvg;
    }

    private BigDecimal applyListingAggregateOnChange(ListingGateway.ListingBrief listing, int oldRating, int newRating) {
        int count = listing.reviewCount();
        BigDecimal newSum = sum(listing.averageRating(), count)
                .subtract(BigDecimal.valueOf(oldRating)).add(BigDecimal.valueOf(newRating));
        BigDecimal newAvg = avg(newSum, count);
        listingGateway.updateReviewAggregate(listing.id(), newAvg, count);
        return newAvg;
    }

    private BigDecimal applyListingAggregateOnRemove(ListingGateway.ListingBrief listing, int rating) {
        int newCount = Math.max(0, listing.reviewCount() - 1);
        BigDecimal newSum = sum(listing.averageRating(), listing.reviewCount()).subtract(BigDecimal.valueOf(rating));
        BigDecimal newAvg = avg(newSum, newCount);
        listingGateway.updateReviewAggregate(listing.id(), newAvg, newCount);
        return newAvg;
    }

    private BigDecimal applyLandlordAggregateOnAdd(Long landlordId, int rating) {
        UserGateway.LandlordAggregate agg = userGateway.getLandlordAggregate(landlordId);
        int newCount = agg.reviewCount() + 1;
        BigDecimal newAvg = avg(sum(agg.averageRating(), agg.reviewCount()).add(BigDecimal.valueOf(rating)), newCount);
        userGateway.updateLandlordReviewAggregate(landlordId, newAvg, newCount);
        return newAvg;
    }

    private BigDecimal applyLandlordAggregateOnChange(Long landlordId, int oldRating, int newRating) {
        UserGateway.LandlordAggregate agg = userGateway.getLandlordAggregate(landlordId);
        int count = agg.reviewCount();
        BigDecimal newAvg = avg(sum(agg.averageRating(), count)
                .subtract(BigDecimal.valueOf(oldRating)).add(BigDecimal.valueOf(newRating)), count);
        userGateway.updateLandlordReviewAggregate(landlordId, newAvg, count);
        return newAvg;
    }

    private BigDecimal applyLandlordAggregateOnRemove(Long landlordId, int rating) {
        UserGateway.LandlordAggregate agg = userGateway.getLandlordAggregate(landlordId);
        int newCount = Math.max(0, agg.reviewCount() - 1);
        BigDecimal newAvg = avg(sum(agg.averageRating(), agg.reviewCount()).subtract(BigDecimal.valueOf(rating)), newCount);
        userGateway.updateLandlordReviewAggregate(landlordId, newAvg, newCount);
        return newAvg;
    }

    private BigDecimal sum(BigDecimal average, int count) {
        BigDecimal a = average == null ? BigDecimal.ZERO : average;
        return a.multiply(BigDecimal.valueOf(count));
    }

    private BigDecimal avg(BigDecimal sum, int count) {
        if (count <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    // ----- Ánh xạ + thống kê -----

    private PageResponse<ReviewResponse> mapReviewPage(Page<Review> page, Integer ratingFilter, boolean withListing) {
        List<Long> userIds = page.getContent().stream().map(Review::getUserId).distinct().toList();
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(userIds);
        Map<Long, ListingGateway.ListingBrief> listings = withListing
                ? listingGateway.getBriefs(page.getContent().stream().map(Review::getListingId).distinct().toList())
                : Map.of();

        return PageResponse.from(page, r -> {
            AuthorResponse author = reviewMapper.toAuthor(authors.get(r.getUserId()));
            ReviewResponse resp = reviewMapper.toResponse(r, author, false, null);
            if (withListing) {
                ListingGateway.ListingBrief lb = listings.get(r.getListingId());
                if (lb != null) {
                    resp.setListingTitle(lb.title());
                    resp.setListingThumbnailUrl(lb.thumbnailUrl());
                    resp.setListingStatus(lb.status() == null ? null : lb.status().name());
                }
            }
            return resp;
        });
        // Ghi chú: tham số lọc `rating` không áp ở DB (ReviewRepository không có finder theo rating);
        // FE có thể lọc, hoặc bổ sung finder có @Query. Giữ nguyên trang để không phá phân trang.
    }

    private Map<String, Long> distributionForListing(Long listingId) {
        Page<Review> all = reviewRepository.findByListingIdAndStatusAndDeletedAtIsNull(
                listingId, ReviewStatus.VISIBLE, PageRequest.of(0, SUMMARY_SCAN_CAP));
        return distribution(all.getContent());
    }

    private Map<String, Long> distributionForLandlord(Long landlordId) {
        Page<Review> all = reviewRepository.findByLandlordIdAndStatusAndDeletedAtIsNull(
                landlordId, ReviewStatus.VISIBLE, PageRequest.of(0, SUMMARY_SCAN_CAP));
        return distribution(all.getContent());
    }

    private Map<String, Long> distribution(List<Review> reviews) {
        Map<String, Long> dist = new HashMap<>();
        for (int star = 1; star <= 5; star++) {
            dist.put(String.valueOf(star), 0L);
        }
        for (Review r : reviews) {
            String k = String.valueOf(r.getRating());
            dist.merge(k, 1L, Long::sum);
        }
        return dist;
    }

    private ReviewResponse single(Review r, UserGateway.UserBrief authorBrief, Long viewerId) {
        AuthorResponse author = reviewMapper.toAuthor(authorBrief);
        boolean editable = viewerId != null && viewerId.equals(r.getUserId())
                && r.getStatus() == ReviewStatus.VISIBLE && Instant.now().isBefore(reviewEditableUntil(r));
        return reviewMapper.toResponse(r, author, editable, editable ? reviewEditableUntil(r) : null);
    }

    private String reasonMessage(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "OWN_LISTING" -> "Bạn không thể đánh giá tin đăng của chính mình";
            case "ALREADY_REVIEWED" -> "Bạn đã đánh giá tin đăng này rồi";
            case "NOT_CONTACTED" -> "Bạn cần liên hệ chủ trọ trước khi đánh giá tin này";
            default -> "Bạn chưa đủ điều kiện đánh giá tin này";
        };
    }
}
