package com.webtro.modules.listing.repository;

import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.modules.listing.entity.RentalUnit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link RentalUnit}. Điều kiện {@code deleted_at IS NULL} viết tường minh như các
 * bảng nghiệp vụ khác.
 */
@Repository
public interface RentalUnitRepository
        extends JpaRepository<RentalUnit, Long>, JpaSpecificationExecutor<RentalUnit> {

    @Query("SELECT u FROM RentalUnit u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<RentalUnit> findAliveById(@Param("id") Long id);

    /**
     * Khoá dòng phòng ({@code SELECT … FOR UPDATE}). Dùng khi tạo tin cho phòng (RU-2: 2 yêu cầu
     * cùng lúc không tạo được 2 tin sống) và khi tính lại điểm (mục 10.2 bước 1).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM RentalUnit u WHERE u.id = :id AND u.deletedAt IS NULL")
    Optional<RentalUnit> findAliveByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM RentalUnit u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<RentalUnit> findAliveByIds(@Param("ids") Collection<Long> ids);

    /** Phòng còn dùng trong một phường — ứng viên để dò trùng địa chỉ (RU-6). */
    @Query("SELECT u FROM RentalUnit u WHERE u.wardId = :wardId AND u.status = :status "
            + "AND u.deletedAt IS NULL")
    List<RentalUnit> findByWardAndStatus(@Param("wardId") Long wardId,
                                         @Param("status") RentalUnitStatus status);

    /**
     * Ghi điểm phòng — đường ghi DUY NHẤT vào {@code average_rating}/{@code review_count}
     * (mục 10.2 bước 3). Phòng đã có đánh giá thì khoá địa chỉ, và khoá này không tự mở lại.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE rental_units SET average_rating = :avg, review_count = :cnt, "
            + "address_locked = (address_locked OR :cnt > 0) WHERE id = :id", nativeQuery = true)
    int applyRating(@Param("id") Long id, @Param("avg") BigDecimal averageRating, @Param("cnt") int reviewCount);

    /** Phòng cần đối soát điểm: đang có điểm, hoặc có ít nhất một đánh giá (kể cả đã ẩn/xoá). */
    @Query(value = "SELECT u.id FROM rental_units u WHERE u.deleted_at IS NULL AND (u.review_count > 0 "
            + "OR EXISTS (SELECT 1 FROM reviews r WHERE r.rental_unit_id = u.id)) ORDER BY u.id",
            nativeQuery = true)
    List<Long> findIdsNeedingRatingReconcile();
}
