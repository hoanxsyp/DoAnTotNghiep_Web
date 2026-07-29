package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.BulkActionResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.CommentStatus;
import com.webtro.common.enums.SentimentLabel;
import com.webtro.exception.BusinessException;
import com.webtro.constant.ErrorCode;
import com.webtro.modules.interaction.dto.request.BulkCommentActionRequest;
import com.webtro.modules.interaction.dto.request.ModerationReasonRequest;
import com.webtro.modules.interaction.dto.response.AdminCommentResponse;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API kiểm duyệt bình luận (quản trị) — canonical 4.7.5, {@code [§3.11]}. Quyền chung
 * {@code COMMENT_MODERATE} (Moderator + Admin).
 */
@RestController
@RequestMapping("/api/admin/comments")
@RequiredArgsConstructor
@Tag(name = "07. Comment & Review", description = "Bình luận và đánh giá tin")
public class AdminCommentController {

    private final CommentService commentService;

    /** Danh sách bình luận cho kiểm duyệt: lọc theo trạng thái/cảm xúc/spam/từ khóa/tin. */
    @GetMapping
    @PreAuthorize("hasAuthority('COMMENT_MODERATE')")
    @Operation(summary = "Danh sách bình luận (kiểm duyệt)",
            description = "Lọc theo trạng thái, nhãn cảm xúc, cờ spam, từ khóa nội dung và tin; phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<AdminCommentResponse>>> list(
            @RequestParam(required = false) CommentStatus status,
            @RequestParam(required = false) SentimentLabel sentiment,
            @RequestParam(required = false) Boolean isSpam,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long listingId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<AdminCommentResponse> data = commentService.adminListComments(
                status, sentiment, isSpam, keyword, listingId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách bình luận thành công"));
    }

    /** Ẩn bình luận vi phạm (kèm lý do bắt buộc). */
    @PutMapping("/{id}/hide")
    @PreAuthorize("hasAuthority('COMMENT_MODERATE')")
    @Operation(summary = "Ẩn bình luận vi phạm")
    public ResponseEntity<ApiResponse<AdminCommentResponse>> hide(
            @PathVariable Long id,
            @Valid @RequestBody ModerationReasonRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AdminCommentResponse data = commentService.adminHideComment(id, currentUser.getId(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã ẩn bình luận"));
    }

    /** Khôi phục bình luận đã ẩn. */
    @PutMapping("/{id}/unhide")
    @PreAuthorize("hasAuthority('COMMENT_MODERATE')")
    @Operation(summary = "Khôi phục bình luận đã ẩn")
    public ResponseEntity<ApiResponse<AdminCommentResponse>> unhide(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AdminCommentResponse data = commentService.unhideByModeration(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã khôi phục bình luận"));
    }

    /** Đánh dấu bình luận là spam (loại khỏi công thức tính điểm uy tín). */
    @PutMapping("/{id}/spam")
    @PreAuthorize("hasAuthority('COMMENT_MODERATE')")
    @Operation(summary = "Đánh dấu bình luận spam")
    public ResponseEntity<ApiResponse<AdminCommentResponse>> markSpam(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        AdminCommentResponse data = commentService.markSpam(id, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã đánh dấu bình luận là spam"));
    }

    /** Kiểm duyệt bình luận hàng loạt: ẩn (HIDE) hoặc đánh dấu spam (SPAM). */
    @PutMapping("/bulk")
    @PreAuthorize("hasAuthority('COMMENT_MODERATE')")
    @Operation(summary = "Kiểm duyệt bình luận hàng loạt",
            description = "Áp một hành động (HIDE/SPAM) cho nhiều bình luận; mỗi bình luận xử lý độc lập; trả về danh sách thành công và thất bại.")
    public ResponseEntity<ApiResponse<BulkActionResponse>> bulkModerate(
            @Valid @RequestBody BulkCommentActionRequest request,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        String action = request.getAction() != null ? request.getAction().trim().toUpperCase() : "";
        String reason = request.getReason();
        if ("HIDE".equals(action) && (reason == null || reason.isBlank())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Hành động HIDE yêu cầu nhập lý do");
        }

        BulkActionResponse result = new BulkActionResponse();
        for (Long id : request.getIds()) {
            if (id == null) {
                continue;
            }
            try {
                switch (action) {
                    case "HIDE" -> commentService.adminHideComment(id, currentUser.getId(), reason);
                    case "SPAM" -> commentService.markSpam(id, currentUser.getId());
                    default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                            "Hành động không hợp lệ: " + action);
                }
                result.addSuccess(id);
            } catch (RuntimeException ex) {
                result.addFailure(id, ex.getMessage());
            }
        }
        return ResponseEntity.ok(ApiResponse.success(result.finish(), "Đã xử lý thao tác hàng loạt"));
    }
}
