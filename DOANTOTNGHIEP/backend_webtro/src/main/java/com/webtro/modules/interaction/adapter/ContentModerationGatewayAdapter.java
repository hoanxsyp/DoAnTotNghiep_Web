package com.webtro.modules.interaction.adapter;

import com.webtro.modules.interaction.repository.CommentRepository;
import com.webtro.modules.interaction.repository.ReviewRepository;
import com.webtro.modules.interaction.service.CommentService;
import com.webtro.modules.interaction.service.ReviewService;
import com.webtro.modules.moderation.spi.ContentModerationGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Adapter (canonical luật 4, [§2.8] RPT-02/03) — hiện thực SPI {@link ContentModerationGateway} của
 * module {@code moderation} bằng repository + service của module {@code interaction}.
 *
 * <p>Đọc tóm tắt nội dung bị báo cáo qua {@link CommentRepository}/{@link ReviewRepository}; việc ẩn
 * đi qua {@link CommentService}/{@link ReviewService} để giữ đúng ràng buộc
 * {@code hidden_reason}/{@code hidden_by}/{@code hidden_at} và đồng bộ lại số đếm/điểm uy tín.
 */
@Component
@RequiredArgsConstructor
public class ContentModerationGatewayAdapter implements ContentModerationGateway {

    /** Độ dài trích nội dung tối đa hiển thị cho Moderator. */
    private static final int EXCERPT_MAX = 200;

    private final CommentRepository commentRepository;
    private final ReviewRepository reviewRepository;
    private final CommentService commentService;
    private final ReviewService reviewService;

    /** Tóm tắt bình luận còn sống nếu tồn tại. */
    @Override
    @Transactional(readOnly = true)
    public Optional<ContentRef> findComment(Long commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .map(c -> new ContentRef(c.getId(), c.getUserId(), c.getListingId(), excerpt(c.getContent())));
    }

    /** Tóm tắt đánh giá còn sống nếu tồn tại. */
    @Override
    @Transactional(readOnly = true)
    public Optional<ContentRef> findReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .filter(r -> r.getDeletedAt() == null)
                .map(r -> new ContentRef(r.getId(), r.getUserId(), r.getListingId(), excerpt(r.getContent())));
    }

    /** Ẩn một bình luận do kiểm duyệt — bắc cầu sang service để ghi lý do/người/thời điểm ẩn. */
    @Override
    @Transactional
    public void hideComment(Long commentId, Long moderatorId, String reason) {
        commentService.hideByModeration(commentId, moderatorId, reason);
    }

    /** Ẩn một đánh giá do kiểm duyệt — bắc cầu sang service (kèm tính lại trung bình + uy tín). */
    @Override
    @Transactional
    public void hideReview(Long reviewId, Long moderatorId, String reason) {
        reviewService.hideByModeration(reviewId, moderatorId, reason);
    }

    // ------------------------------------------------------------------

    private static String excerpt(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= EXCERPT_MAX ? content : content.substring(0, EXCERPT_MAX);
    }
}
