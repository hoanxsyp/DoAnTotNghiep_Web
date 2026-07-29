package com.webtro.modules.interaction.spi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code interaction} cần từ module {@code user} — canonical luật 4.
 *
 * <p>Xem giải thích quyết định kiến trúc ở {@link ListingGateway}. Module {@code user} phải cung
 * cấp một {@code @Component} hiện thực interface này (bọc {@code UserRepository} +
 * {@code LandlordProfileRepository}).
 */
public interface UserGateway {

    /**
     * Thông tin công khai của một người dùng để hiển thị (tác giả bình luận/đánh giá, người liên hệ).
     *
     * @param memberSince thời điểm tạo tài khoản (users.created_at)
     */
    record UserBrief(Long id, String fullName, String avatarUrl, String phone, Instant memberSince) {
    }

    /** Tổng hợp đánh giá của chủ trọ (landlord_profiles) phục vụ tính lại trung bình khi có review mới. */
    record LandlordAggregate(BigDecimal averageRating, int reviewCount) {
    }

    /**
     * Thông tin liên hệ công khai của chủ trọ (từ {@code landlord_profiles} + {@code users}) — dùng
     * cho {@code GET /api/listings/{id}/contact-info}.
     *
     * @param contactZalo Zalo liên hệ; schema {@code landlord_profiles} chưa có cột riêng nên bên
     *                    hiện thực có thể trả trùng {@code contactPhone} (đúng ví dụ canonical 4.6.1).
     */
    record LandlordContactInfo(Long landlordId, String landlordName, String contactName,
                               String contactPhone, String contactZalo,
                               boolean chatEnabled, boolean verified) {
    }

    /** Thông tin liên hệ của chủ trọ; ném {@code USER_NOT_FOUND} nếu không có hồ sơ. */
    LandlordContactInfo getLandlordContactInfo(Long landlordId);

    /** Lấy thông tin người dùng; ném {@code ResourceNotFoundException(USER_NOT_FOUND)} nếu không có. */
    UserBrief getBrief(Long userId);

    /** Lấy thông tin người dùng nếu tồn tại. */
    Optional<UserBrief> findBrief(Long userId);

    /** Nạp hàng loạt (tránh N+1 khi render danh sách bình luận/đánh giá/liên hệ). */
    Map<Long, UserBrief> getBriefs(Collection<Long> userIds);

    /** Chủ trọ có bật chat không ({@code landlord_profiles.allow_chat}); false nếu không có hồ sơ chủ trọ. */
    boolean isLandlordChatEnabled(Long landlordId);

    /**
     * Người dùng có đang bị hạn chế liên hệ không ({@code users.contact_restricted_until} còn hiệu
     * lực, hoặc bị report SPAM đã xử lý) — canonical 4.6.1 quy tắc 5.
     */
    boolean isContactRestricted(Long userId);

    /** Người dùng có đang bị tạm khóa bình luận không ({@code users.comment_restricted_until}). */
    boolean isCommentSuspended(Long userId);

    /** Đọc tổng hợp đánh giá hiện tại của chủ trọ (để tính lại trung bình khi thêm/sửa/xóa review). */
    LandlordAggregate getLandlordAggregate(Long landlordId);

    /** Cập nhật {@code landlord_profiles.average_rating} + {@code review_count} (canonical 4.7.8 quy tắc 5). */
    void updateLandlordReviewAggregate(Long landlordId, BigDecimal averageRating, int reviewCount);
}
