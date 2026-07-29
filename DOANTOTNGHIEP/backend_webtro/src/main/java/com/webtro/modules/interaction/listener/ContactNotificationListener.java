package com.webtro.modules.interaction.listener;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.event.ContactCreatedEvent;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.modules.interaction.spi.UserGateway;
import com.webtro.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/**
 * Lắng nghe {@link ContactCreatedEvent} để báo cho chủ trọ khi có người liên hệ tin đăng
 * (NOTI-04, {@code [§5.6]}, canonical §5 — luật 7 giao tiếp ngược chiều qua ApplicationEvent).
 *
 * <p>Đặt ở module interaction vì dữ liệu liên hệ ({@code contact_logs}, hội thoại) thuộc module
 * này; sự kiện được phát bởi cả gửi yêu cầu liên hệ ({@code ContactServiceImpl}) lẫn tin nhắn
 * đầu tiên trong hội thoại ({@code ConversationServiceImpl}), nên gộp một nơi xử lý tránh lặp.
 * Gửi sau khi giao dịch ghi liên hệ commit ({@link TransactionPhase#AFTER_COMMIT}) để không báo
 * nhầm khi giao dịch bị rollback. {@code notifyUser} tự tôn trọng tùy chọn nhận thông báo và
 * gửi kèm email nếu chủ trọ bật.
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ContactNotificationListener {

    private static final String CONTACTS_PAGE = "/quan-ly/nguoi-lien-he";

    private final ListingGateway listingGateway;
    private final UserGateway userGateway;
    private final NotificationService notificationService;

    // AFTER_COMMIT chạy sau khi giao dịch gốc đã commit -> không còn transaction đang mở, nên
    // listener PHẢI mở transaction MỚI (REQUIRES_NEW). readOnly=false vì notifyUser GHI bản ghi
    // notifications: @Transactional của notifyUser dùng REQUIRED nên nhập vào transaction của
    // listener; nếu để readOnly=true, kết nối bị đặt read-only và INSERT sẽ bị MySQL từ chối.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onContactCreated(ContactCreatedEvent event) {
        String contacterName = userGateway.findBrief(event.contacterId())
                .map(UserGateway.UserBrief::fullName)
                .orElse("Một người dùng");
        String listingTitle = safeListingTitle(event.listingId());

        String title = "Có người liên hệ tin đăng của bạn";
        String content = contacterName + " vừa liên hệ về tin \"" + listingTitle + "\".";

        try {
            notificationService.notifyUser(
                    event.ownerId(),
                    NotificationType.NEW_CONTACT,
                    title, content, CONTACTS_PAGE,
                    Map.of("listingId", event.listingId(), "contacterId", event.contacterId()));
        } catch (RuntimeException e) {
            // Lỗi gửi thông báo không được ảnh hưởng luồng liên hệ đã hoàn tất.
            log.warn("Không gửi được thông báo liên hệ cho chủ trọ {} (tin {}): {}",
                    event.ownerId(), event.listingId(), e.getMessage());
        }
    }

    private String safeListingTitle(Long listingId) {
        try {
            ListingGateway.ListingBrief brief = listingGateway.getBrief(listingId);
            if (brief != null && brief.title() != null && !brief.title().isBlank()) {
                return brief.title();
            }
        } catch (RuntimeException e) {
            log.debug("Không lấy được tiêu đề tin {} khi báo liên hệ: {}", listingId, e.getMessage());
        }
        return "tin đăng của bạn";
    }
}
