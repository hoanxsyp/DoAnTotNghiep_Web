package com.webtro.common.event;

/**
 * Sự kiện phát ra khi có bình luận mới (canonical luật 7 + mục 10.1). Chỉ mang {@code commentId}
 * (không mang cả entity) để listener AI nạp lại trong transaction mới sau khi commit
 * ({@code @TransactionalEventListener AFTER_COMMIT}) — tránh race đọc DB trước commit.
 *
 * @param commentId id bình luận vừa tạo
 * @param listingId id tin đăng chứa bình luận
 */
public record CommentCreatedEvent(Long commentId, Long listingId) {
}
