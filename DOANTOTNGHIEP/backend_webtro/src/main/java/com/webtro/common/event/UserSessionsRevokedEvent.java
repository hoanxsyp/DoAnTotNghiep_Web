package com.webtro.common.event;

/**
 * Sự kiện phát ra khi mọi phiên đăng nhập của một người dùng bị thu hồi (Admin khóa tài khoản, đổi vai trò).
 *
 * <p>Module {@code admin} publish, module {@code interaction} lắng nghe để đóng các kết nối WebSocket
 * chat đang mở của người đó — token chỉ được kiểm lúc bắt tay nên nếu không đóng, kết nối cũ vẫn nhận
 * tin nhắn vô thời hạn.
 *
 * @param userId id người dùng bị thu hồi phiên
 * @param reason lý do thu hồi (ví dụ {@code ADMIN_LOCK}, {@code ROLE_CHANGE})
 */
public record UserSessionsRevokedEvent(Long userId, String reason) {
}
