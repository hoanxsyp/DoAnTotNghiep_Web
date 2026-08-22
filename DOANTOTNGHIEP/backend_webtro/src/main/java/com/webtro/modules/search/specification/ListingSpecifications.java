package com.webtro.modules.search.specification;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ToiletType;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.entity.ListingAmenity;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Tập các {@link Specification} tĩnh cho truy vấn lọc động tin đăng (SRCH-01..08, {@code [§3.7]}).
 *
 * <p>100% tham số hóa qua JPA Criteria (canonical mục 8, {@code [§11.1]}) — không nối chuỗi SQL.
 * Mỗi phương thức trả về một {@link Specification} cho MỘT tiêu chí; service {@code AND} chúng lại
 * bằng {@link Specification#allOf}. Các phương thức có tham số {@code null} trả về {@code null}
 * (Spring Data bỏ qua) nên service không cần rẽ nhánh trước khi thêm.
 *
 * <p>Tin công khai được lọc bằng {@link #statusIn} + {@link #alive} + {@link #notExpired}; tập
 * trạng thái public do {@code ListingVisibilityService.publicStatuses()} cấp (canonical mục 5.2),
 * KHÔNG viết cứng {@code ACTIVE} tại đây.
 */
public final class ListingSpecifications {

    private ListingSpecifications() {
    }

    // ---------------------------------------------------------------------
    //  Điều kiện khả kiến công khai
    // ---------------------------------------------------------------------

    /** Chưa xóa mềm ({@code deleted_at IS NULL}) — canonical mục 6.1. */
    public static Specification<Listing> alive() {
        return (root, query, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /** Trạng thái nằm trong tập public (canonical mục 5.2). */
    public static Specification<Listing> statusIn(Collection<ListingStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            // Không có trạng thái nào public → chặn toàn bộ (predicate luôn sai).
            return (root, query, cb) -> cb.disjunction();
        }
        return (root, query, cb) -> root.get("status").in(statuses);
    }

    /** Còn hạn hiển thị: {@code expired_at IS NULL OR expired_at > now} ({@code [§3.7]}). */
    public static Specification<Listing> notExpired(Instant now) {
        return (root, query, cb) -> cb.or(
                cb.isNull(root.get("expiredAt")),
                cb.greaterThan(root.get("expiredAt"), now));
    }

    // ---------------------------------------------------------------------
    //  Bộ lọc người dùng
    // ---------------------------------------------------------------------

    /** Từ khóa: LIKE không phân biệt hoa/thường trên tiêu đề HOẶC mô tả (SRCH-01). */
    public static Specification<Listing> keyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("description")), pattern));
    }

    public static Specification<Listing> provinceId(Long provinceId) {
        return provinceId == null ? null
                : (root, query, cb) -> cb.exists(wardLocationSubquery(root, query, cb,
                "district.province.id", provinceId));
    }

    public static Specification<Listing> districtId(Long districtId) {
        return districtId == null ? null
                : (root, query, cb) -> cb.exists(wardLocationSubquery(root, query, cb,
                "district.id", districtId));
    }

    public static Specification<Listing> wardId(Long wardId) {
        return wardId == null ? null
                : (root, query, cb) -> cb.equal(root.get("wardId"), wardId);
    }

    public static Specification<Listing> categoryId(Long categoryId) {
        return categoryId == null ? null
                : (root, query, cb) -> cb.equal(root.get("categoryId"), categoryId);
    }

    /**
     * Lọc theo mã danh mục (SEO). Vì {@link Listing} chỉ giữ {@code category_id} (Long, canonical
     * luật 8) nên khớp qua subquery EXISTS trên bảng {@code categories}.
     */
    public static Specification<Listing> categoryCode(CategoryCode code) {
        if (code == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<Category> cat = sub.from(Category.class);
            sub.select(cat.get("id"));
            sub.where(
                    cb.equal(cat.get("id"), root.get("categoryId")),
                    cb.equal(cat.get("code"), code));
            return cb.exists(sub);
        };
    }

    public static Specification<Listing> priceFrom(BigDecimal priceFrom) {
        return priceFrom == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), priceFrom);
    }

    public static Specification<Listing> priceTo(BigDecimal priceTo) {
        return priceTo == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), priceTo);
    }

    public static Specification<Listing> areaFrom(BigDecimal areaFrom) {
        return areaFrom == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("area"), areaFrom);
    }

    public static Specification<Listing> areaTo(BigDecimal areaTo) {
        return areaTo == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("area"), areaTo);
    }

    /** Tiền cọc tối đa: {@code deposit_amount <= depositMax}. */
    public static Specification<Listing> depositMax(BigDecimal depositMax) {
        return depositMax == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("depositAmount"), depositMax);
    }

    /** {@code max_occupants >= value} (SRCH-07). */
    public static Specification<Listing> maxOccupantsAtLeast(Integer value) {
        return value == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("maxOccupants"), value);
    }

    public static Specification<Listing> roomCountAtLeast(Integer value) {
        return value == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("roomCount"), value);
    }

    public static Specification<Listing> toiletCountAtLeast(Integer value) {
        return value == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("toiletCount"), value);
    }

    public static Specification<Listing> genderRequirement(GenderRequirement value) {
        return value == null ? null
                : (root, query, cb) -> cb.equal(root.get("genderRequirement"), value);
    }

    public static Specification<Listing> furnitureStatus(FurnitureStatus value) {
        return value == null ? null
                : (root, query, cb) -> cb.equal(root.get("furnitureStatus"), value);
    }

    public static Specification<Listing> curfewType(CurfewType value) {
        return value == null ? null
                : (root, query, cb) -> cb.equal(root.get("curfewType"), value);
    }

    public static Specification<Listing> toiletType(ToiletType value) {
        return value == null ? null
                : (root, query, cb) -> cb.equal(root.get("toiletType"), value);
    }

    /** Chỉ áp khi cờ = {@code true}; {@code false}/{@code null} → không lọc (giữ cả hai loại tin). */
    public static Specification<Listing> petAllowed(Boolean petAllowed) {
        return Boolean.TRUE.equals(petAllowed)
                ? (root, query, cb) -> cb.isTrue(root.<Boolean>get("petAllowed")) : null;
    }

    public static Specification<Listing> parkingAvailable(Boolean parkingAvailable) {
        return Boolean.TRUE.equals(parkingAvailable)
                ? (root, query, cb) -> cb.isTrue(root.<Boolean>get("parkingAvailable")) : null;
    }

    /** Có thể vào ở từ ngày yêu cầu trở về trước: {@code available_from <= availableFrom}. */
    public static Specification<Listing> availableFromBefore(LocalDate availableFrom) {
        return availableFrom == null ? null
                : (root, query, cb) -> cb.lessThanOrEqualTo(root.get("availableFrom"), availableFrom);
    }

    /** Điểm uy tín tối thiểu: {@code trust_score >= value} ({@code [§5.8]}). */
    public static Specification<Listing> minTrustScore(Integer value) {
        return value == null ? null
                : (root, query, cb) -> cb.greaterThanOrEqualTo(
                        root.get("trustScore"), new BigDecimal(value));
    }

    public static Specification<Listing> idNot(Long id) {
        return id == null ? null
                : (root, query, cb) -> cb.notEqual(root.get("id"), id);
    }

    /** Lọc theo chủ sở hữu tin ({@code owner_id}) — trang hồ sơ chủ trọ công khai. */
    public static Specification<Listing> ownerId(Long ownerId) {
        return ownerId == null ? null
                : (root, query, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    // ---------------------------------------------------------------------
    //  Tiện ích
    // ---------------------------------------------------------------------

    /**
     * Lọc theo danh sách id tiện ích với ngữ nghĩa <b>AND</b> (tin phải có TẤT CẢ): mỗi id thêm
     * một EXISTS trên {@code listing_amenities} (SRCH-06). Danh sách rỗng → không lọc.
     */
    public static Specification<Listing> hasAllAmenityIds(Collection<Long> amenityIds) {
        if (amenityIds == null || amenityIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            for (Long amenityId : amenityIds) {
                if (amenityId == null) {
                    continue;
                }
                Subquery<Long> sub = query.subquery(Long.class);
                Root<ListingAmenity> la = sub.from(ListingAmenity.class);
                sub.select(la.get("id"));
                sub.where(
                        cb.equal(la.get("listing"), root),
                        cb.equal(la.get("amenityId"), amenityId),
                        cb.isNull(la.get("deletedAt")));
                predicates.add(cb.exists(sub));
            }
            return predicates.isEmpty() ? cb.conjunction()
                    : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Cờ tiện ích theo <b>mã</b> (ban công, máy lạnh, máy giặt, thang máy). Vì
     * {@code listing_amenities} chỉ giữ {@code amenity_id} nên khớp qua EXISTS nối
     * {@code listing_amenities} với {@code amenities} theo {@code code}. Chỉ áp khi cờ = {@code true}.
     */
    public static Specification<Listing> hasAmenityCode(String code, Boolean flag) {
        if (!Boolean.TRUE.equals(flag) || code == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ListingAmenity> la = sub.from(ListingAmenity.class);
            Root<Amenity> am = sub.from(Amenity.class);
            sub.select(la.get("id"));
            sub.where(
                    cb.equal(la.get("listing"), root),
                    cb.equal(la.get("amenityId"), am.get("id")),
                    cb.equal(am.get("code"), code),
                    cb.isNull(la.get("deletedAt")));
            return cb.exists(sub);
        };
    }

    // ---------------------------------------------------------------------
    //  Địa lý (lọc thô bằng bounding box)
    // ---------------------------------------------------------------------

    /**
     * Lọc thô theo hộp bao (bounding box) quanh tâm ({@code latitude}, {@code longitude}) bán kính
     * {@code radiusKm}. Rẻ và tận dụng index lat/lng; khoảng cách chính xác (Haversine) chỉ cần khi
     * hiển thị {@code distanceKm}. Ba tham số phải đi cùng nhau, nếu thiếu → không lọc.
     */
    public static Specification<Listing> withinBoundingBox(Double centerLat, Double centerLng,
                                                           Double radiusKm) {
        if (centerLat == null || centerLng == null || radiusKm == null) {
            return null;
        }
        double latDelta = radiusKm / 111.0; // 1 độ vĩ ~ 111 km
        double cosLat = Math.max(Math.cos(Math.toRadians(centerLat)), 0.000001);
        double lngDelta = radiusKm / (111.0 * cosLat);
        BigDecimal latMin = BigDecimal.valueOf(centerLat - latDelta);
        BigDecimal latMax = BigDecimal.valueOf(centerLat + latDelta);
        BigDecimal lngMin = BigDecimal.valueOf(centerLng - lngDelta);
        BigDecimal lngMax = BigDecimal.valueOf(centerLng + lngDelta);
        return (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("latitude")),
                cb.isNotNull(root.get("longitude")),
                cb.between(root.get("latitude"), latMin, latMax),
                cb.between(root.get("longitude"), lngMin, lngMax));
    }

    // ---------------------------------------------------------------------
    //  Sắp xếp
    // ---------------------------------------------------------------------

    /**
     * Áp thứ tự: <b>promotedPriority còn hạn DESC luôn đứng đầu</b> (chỉ trong phạm vi kết quả đã
     * lọc — {@code [§3.7]}), sau đó tới tiêu chí người dùng chọn.
     *
     * <p>Trả về một {@link Specification} luôn đúng (chỉ đặt {@code orderBy}), để service ghép cùng
     * các predicate lọc. Bỏ qua khi là truy vấn đếm (count) để không thêm {@code ORDER BY} thừa.
     */
    public static Specification<Listing> orderBy(ListingSortOption sort, Instant now) {
        return (root, query, cb) -> {
            Class<?> resultType = query.getResultType();
            boolean isCountQuery = resultType == Long.class || resultType == long.class;
            if (!isCountQuery) {
                // Ưu tiên tin đẩy còn hạn: CASE WHEN is_promoted AND promoted_until > now THEN priority ELSE 0.
                Expression<Integer> effectivePriority = cb.<Integer>selectCase()
                        .when(cb.and(
                                        cb.isTrue(root.<Boolean>get("isPromoted")),
                                        cb.greaterThan(root.<Instant>get("promotedUntil"), now)),
                                root.<Integer>get("promotionPriority"))
                        .otherwise(0);

                List<Order> orders = new ArrayList<>();
                orders.add(cb.desc(effectivePriority));
                orders.addAll(secondaryOrders(sort, root, cb));
                query.orderBy(orders);
            }
            return cb.conjunction();
        };
    }

    private static List<Order> secondaryOrders(ListingSortOption sort, Root<Listing> root,
                                               jakarta.persistence.criteria.CriteriaBuilder cb) {
        List<Order> orders = new ArrayList<>();
        switch (sort) {
            case NEWEST -> orders.add(cb.desc(root.get("publishedAt")));
            case OLDEST -> orders.add(cb.asc(root.get("publishedAt")));
            case PRICE_ASC -> orders.add(cb.asc(root.get("price")));
            case PRICE_DESC -> orders.add(cb.desc(root.get("price")));
            case AREA_ASC -> orders.add(cb.asc(root.get("area")));
            case AREA_DESC -> orders.add(cb.desc(root.get("area")));
            case VIEW_DESC -> orders.add(cb.desc(root.get("viewCount")));
            case TRUST_DESC -> orders.add(cb.desc(root.get("trustScore")));
            // DISTANCE_ASC không sắp xếp chính xác được bằng Criteria thuần (BigDecimal + hàm
            // lượng giác); rơi về "mới nhất" cùng với bounding box đã lọc. Xem ghi chú service.
            case DISTANCE_ASC, RELEVANCE -> orders.add(cb.desc(root.get("publishedAt")));
            default -> orders.add(cb.desc(root.get("publishedAt")));
        }
        // Chốt hạ bằng id để thứ tự ổn định giữa các trang.
        orders.add(cb.desc(root.get("id")));
        return orders;
    }

    private static Subquery<Long> wardLocationSubquery(
            Root<Listing> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            String path,
            Long expectedId) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<Ward> ward = sub.from(Ward.class);
        sub.select(ward.get("id"));
        jakarta.persistence.criteria.Path<?> current = ward;
        for (String part : path.split("\\.")) {
            current = current.get(part);
        }
        sub.where(
                cb.equal(ward.get("id"), root.get("wardId")),
                cb.equal(current, expectedId));
        return sub;
    }
}
