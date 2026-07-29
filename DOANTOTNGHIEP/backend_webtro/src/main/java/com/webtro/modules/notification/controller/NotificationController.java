package com.webtro.modules.notification.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.enums.NotificationType;
import com.webtro.modules.notification.dto.request.UpdatePreferenceRequest;
import com.webtro.modules.notification.dto.response.MarkAllReadResponse;
import com.webtro.modules.notification.dto.response.MarkReadResponse;
import com.webtro.modules.notification.dto.response.NotificationListResponse;
import com.webtro.modules.notification.dto.response.NotificationPreferencesResponse;
import com.webtro.modules.notification.dto.response.UnreadCountResponse;
import com.webtro.modules.notification.service.NotificationQueryService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API thông báo người dùng (NOTI-01→06) — docs/03 mục 4.10.
 *
 * <p>Mọi endpoint yêu cầu đăng nhập và chỉ thao tác trên thông báo của chính người dùng
 * (kiểm quyền sở hữu ở tầng service).
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "10. Notification", description = "Thông báo trong ứng dụng và cài đặt nhận thông báo")
public class NotificationController {

    private final NotificationQueryService notificationQueryService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách thông báo của tôi",
            description = "Phân trang, lọc theo loại và trạng thái chưa đọc; kèm tổng số chưa đọc.")
    public ResponseEntity<ApiResponse<NotificationListResponse>> list(
            @RequestParam(required = false) List<NotificationType> type,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        NotificationListResponse data = notificationQueryService.list(user.getId(), type, unreadOnly, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy thông báo thành công"));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Số thông báo chưa đọc",
            description = "Endpoint nhẹ để FE poll badge; gộp cả số tin nhắn chat chưa đọc.")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal CustomUserDetails user) {
        UnreadCountResponse data = notificationQueryService.unreadCount(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy số thông báo chưa đọc thành công"));
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc một thông báo",
            description = "Idempotent: đánh dấu lại thông báo đã đọc vẫn trả 200, giữ nguyên thời điểm đọc lần đầu.")
    public ResponseEntity<ApiResponse<MarkReadResponse>> markRead(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        MarkReadResponse data = notificationQueryService.markRead(user.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(data, "Đã đánh dấu đã đọc"));
    }

    @PutMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đánh dấu đã đọc tất cả",
            description = "Truyền tham số type để chỉ đánh dấu một loại.")
    public ResponseEntity<ApiResponse<MarkAllReadResponse>> markAllRead(
            @RequestParam(required = false) NotificationType type,
            @AuthenticationPrincipal CustomUserDetails user) {
        MarkAllReadResponse data = notificationQueryService.markAllRead(user.getId(), type);
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã đánh dấu " + data.getMarkedCount() + " thông báo là đã đọc"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa một thông báo", description = "Xóa mềm thông báo của chính người dùng.")
    public ResponseEntity<Void> delete(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        notificationQueryService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem cài đặt thông báo",
            description = "16 loại; loại nào chưa lưu tùy chọn = mặc định; loại bắt buộc có optional=false (không tắt được).")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> getPreferences(
            @AuthenticationPrincipal CustomUserDetails user) {
        NotificationPreferencesResponse data = notificationQueryService.getPreferences(user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy cài đặt thông báo thành công"));
    }

    @PutMapping("/preferences")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật cài đặt thông báo",
            description = "Không cho tắt loại bắt buộc (optional=false) — vi phạm trả 422 NOTIFICATION_TYPE_NOT_OPTIONAL.")
    public ResponseEntity<ApiResponse<NotificationPreferencesResponse>> updatePreferences(
            @Valid @RequestBody UpdatePreferenceRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        NotificationPreferencesResponse data = notificationQueryService.updatePreferences(user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật cài đặt thông báo thành công"));
    }
}
