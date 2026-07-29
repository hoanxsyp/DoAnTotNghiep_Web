package com.webtro.modules.ai.repository;

import com.webtro.common.enums.ChatbotConversationStatus;
import com.webtro.modules.ai.entity.ChatbotConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Truy vấn phiên hội thoại chatbot ({@code chatbot_conversations}).
 *
 * <p>Bảng nghiệp vụ có xóa mềm nên các truy vấn công khai lọc {@code deleted_at IS NULL} tường minh.
 */
@Repository
public interface ChatbotConversationRepository extends JpaRepository<ChatbotConversation, Long> {

    /** Tìm phiên đang hoạt động theo session (một phiên = một hội thoại). */
    Optional<ChatbotConversation> findBySessionIdAndDeletedAtIsNull(String sessionId);

    /** Lịch sử phiên của một người dùng (phân trang). */
    Page<ChatbotConversation> findByUserIdAndDeletedAtIsNullOrderByStartedAtDesc(Long userId, Pageable pageable);

    /** Phiên còn ACTIVE nhưng đã im lặng quá lâu — job đóng phiên bỏ dở. */
    List<ChatbotConversation> findByStatusAndLastMessageAtBeforeAndDeletedAtIsNull(
            ChatbotConversationStatus status, Instant threshold);
}
