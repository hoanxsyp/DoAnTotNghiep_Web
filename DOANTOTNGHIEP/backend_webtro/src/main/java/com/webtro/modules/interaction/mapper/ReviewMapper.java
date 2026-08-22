package com.webtro.modules.interaction.mapper;

import com.webtro.modules.interaction.dto.response.AdminReviewResponse;
import com.webtro.modules.interaction.dto.response.AuthorResponse;
import com.webtro.modules.interaction.dto.response.ReviewResponse;
import com.webtro.modules.interaction.entity.Review;
import com.webtro.modules.interaction.spi.UserGateway.UserBrief;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Ánh xạ Review → DTO (thủ công, Builder). Các trường {@code listing*}/{@code landlord*} bổ sung
 * do service set sau (chỉ có ở một số endpoint).
 */
@Component
public class ReviewMapper {

    public AuthorResponse toAuthor(UserBrief author) {
        if (author == null) {
            return null;
        }
        return AuthorResponse.builder()
                .id(author.id())
                .fullName(author.fullName())
                .avatarUrl(author.avatarUrl())
                .build();
    }

    public ReviewResponse toResponse(Review r, AuthorResponse author,
                                     boolean editable, Instant editableUntil) {
        return ReviewResponse.builder()
                .id(r.getId())
                .listingId(r.getListingId())
                .rating(r.getRating())
                .content(r.getContent())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .author(author)
                .editable(editable)
                .editableUntil(editableUntil)
                .editedAt(r.getEditedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    /**
     * Ánh xạ sang DTO cho màn hình kiểm duyệt (kèm thông tin ẩn và các khóa liên quan).
     *
     * @param author tác giả đã nạp qua gateway (có thể null nếu tài khoản đã bị xóa)
     */
    public AdminReviewResponse toAdminResponse(Review r, AuthorResponse author, Long landlordId) {
        return AdminReviewResponse.builder()
                .id(r.getId())
                .listingId(r.getListingId())
                .landlordId(landlordId)
                .rating(r.getRating())
                .content(r.getContent())
                .status(r.getStatus() == null ? null : r.getStatus().name())
                .statusLabel(r.getStatus() == null ? null : r.getStatus().getLabel())
                .author(author)
                .isVerifiedContact(r.getIsVerifiedContact())
                .hiddenReason(r.getHiddenReason())
                .hiddenBy(r.getHiddenBy())
                .hiddenAt(r.getHiddenAt())
                .editedAt(r.getEditedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
