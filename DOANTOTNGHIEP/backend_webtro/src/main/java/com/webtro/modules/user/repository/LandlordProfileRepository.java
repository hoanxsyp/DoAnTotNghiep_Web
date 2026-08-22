package com.webtro.modules.user.repository;

import com.webtro.common.enums.VerificationStatus;
import com.webtro.modules.user.entity.LandlordProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link LandlordProfile} (hồ sơ chủ trọ, 1-1 với user).
 *
 * <p>Kèm {@link JpaSpecificationExecutor} vì màn quản lý chủ trọ (canonical 4.13.6) cần lọc động
 * (từ khóa, trạng thái xác thực, khoảng điểm uy tín, trạng thái hạn chế đăng tin).
 */
@Repository
public interface LandlordProfileRepository
        extends JpaRepository<LandlordProfile, Long>, JpaSpecificationExecutor<LandlordProfile> {

    /** Lấy hồ sơ chủ trọ theo người dùng (còn hiệu lực). */
    Optional<LandlordProfile> findByUser_IdAndDeletedAtIsNull(Long userId);

    /** Kiểm tra người dùng đã có hồ sơ chủ trọ chưa. */
    boolean existsByUser_IdAndDeletedAtIsNull(Long userId);

    /** Danh sách hồ sơ chủ trọ theo trạng thái xác minh (hàng đợi duyệt của moderator). */
    List<LandlordProfile> findByVerificationStatusAndDeletedAtIsNull(VerificationStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE landlord_profiles
            SET total_listings = GREATEST(0, CAST(total_listings AS SIGNED) + :totalDelta),
                total_active_listings = GREATEST(0, CAST(total_active_listings AS SIGNED) + :activeDelta)
            WHERE user_id = :ownerId
              AND deleted_at IS NULL
            """, nativeQuery = true)
    int incrementListingStats(@Param("ownerId") Long ownerId,
                              @Param("totalDelta") int totalDelta,
                              @Param("activeDelta") int activeDelta);
}
