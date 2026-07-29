package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Tin nhắn trong một hội thoại — bảng {@code messages}.
 *
 * <p>{@code conversation} là quan hệ <b>trong cùng module</b> interaction nên map
 * {@code @ManyToOne(LAZY)}. {@code senderId} trỏ sang module user (chéo module) nên giữ {@code Long}.
 */
@Entity
@Table(
        name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_id_created_at", columnList = "conversation_id, created_at"),
                @Index(name = "idx_messages_sender_id", columnList = "sender_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Message extends AuditableEntity {

    /** Hội thoại chứa tin nhắn (quan hệ trong module). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    /** Người gửi (FK users.id — chéo module, giữ Long). */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /** Người nhận đã đọc chưa. */
    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    /** Mốc được đọc — bắt buộc có khi {@code isRead = true} (ck_messages_read). */
    @Column(name = "read_at")
    private Instant readAt;
}
