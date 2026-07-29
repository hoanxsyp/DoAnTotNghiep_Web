package com.webtro.modules.ai.repository;

import com.webtro.modules.ai.entity.ChatbotMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Truy vấn tin nhắn chatbot ({@code chatbot_messages}, append-only). Không có xóa mềm.
 *
 * <p>Quan hệ tới phiên map {@code @ManyToOne} nên duyệt property {@code conversation.id}
 * ({@code findByConversation_Id...}).
 */
@Repository
public interface ChatbotMessageRepository extends JpaRepository<ChatbotMessage, Long> {

    /** Nạp toàn bộ tin nhắn của một phiên theo thứ tự thời gian. */
    List<ChatbotMessage> findByConversation_IdOrderByCreatedAtAsc(Long conversationId);

    /** Nạp tin nhắn của một phiên (phân trang, mới nhất trước). */
    Page<ChatbotMessage> findByConversation_IdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    /** Đếm số tin nhắn trong một phiên. */
    long countByConversation_Id(Long conversationId);

    /** Id các tin nhắn chatbot cũ hơn {@code threshold} — {@code DataRetentionJob} xóa theo lô. */
    @Query("SELECT m.id FROM ChatbotMessage m WHERE m.createdAt < :threshold ORDER BY m.id ASC")
    List<Long> findIdsCreatedBefore(@Param("threshold") Instant threshold, Pageable pageable);
}
