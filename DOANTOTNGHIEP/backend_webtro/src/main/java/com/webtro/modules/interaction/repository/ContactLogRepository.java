package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.ContactLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn nhật ký liên hệ. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface ContactLogRepository extends JpaRepository<ContactLog, Long> {

    /** Có liên hệ trùng của cùng user cho cùng tin sau mốc thời gian ({@code contact.dedup_minutes}). */
    boolean existsByListingIdAndUserIdAndCreatedAtAfterAndDeletedAtIsNull(Long listingId, Long userId, Instant since);

    /** Danh sách liên hệ mà một chủ tin nhận được (phân trang). */
    Page<ContactLog> findByOwnerIdAndDeletedAtIsNull(Long ownerId, Pageable pageable);

    /** Đếm liên hệ chủ tin chưa đọc. */
    long countByOwnerIdAndIsReadByOwnerFalseAndDeletedAtIsNull(Long ownerId);

    /** Liên hệ mới nhất của một người kể từ {@code since} (giới hạn qua {@code Pageable}) — tín hiệu hành vi w=5. */
    List<ContactLog> findByUserIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, Instant since, Pageable pageable);

    /** Người dùng đã từng liên hệ tin nào chưa (quyết định cold-start). */
    boolean existsByUserIdAndDeletedAtIsNull(Long userId);
}
