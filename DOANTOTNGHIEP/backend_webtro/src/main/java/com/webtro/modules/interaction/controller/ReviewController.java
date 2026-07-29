package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.CreateReviewRequest;
import com.webtro.modules.interaction.dto.response.ReviewEligibilityResponse;
import com.webtro.modules.interaction.dto.response.ReviewPageResponse;
import com.webtro.modules.interaction.dto.response.ReviewResponse;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API đánh giá (REV) — canonical mục 4.7.6–4.7.12, {@code [§3.12]}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "07. Comment & Review", description = "Bình luận và đánh giá tin")
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/listings/{id}/reviews")
    @Operation(summary = "Danh sách đánh giá của tin (công khai)")
    public ResponseEntity<ApiResponse<ReviewPageResponse>> listListingReviews(
            @PathVariable Long id,
            @RequestParam(required = false) Integer rating,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ReviewPageResponse data = reviewService.listListingReviews(id, rating, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy đánh giá thành công"));
    }

    @GetMapping("/listings/{id}/reviews/eligibility")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Kiểm tra điều kiện đánh giá")
    public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> eligibility(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        ReviewEligibilityResponse data = reviewService.checkEligibility(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, data.getReasonMessage() != null
                ? data.getReasonMessage() : "Bạn có thể đánh giá tin đăng này"));
    }

    @PostMapping("/listings/{id}/reviews")
    @PreAuthorize("hasAuthority('REVIEW_CREATE')")
    @Operation(summary = "Tạo đánh giá")
    public ResponseEntity<ApiResponse<ReviewResponse>> create(
            @PathVariable Long id, @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ReviewResponse data = reviewService.createReview(id, request, user.getId());
        return ResponseEntity.created(URI.create("/api/reviews/" + data.getId()))
                .body(ApiResponse.success(data, "Cảm ơn bạn đã gửi đánh giá!"));
    }

    @PutMapping("/reviews/{id}")
    @PreAuthorize("hasAuthority('REVIEW_CREATE')")
    @Operation(summary = "Sửa đánh giá (trong cửa sổ cho phép)")
    public ResponseEntity<ApiResponse<ReviewResponse>> update(
            @PathVariable Long id, @Valid @RequestBody CreateReviewRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ReviewResponse data = reviewService.updateReview(id, request, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã cập nhật đánh giá"));
    }

    @DeleteMapping("/reviews/{id}")
    @PreAuthorize("hasAnyAuthority('REVIEW_CREATE','REVIEW_MODERATE')")
    @Operation(summary = "Xóa (tác giả) hoặc ẩn (kiểm duyệt viên) đánh giá")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestParam(required = false) String reason,
            @AuthenticationPrincipal CustomUserDetails user) {
        reviewService.deleteReview(id, user.getId(), reason);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reviews/my")
    @PreAuthorize("hasAuthority('REVIEW_CREATE')")
    @Operation(summary = "Đánh giá của tôi")
    public ResponseEntity<ApiResponse<PageResponse<ReviewResponse>>> myReviews(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<ReviewResponse> data = reviewService.listMyReviews(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy đánh giá của bạn thành công"));
    }

    @GetMapping("/users/{id}/reviews")
    @Operation(summary = "Đánh giá về một chủ trọ (tổng hợp)")
    public ResponseEntity<ApiResponse<ReviewPageResponse>> landlordReviews(
            @PathVariable Long id,
            @RequestParam(required = false) Integer rating,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        ReviewPageResponse data = reviewService.listLandlordReviews(id, rating, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy đánh giá về chủ trọ thành công"));
    }
}
