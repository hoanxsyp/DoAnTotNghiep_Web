package com.webtro.modules.notification.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.NotificationType;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.service.ConversationService;
import com.webtro.modules.notification.dto.request.UpdatePreferenceRequest;
import com.webtro.modules.notification.dto.response.MarkAllReadResponse;
import com.webtro.modules.notification.dto.response.MarkReadResponse;
import com.webtro.modules.notification.dto.response.NotificationListResponse;
import com.webtro.modules.notification.dto.response.NotificationPreferenceResponse;
import com.webtro.modules.notification.dto.response.NotificationResponse;
import com.webtro.modules.notification.dto.response.NotificationPreferencesResponse;
import com.webtro.modules.notification.dto.response.UnreadCountResponse;
import com.webtro.modules.notification.entity.Notification;
import com.webtro.modules.notification.entity.NotificationPreference;
import com.webtro.modules.notification.mapper.NotificationMapper;
import com.webtro.modules.notification.repository.NotificationPreferenceRepository;
import com.webtro.modules.notification.repository.NotificationRepository;
import com.webtro.modules.notification.service.NotificationDefaults;
import com.webtro.modules.notification.service.NotificationQueryService;
import com.webtro.security.RateLimitService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Hiện thực thao tác đọc/đánh dấu/cài đặt thông báo (NOTI-01→06, docs/03 mục 4.10).
 *
 * <p>Rate limit (docs/03): unread-count 60/phút, read-all 10/phút, cập nhật cài đặt 20/giờ. Ba
 * ngưỡng này CHƯA có {@code ConfigKey} riêng (canonical §9 không liệt kê) nên tạm dùng hằng số nội
 * bộ — kiến nghị bổ sung config key để đồng bộ với chính sách "không hardcode ngưỡng".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationQueryServiceImpl implements NotificationQueryService {

    /** Trường sort duy nhất được phép (docs/03 mục 4.10.1). */
    private static final String SORT_FIELD_CREATED_AT = "createdAt";

    private static final int UNREAD_COUNT_MAX_PER_MINUTE = 60;
    private static final int READ_ALL_MAX_PER_MINUTE = 10;
    private static final int UPDATE_PREF_MAX_PER_HOUR = 20;

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationMapper notificationMapper;
    private final RateLimitService rateLimitService;
    private final ConversationService conversationService;

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse list(Long userId, List<NotificationType> types, boolean unreadOnly,
                                         Pageable pageable) {
        validateSort(pageable.getSort());
        Specification<Notification> spec = buildSpec(userId, types, unreadOnly);
        Page<Notification> page = notificationRepository.findAll(spec, pageable);
        PageResponse<NotificationResponse> body = PageResponse.from(page, notificationMapper::toResponse);
        long unread = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
        return NotificationListResponse.builder()
                .page(body)
                .unreadCount(unread)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(Long userId) {
        rateLimitService.checkLimit("notif-unread-count", String.valueOf(userId),
                UNREAD_COUNT_MAX_PER_MINUTE, Duration.ofMinutes(1));
        long unread = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
        long unreadMessages = conversationService.countUnreadMessages(userId);
        return UnreadCountResponse.builder()
                .unreadCount(unread)
                .unreadMessageCount(unreadMessages)
                .build();
    }

    @Override
    @Transactional
    public MarkReadResponse markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        // Idempotent: đã đọc rồi thì giữ nguyên read_at lần đầu (docs/03 mục 4.10.3).
        if (!Boolean.TRUE.equals(n.getIsRead())) {
            n.setIsRead(true);
            n.setReadAt(Instant.now());
            notificationRepository.save(n);
        }
        long unread = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
        return MarkReadResponse.builder()
                .id(n.getId())
                .read(true)
                .readAt(n.getReadAt())
                .unreadCount(unread)
                .build();
    }

    @Override
    @Transactional
    public MarkAllReadResponse markAllRead(Long userId, NotificationType type) {
        rateLimitService.checkLimit("notif-read-all", String.valueOf(userId),
                READ_ALL_MAX_PER_MINUTE, Duration.ofMinutes(1));
        Instant now = Instant.now();
        int marked;
        if (type == null) {
            marked = notificationRepository.markAllRead(userId, now);
        } else {
            // Đánh dấu theo một loại: lọc qua Specification rồi cập nhật trong bộ nhớ.
            Specification<Notification> spec = buildSpec(userId, List.of(type), true);
            List<Notification> unreadOfType = notificationRepository.findAll(spec);
            for (Notification n : unreadOfType) {
                n.setIsRead(true);
                n.setReadAt(now);
            }
            notificationRepository.saveAll(unreadOfType);
            marked = unreadOfType.size();
        }
        long unread = notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
        return MarkAllReadResponse.builder()
                .markedCount(marked)
                .unreadCount(unread)
                .build();
    }

    @Override
    @Transactional
    public void delete(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndDeletedAtIsNull(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOTIFICATION_NOT_FOUND));
        if (!n.getUserId().equals(userId)) {
            throw new ForbiddenException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }
        n.softDelete();
        notificationRepository.save(n);
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getPreferences(Long userId) {
        Map<NotificationType, NotificationPreference> saved = preferenceRepository
                .findByUserIdAndDeletedAtIsNull(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getNotificationType,
                        Function.identity(), (a, b) -> a));
        List<NotificationPreferenceResponse> items = NotificationDefaults.PREFERENCE_TYPES.stream()
                .map(type -> notificationMapper.toPreferenceResponse(type, saved.get(type)))
                .toList();
        return NotificationPreferencesResponse.builder().preferences(items).build();
    }

    @Override
    @Transactional
    public NotificationPreferencesResponse updatePreferences(Long userId, UpdatePreferenceRequest request) {
        rateLimitService.checkLimit("notif-pref-update", String.valueOf(userId),
                UPDATE_PREF_MAX_PER_HOUR, Duration.ofHours(1));

        for (UpdatePreferenceRequest.PreferenceItem item : request.getPreferences()) {
            NotificationType type = item.getType();
            if (!NotificationDefaults.isConfigurable(type)) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "Loại thông báo không hỗ trợ cài đặt: " + type);
            }
            boolean inApp = Boolean.TRUE.equals(item.getInApp());
            boolean email = Boolean.TRUE.equals(item.getEmail());
            if (NotificationDefaults.isMandatory(type)) {
                if (!inApp || !email) {
                    throw new BusinessRuleException(ErrorCode.NOTIFICATION_TYPE_NOT_OPTIONAL,
                            "Không thể tắt loại thông báo quan trọng: " + type.getLabel());
                }
                // Loại bắt buộc luôn bật cả hai kênh — không cần lưu bản ghi ngoại lệ.
                continue;
            }
            upsertPreference(userId, type, inApp, email);
        }
        return getPreferences(userId);
    }

    // ------------------------------------------------------------------

    private void upsertPreference(Long userId, NotificationType type, boolean inApp, boolean email) {
        NotificationPreference existing = preferenceRepository
                .findByUserIdAndNotificationTypeAndDeletedAtIsNull(userId, type)
                .orElse(null);
        if (existing != null) {
            existing.setInApp(inApp);
            existing.setEmail(email);
            preferenceRepository.save(existing);
        } else {
            preferenceRepository.save(NotificationPreference.builder()
                    .userId(userId)
                    .notificationType(type)
                    .inApp(inApp)
                    .email(email)
                    .build());
        }
    }

    private Specification<Notification> buildSpec(Long userId, List<NotificationType> types, boolean unreadOnly) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("userId"), userId));
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (types != null && !types.isEmpty()) {
                predicates.add(root.get("type").in(types));
            }
            if (unreadOnly) {
                predicates.add(cb.equal(root.get("isRead"), Boolean.FALSE));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /** Chỉ cho phép sort theo {@code createdAt} (docs/03 mục 4.10.1) — sai → 400 INVALID_SORT_FIELD. */
    private void validateSort(Sort sort) {
        for (Sort.Order order : sort) {
            if (!SORT_FIELD_CREATED_AT.equals(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_SORT_FIELD,
                        "Không thể sắp xếp theo trường " + order.getProperty());
            }
        }
    }
}
