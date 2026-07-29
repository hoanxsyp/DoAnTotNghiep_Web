package com.webtro.modules.interaction.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.response.SearchHistoryResponse;
import com.webtro.modules.interaction.dto.response.ViewHistoryResponse;
import com.webtro.modules.interaction.service.HistoryService;
import com.webtro.security.CustomUserDetails;
import com.webtro.security.RateLimitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDate;

/**
 * API lịch sử xem tin và lịch sử tìm kiếm (HIST) — canonical mục 4.5.4–4.5.8.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "05. Favorite & History", description = "Lưu tin, lịch sử xem/tìm kiếm")
public class HistoryController {

    private static final int HISTORY_DELETE_MAX_PER_HOUR = 5;

    private final HistoryService historyService;
    private final RateLimitService rateLimitService;

    @GetMapping("/history/views")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử xem tin")
    public ResponseEntity<ApiResponse<PageResponse<ViewHistoryResponse>>> listViews(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20, sort = "viewedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<ViewHistoryResponse> data = historyService.listViews(user.getId(), from, to, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy lịch sử xem thành công"));
    }

    @DeleteMapping("/history/views")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa toàn bộ lịch sử xem")
    public ResponseEntity<Void> clearViews(@AuthenticationPrincipal CustomUserDetails user) {
        rateLimitService.checkLimit("history-clear", String.valueOf(user.getId()),
                HISTORY_DELETE_MAX_PER_HOUR, Duration.ofHours(1));
        historyService.clearViews(user.getId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/history/views/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa một mục lịch sử xem")
    public ResponseEntity<Void> deleteView(@PathVariable Long id,
                                           @AuthenticationPrincipal CustomUserDetails user) {
        historyService.deleteView(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search/histories")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Lịch sử tìm kiếm")
    public ResponseEntity<ApiResponse<PageResponse<SearchHistoryResponse>>> listSearches(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<SearchHistoryResponse> data = historyService.listSearches(user.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy lịch sử tìm kiếm thành công"));
    }

    @DeleteMapping("/search/histories")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa lịch sử tìm kiếm (toàn bộ hoặc một mục)")
    public ResponseEntity<Void> deleteSearches(@RequestParam(required = false) Long id,
                                               @AuthenticationPrincipal CustomUserDetails user) {
        rateLimitService.checkLimit("search-history-clear", String.valueOf(user.getId()),
                HISTORY_DELETE_MAX_PER_HOUR, Duration.ofHours(1));
        historyService.deleteSearches(user.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
