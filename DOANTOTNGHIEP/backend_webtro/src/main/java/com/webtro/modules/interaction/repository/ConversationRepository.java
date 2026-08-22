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

    /** Danh sách hội thoại của người thuê, mới nhất trước. */
    Page<Conversation> findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long tenantId, Pageable pageable);

    /** Danh sách hội thoại của chủ trọ, mới nhất trước. */
    @Query(value = """
            SELECT c
            FROM Conversation c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :landlordId
              AND c.deletedAt IS NULL
            ORDER BY c.lastMessageAt DESC
            """,
            countQuery = """
            SELECT COUNT(c)
            FROM Conversation c, Listing l
            WHERE l.id = c.listingId
              AND l.ownerId = :landlordId
              AND c.deletedAt IS NULL
            """)
    Page<Conversation> findByLandlordIdAndDeletedAtIsNullOrderByLastMessageAtDesc(
            @Param("landlordId") Long landlordId, Pageable pageable);
}
