package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.interaction.dto.request.CreateCommentRequest;
import com.webtro.modules.interaction.dto.request.UpdateCommentRequest;
import com.webtro.modules.interaction.dto.response.CommentPageResponse;
import com.webtro.modules.interaction.dto.response.CommentResponse;
import com.webtro.modules.interaction.service.CommentService;
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
 * API bình luận (CMT) — canonical mục 4.7.1–4.7.5, {@code [§3.11]}.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "07. Comment & Review", description = "Bình luận và đánh giá tin")
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/listings/{id}/comments")
    @Operation(summary = "Danh sách bình luận của tin (công khai)")
    public ResponseEntity<ApiResponse<CommentPageResponse>> list(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "true") boolean includeReplies,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        Long viewerId = user == null ? null : user.getId();
        CommentPageResponse data = commentService.listComments(id, includeReplies, viewerId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy bình luận thành công"));
    }

    @PostMapping("/listings/{id}/comments")
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    @Operation(summary = "Tạo bình luận")
    public ResponseEntity<ApiResponse<CommentResponse>> create(
            @PathVariable Long id, @Valid @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        CommentResponse data = commentService.createComment(id, request, user.getId());
        return ResponseEntity.created(URI.create("/api/listings/" + id + "/comments/" + data.getId()))
                .body(ApiResponse.success(data, "Đã đăng bình luận"));
    }

    @PostMapping("/comments/{id}/reply")
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    @Operation(summary = "Trả lời bình luận")
    public ResponseEntity<ApiResponse<CommentResponse>> reply(
            @PathVariable Long id, @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        CommentResponse data = commentService.replyComment(id, request, user.getId());
        return ResponseEntity.created(URI.create("/api/listings/" + data.getListingId() + "/comments/" + data.getId()))
                .body(ApiResponse.success(data, "Đã đăng trả lời"));
    }

    @PutMapping("/comments/{id}")
    @PreAuthorize("hasAuthority('COMMENT_CREATE')")
    @Operation(summary = "Sửa bình luận (trong cửa sổ cho phép)")
    public ResponseEntity<ApiResponse<CommentResponse>> update(
            @PathVariable Long id, @Valid @RequestBody UpdateCommentRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        CommentResponse data = commentService.updateComment(id, request, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã cập nhật bình luận"));
    }

    @DeleteMapping("/comments/{id}")
    @PreAuthorize("hasAnyAuthority('COMMENT_CREATE','COMMENT_MODERATE')")
    @Operation(summary = "Xóa bình luận (tác giả trong cửa sổ, hoặc kiểm duyệt viên)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal CustomUserDetails user) {
        commentService.deleteComment(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
