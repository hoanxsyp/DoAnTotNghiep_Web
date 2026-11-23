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
     * Thông tin công khai của một người dùng để hiển thị (tác giả bình luận/đánh giá hoặc người tham gia hội thoại).
     *
     * @param memberSince thời điểm tạo tài khoản (users.created_at)
     */
    record UserBrief(Long id, String fullName, String avatarUrl, String phone, Instant memberSince) {
    }

    /** Tổng hợp đánh giá của chủ trọ (landlord_profiles) phục vụ tính lại trung bình khi có review mới. */
    record LandlordAggregate(BigDecimal averageRating, int reviewCount) {
    }

    /** Lấy thông tin người dùng; ném {@code ResourceNotFoundException(USER_NOT_FOUND)} nếu không có. */
    UserBrief getBrief(Long userId);

    /** Lấy thông tin người dùng nếu tồn tại. */
    Optional<UserBrief> findBrief(Long userId);

    /** Nạp hàng loạt (tránh N+1 khi render danh sách bình luận/đánh giá). */
    Map<Long, UserBrief> getBriefs(Collection<Long> userIds);

    /** Chủ trọ có bật chat không ({@code landlord_profiles.allow_chat}); false nếu không có hồ sơ chủ trọ. */
    boolean isLandlordChatEnabled(Long landlordId);

    /** Người dùng có hồ sơ chủ trọ không; tenant đăng tin ở ghép thì không có. */
    boolean hasLandlordProfile(Long userId);

    /**
     * Người dùng có đang bị hạn chế liên hệ không ({@code users.contact_restricted_until} còn hiệu
     * lực, hoặc bị report SPAM đã xử lý) — canonical 4.6.1 quy tắc 5.
     */
    boolean isContactRestricted(Long userId);

    /** Người dùng có đang bị tạm khóa bình luận không ({@code users.comment_restricted_until}). */
    boolean isCommentSuspended(Long userId);

    /** Đọc tổng hợp đánh giá hiện tại của chủ trọ (để tính lại trung bình khi thêm/sửa/xóa review). */
    LandlordAggregate getLandlordAggregate(Long landlordId);

    /**
     * Cập nhật {@code landlord_profiles.average_rating} + {@code review_count}. Chủ phòng không có
     * hồ sơ chủ trọ (ví dụ người thuê đăng tin ở ghép) thì bỏ qua.
     */
    void updateLandlordReviewAggregate(Long landlordId, BigDecimal averageRating, int reviewCount);

    /**
     * Khoá dòng hồ sơ chủ trọ ({@code SELECT … FOR UPDATE}) trước khi tính lại điểm chủ trọ
     * (mục 10.2 bước 1). Trả về {@code false} nếu người này không có hồ sơ chủ trọ.
     */
    boolean lockLandlordProfile(Long landlordId);
}
