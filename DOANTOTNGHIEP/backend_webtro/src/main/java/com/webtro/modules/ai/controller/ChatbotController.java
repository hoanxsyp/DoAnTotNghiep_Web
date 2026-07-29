package com.webtro.modules.ai.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.exception.BusinessException;
import com.webtro.constant.ErrorCode;
import com.webtro.modules.ai.dto.request.ChatbotMessageRequest;
import com.webtro.modules.ai.dto.response.ChatbotConversationResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageHistoryResponse;
import com.webtro.modules.ai.dto.response.ChatbotMessageResponse;
import com.webtro.modules.ai.service.ChatbotService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API chatbot (AI-05, docs/03 mục 7.3). Gửi tin nhắn công khai; xem lịch sử cần đăng nhập + chủ phiên.
 */
@Tag(name = "AI - Chatbot", description = "Trợ lý tìm phòng theo hội thoại")
@RestController
@RequestMapping("/api/ai/chatbot")
@RequiredArgsConstructor
public class ChatbotController {

    private final ChatbotService chatbotService;

    @Operation(summary = "Gửi tin nhắn tới chatbot",
            description = "Công khai. Hiểu nhu cầu, trả tin ACTIVE công khai kèm cảnh báo xác nhận "
                    + "phòng trống; hỏi lại tối đa 3 lượt; nội dung nhạy cảm bị từ chối lịch sự.")
    @PostMapping("/message")
    public ResponseEntity<ApiResponse<ChatbotMessageResponse>> sendMessage(
            @Valid @RequestBody ChatbotMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        ChatbotMessageResponse data = chatbotService.handleMessage(request, principal);
        return ResponseEntity.ok(ApiResponse.success(data, "Chatbot đã phản hồi"));
    }

    @Operation(summary = "Danh sách phiên hội thoại của tôi", description = "Cần đăng nhập.")
    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ChatbotConversationResponse>>> listConversations(
            @PageableDefault(size = 20, sort = "startedAt") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails principal) {
        PageResponse<ChatbotConversationResponse> data =
                chatbotService.listConversations(principal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách phiên trò chuyện thành công"));
    }

    @Operation(summary = "Lịch sử tin nhắn của một phiên", description = "Cần đăng nhập + là chủ phiên.")
    @GetMapping("/conversations/{id}/messages")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<ChatbotMessageHistoryResponse>>> listMessages(
            @PathVariable Long id,
            @PageableDefault(size = 30, sort = "createdAt") Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        PageResponse<ChatbotMessageHistoryResponse> data =
                chatbotService.listMessages(id, principal.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy lịch sử trò chuyện thành công"));
    }
}
