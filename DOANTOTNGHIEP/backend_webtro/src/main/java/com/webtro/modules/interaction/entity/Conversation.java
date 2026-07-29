package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ConversationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Hội thoại chat giữa người thuê và chủ trọ về một tin — bảng {@code conversations}.
 *
 * <p>Duy nhất theo bộ ba ({@code listingId}, {@code tenantId}, {@code landlordId}). Lưu các bộ đếm
 * chưa đọc và mốc phản hồi đầu tiên phục vụ tính tỷ lệ phản hồi chủ trọ {@code [§5.7]}.
 * FK chéo module giữ dạng {@code Long}.
 */
@Entity
@Table(
        name = "conversations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_conversations_listing_tenant_landlord",
                columnNames = {"listing_id", "tenant_id", "landlord_id"}),
        indexes = {
                @Index(name = "idx_conversations_tenant_id_last_message_at", columnList = "tenant_id, last_message_at"),
                @Index(name = "idx_conversations_landlord_id_last_message_at", columnList = "landlord_id, last_message_at"),
                @Index(name = "idx_conversations_listing_id", columnList = "listing_id"),
                @Index(name = "idx_conversations_landlord_id_created_at", columnList = "landlord_id, created_at, first_response_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Conversation extends AuditableEntity {

    /** Tin được trao đổi (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Người thuê (FK users.id — chéo module, giữ Long). */
    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    /** Chủ trọ (FK users.id — chéo module, giữ Long). */
    @Column(name = "landlord_id", nullable = false)
    private Long landlordId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 10)
    private ConversationStatus status = ConversationStatus.ACTIVE;

    /** Mốc chủ trọ phản hồi lần đầu — dùng tính SLA phản hồi. */
    @Column(name = "first_response_at")
    private Instant firstResponseAt;

    /** Mốc tin nhắn gần nhất — dùng sắp xếp danh sách hội thoại. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Trích đoạn tin nhắn gần nhất — hiển thị nhanh ở danh sách. */
    @Column(name = "last_message_preview", length = 200)
    private String lastMessagePreview;

    @Builder.Default
    @Column(name = "tenant_unread_count", nullable = false)
    private Integer tenantUnreadCount = 0;

    @Builder.Default
    @Column(name = "landlord_unread_count", nullable = false)
    private Integer landlordUnreadCount = 0;

    @Builder.Default
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;
}
