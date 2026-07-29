package com.webtro.modules.admin.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.modules.admin.dto.request.RejectLandlordVerificationRequest;
import com.webtro.modules.admin.dto.request.RestrictLandlordPostingRequest;
import com.webtro.modules.admin.dto.request.UnverifyLandlordRequest;
import com.webtro.modules.admin.dto.request.VerifyLandlordRequest;
import com.webtro.modules.admin.dto.response.AdminLandlordResponse;
import com.webtro.modules.admin.dto.response.LandlordPostingRestrictionResponse;
import com.webtro.modules.admin.dto.response.LandlordVerificationActionResponse;
import com.webtro.modules.admin.service.AdminUserService;
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

import java.util.List;

/**
 * API quản lý chủ trọ (canonical 4.13.6–4.13.8). Danh sách + xác thực/hủy xác thực chủ trọ. Toàn bộ
 * yêu cầu quyền {@code LANDLORD_VERIFY} (Moderator cũng có quyền này — canonical §4.2).
 */
@RestController
@RequestMapping("/api/admin/landlords")
@RequiredArgsConstructor
@Tag(name = "13. Admin - User", description = "Quản lý người dùng và chủ trọ")
public class AdminLandlordController {

    private final AdminUserService adminUserService;

    @GetMapping
    @PreAuthorize("hasAuthority('LANDLORD_VERIFY')")
    @Operation(summary = "Quản lý chủ trọ",
            description = "Danh sách chủ trọ kèm điểm uy tín, số tin, số report; lọc theo trạng thái xác thực, khoảng uy tín, trạng thái hạn chế đăng tin.")
    public ResponseEntity<ApiResponse<PageResponse<AdminLandlordResponse>>> listLandlords(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) List<VerificationStatus> verificationStatus,
            @RequestParam(required = false) Integer minTrustScore,
            @RequestParam(required = false) Integer maxTrustScore,
            @RequestParam(required = false) Boolean postingSuspended,
            @PageableDefault(size = 20, sort = "trustScore", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<AdminLandlordResponse> data = adminUserService.listLandlords(
                keyword, verificationStatus, minTrustScore, maxTrustScore, postingSuspended, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách chủ trọ thành công"));
    }

    @PutMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('LANDLORD_VERIFY')")
    @Operation(summary = "Xác thực chủ trọ",
            description = "Đặt trạng thái xác thực = VERIFIED cho hồ sơ chủ trọ; ghi audit; thông báo cho chủ trọ.")
    public ResponseEntity<ApiResponse<LandlordVerificationActionResponse>> verifyLandlord(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) VerifyLandlordRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LandlordVerificationActionResponse data = adminUserService.verifyLandlord(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã xác thực chủ trọ"));
    }

    @PutMapping("/{id}/unverify")
    @PreAuthorize("hasAuthority('LANDLORD_VERIFY')")
    @Operation(summary = "Hủy xác thực chủ trọ",
            description = "Đưa hồ sơ chủ trọ về trạng thái PENDING (hàng đợi chờ duyệt); ghi audit; thông báo cho chủ trọ.")
    public ResponseEntity<ApiResponse<LandlordVerificationActionResponse>> unverifyLandlord(
            @PathVariable Long id,
            @Valid @RequestBody UnverifyLandlordRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LandlordVerificationActionResponse data = adminUserService.unverifyLandlord(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã hủy xác thực chủ trọ. Hồ sơ trở lại hàng đợi chờ duyệt."));
    }

    @PutMapping("/{id}/reject-verification")
    @PreAuthorize("hasAuthority('LANDLORD_VERIFY')")
    @Operation(summary = "Từ chối xác thực chủ trọ",
            description = "Từ chối yêu cầu xác thực chủ trọ đang chờ duyệt (đặt REJECTED); bắt buộc lý do; ghi audit; thông báo cho chủ trọ.")
    public ResponseEntity<ApiResponse<LandlordVerificationActionResponse>> rejectVerification(
            @PathVariable Long id,
            @Valid @RequestBody RejectLandlordVerificationRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LandlordVerificationActionResponse data =
                adminUserService.rejectLandlordVerification(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã từ chối yêu cầu xác thực chủ trọ"));
    }

    @PutMapping("/{id}/restrict-posting")
    @PreAuthorize("hasAuthority('LANDLORD_VERIFY') or hasAuthority('USER_MANAGE')")
    @Operation(summary = "Tạm hạn chế đăng tin của chủ trọ",
            description = "Đặt hạn chế đăng tin tới thời điểm chỉ định (posting_restricted_until) + lý do; ghi audit; thông báo cho chủ trọ.")
    public ResponseEntity<ApiResponse<LandlordPostingRestrictionResponse>> restrictPosting(
            @PathVariable Long id,
            @Valid @RequestBody RestrictLandlordPostingRequest request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        LandlordPostingRestrictionResponse data =
                adminUserService.restrictLandlordPosting(id, request, principal.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Đã tạm hạn chế đăng tin của chủ trọ"));
    }
}
