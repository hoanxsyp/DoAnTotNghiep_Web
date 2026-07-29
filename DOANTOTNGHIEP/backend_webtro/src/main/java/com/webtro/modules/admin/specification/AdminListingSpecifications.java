package com.webtro.modules.admin.specification;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.listing.entity.Listing;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification lọc động cho màn quản trị tin đăng (canonical 4.14.1). Admin thấy cả tin đã xóa
 * mềm {@code [§3.6]} nên KHÔNG lọc {@code deleted_at} mặc định; chỉ lọc theo các tiêu chí truyền vào.
 */
public final class AdminListingSpecifications {

    private AdminListingSpecifications() {
    }

    public static Specification<Listing> filter(List<ListingStatus> statuses, String keyword,
                                                Long ownerId, Long categoryId, Long provinceId,
                                                Long districtId, Long wardId,
                                                Boolean priceDeviationFlagged,
                                                Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            if (statuses != null && !statuses.isEmpty()) {
                ps.add(root.get("status").in(statuses));
            } else {
                // Mặc định: tất cả trừ DELETED (canonical 4.14.1).
                ps.add(cb.notEqual(root.get("status"), ListingStatus.DELETED));
            }
            if (keyword != null && !keyword.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("title")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (ownerId != null) {
                ps.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (categoryId != null) {
                ps.add(cb.equal(root.get("categoryId"), categoryId));
            }
            if (provinceId != null) {
                ps.add(cb.equal(root.get("provinceId"), provinceId));
            }
            if (districtId != null) {
                ps.add(cb.equal(root.get("districtId"), districtId));
            }
            if (wardId != null) {
                ps.add(cb.equal(root.get("wardId"), wardId));
            }
            if (priceDeviationFlagged != null) {
                ps.add(cb.equal(root.get("priceDeviationFlag"), priceDeviationFlagged));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
