package com.webtro.modules.moderation.repository;

import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.moderation.entity.Report;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Truy vấn báo cáo. Có {@link JpaSpecificationExecutor} phục vụ lọc động ở màn hình quản trị.
 * Mọi truy vấn công khai đều kèm điều kiện {@code deleted_at IS NULL} (xóa mềm).
 */
@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    /** Lấy một báo cáo chưa bị xóa mềm theo id. */
    Optional<Report> findByIdAndDeletedAtIsNull(Long id);

    /** Danh sách báo cáo theo trạng thái (phân trang), bỏ qua bản ghi đã xóa mềm. */
    Page<Report> findByStatusAndDeletedAtIsNull(ReportStatus status, Pageable pageable);

    /** Danh sách báo cáo trỏ tới một đối tượng cụ thể, bỏ qua bản ghi đã xóa mềm. */
    Page<Report> findByTargetTypeAndTargetIdAndDeletedAtIsNull(
            ReportTargetType targetType, Long targetId, Pageable pageable);

    /** Kiểm tra người dùng đã báo cáo trùng (chống spam theo dedup_key) hay chưa. */
    boolean existsByReporterIdAndDedupKeyAndDeletedAtIsNull(Long reporterId, String dedupKey);

    /** Đếm số báo cáo chưa xóa mềm trỏ tới một đối tượng (phục vụ ngưỡng tự ẩn). */
    long countByTargetTypeAndTargetIdAndDeletedAtIsNull(ReportTargetType targetType, Long targetId);

    /** Count owner's listings that still have pending reports. */
    @Query(value = """
            SELECT COUNT(DISTINCT r.listing_id)
            FROM reports r
            JOIN listings l ON l.id = r.listing_id
            WHERE l.owner_id = :ownerId
              AND l.deleted_at IS NULL
              AND r.deleted_at IS NULL
              AND r.status = 'PENDING'
              AND r.listing_id IS NOT NULL
            """, nativeQuery = true)
    long countPendingReportedListingsForOwner(@Param("ownerId") Long ownerId);
}
