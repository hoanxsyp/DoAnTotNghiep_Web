package com.webtro.modules.interaction.repository;

import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.interaction.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Truy vấn đánh giá. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}. Dùng
 * {@link JpaSpecificationExecutor} cho lọc động ở màn hình kiểm duyệt (trạng thái, sao, tin, phòng,
 * chủ trọ).
 *
 * <p>Đánh giá gắn vào Phòng ({@code rental_unit_id}); chủ trọ suy ra từ {@code rental_units.owner_id}
 * nên đánh giá vẫn đúng chủ kể cả khi tin đã đóng hoặc đã xoá mềm.
 */
@Repository
public interface ReviewRepository extends JpaRepository<Review, Long>, JpaSpecificationExecutor<Review> {

    /** Đánh giá còn hiệu lực của một người cho một phòng (RE-5: tối đa một). */
    Optional<Review> findByUserIdAndRentalUnitIdAndDeletedAtIsNull(Long userId, Long rentalUnitId);

    /** Người dùng đã đánh giá phòng này chưa (409 CONFLICT nếu đã có). */
    boolean existsByUserIdAndRentalUnitIdAndDeletedAtIsNull(Long userId, Long rentalUnitId);

    /** Các đánh giá chưa xóa mềm do chính người dùng gửi. */
    Page<Review> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);

    /** Đánh giá hiển thị của một phòng, lọc theo số sao nếu có, mới nhất trước. */
    @Query("""
            SELECT r FROM Review r
            WHERE r.rentalUnitId = :unitId
              AND r.status = :status
              AND r.deletedAt IS NULL
              AND (:rating IS NULL OR r.rating = :rating)
            ORDER BY r.createdAt DESC
            """)
    Page<Review> findByUnit(@Param("unitId") Long rentalUnitId, @Param("status") ReviewStatus status,
                            @Param("rating") Integer rating, Pageable pageable);

    /** Đánh giá hiển thị về một chủ trọ (mọi phòng của chủ trọ), lọc theo số sao nếu có. */
    @Query(value = """
            SELECT r
            FROM Review r, RentalUnit u
            WHERE u.id = r.rentalUnitId
              AND u.ownerId = :landlordId
              AND r.status = :status
              AND r.deletedAt IS NULL
              AND (:rating IS NULL OR r.rating = :rating)
            ORDER BY r.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(r)
            FROM Review r, RentalUnit u
            WHERE u.id = r.rentalUnitId
              AND u.ownerId = :landlordId
              AND r.status = :status
              AND r.deletedAt IS NULL
              AND (:rating IS NULL OR r.rating = :rating)
            """)
    Page<Review> findByLandlord(@Param("landlordId") Long landlordId, @Param("status") ReviewStatus status,
                                @Param("rating") Integer rating, Pageable pageable);

    /** Phân bố số sao của một phòng: {@code [rating, count]}. */
    @Query("""
            SELECT r.rating, COUNT(r) FROM Review r
            WHERE r.rentalUnitId = :unitId AND r.status = :status AND r.deletedAt IS NULL
            GROUP BY r.rating
            """)
    List<Object[]> ratingDistributionByUnit(@Param("unitId") Long rentalUnitId,
                                            @Param("status") ReviewStatus status);

    /** Phân bố số sao của một chủ trọ: {@code [rating, count]}. */
    @Query("""
            SELECT r.rating, COUNT(r) FROM Review r, RentalUnit u
            WHERE u.id = r.rentalUnitId AND u.ownerId = :landlordId
              AND r.status = :status AND r.deletedAt IS NULL
            GROUP BY r.rating
            """)
    List<Object[]> ratingDistributionByLandlord(@Param("landlordId") Long landlordId,
                                                @Param("status") ReviewStatus status);

    // ==================================================================
    //  Tính lại điểm (docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 10.2)
    // ==================================================================
    //  Dùng LOCK IN SHARE MODE (đọc khoá) thay vì đọc thường: ở mức REPEATABLE READ, đọc thường
    //  dùng ảnh chụp dữ liệu từ đầu transaction nên có thể bỏ sót đánh giá vừa được transaction
    //  khác commit trong lúc mình chờ khoá phòng. Đọc khoá luôn thấy bản đã commit mới nhất.

    @Query(value = "SELECT COUNT(*) FROM reviews WHERE rental_unit_id = :unitId "
            + "AND status = 'VISIBLE' AND deleted_at IS NULL LOCK IN SHARE MODE", nativeQuery = true)
    long countVisibleByUnitLocking(@Param("unitId") Long rentalUnitId);

    @Query(value = "SELECT CAST(COALESCE(SUM(rating), 0) AS SIGNED) FROM reviews WHERE rental_unit_id = :unitId "
            + "AND status = 'VISIBLE' AND deleted_at IS NULL LOCK IN SHARE MODE", nativeQuery = true)
    long sumVisibleRatingByUnitLocking(@Param("unitId") Long rentalUnitId);

    @Query(value = "SELECT COUNT(*) FROM reviews r JOIN rental_units u ON u.id = r.rental_unit_id "
            + "WHERE u.owner_id = :ownerId AND r.status = 'VISIBLE' AND r.deleted_at IS NULL "
            + "LOCK IN SHARE MODE", nativeQuery = true)
    long countVisibleByOwnerLocking(@Param("ownerId") Long ownerId);

    @Query(value = "SELECT CAST(COALESCE(SUM(r.rating), 0) AS SIGNED) FROM reviews r "
            + "JOIN rental_units u ON u.id = r.rental_unit_id "
            + "WHERE u.owner_id = :ownerId AND r.status = 'VISIBLE' AND r.deleted_at IS NULL "
            + "LOCK IN SHARE MODE", nativeQuery = true)
    long sumVisibleRatingByOwnerLocking(@Param("ownerId") Long ownerId);
}
