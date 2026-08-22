package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.ContactLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query(value = """
            SELECT c
            FROM ContactLog c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :ownerId
              AND c.deletedAt IS NULL
            ORDER BY c.createdAt DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM ContactLog c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :ownerId
              AND c.deletedAt IS NULL
            """)
    Page<ContactLog> findByOwnerIdAndDeletedAtIsNull(@Param("ownerId") Long ownerId, Pageable pageable);

    /** Đếm liên hệ chủ tin chưa đọc. */
    @Query("""
            SELECT COUNT(c)
            FROM ContactLog c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :ownerId
              AND c.isReadByOwner = false
              AND c.deletedAt IS NULL
            """)
    long countByOwnerIdAndIsReadByOwnerFalseAndDeletedAtIsNull(@Param("ownerId") Long ownerId);

    /** Liên hệ mới nhất của một người kể từ {@code since} (giới hạn qua {@code Pageable}) — tín hiệu hành vi w=5. */
    List<ContactLog> findByUserIdAndCreatedAtAfterAndDeletedAtIsNullOrderByCreatedAtDesc(
            Long userId, Instant since, Pageable pageable);

    /** Người dùng đã từng liên hệ tin nào chưa (quyết định cold-start). */
    boolean existsByUserIdAndDeletedAtIsNull(Long userId);

    /** Count counted contacts for owner's listings in [from, to). */
    @Query("""
            SELECT COUNT(c)
            FROM ContactLog c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :ownerId
              AND c.isCounted = true
              AND c.createdAt >= :from
              AND c.createdAt < :to
              AND c.deletedAt IS NULL
            """)
    long countByOwnerIdAndIsCountedTrueAndCreatedAtGreaterThanEqualAndCreatedAtLessThanAndDeletedAtIsNull(
            @Param("ownerId") Long ownerId, @Param("from") Instant from, @Param("to") Instant to);

    /** Daily counted contacts for owner's listings in [from, to). */
    @Query(value = """
            SELECT DATE(c.created_at) AS day, COUNT(*) AS total
            FROM contact_logs c
            JOIN listings l ON l.id = c.listing_id
            WHERE l.owner_id = :ownerId
              AND c.deleted_at IS NULL
              AND c.is_counted = true
              AND c.created_at >= :from
              AND c.created_at < :to
            GROUP BY DATE(c.created_at)
            """, nativeQuery = true)
    List<Object[]> countDailyContactsForOwnerBetween(@Param("ownerId") Long ownerId,
                                                     @Param("from") Instant from,
                                                     @Param("to") Instant to);
}
