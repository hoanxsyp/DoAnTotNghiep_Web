package com.webtro.modules.moderation.spi;

import java.time.Instant;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code moderation} cần từ module {@code user} — canonical luật 4.
 *
 * <p>Xem giải thích quyết định kiến trúc ở {@link ListingModerationGateway}. Module {@code user}
 * phải cung cấp một {@code @Component} hiện thực interface này (bọc {@code UserRepository} +
 * {@code LandlordProfileRepository}).
 */
public interface UserModerationGateway {

    /**
     * Thông tin người dùng phục vụ kiểm duyệt.
     *
     * @param id          users.id
     * @param fullName    họ tên hiển thị
     * @param email       email
     * @param memberSince thời điểm tạo tài khoản (dùng suy ra tuổi tài khoản)
     * @param trustScore  điểm uy tín chủ trọ (0..100); 100 nếu chưa phải chủ trọ
     * @param isAdmin     có phải quản trị viên không (không được cảnh báo/khóa Admin khác)
     */
    record UserRef(Long id, String fullName, String email, Instant memberSince,
                   int trustScore, boolean isAdmin) {
    }

    /** Lấy thông tin người dùng còn sống; ném {@code ResourceNotFoundException(USER_NOT_FOUND)} nếu không có. */
    UserRef getRef(Long userId);

    /** Lấy thông tin người dùng nếu tồn tại (không ném). */
    Optional<UserRef> findRef(Long userId);

    /** Người dùng còn sống có tồn tại không. */
    boolean existsById(Long userId);

    /**
     * Tạm khóa chức năng đăng tin tới thời điểm {@code until} (canonical §5.4: 3 cảnh báo/30 ngày
     * → khóa đăng tin tạm thời). Bên hiện thực đặt {@code users.posting_suspended_until}.
     */
    void suspendPosting(Long userId, Instant until, String reason);
}
