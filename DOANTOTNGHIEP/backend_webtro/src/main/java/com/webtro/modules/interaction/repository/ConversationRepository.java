package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Truy vấn hội thoại chat. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Tìm hội thoại đang tồn tại theo tin + tenant (tránh tạo trùng — {@code uk_conversations_listing_tenant}). */
    Optional<Conversation> findByListingIdAndTenantIdAndDeletedAtIsNull(Long listingId, Long tenantId);

    /** Apply membership and optional filters before pagination. */
    @Query(value = """
            SELECT c FROM Conversation c, Listing l
            WHERE l.id = c.listingId AND c.deletedAt IS NULL
              AND ((:role = 'AS_TENANT' AND c.tenantId = :userId)
                OR (:role = 'AS_LANDLORD' AND l.ownerId = :userId)
                OR (:role = 'ALL' AND (c.tenantId = :userId OR l.ownerId = :userId)))
              AND (:listingId IS NULL OR c.listingId = :listingId)
              AND (:unreadOnly = false
                OR (c.tenantId = :userId AND c.tenantUnreadCount > 0)
                OR (l.ownerId = :userId AND c.landlordUnreadCount > 0))
            """,
            countQuery = """
            SELECT COUNT(c) FROM Conversation c, Listing l
            WHERE l.id = c.listingId AND c.deletedAt IS NULL
              AND ((:role = 'AS_TENANT' AND c.tenantId = :userId)
                OR (:role = 'AS_LANDLORD' AND l.ownerId = :userId)
                OR (:role = 'ALL' AND (c.tenantId = :userId OR l.ownerId = :userId)))
              AND (:listingId IS NULL OR c.listingId = :listingId)
              AND (:unreadOnly = false
                OR (c.tenantId = :userId AND c.tenantUnreadCount > 0)
                OR (l.ownerId = :userId AND c.landlordUnreadCount > 0))
            """)
    Page<Conversation> findAccessible(@Param("userId") Long userId,
                                      @Param("role") String role,
                                      @Param("unreadOnly") boolean unreadOnly,
                                      @Param("listingId") Long listingId,
                                      Pageable pageable);

    /** Number of accessible conversations containing unread messages. */
    @Query("""
            SELECT COUNT(c) FROM Conversation c, Listing l
            WHERE l.id = c.listingId AND c.deletedAt IS NULL
              AND ((c.tenantId = :userId AND c.tenantUnreadCount > 0)
                OR (l.ownerId = :userId AND c.landlordUnreadCount > 0))
            """)
    long countUnreadConversations(@Param("userId") Long userId);

    /** Total unread messages across every conversation visible to the user. */
    @Query("""
            SELECT COALESCE(SUM(
                CASE WHEN c.tenantId = :userId THEN c.tenantUnreadCount ELSE 0 END
                + CASE WHEN l.ownerId = :userId THEN c.landlordUnreadCount ELSE 0 END
            ), 0)
            FROM Conversation c, Listing l
            WHERE l.id = c.listingId AND c.deletedAt IS NULL
              AND (c.tenantId = :userId OR l.ownerId = :userId)
            """)
    long sumUnreadMessages(@Param("userId") Long userId);
}
