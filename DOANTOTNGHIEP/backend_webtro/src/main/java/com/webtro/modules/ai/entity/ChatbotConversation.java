package com.webtro.modules.ai.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ChatbotConversationStatus;
import com.webtro.common.enums.ChatbotIntent;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Phiên hội thoại chatbot ({@code chatbot_conversations}) — mục 42 của V1 baseline.
 *
 * <p><b>Bảng nghiệp vụ</b> (có {@code created_at}/{@code updated_at}/{@code created_by}/
 * {@code updated_by}/{@code deleted_at}) nên kế thừa {@link AuditableEntity} và dùng
 * {@code @SuperBuilder}.
 *
 * <p>{@code userId} nullable (khách ẩn danh) trỏ sang module user — chéo module, giữ {@code Long}
 * (canonical luật 8). Không map {@code @OneToMany} tới {@link ChatbotMessage} để tránh nạp thừa;
 * quan hệ được nạp chủ động ở tầng service khi cần.
 */
@Entity
@Table(
        name = "chatbot_conversations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chatbot_conversations_session_id", columnNames = "session_id")
        },
        indexes = {
                @Index(name = "idx_chatbot_conversations_user_id_started_at", columnList = "user_id, started_at"),
                @Index(name = "idx_chatbot_conversations_status_last_message_at", columnList = "status, last_message_at"),
                @Index(name = "idx_chatbot_conversations_last_intent", columnList = "last_intent")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ChatbotConversation extends AuditableEntity {

    /** Người dùng của phiên — {@code null} nếu khách ẩn danh (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id")
    private Long userId;

    /** Định danh phiên (UUID, unique = một phiên một hội thoại). */
    @Column(name = "session_id", nullable = false, columnDefinition = "CHAR(36)")
    private String sessionId;

    /** Trạng thái vòng đời phiên. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private ChatbotConversationStatus status = ChatbotConversationStatus.ACTIVE;

    /** Intent gần nhất của phiên (thống kê intent). */
    @Enumerated(EnumType.STRING)
    @Column(name = "last_intent", length = 15)
    private ChatbotIntent lastIntent;

    /** Bộ lọc đã thu thập qua các lượt hỏi lại (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "collected_filters", nullable = false, columnDefinition = "json")
    private String collectedFilters;

    /** Số lượt hỏi lại đã dùng [0, 3]. */
    @Builder.Default
    @Column(name = "clarify_turn_count", nullable = false)
    private Integer clarifyTurnCount = 0;

    /** Số tin nhắn trong phiên. */
    @Builder.Default
    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    /** Thời điểm bắt đầu phiên (UTC). */
    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    /** Thời điểm tin nhắn gần nhất. */
    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    /** Thời điểm kết thúc phiên. */
    @Column(name = "ended_at")
    private Instant endedAt;
}
