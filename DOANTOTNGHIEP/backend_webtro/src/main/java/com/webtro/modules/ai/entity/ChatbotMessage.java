package com.webtro.modules.ai.entity;

import com.webtro.common.enums.ChatbotIntent;
import com.webtro.common.enums.ChatbotSender;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Tin nhắn trong một phiên chatbot ({@code chatbot_messages}) — mục 43 của V1 baseline.
 *
 * <p><b>Append-only</b> (chỉ {@code id} + {@code created_at}) nên <b>không kế thừa</b> base class;
 * khai {@code id}/{@code created_at} tại chỗ.
 *
 * <p>{@code conversation} là quan hệ <b>trong cùng module</b> ai nên map {@code @ManyToOne(LAZY)}.
 * {@code resultListingIds} chỉ lưu id tin (JSON) — chéo module, không map quan hệ.
 */
@Entity
@Table(
        name = "chatbot_messages",
        indexes = {
                @Index(name = "idx_chatbot_messages_conversation_id_created_at", columnList = "conversation_id, created_at"),
                @Index(name = "idx_chatbot_messages_intent_created_at", columnList = "intent, created_at"),
                @Index(name = "idx_chatbot_messages_is_fallback", columnList = "is_fallback, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatbotMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Phiên chứa tin nhắn (quan hệ trong module). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatbotConversation conversation;

    /** Bên gửi (USER/BOT). */
    @Enumerated(EnumType.STRING)
    @Column(name = "sender", nullable = false, length = 10)
    private ChatbotSender sender;

    /** Nội dung tin nhắn. */
    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    /** Intent nhận diện (chỉ tin của USER; tin BOT phải null theo ck_chatbot_messages_bot_intent). */
    @Enumerated(EnumType.STRING)
    @Column(name = "intent", length = 15)
    private ChatbotIntent intent;

    /** Độ tin cậy nhận diện intent [0, 1]. */
    @Column(name = "intent_confidence", precision = 4, scale = 3)
    private BigDecimal intentConfidence;

    /** Các slot trích xuất từ câu hỏi (JSON thô). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_slots", columnDefinition = "json")
    private String extractedSlots;

    /** Danh sách id tin trả về cho lượt tìm phòng (JSON thô — chéo module, giữ id). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_listing_ids", columnDefinition = "json")
    private String resultListingIds;

    /** Số kết quả trả về. */
    @Column(name = "result_count")
    private Integer resultCount;

    /** Bot không trả lời được (fallback) — phục vụ tìm câu hỏi cần bổ sung FAQ. */
    @Builder.Default
    @Column(name = "is_fallback", nullable = false)
    private Boolean isFallback = false;

    /** Thời gian phản hồi (ms). */
    @Column(name = "response_ms")
    private Integer responseMs;

    /** Thời điểm ghi nhận (append-only). */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
