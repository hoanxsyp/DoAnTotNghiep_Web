package com.webtro.modules.interaction.repository;

import com.webtro.modules.interaction.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn tin nhắn. Đi qua quan hệ {@code conversation} (trong module) bằng path
 * {@code conversation.id}. Mọi truy vấn công khai lọc {@code deleted_at IS NULL}.
 */
@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    /** Danh sách tin nhắn của một hội thoại theo thời gian tăng dần (phân trang). */
    Page<Message> findByConversation_IdAndDeletedAtIsNullOrderByCreatedAtAsc(Long conversationId, Pageable pageable);

    /** Đếm tin nhắn chưa đọc trong một hội thoại (không tính tin do chính người nhận gửi). */
    long countByConversation_IdAndSenderIdNotAndIsReadFalseAndDeletedAtIsNull(Long conversationId, Long readerId);
}
