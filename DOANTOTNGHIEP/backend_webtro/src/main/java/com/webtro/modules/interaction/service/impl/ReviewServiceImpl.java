package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AuditAction;
import com.webtro.common.enums.AuditActorType;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.dto.response.AdminReviewResponse;
import com.webtro.modules.interaction.dto.response.AuthorResponse;
import com.webtro.modules.interaction.dto.response.ReviewEligibilityResponse;
import com.webtro.modules.interaction.dto.response.ReviewPageResponse;
import com.webtro.modules.interaction.dto.response.ReviewResponse;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.mapper.ReviewMapper;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.service.ReviewRatingRecalculator;
import com.webtro.modules.interaction.service.ReviewService;
import com.webtro.modules.interaction.specification.ReviewSpecifications;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.RentalEligibilityGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.security.RateLimitService;
import com.webtro.security.SecurityUtils;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Hiện thực nghiệp vụ đánh giá (REV) — canonical 4.7.6–4.7.12, {@code [§3.12]}.
 *
 * <p><b>Đánh giá theo Phòng</b> (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 3): đánh giá gắn vào
 * {@code rental_units}, hiện trên mọi tin của phòng (RE-2). Quyền đánh giá: từng thuê
 * ({@code RENTED}/{@code CHECKED_OUT}) qua bất kỳ tin nào của phòng (RE-1). Mỗi người một đánh
 * giá cho mỗi phòng (RE-5). Chủ trọ suy ra từ phòng nên đánh giá trên tin đã đóng/đã xoá mềm
 * vẫn sửa, xoá, ẩn được.
 *
 * <p><b>Điểm</b> chỉ được ghi qua {@link ReviewRatingRecalculator}: khoá chủ trọ + phòng trước khi
 * đổi đánh giá, rồi tính lại từ đầu bằng AVG/COUNT trong cùng transaction (mục 10).
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final int REVIEW_MAX_PER_DAY = 10;
    private static final int CONTENT_REQUIRED_BELOW = 3;

    private final ReviewRepository reviewRepository;
    private final ListingGateway listingGateway;
    private final RentalEligibilityGateway rentalEligibilityGateway;
    private final UserGateway userGateway;
    private final BannedKeywordGateway bannedKeywordGateway;
    private final ReviewMapper reviewMapper;
    private final NotificationService notificationService;
    private final RateLimitService rateLimitService;
    private final AuditLogService auditLogService;
    private final ReviewRatingRecalculator ratingRecalculator;

    // ==================================================================
    //  Đọc công khai
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse listListingReviews(Long listingId, Integer rating, Pageable pageable) {
        Long unitId = listingGateway.rentalUnitIdOf(listingId);
        ListingGateway.RentalUnitBrief unit = listingGateway.getUnitBrief(unitId);
        Page<Review> page = reviewRepository.findByUnit(unitId, ReviewStatus.VISIBLE, rating, pageable);

        return ReviewPageResponse.builder()
                .page(mapReviewPage(page, false))
                .summary(ReviewPageResponse.ReviewSummary.builder()
                        .rentalUnitId(unit.id())
                        .rentalUnitName(unit.name())
                        .averageRating(unit.averageRating())
                        .totalReviews(unit.reviewCount())
                        .distribution(distribution(
                                reviewRepository.ratingDistributionByUnit(unitId, ReviewStatus.VISIBLE)))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewPageResponse listLandlordReviews(Long landlordId, Integer rating, Pageable pageable) {
        UserGateway.LandlordAggregate agg = userGateway.getLandlordAggregate(landlordId);
        Page<Review> page = reviewRepository.findByLandlord(landlordId, ReviewStatus.VISIBLE, rating, pageable);

        return ReviewPageResponse.builder()
                .page(mapReviewPage(page, true))
                .summary(ReviewPageResponse.ReviewSummary.builder()
                        .averageRating(agg.averageRating())
                        .totalReviews(agg.reviewCount())
                        .distribution(distribution(
                                reviewRepository.ratingDistributionByLandlord(landlordId, ReviewStatus.VISIBLE)))
                        .build())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse checkEligibility(Long listingId, Long userId) {
        Long unitId = listingGateway.rentalUnitIdOf(listingId);
        ListingGateway.RentalUnitBrief unit = listingGateway.getUnitBrief(unitId);
        Review existing = reviewRepository.findByUserIdAndRentalUnitIdAndDeletedAtIsNull(userId, unitId).orElse(null);
        boolean rentalVerified = rentalEligibilityGateway.findRentedListingInUnit(unitId, userId).isPresent();

        String reason = null;
        boolean eligible = true;
        if (unit.ownerId().equals(userId)) {
            eligible = false;
            reason = "OWN_LISTING";
        } else if (existing != null) {
            eligible = false;
            reason = "ALREADY_REVIEWED";
        } else if (!rentalVerified) {
            eligible = false;
            reason = "NOT_RENTER";
        }

        return ReviewEligibilityResponse.builder()
                .listingId(listingId)
                .rentalUnitId(unitId)
                .rentalUnitName(unit.name())
                .eligible(eligible)
                .reason(reason)
                .reasonMessage(reasonMessage(reason))
                .alreadyReviewed(existing != null)
                .existingReviewId(existing == null ? null : existing.getId())
                .contactRequired(false)
                .hasContacted(rentalVerified)
                .rentalVerified(rentalVerified)
                .contentRequiredWhenRatingBelow(CONTENT_REQUIRED_BELOW)
                .build();
    }

    // ==================================================================
    //  Ghi (người thuê)
    // ==================================================================

    @Override
    @Transactional
    public ReviewResponse createReview(Long listingId, CreateReviewRequest request, Long userId) {
        rateLimitService.checkLimit("review", String.valueOf(userId), REVIEW_MAX_PER_DAY, Duration.ofDays(1));

        Long unitId = listingGateway.rentalUnitIdOf(listingId);
        ListingGateway.RentalUnitBrief unit = listingGateway.getUnitBrief(unitId);
        if (unit.ownerId().equals(userId)) {
            throw new BusinessRuleException(ErrorCode.REVIEW_SELF_FORBIDDEN);
        }
        String content = validateContent(request.getRating(), request.getContent());

        ReviewRatingRecalculator.Scope scope = ratingRecalculator.lock(unitId);
        if (reviewRepository.existsByUserIdAndRentalUnitIdAndDeletedAtIsNull(userId, unitId)) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }
        // Ghi lại tin mà người này đã thuê qua (có thể là tin cũ của phòng, không phải tin đang xem).
        Long rentedViaListingId = rentalEligibilityGateway.findRentedListingInUnit(unitId, userId)
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.REVIEW_RENTAL_REQUIRED));

        Review review = Review.builder()
                .rentalUnitId(unitId)
                .listingId(rentedViaListingId)
                .userId(userId)
                .rating(request.getRating())
                .content(content)
                .status(ReviewStatus.VISIBLE)
                .isVerifiedContact(true)
                .activeMarker((byte) 1)
                .build();
        try {
            review = reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_EXISTS);
        }

        ReviewRatingRecalculator.Result rating = ratingRecalculator.recalc(scope);

        notificationService.notifyUser(unit.ownerId(), NotificationType.NEW_REVIEW,
                "Đánh giá mới", "Phòng \"" + unit.name() + "\" vừa nhận đánh giá " + request.getRating() + " sao.");

        return withRating(single(review, userGateway.findBrief(userId).orElse(null), userId, unit), rating);
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
        if (r.getStatus() != ReviewStatus.VISIBLE) {
            throw new BusinessRuleException(ErrorCode.REVIEW_INVALID_TRANSITION);
        }
        String content = validateContent(request.getRating(), request.getContent());

        ReviewRatingRecalculator.Scope scope = ratingRecalculator.lock(r.getRentalUnitId());
        r.setRating(request.getRating());
        r.setContent(content);
        r.setEditedAt(Instant.now());
        reviewRepository.saveAndFlush(r);
        ReviewRatingRecalculator.Result rating = ratingRecalculator.recalc(scope);

        ListingGateway.RentalUnitBrief unit = listingGateway.getUnitBrief(r.getRentalUnitId());
        return withRating(single(r, userGateway.findBrief(userId).orElse(null), userId, unit), rating);
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
        if (!author && (moderationReason == null || moderationReason.trim().length() < 10
                || moderationReason.trim().length() > 255)) {
            // Kiểm duyệt viên ẩn (không xóa) + bắt buộc lý do — [§10.9], canonical 4.7.10.
            throw new BusinessException(ErrorCode.MODERATION_REASON_REQUIRED);
        }

        ReviewRatingRecalculator.Scope scope = ratingRecalculator.lock(r.getRentalUnitId());
        boolean wasVisible = r.getStatus() == ReviewStatus.VISIBLE;
        if (!author) {
            r.setStatus(ReviewStatus.HIDDEN);
            r.setHiddenReason(moderationReason.trim());
            r.setHiddenBy(userId);
            r.setHiddenAt(Instant.now());
        } else {
            // Tác giả luôn có thể xóa mềm đánh giá của chính mình.
            r.setStatus(ReviewStatus.DELETED);
            r.setActiveMarker(null);
            r.softDelete();
        }
        reviewRepository.saveAndFlush(r);
        if (wasVisible) {
            ratingRecalculator.recalc(scope);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> listMyReviews(Long userId, Pageable pageable) {
        Page<Review> page = reviewRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefsIncludingDeleted(
                page.getContent().stream().map(Review::getListingId).filter(Objects::nonNull).distinct().toList());
        Map<Long, ListingGateway.RentalUnitBrief> units = listingGateway.getUnitBriefs(
                page.getContent().stream().map(Review::getRentalUnitId).distinct().toList());
        AuthorResponse author = reviewMapper.toAuthor(userGateway.findBrief(userId).orElse(null));

        return PageResponse.from(page, r -> {
            boolean editable = r.getStatus() == ReviewStatus.VISIBLE;
            ReviewResponse response = reviewMapper.toResponse(r, author, editable, null);
            fillUnitAndVia(response, r, units, listings);
            ListingGateway.ListingBrief listing = r.getListingId() == null ? null : listings.get(r.getListingId());
            if (listing != null) {
                response.setListingTitle(listing.title());
                response.setListingThumbnailUrl(listing.thumbnailUrl());
                response.setListingStatus(listing.status() == null ? null : listing.status().name());
            }
            if (r.getStatus() == ReviewStatus.HIDDEN) {
                response.setModerationReason(r.getHiddenReason());
            }
            return response;
        });
    }

    // ==================================================================
    //  Kiểm duyệt (REVIEW_MODERATE)
    // ==================================================================

    @Override
    @Transactional
    public void hideByModeration(Long reviewId, Long moderatorId, String reason) {
        Review r = aliveById(reviewId);
        ReviewRatingRecalculator.Scope scope = ratingRecalculator.lock(r.getRentalUnitId());
        boolean wasVisible = r.getStatus() == ReviewStatus.VISIBLE;
        r.setStatus(ReviewStatus.HIDDEN);
        r.setHiddenReason(reason == null ? null
                : (reason.trim().length() <= 500 ? reason.trim() : reason.trim().substring(0, 500)));
        r.setHiddenBy(moderatorId);
        r.setHiddenAt(Instant.now());
        reviewRepository.saveAndFlush(r);

        // Chỉ tính lại khi đánh giá vừa rời khỏi tập VISIBLE.
        if (wasVisible) {
            ratingRecalculator.recalc(scope);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> adminListReviews(ReviewStatus status, Integer rating, Long listingId,
                                                              Long rentalUnitId, Long landlordId, String keyword,
                                                              Pageable pageable) {
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
        if (rentalUnitId != null) {
            spec = spec.and(ReviewSpecifications.rentalUnitIdEq(rentalUnitId));
        }
        if (landlordId != null) {
            spec = spec.and(ReviewSpecifications.landlordIdEq(landlordId));
        }
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and(ReviewSpecifications.contentContains(keyword));
        }

        Page<Review> page = reviewRepository.findAll(spec, pageable);
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(
                page.getContent().stream().map(Review::getUserId).distinct().toList());
        Map<Long, ListingGateway.RentalUnitBrief> units = listingGateway.getUnitBriefs(
                page.getContent().stream().map(Review::getRentalUnitId).distinct().toList());

        return PageResponse.from(page, r -> {
            ListingGateway.RentalUnitBrief unit = units.get(r.getRentalUnitId());
            return reviewMapper.toAdminResponse(r, reviewMapper.toAuthor(authors.get(r.getUserId())),
                    unit == null ? null : unit.ownerId(), unit == null ? null : unit.name());
        });
    }

    @Override
    @Transactional
    public AdminReviewResponse adminHideReview(Long reviewId, Long moderatorId, String reason) {
        Review r = aliveById(reviewId);
        if (r.getStatus() == ReviewStatus.HIDDEN) {
            throw new ConflictException(ErrorCode.REVIEW_ALREADY_HIDDEN);
        }
        // Tái dùng nghiệp vụ ẩn chuẩn (ghi hidden_* + tính lại điểm khi rời tập VISIBLE).
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
        ReviewRatingRecalculator.Scope scope = ratingRecalculator.lock(r.getRentalUnitId());
        r.setStatus(ReviewStatus.VISIBLE);
        r.setHiddenReason(null);
        r.setHiddenBy(null);
        r.setHiddenAt(null);
        reviewRepository.saveAndFlush(r);

        // Đánh giá quay lại tập VISIBLE → tính lại điểm phòng, tin và chủ trọ.
        ratingRecalculator.recalc(scope);

        auditLogService.record(AuditAction.REVIEW_UNHIDE, AuditActorType.USER, moderatorId,
                "REVIEW", reviewId, "Khôi phục đánh giá bị ẩn");
        return buildAdmin(r);
    }

    // ==================================================================
    //  Helper
    // ==================================================================

    private AdminReviewResponse buildAdmin(Review r) {
        AuthorResponse author = reviewMapper.toAuthor(userGateway.findBrief(r.getUserId()).orElse(null));
        ListingGateway.RentalUnitBrief unit = listingGateway.getUnitBrief(r.getRentalUnitId());
        return reviewMapper.toAdminResponse(r, author, unit.ownerId(), unit.name());
    }

    private Review aliveById(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .filter(r -> r.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REVIEW_NOT_FOUND));
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

    /**
     * Ánh xạ một trang đánh giá: luôn kèm tên phòng và tin đã thuê qua; {@code withListing} thêm
     * tiêu đề/ảnh/trạng thái của tin (dùng ở hồ sơ chủ trọ).
     */
    private PageResponse<ReviewResponse> mapReviewPage(Page<Review> page, boolean withListing) {
        Map<Long, UserGateway.UserBrief> authors = userGateway.getBriefs(
                page.getContent().stream().map(Review::getUserId).distinct().toList());
        Map<Long, ListingGateway.RentalUnitBrief> units = listingGateway.getUnitBriefs(
                page.getContent().stream().map(Review::getRentalUnitId).distinct().toList());
        Map<Long, ListingGateway.ListingBrief> listings = listingGateway.getBriefsIncludingDeleted(
                page.getContent().stream().map(Review::getListingId).filter(Objects::nonNull).distinct().toList());

        return PageResponse.from(page, r -> {
            AuthorResponse author = reviewMapper.toAuthor(authors.get(r.getUserId()));
            ReviewResponse resp = reviewMapper.toResponse(r, author, false, null);
            fillUnitAndVia(resp, r, units, listings);
            if (withListing) {
                ListingGateway.ListingBrief lb = r.getListingId() == null ? null : listings.get(r.getListingId());
                if (lb != null) {
                    resp.setListingTitle(lb.title());
                    resp.setListingThumbnailUrl(lb.thumbnailUrl());
                    resp.setListingStatus(lb.status() == null ? null : lb.status().name());
                }
            }
            return resp;
        });
    }

    private void fillUnitAndVia(ReviewResponse resp, Review r,
                                Map<Long, ListingGateway.RentalUnitBrief> units,
                                Map<Long, ListingGateway.ListingBrief> listings) {
        ListingGateway.RentalUnitBrief unit = units.get(r.getRentalUnitId());
        if (unit != null) {
            resp.setRentalUnitName(unit.name());
        }
        ListingGateway.ListingBrief via = r.getListingId() == null ? null : listings.get(r.getListingId());
        if (via != null) {
            resp.setRentedViaListingTitle(via.title());
            resp.setRentedViaListingCreatedAt(via.createdAt());
        }
    }

    private Map<String, Long> distribution(List<Object[]> rows) {
        Map<String, Long> dist = new LinkedHashMap<>();
        for (int star = 1; star <= 5; star++) {
            dist.put(String.valueOf(star), 0L);
        }
        for (Object[] row : rows) {
            dist.put(String.valueOf(((Number) row[0]).intValue()), ((Number) row[1]).longValue());
        }
        return dist;
    }

    private ReviewResponse single(Review r, UserGateway.UserBrief authorBrief, Long viewerId,
                                  ListingGateway.RentalUnitBrief unit) {
        AuthorResponse author = reviewMapper.toAuthor(authorBrief);
        boolean editable = viewerId != null && viewerId.equals(r.getUserId())
                && r.getStatus() == ReviewStatus.VISIBLE;
        ReviewResponse resp = reviewMapper.toResponse(r, author, editable, null);
        resp.setRentalUnitName(unit == null ? null : unit.name());
        return resp;
    }

    /** Điểm vừa tính lại (của phòng — tin là bản sao — và của chủ trọ). */
    private ReviewResponse withRating(ReviewResponse resp, ReviewRatingRecalculator.Result rating) {
        resp.setListingAverageRating(rating.unitAverage());
        resp.setListingReviewCount(rating.unitCount());
        resp.setLandlordAverageRating(rating.landlordAverage());
        return resp;
    }

    private String reasonMessage(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "OWN_LISTING" -> "Bạn không thể đánh giá phòng của chính mình";
            case "ALREADY_REVIEWED" -> "Bạn đã đánh giá phòng này rồi";
            case "NOT_RENTER" -> "Chỉ người đang thuê hoặc đã trả phòng này mới được đánh giá";
            default -> "Bạn chưa đủ điều kiện đánh giá phòng này";
        };
    }
}
