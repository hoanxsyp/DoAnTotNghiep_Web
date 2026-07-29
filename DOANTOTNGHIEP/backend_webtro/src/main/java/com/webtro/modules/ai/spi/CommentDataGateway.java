package com.webtro.modules.ai.spi;

import com.webtro.common.enums.SentimentLabel;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code ai} cần từ module {@code interaction} để phân tích cảm xúc bình
 * luận — canonical luật 4 (gọi module khác qua interface, không đụng repository của nó) và luật 7/8.
 *
 * <p><b>Quyết định kiến trúc:</b> hợp đồng cross-module do <i>bên tiêu dùng sở hữu</i> (đặt tại
 * {@code ai.spi}), theo đúng khuôn mẫu {@code interaction.spi.ListingGateway}. Module
 * {@code interaction} phải cung cấp một {@code @Component} hiện thực interface này (ví dụ
 * {@code CommentDataGatewayImpl} bọc {@code CommentRepository}).
 *
 * <p>Đây là hiện thực của ghi chú trong entity {@code Comment}: <i>"Các cột sentiment_* do
 * SentimentAnalyzer (module ai) cập nhật qua service của module interaction (không @Autowired
 * ngược)"</i>.
 */
public interface CommentDataGateway {

    /**
     * Ảnh chụp tối thiểu của một bình luận phục vụ phân tích cảm xúc.
     *
     * @param id        id bình luận
     * @param listingId tin đăng chứa bình luận
     * @param userId    tác giả bình luận
     * @param content   nội dung thô (chưa chuẩn hóa)
     * @param spam      bình luận đã bị Moderator đánh dấu spam (loại khỏi thống kê uy tín §9.1)
     */
    record CommentSnapshot(Long id, Long listingId, Long userId, String content, boolean spam) {
    }

    /** Nạp ảnh chụp bình luận còn sống theo id (bỏ qua đã xóa mềm). */
    Optional<CommentSnapshot> findComment(Long commentId);

    /**
     * Ghi kết quả phân tích cảm xúc ngược về bảng {@code comments} (các cột sentiment_*). Được gọi
     * trong transaction riêng của module ai sau khi phân tích xong.
     *
     * @param commentId   bình luận đích
     * @param label       nhãn cảm xúc
     * @param score       điểm [-1, 1] (có thể {@code null} khi PENDING_ANALYSIS)
     * @param confidence  độ tin cậy [0, 1] (có thể {@code null})
     * @param weight      trọng số bình luận trong công thức uy tín [0, 1]
     * @param riskComment có phải bình luận rủi ro không
     */
    void applySentiment(Long commentId, SentimentLabel label, BigDecimal score,
                        BigDecimal confidence, BigDecimal weight, boolean riskComment);
}
