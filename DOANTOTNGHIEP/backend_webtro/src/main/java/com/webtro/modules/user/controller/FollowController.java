package com.webtro.modules.user.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.user.dto.response.FollowResponse;
import com.webtro.modules.user.dto.response.FollowingItemResponse;
import com.webtro.modules.user.service.FollowService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Set;

/**
 * API theo dõi chủ trọ (FOLLOW-01/02, docs/03 mục 4.2.7-4.2.9).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "02. User", description = "Theo dõi chủ trọ")
public class FollowController {

    /** Trường sắp xếp hợp lệ cho danh sách đang theo dõi (mục 4.2.9). */
    private static final Set<String> ALLOWED_SORT = Set.of("createdAt", "trustScore");

    private final FollowService followService;

    @PostMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Theo dõi chủ trọ", description = "Bắt đầu theo dõi một chủ trọ để nhận thông báo tin mới (FOLLOW-01)")
    public ResponseEntity<ApiResponse<FollowResponse>> follow(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        FollowResponse data = followService.follow(currentUser.getId(), id);
        return ResponseEntity.created(URI.create("/api/users/" + id + "/follow"))
                .body(ApiResponse.success(data, "Đã theo dõi chủ trọ"));
    }

    @DeleteMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Bỏ theo dõi", description = "Ngừng theo dõi một chủ trọ (FOLLOW-01)")
    public ResponseEntity<Void> unfollow(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        followService.unfollow(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/following")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Danh sách đang theo dõi", description = "Danh sách chủ trọ mà tôi đang theo dõi (FOLLOW-02)")
    public ResponseEntity<ApiResponse<PageResponse<FollowingItemResponse>>> getFollowing(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        validateSort(pageable);
        PageResponse<FollowingItemResponse> data = followService.getFollowing(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách đang theo dõi thành công"));
    }

    private void validateSort(Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!ALLOWED_SORT.contains(order.getProperty())) {
                throw new BusinessException(ErrorCode.INVALID_SORT_FIELD,
                        "Không thể sắp xếp theo trường " + order.getProperty());
            }
        }
    }
}
