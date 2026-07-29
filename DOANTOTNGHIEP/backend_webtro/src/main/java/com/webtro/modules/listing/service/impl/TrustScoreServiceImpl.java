package com.webtro.modules.listing.service.impl;

import com.webtro.common.enums.TrustLabel;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Cài đặt điểm uy tín tin đăng và chủ trọ (canonical §10.5, §5.7, §5.8). Mọi hệ số đọc từ
 * {@code system_configs} qua {@link SystemConfigService} — không hardcode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustScoreServiceImpl implements com.webtro.modules.listing.service.TrustScoreService {

    private final SystemConfigService config;
    private final ListingRepository listingRepository;
    private final LandlordProfileRepository landlordProfileRepository;

    @Override
    public int calculateListingTrustScore(int positiveComments, int negativeComments,
                                          BigDecimal averageRating, int validReports,
                                          int violationWarnings) {
        int base = config.getInt(ConfigKey.TRUST_BASE_SCORE);
        int wPos = config.getInt(ConfigKey.TRUST_WEIGHT_POSITIVE_COMMENT);
        int wNeg = config.getInt(ConfigKey.TRUST_WEIGHT_NEGATIVE_COMMENT);
        int wRating = config.getInt(ConfigKey.TRUST_WEIGHT_AVERAGE_RATING);
        int wReport = config.getInt(ConfigKey.TRUST_WEIGHT_VALID_REPORT);
        int wWarn = config.getInt(ConfigKey.TRUST_WEIGHT_VIOLATION_WARNING);
        int min = config.getInt(ConfigKey.TRUST_MIN);
        int max = config.getInt(ConfigKey.TRUST_MAX);

        BigDecimal rating = averageRating == null ? BigDecimal.ZERO : averageRating;
        BigDecimal score = BigDecimal.valueOf(base)
                .add(BigDecimal.valueOf((long) positiveComments * wPos))
                .subtract(BigDecimal.valueOf((long) negativeComments * wNeg))
                .add(rating.multiply(BigDecimal.valueOf(wRating)))
                .subtract(BigDecimal.valueOf((long) validReports * wReport))
                .subtract(BigDecimal.valueOf((long) violationWarnings * wWarn));

        int rounded = score.setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(min, Math.min(max, rounded));
    }

    @Override
    @Transactional
    public void recalculateAndSaveListing(Long listingId) {
        Listing listing = listingRepository.findAliveById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));

        // Số liệu đọc trực tiếp từ các cột đếm sẵn của tin. Báo cáo hợp lệ và cảnh báo vi phạm được
        // quy về điểm uy tín ở cấp chủ trọ (§5.8), nên ở cấp tin để 0 nếu chưa có bộ đếm riêng.
        int positive = nz(listing.getPositiveCommentCount());
        int negative = nz(listing.getNegativeCommentCount());
        BigDecimal avgRating = listing.getAverageRating() == null ? BigDecimal.ZERO : listing.getAverageRating();

        int score = calculateListingTrustScore(positive, negative, avgRating, 0, 0);
        listing.setTrustScore(BigDecimal.valueOf(score).setScale(2, RoundingMode.HALF_UP));
        listingRepository.save(listing);
    }

    @Override
    @Transactional
    public void recalculateLandlord(Long landlordId) {
        LandlordProfile profile = landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(landlordId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LANDLORD_PROFILE_NOT_FOUND,
                        "Không tìm thấy hồ sơ chủ trọ với userId = " + landlordId));

        // AVG(trust tin còn sống của chủ trọ). Không có tin → dùng điểm nền.
        Specification<Listing> spec = (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.equal(root.get("ownerId"), landlordId));
            ps.add(cb.isNull(root.get("deletedAt")));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        List<Listing> listings = listingRepository.findAll(spec);
        BigDecimal avgTrust;
        if (listings.isEmpty()) {
            avgTrust = BigDecimal.valueOf(config.getInt(ConfigKey.TRUST_BASE_SCORE));
        } else {
            BigDecimal sum = listings.stream()
                    .map(l -> l.getTrustScore() == null ? BigDecimal.ZERO : l.getTrustScore())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            avgTrust = sum.divide(BigDecimal.valueOf(listings.size()), 4, RoundingMode.HALF_UP);
        }

        int wWarn = config.getInt(ConfigKey.TRUST_WEIGHT_VIOLATION_WARNING);
        BigDecimal warningPenalty = BigDecimal.valueOf((long) nz(profile.getWarningCount()) * wWarn);
        BigDecimal responseTerm = responseTerm(profile);

        int min = config.getInt(ConfigKey.TRUST_MIN);
        int max = config.getInt(ConfigKey.TRUST_MAX);
        BigDecimal result = avgTrust.subtract(warningPenalty).add(responseTerm);
        BigDecimal clamped = result.max(BigDecimal.valueOf(min)).min(BigDecimal.valueOf(max))
                .setScale(2, RoundingMode.HALF_UP);
        profile.setTrustScore(clamped);
        landlordProfileRepository.save(profile);
    }

    /**
     * ResponseTerm (§5.7) = weight × (responseRate×100 − neutralPercent)/100, chỉ tính khi số hội
     * thoại đạt ngưỡng mẫu tối thiểu; dưới ngưỡng hoặc chưa có dữ liệu → 0.
     */
    private BigDecimal responseTerm(LandlordProfile profile) {
        Integer ratePercent = profile.getResponseRatePercent();
        int minConversations = config.getInt(ConfigKey.TRUST_RESPONSE_RATE_MIN_CONVERSATIONS);
        if (ratePercent == null || nz(profile.getResponseConversationCount()) < minConversations) {
            return BigDecimal.ZERO;
        }
        int wResp = config.getInt(ConfigKey.TRUST_WEIGHT_LANDLORD_RESPONSE_RATE);
        int neutralPercent = config.getInt(ConfigKey.TRUST_RESPONSE_RATE_NEUTRAL_PERCENT);
        return BigDecimal.valueOf(wResp)
                .multiply(BigDecimal.valueOf((long) ratePercent - neutralPercent))
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
    }

    @Override
    public TrustLabel labelOf(int trustScore) {
        int risky = config.getInt(ConfigKey.TRUST_THRESHOLD_RISKY);
        int needReview = config.getInt(ConfigKey.TRUST_THRESHOLD_NEED_REVIEW);
        if (trustScore < needReview) {
            return TrustLabel.NEED_REVIEW;
        }
        if (trustScore < risky) {
            return TrustLabel.RISKY;
        }
        int max = config.getInt(ConfigKey.TRUST_MAX);
        // "GOOD" khi ở nửa trên khoảng an toàn, còn lại NORMAL.
        int goodThreshold = risky + (max - risky) / 2;
        return trustScore >= goodThreshold ? TrustLabel.GOOD : TrustLabel.NORMAL;
    }

    private int nz(Integer v) {
        return v == null ? 0 : v;
    }
}
