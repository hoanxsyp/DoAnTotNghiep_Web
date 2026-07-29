package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ReviewStatus;
import com.webtro.modules.interaction.dto.request.ModerationReasonRequest;
import com.webtro.modules.interaction.dto.response.AdminReviewResponse;
import com.webtro.modules.interaction.service.ReviewService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kiểm duyệt đánh giá (quản trị) — canonical 4.7.10, {@code [§3.12]}. Quyền chung
 * {@code REVIEW_MODERATE} (Moderator + Admin).
 */
@RestController
@RequestMapping("/api/admin/reviews")
@RequiredArgsConstructor
@Tag(name = "07. Comment & Review", description = "Bình luận và đánh giá tin")
public class AdminReviewController {

    private final ReviewService reviewService;

    /** Danh sách đánh giá cho kiểm duyệt: lọc theo trạng thái/số sao/tin/chủ trọ/từ khóa. */
    @GetMapping
    @PreAuthorize("hasAuthority('REVIEW_MODERATE')")
    @Operation(summary = "Danh sách đánh giá (kiểm duyệt)",
            description = "Lọc theo trạng thái, số sao, tin, chủ trọ và từ khóa nội dung; phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<AdminReviewResponse>>> list(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Integer rating,
            @RequestParam(required = false) Long listingId,
            @RequestParam(required = false) Long landlordId,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AdminReviewResponse> data = reviewService.adminListReviews(
                status, rating, listingId, landlordId, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách đánh giá thành công"));
    }

    /** Ẩn đánh giá vi phạm (kèm lý do bắt buộc). */
    @PutMapping("/{id}/hide")
    @PreAuthorize("hasAuthority('REVIEW_MODERATE')")
    @Operation(summary = "Ẩn đánh giá vi phạm")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> hide(
            @PathVariable Long id,
            @Valid @RequestBody ModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AdminReviewResponse data = reviewService.adminHideReview(id, currentUser.getId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã ẩn đánh giá"));
    }

    /** Khôi phục đánh giá đã ẩn. */
    @PutMapping("/{id}/unhide")
    @PreAuthorize("hasAuthority('REVIEW_MODERATE')")
    @Operation(summary = "Khôi phục đánh giá đã ẩn")
    public ResponseEntity<ApiResponse<AdminReviewResponse>> unhide(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AdminReviewResponse data = reviewService.unhideByModeration(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã khôi phục đánh giá"));
    }
}
