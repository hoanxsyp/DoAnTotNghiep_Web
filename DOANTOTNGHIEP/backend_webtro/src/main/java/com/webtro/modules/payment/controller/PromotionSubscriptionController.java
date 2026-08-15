package com.webtro.modules.payment.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.SubscriptionStatus;
import com.webtro.modules.payment.dto.response.PromotionSubscriptionResponse;
import com.webtro.modules.payment.service.PromotionService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API danh sách gói đẩy đã mua của tôi (canonical 4.9.8).
 */
@RestController
@RequestMapping("/api/promotion-subscriptions")
@RequiredArgsConstructor
@Tag(name = "09. Payment & Promotion", description = "Thanh toán, gói dịch vụ, khuyến mãi")
public class PromotionSubscriptionController {

    private final PromotionService promotionService;

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    @Operation(summary = "Gói đẩy tin đã mua của tôi")
    public ResponseEntity<ApiResponse<PageResponse<PromotionSubscriptionResponse>>> mySubscriptions(
            @RequestParam(required = false) List<SubscriptionStatus> status,
            @RequestParam(required = false) Long listingId,
            @PageableDefault(size = 20, sort = "startAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        PageResponse<PromotionSubscriptionResponse> data =
                promotionService.getMySubscriptions(user.getId(), status, listingId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách gói đã mua thành công"));
    }
}
