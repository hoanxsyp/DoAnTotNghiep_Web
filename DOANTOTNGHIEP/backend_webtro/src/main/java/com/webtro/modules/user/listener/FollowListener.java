package com.webtro.modules.user.listener;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.event.ListingApprovedEvent;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.user.entity.Follow;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.FollowRepository;
import com.webtro.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Map;

/**
 * Lắng nghe {@link ListingApprovedEvent} để báo cho người theo dõi chủ trọ khi có tin mới được
 * duyệt (FOLLOW-02, {@code [§2.5]}, canonical §5).
 *
 * <p>Đặt ở module user vì dữ liệu quan hệ {@code follows} thuộc module này; giao tiếp ngược chiều
 * qua ApplicationEvent (canonical luật 7) — không để module listing phụ thuộc trực tiếp vào user.
 * Gửi sau khi giao dịch duyệt tin commit ({@link TransactionPhase#AFTER_COMMIT}) để không thông
 * báo nhầm khi duyệt bị rollback.
 */
@Slf4j
@RequiredArgsConstructor
@org.springframework.stereotype.Component
public class FollowListener {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // AFTER_COMMIT chạy sau khi giao dịch gốc đã commit -> không còn transaction đang mở, nên
    // listener PHẢI mở transaction MỚI (REQUIRES_NEW). readOnly=false vì notifyUser GHI bản ghi
    // notifications: @Transactional của notifyUser dùng REQUIRED nên nhập vào transaction của
    // listener; nếu để readOnly=true, kết nối bị đặt read-only và INSERT sẽ bị MySQL từ chối.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onListingApproved(ListingApprovedEvent event) {
        Long landlordId = event.ownerId();
        List<Follow> followers = followRepository.findByLandlord_IdAndDeletedAtIsNull(landlordId);
        if (followers.isEmpty()) {
            return;
        }
        String landlordName = userRepository.findByIdAndDeletedAtIsNull(landlordId)
                .map(User::getFullName)
                .orElse("Chủ trọ");
        String title = "Chủ trọ bạn theo dõi có tin mới";
        String content = landlordName + " vừa đăng một tin cho thuê mới.";
        String actionUrl = "/listings/" + event.listingId();

        for (Follow follow : followers) {
            if (!Boolean.TRUE.equals(follow.getNotifyNewListing())) {
                continue;
            }
            try {
                notificationService.notifyUser(
                        follow.getFollower().getId(),
                        NotificationType.FOLLOWED_LANDLORD_NEW_LISTING,
                        title, content, actionUrl,
                        Map.of("landlordId", landlordId, "listingId", event.listingId()));
            } catch (RuntimeException e) {
                // Một người nhận lỗi không được chặn những người còn lại.
                log.warn("Không gửi được thông báo tin mới cho follower {}: {}",
                        follow.getFollower().getId(), e.getMessage());
            }
        }
    }
}
