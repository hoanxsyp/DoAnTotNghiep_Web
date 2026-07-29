package com.webtro.modules.admin.specification;

import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.modules.user.entity.LandlordProfile;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification lọc động cho màn quản lý chủ trọ (canonical 4.13.6).
 *
 * <p>Lọc trên {@link LandlordProfile} + join sang {@code user} (ManyToOne) cho từ khóa (tên/email/
 * SĐT) và loại trừ tài khoản đã xóa mềm. Không viết cứng trạng thái public.
 */
public final class AdminLandlordSpecifications {

    private AdminLandlordSpecifications() {
    }

    public static Specification<LandlordProfile> filter(String keyword,
                                                        List<VerificationStatus> statuses,
                                                        Integer minTrustScore, Integer maxTrustScore,
                                                        Boolean postingSuspended, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            // Hồ sơ còn hiệu lực + tài khoản còn hiệu lực (không hiện chủ trọ đã xóa).
            ps.add(cb.isNull(root.get("deletedAt")));
            Join<Object, Object> user = root.join("user", JoinType.INNER);
            ps.add(cb.isNull(user.get("deletedAt")));
            ps.add(cb.notEqual(user.get("status"), UserStatus.DELETED));

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(user.get("fullName")), like),
                        cb.like(cb.lower(user.get("email")), like),
                        cb.like(cb.lower(user.get("phone")), like),
                        cb.like(cb.lower(root.get("companyName")), like),
                        cb.like(cb.lower(root.get("displayName")), like)));
            }

            if (statuses != null && !statuses.isEmpty()) {
                ps.add(root.get("verificationStatus").in(statuses));
            }

            if (minTrustScore != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("trustScore"), new BigDecimal(minTrustScore)));
            }
            if (maxTrustScore != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("trustScore"), new BigDecimal(maxTrustScore)));
            }

            if (postingSuspended != null) {
                if (postingSuspended) {
                    ps.add(cb.and(
                            cb.isNotNull(root.get("postingRestrictedUntil")),
                            cb.greaterThan(root.get("postingRestrictedUntil"), now)));
                } else {
                    ps.add(cb.or(
                            cb.isNull(root.get("postingRestrictedUntil")),
                            cb.lessThanOrEqualTo(root.get("postingRestrictedUntil"), now)));
                }
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
