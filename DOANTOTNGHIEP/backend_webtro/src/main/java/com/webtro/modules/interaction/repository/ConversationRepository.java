package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Truy vấn hội thoại chat. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    /** Tìm hội thoại đang tồn tại theo bộ ba (tránh tạo trùng — {@code uk_conversations_listing_tenant_landlord}). */
    Optional<Conversation> findByListingIdAndTenantIdAndLandlordIdAndDeletedAtIsNull(
            Long listingId, Long tenantId, Long landlordId);

    /** Danh sách hội thoại của người thuê, mới nhất trước. */
    Page<Conversation> findByTenantIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long tenantId, Pageable pageable);

    /** Danh sách hội thoại của chủ trọ, mới nhất trước. */
    Page<Conversation> findByLandlordIdAndDeletedAtIsNullOrderByLastMessageAtDesc(Long landlordId, Pageable pageable);
}
