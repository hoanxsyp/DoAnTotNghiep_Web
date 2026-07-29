package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.CreateConversationRequest;
import com.webtro.modules.interaction.dto.request.SendMessageRequest;
import com.webtro.modules.interaction.dto.response.ConversationCreatedResponse;
import com.webtro.modules.interaction.dto.response.ConversationPageResponse;
import com.webtro.modules.interaction.dto.response.ConversationResponse;
import com.webtro.modules.interaction.dto.response.MessageResponse;
import com.webtro.modules.interaction.service.ConversationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * API hội thoại và tin nhắn nội bộ (CONT-03) — canonical mục 4.6.4–4.6.9, {@code [§3.10]}.
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Tag(name = "06. Contact & Chat", description = "Liên hệ chủ tin, chat nội bộ")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Danh sách cuộc trò chuyện")
    public ResponseEntity<ApiResponse<ConversationPageResponse>> list(
            @RequestParam(required = false, defaultValue = "ALL") String role,
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) Long listingId,
            @PageableDefault(size = 20, sort = "lastMessageAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        ConversationPageResponse data = conversationService.listConversations(
                user.getId(), role, unreadOnly, listingId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách cuộc trò chuyện thành công"));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Tạo (hoặc lấy lại) cuộc trò chuyện")
    public ResponseEntity<ApiResponse<ConversationCreatedResponse>> create(
            @Valid @RequestBody CreateConversationRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        ConversationCreatedResponse data = conversationService.createConversation(request, user.getId());
        URI location = URI.create("/api/conversations/" + data.getId());
        if (Boolean.TRUE.equals(data.getAlreadyExisted())) {
            // Idempotent: hội thoại đã tồn tại → 200 (canonical 4.6.5).
            return ResponseEntity.ok(ApiResponse.success(data, "Đã lấy lại cuộc trò chuyện"));
        }
        return ResponseEntity.created(location).body(ApiResponse.success(data, "Đã tạo cuộc trò chuyện"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Chi tiết cuộc trò chuyện")
    public ResponseEntity<ApiResponse<ConversationResponse>> detail(
            @PathVariable Long id, @AuthenticationPrincipal CustomUserDetails user) {
        ConversationResponse data = conversationService.getConversation(id, user.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết cuộc trò chuyện thành công"));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Danh sách tin nhắn của cuộc trò chuyện")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> messages(
            @PathVariable Long id,
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<MessageResponse> data = conversationService.listMessages(id, user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy tin nhắn thành công"));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Gửi tin nhắn")
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            @PathVariable Long id, @Valid @RequestBody SendMessageRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        MessageResponse data = conversationService.sendMessage(id, request, user.getId());
        return ResponseEntity.created(URI.create("/api/conversations/" + id + "/messages/" + data.getId()))
                .body(ApiResponse.success(data, "Đã gửi tin nhắn"));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("hasAuthority('CONTACT_CREATE')")
    @Operation(summary = "Đánh dấu đã đọc")
    public ResponseEntity<Void> markRead(@PathVariable Long id,
                                         @AuthenticationPrincipal CustomUserDetails user) {
        conversationService.markRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
