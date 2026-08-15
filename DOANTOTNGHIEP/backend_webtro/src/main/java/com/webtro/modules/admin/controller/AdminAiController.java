package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.AiModule;
import com.webtro.modules.admin.dto.request.ReanalyzeSentimentRequest;
import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.AiAlertResponse;
import com.webtro.modules.admin.dto.response.AiConfigResponse;
import com.webtro.modules.admin.dto.response.AiLogResponse;
import com.webtro.modules.admin.dto.response.AiPriceDeviationResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;
import com.webtro.modules.admin.service.AdminAiService;
import com.webtro.modules.ai.dto.request.SentimentAnalyzeRequest;
import com.webtro.modules.ai.dto.response.SentimentResponse;
import com.webtro.modules.ai.service.SentimentService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * API quản trị AI (canonical 4.19): cấu hình ({@code AI_CONFIG_MANAGE}) và log ({@code AI_LOG_VIEW}).
 */
@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
@Tag(name = "19. Admin - AI", description = "Cấu hình và log AI")
public class AdminAiController {

    private final AdminAiService adminAiService;
    private final SentimentService sentimentService;

    @GetMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem cấu hình AI", description = "Cấu hình 4 module AI + trọng số điểm uy tín.")
    public ResponseEntity<ApiResponse<AiConfigResponse>> getConfig() {
        return ResponseEntity.ok(ApiResponse.success(adminAiService.getConfig(), "Lấy cấu hình AI thành công"));
    }

    @PutMapping("/config")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật cấu hình AI", description = "Đổi ngưỡng/trọng số/bật-tắt module; ghi audit bắt buộc.")
    public ResponseEntity<ApiResponse<UpdateConfigResponse>> updateConfig(
            @Valid @RequestBody UpdateConfigRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        UpdateConfigResponse data = adminAiService.updateConfig(request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã cập nhật " + data.getUpdated().size() + " cấu hình AI"));
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Xem log AI", description = "Lịch sử sentiment/recommendation/price/chatbot theo module.")
    public ResponseEntity<ApiResponse<AiLogResponse>> getLogs(
            @RequestParam AiModule module,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        Instant fromInstant = from != null ? from.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        Instant toInstant = to != null ? to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        AiLogResponse data = adminAiService.getLogs(module, fromInstant, toInstant, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy log AI thành công"));
    }

    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Cảnh báo cảm xúc tiêu cực",
            description = "Tin bị AI đánh dấu cần kiểm tra do tỷ lệ bình luận tiêu cực cao "
                    + "(need_review_count > 0), kèm tỷ lệ tiêu cực. Phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<AiAlertResponse>>> getAlerts(
            @PageableDefault(size = 20, sort = "lastNeedReviewAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<AiAlertResponse> data = adminAiService.getSentimentAlerts(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách cảnh báo cảm xúc thành công"));
    }

    @GetMapping("/price-deviations")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Tin lệch giá",
            description = "Tin bị AI đánh cờ lệch giá (price_deviation_flag = true), kèm giá đề xuất "
                    + "và tỷ lệ lệch từ dự đoán gần nhất. Phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<AiPriceDeviationResponse>>> getPriceDeviations(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<AiPriceDeviationResponse> data = adminAiService.getPriceDeviations(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tin lệch giá thành công"));
    }

    @PostMapping("/sentiment/reanalyze")
    @PreAuthorize("hasAnyRole('MODERATOR','ADMIN')")
    @Operation(summary = "Phân tích lại cảm xúc một bình luận",
            description = "Chạy lại phân tích cảm xúc cho một bình luận có sẵn, ghi đè kết quả và "
                    + "tính lại điểm uy tín tin. Ủy quyền cho SentimentService.analyze (persist).")
    public ResponseEntity<ApiResponse<SentimentResponse>> reanalyzeSentiment(
            @Valid @RequestBody ReanalyzeSentimentRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        SentimentAnalyzeRequest analyzeRequest = SentimentAnalyzeRequest.builder()
                .commentId(request.getCommentId())
                .persist(true)
                .build();
        SentimentResponse data = sentimentService.analyze(analyzeRequest, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã phân tích lại cảm xúc bình luận"));
    }
}
