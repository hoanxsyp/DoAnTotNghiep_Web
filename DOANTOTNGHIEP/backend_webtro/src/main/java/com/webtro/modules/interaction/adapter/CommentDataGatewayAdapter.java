package com.webtro.modules.interaction.adapter;

import com.webtro.common.enums.SentimentLabel;
import com.webtro.modules.ai.spi.CommentDataGateway;
import com.webtro.modules.interaction.entity.Comment;
import com.webtro.modules.interaction.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Adapter (canonical luật 4/7) — hiện thực SPI {@link CommentDataGateway} của module {@code ai}
 * bằng {@link CommentRepository} của module {@code interaction}. Module {@code ai} chỉ khai báo cổng
 * đọc/ghi cảm xúc bình luận; lớp này lấp đầy cổng đó, không cho module {@code ai} chạm thẳng repository.
 *
 * <p>Hiện thực ghi chú trên entity {@code Comment}: các cột {@code sentiment_*} do
 * {@code SentimentAnalyzer} (module ai) cập nhật qua cổng của module interaction, không @Autowired ngược.
 */
@Component
@RequiredArgsConstructor
public class CommentDataGatewayAdapter implements CommentDataGateway {

    private final CommentRepository commentRepository;

    /** Nạp ảnh chụp bình luận còn sống theo id (bỏ qua đã xóa mềm). */
    @Override
    @Transactional(readOnly = true)
    public Optional<CommentSnapshot> findComment(Long commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .map(c -> new CommentSnapshot(
                        c.getId(),
                        c.getListingId(),
                        c.getUserId(),
                        c.getContent(),
                        Boolean.TRUE.equals(c.getIsSpam())));
    }

    /** Ghi kết quả phân tích cảm xúc ngược về các cột {@code sentiment_*} của bảng {@code comments}. */
    @Override
    @Transactional
    public void applySentiment(Long commentId, SentimentLabel label, BigDecimal score,
                               BigDecimal confidence, BigDecimal weight, boolean riskComment) {
        Comment c = commentRepository.findByIdAndDeletedAtIsNull(commentId).orElse(null);
        if (c == null) {
            // Bình luận đã bị xóa mềm giữa lúc phân tích — bỏ qua, không dựng dữ liệu mồ côi.
            return;
        }
        c.setSentimentLabel(label == null ? SentimentLabel.PENDING_ANALYSIS : label);
        c.setSentimentScore(score);
        c.setSentimentConfidence(confidence);
        if (weight != null) {
            c.setSentimentWeight(weight);
        }
        c.setIsRiskComment(riskComment);
        commentRepository.save(c);
    }
}
