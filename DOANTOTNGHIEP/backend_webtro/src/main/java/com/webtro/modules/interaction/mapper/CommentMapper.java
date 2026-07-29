package com.webtro.modules.interaction.mapper;

import com.webtro.modules.interaction.dto.response.AdminCommentResponse;
import com.webtro.modules.interaction.dto.response.AuthorResponse;
import com.webtro.modules.interaction.dto.response.CommentResponse;
import com.webtro.modules.interaction.entity.Comment;
import com.webtro.modules.interaction.spi.UserGateway.UserBrief;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Ánh xạ Comment → DTO (thủ công, Builder). Cờ quyền ({@code editable}/{@code deletable}) và
 * {@code editableUntil} do service tính (phụ thuộc người xem + cửa sổ sửa + permission).
 */
@Component
public class CommentMapper {

    public AuthorResponse toAuthor(UserBrief author, Long listingOwnerId) {
        if (author == null) {
            return null;
        }
        return AuthorResponse.builder()
                .id(author.id())
                .fullName(author.fullName())
                .avatarUrl(author.avatarUrl())
                .isLandlordOfListing(listingOwnerId != null && listingOwnerId.equals(author.id()))
                .build();
    }

    public CommentResponse toResponse(Comment c, AuthorResponse author,
                                      boolean editable, Instant editableUntil, boolean deletable,
                                      List<CommentResponse> replies) {
        return CommentResponse.builder()
                .id(c.getId())
                .listingId(c.getListingId())
                .parentCommentId(c.getParent() == null ? null : c.getParent().getId())
                .content(c.getContent())
                .status(c.getStatus() == null ? null : c.getStatus().name())
                .author(author)
                .sentimentLabel(c.getSentimentLabel() == null ? null : c.getSentimentLabel().name())
                .sentimentScore(c.getSentimentScore())
                .editable(editable)
                .editableUntil(editableUntil)
                .deletable(deletable)
                .editedAt(c.getEditedAt())
                .createdAt(c.getCreatedAt())
                .replies(replies)
                .build();
    }

    /**
     * Ánh xạ sang DTO cho màn hình kiểm duyệt (kèm thông tin ẩn/spam/rủi ro và nhãn cảm xúc đầy đủ).
     *
     * @param author tác giả đã nạp qua gateway (có thể null nếu tài khoản đã bị xóa)
     */
    public AdminCommentResponse toAdminResponse(Comment c, AuthorResponse author) {
        return AdminCommentResponse.builder()
                .id(c.getId())
                .listingId(c.getListingId())
                .parentCommentId(c.getParent() == null ? null : c.getParent().getId())
                .content(c.getContent())
                .status(c.getStatus() == null ? null : c.getStatus().name())
                .statusLabel(c.getStatus() == null ? null : c.getStatus().getLabel())
                .author(author)
                .sentimentLabel(c.getSentimentLabel() == null ? null : c.getSentimentLabel().name())
                .sentimentLabelText(c.getSentimentLabel() == null ? null : c.getSentimentLabel().getLabel())
                .sentimentScore(c.getSentimentScore())
                .sentimentConfidence(c.getSentimentConfidence())
                .isSpam(c.getIsSpam())
                .isRiskComment(c.getIsRiskComment())
                .containsBannedKeyword(c.getContainsBannedKeyword())
                .isOwnerReply(c.getIsOwnerReply())
                .replyCount(c.getReplyCount())
                .hiddenReason(c.getHiddenReason())
                .hiddenBy(c.getHiddenBy())
                .hiddenAt(c.getHiddenAt())
                .editedAt(c.getEditedAt())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
