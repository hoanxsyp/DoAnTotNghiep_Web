package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.BulkActionResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.admin.dto.request.ApproveListingRequest;
import com.webtro.modules.admin.dto.request.BulkListingActionRequest;
import com.webtro.modules.admin.dto.request.ListingModerationReasonRequest;
import com.webtro.modules.admin.dto.request.LockListingRequest;
import com.webtro.modules.admin.dto.request.RejectListingRequest;
import com.webtro.modules.admin.dto.request.RequestListingEditRequest;
import com.webtro.modules.admin.dto.request.UnlockListingRequest;
import com.webtro.modules.admin.dto.response.AdminListingActionResponse;
import com.webtro.modules.admin.dto.response.AdminListingResponse;
import com.webtro.modules.admin.service.AdminListingService;
import com.webtro.modules.moderation.dto.response.AdminModerationActionResponse;
import com.webtro.modules.moderation.service.ModerationService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * API kiểm duyệt tin đăng + hàng đợi/nhật ký kiểm duyệt (canonical 4.14). Xem cần
 * {@code LISTING_VIEW_ANY}; duyệt/từ chối/ẩn/gắn cờ cần {@code LISTING_MODERATE}; khóa/mở khóa cần
 * {@code LISTING_LOCK} (chỉ Admin).
 *
 * <p>Base path đặt ở {@code /api/admin} để cùng controller phục vụ cả nhóm {@code /listings/**} lẫn
 * các endpoint hàng đợi ({@code /moderation-queue}) và nhật ký ({@code /moderation-actions}).
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "14. Admin - Listing", description = "Kiểm duyệt tin đăng")
public class AdminListingController {

    private final AdminListingService adminListingService;
    private final ModerationService moderationService;

    @GetMapping("/listings")
    @PreAuthorize("hasAuthority('LISTING_VIEW_ANY')")
    @Operation(summary = "Quản lý tin đăng", description = "Lọc theo trạng thái, chủ trọ, danh mục, khu vực, lệch giá, thời gian.")
    public ResponseEntity<ApiResponse<PageResponse<AdminListingResponse>>> searchListings(
            @RequestParam(required = false) List<ListingStatus> status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long provinceId,
            @RequestParam(required = false) Long districtId,
            @RequestParam(required = false) Long wardId,
            @RequestParam(required = false) Boolean priceDeviationFlagged,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        PageResponse<AdminListingResponse> data = adminListingService.searchListings(
                status, keyword, ownerId, categoryId, provinceId, districtId, wardId,
                priceDeviationFlagged, fromInstant, toInstant, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tin đăng thành công"));
    }

    @GetMapping("/moderation-queue")
    @PreAuthorize("hasAuthority('LISTING_VIEW_ANY')")
    @Operation(summary = "Hàng đợi kiểm duyệt",
            description = "Tin chờ xử lý (PENDING + NEED_REVIEW), phân trang, ưu tiên tin cũ trước.")
    public ResponseEntity<ApiResponse<PageResponse<AdminListingResponse>>> moderationQueue(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        PageResponse<AdminListingResponse> data = adminListingService.moderationQueue(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy hàng đợi kiểm duyệt thành công"));
    }

    @GetMapping("/moderation-actions")
    @PreAuthorize("hasAuthority('LISTING_VIEW_ANY')")
    @Operation(summary = "Nhật ký hành động kiểm duyệt",
            description = "Lịch sử hành động kiểm duyệt gần đây (mới nhất trước), lọc tùy chọn theo đối tượng và tin.")
    public ResponseEntity<ApiResponse<PageResponse<AdminModerationActionResponse>>> moderationActions(
            @RequestParam(required = false) ReportTargetType targetType,
            @RequestParam(required = false) Long listingId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AdminModerationActionResponse> data =
                moderationService.adminListActions(targetType, listingId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy nhật ký kiểm duyệt thành công"));
    }

    @GetMapping("/listings/{id}")
    @PreAuthorize("hasAuthority('LISTING_VIEW_ANY')")
    @Operation(summary = "Chi tiết tin (Admin)", description = "Chi tiết một tin cho kiểm duyệt, gồm cả tin không công khai.")
    public ResponseEntity<ApiResponse<AdminListingResponse>> getListingDetail(@PathVariable Long id) {
        AdminListingResponse data = adminListingService.getListingDetail(id);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết tin đăng thành công"));
    }

    @PutMapping("/listings/{id}/approve")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Duyệt tin", description = "PENDING → ACTIVE; đặt publishedAt/expiredAt; báo chủ trọ + người theo dõi.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> approve(
            @PathVariable Long id, @Valid @RequestBody(required = false) ApproveListingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ApproveListingRequest body = request != null ? request : new ApproveListingRequest();
        AdminListingActionResponse data = adminListingService.approve(id, body, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã duyệt tin đăng"));
    }

    @PutMapping("/listings/{id}/reject")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Từ chối tin", description = "PENDING → REJECTED; bắt buộc lý do; báo chủ trọ.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> reject(
            @PathVariable Long id, @Valid @RequestBody RejectListingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        AdminListingActionResponse data = adminListingService.reject(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã từ chối tin đăng"));
    }

    @PutMapping("/listings/{id}/hide")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Ẩn tin", description = "ACTIVE/NEED_REVIEW → HIDDEN do kiểm duyệt; ghi nhật ký + báo chủ trọ.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> hide(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ListingModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = request != null ? request.getReason() : null;
        AdminListingActionResponse data = adminListingService.hide(id, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã ẩn tin đăng"));
    }

    @PutMapping("/listings/{id}/unhide")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Bỏ ẩn tin", description = "HIDDEN → ACTIVE; khôi phục hiển thị công khai; báo chủ trọ.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> unhide(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ListingModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = request != null ? request.getReason() : null;
        AdminListingActionResponse data = adminListingService.unhide(id, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã bỏ ẩn tin đăng"));
    }

    @PutMapping("/listings/{id}/flag-need-review")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Đánh dấu cần kiểm tra", description = "ACTIVE → NEED_REVIEW; đưa tin vào hàng đợi kiểm duyệt.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> flagNeedReview(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ListingModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = request != null ? request.getReason() : null;
        AdminListingActionResponse data = adminListingService.flagNeedReview(id, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã đánh dấu tin cần kiểm tra"));
    }

    @PutMapping("/listings/{id}/clear-need-review")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Bỏ đánh dấu cần kiểm tra", description = "NEED_REVIEW → ACTIVE khi kết luận không vi phạm.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> clearNeedReview(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ListingModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        String reason = request != null ? request.getReason() : null;
        AdminListingActionResponse data = adminListingService.clearNeedReview(id, reason, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã bỏ đánh dấu cần kiểm tra"));
    }

    @PutMapping("/listings/{id}/lock")
    @PreAuthorize("hasAuthority('LISTING_LOCK')")
    @Operation(summary = "Khóa tin", description = "→ LOCKED; bắt buộc lý do + mức độ vi phạm; báo chủ trọ.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> lock(
            @PathVariable Long id, @Valid @RequestBody LockListingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        AdminListingActionResponse data = adminListingService.lock(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã khóa tin đăng"));
    }

    @PutMapping("/listings/{id}/unlock")
    @PreAuthorize("hasAuthority('LISTING_LOCK')")
    @Operation(summary = "Mở khóa tin", description = "LOCKED → HIDDEN; chủ trọ tự bật lại sau khi sửa.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> unlock(
            @PathVariable Long id, @Valid @RequestBody UnlockListingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        AdminListingActionResponse data = adminListingService.unlock(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã mở khóa tin đăng"));
    }

    @PutMapping("/listings/{id}/request-edit")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Yêu cầu chỉnh sửa tin",
            description = "Gửi yêu cầu chủ trọ chỉnh sửa tin (ghi hành động REQUEST_EDIT + thông báo); KHÔNG đổi trạng thái tin.")
    public ResponseEntity<ApiResponse<AdminListingActionResponse>> requestEdit(
            @PathVariable Long id, @Valid @RequestBody RequestListingEditRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        AdminListingActionResponse data =
                adminListingService.requestEdit(id, request.getReason(), principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã gửi yêu cầu chỉnh sửa tin"));
    }

    @PutMapping("/listings/bulk")
    @PreAuthorize("hasAuthority('LISTING_MODERATE')")
    @Operation(summary = "Kiểm duyệt tin hàng loạt",
            description = "Áp một hành động (APPROVE/REJECT/LOCK/HIDE) cho nhiều tin; mỗi tin xử lý độc lập; trả về danh sách thành công và thất bại.")
    public ResponseEntity<ApiResponse<BulkActionResponse>> bulkModerate(
            @Valid @RequestBody BulkListingActionRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        BulkActionResponse data = adminListingService.bulkModerate(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã xử lý thao tác hàng loạt"));
    }
}
