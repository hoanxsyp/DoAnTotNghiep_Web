package com.webtro.modules.moderation.repository;

import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.moderation.entity.ModerationAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Truy vấn nhật ký hành động kiểm duyệt (append-only, không xóa mềm).
 */
@Repository
public interface ModerationActionRepository extends JpaRepository<ModerationAction, Long> {

    /** Các hành động phát sinh từ một báo cáo. */
    List<ModerationAction> findByReportIdOrderByCreatedAtDesc(Long reportId);

    /** Lịch sử hành động trên một đối tượng (mới nhất trước), phân trang. */
    Page<ModerationAction> findByTargetTypeAndTargetIdOrderByCreatedAtDesc(
            ReportTargetType targetType, Long targetId, Pageable pageable);

    /** Lịch sử hành động trên một tin đăng. */
    Page<ModerationAction> findByListingIdOrderByCreatedAtDesc(Long listingId, Pageable pageable);

    /**
     * Nhật ký hành động kiểm duyệt gần đây có lọc tùy chọn theo loại đối tượng và tin đăng — phục vụ
     * màn lịch sử kiểm duyệt của Admin. Thứ tự sắp xếp lấy từ {@code Pageable} (mặc định mới nhất trước).
     */
    @Query("SELECT a FROM ModerationAction a "
            + "WHERE (:targetType IS NULL OR a.targetType = :targetType) "
            + "AND (:listingId IS NULL OR a.listingId = :listingId)")
    Page<ModerationAction> searchRecent(@Param("targetType") ReportTargetType targetType,
                                        @Param("listingId") Long listingId, Pageable pageable);
}
