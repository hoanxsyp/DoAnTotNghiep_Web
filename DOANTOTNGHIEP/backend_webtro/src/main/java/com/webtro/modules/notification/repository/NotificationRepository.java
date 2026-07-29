package com.webtro.modules.notification.repository;

import com.webtro.common.enums.NotificationType;
import com.webtro.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn thông báo. Bảng nghiệp vụ (xóa mềm) nên mọi truy vấn công khai đều lọc
 * {@code deleted_at IS NULL} tường minh.
 */
@Repository
public interface NotificationRepository
        extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /** Lấy một thông báo chưa bị xóa mềm theo id. */
    Optional<Notification> findByIdAndDeletedAtIsNull(Long id);

    /** Danh sách thông báo của một người dùng (mới nhất trước), phân trang. */
    Page<Notification> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Đếm số thông báo chưa đọc của một người dùng (phục vụ badge). */
    long countByUserIdAndIsReadFalseAndDeletedAtIsNull(Long userId);

    /**
     * Đánh dấu đã đọc một thông báo của người dùng (idempotent theo read_at hiện có).
     *
     * @return số bản ghi bị ảnh hưởng
     */
    @Modifying
    @Query("""
            UPDATE Notification n
               SET n.isRead = true, n.readAt = :readAt
             WHERE n.id = :id AND n.userId = :userId
               AND n.isRead = false AND n.deletedAt IS NULL
            """)
    int markRead(@Param("id") Long id, @Param("userId") Long userId, @Param("readAt") Instant readAt);

    /**
     * Đánh dấu đã đọc toàn bộ thông báo chưa đọc của một người dùng.
     *
     * @return số bản ghi bị ảnh hưởng
     */
    @Modifying
    @Query("""
            UPDATE Notification n
               SET n.isRead = true, n.readAt = :readAt
             WHERE n.userId = :userId AND n.isRead = false AND n.deletedAt IS NULL
            """)
    int markAllRead(@Param("userId") Long userId, @Param("readAt") Instant readAt);

    // ==================================================================
    //  Truy vấn phục vụ job nền (canonical mục 11) — thêm cho scheduler
    // ==================================================================

    /**
     * Đã từng gửi thông báo loại {@code type} tới người dùng với đúng {@code link} này chưa —
     * {@code NewMatchingListingNotifyJob} dùng để KHÔNG gợi ý trùng một tin cho một user (link là
     * đường dẫn tới tin, xác định duy nhất theo tin). Dùng {@code link} vì {@code notifyUser} điền
     * {@code link} nhưng không điền {@code ref_type}/{@code ref_id}.
     */
    boolean existsByUserIdAndTypeAndLinkAndDeletedAtIsNull(
            Long userId, NotificationType type, String link);

    /**
     * Id các thông báo ĐÃ ĐỌC và đọc trước {@code threshold} — {@code DataRetentionJob} xóa theo lô.
     * Chỉ dọn thông báo đã đọc (log-like), không đụng thông báo chưa đọc.
     */
    @Query("SELECT n.id FROM Notification n WHERE n.isRead = true AND n.readAt IS NOT NULL "
            + "AND n.readAt < :threshold ORDER BY n.id ASC")
    List<Long> findReadIdsReadBefore(@Param("threshold") Instant threshold, Pageable pageable);
}
