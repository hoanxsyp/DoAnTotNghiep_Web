package com.webtro.modules.user.controller;

import com.webtro.common.ApiResponse;
import com.webtro.modules.user.dto.response.LandlordDashboardResponse;
import com.webtro.modules.user.service.LandlordDashboardService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Landlord dashboard API.
 */
@RestController
@RequestMapping("/api/landlord")
@RequiredArgsConstructor
@Tag(name = "02. User", description = "Ho so nguoi dung va chu tro")
public class LandlordDashboardController {

    private final LandlordDashboardService landlordDashboardService;

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
    @Operation(summary = "Tong quan chu tro")
    public ResponseEntity<ApiResponse<LandlordDashboardResponse>> getDashboard(
            @RequestParam(name = "days", defaultValue = "30") int days,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        LandlordDashboardResponse data = landlordDashboardService.getDashboard(currentUser.getId(), days);
        return ResponseEntity.ok(ApiResponse.success(data, "Lay tong quan chu tro thanh cong"));
    }
}
