package com.webtro.modules.ai.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.ai.dto.request.SentimentAnalyzeRequest;
import com.webtro.modules.ai.dto.response.SentimentResponse;
import com.webtro.modules.ai.service.SentimentService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API phân tích cảm xúc (AI-01, docs/03 mục 7.1). Endpoint chẩn đoán của Admin/Moderator: phân tích
 * cảm xúc chạy tự động qua listener khi có bình luận mới; endpoint này để thử lại/kiểm tra từ điển.
 */
@Tag(name = "AI - Phân tích cảm xúc", description = "Phân tích lại cảm xúc bình luận (chẩn đoán)")
@RestController
@RequestMapping("/api/ai/sentiment")
@RequiredArgsConstructor
public class AiSentimentController {

    private final SentimentService sentimentService;

    @Operation(summary = "Phân tích cảm xúc bình luận hoặc văn bản",
            description = "Cung cấp đúng một trong hai: commentId (phân tích lại) hoặc text (thử "
                    + "nghiệm từ điển). persist=true (chỉ với commentId) ghi đè kết quả và tính lại uy tín. "
                    + "Yêu cầu quyền AI_LOG_VIEW.")
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    public ResponseEntity<ApiResponse<SentimentResponse>> analyze(
            @Valid @RequestBody SentimentAnalyzeRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        SentimentResponse data = sentimentService.analyze(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Phân tích cảm xúc thành công"));
    }
}
