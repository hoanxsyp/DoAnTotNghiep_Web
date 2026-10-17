package com.webtro.modules.rental.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.rental.dto.request.RentalDecisionRequest;
import com.webtro.modules.rental.dto.response.RentalRequestResponse;
import com.webtro.modules.rental.service.RentalRequestService;
import com.webtro.security.CustomUserDetails;
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

@RestController
@RequestMapping("/api/landlord/rental-requests")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
public class LandlordRentalRequestController {

    private final RentalRequestService rentalRequestService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<RentalRequestResponse>>> list(
            @RequestParam(required = false) RentalRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.listForLandlord(user.getId(), status, pageable),
                "Lấy danh sách yêu cầu thuê phòng thành công"));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RentalDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.approve(id, user.getId(), request), "Đã duyệt yêu cầu thuê phòng"));
    }

    @PutMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RentalDecisionRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.reject(id, user.getId(), request), "Đã từ chối yêu cầu thuê phòng"));
    }

    @PutMapping("/{id}/confirm-rented")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> confirmRented(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.confirmRented(id, user.getId()), "Đã xác nhận người thuê"));
    }

    @PutMapping("/{id}/confirm-checked-out")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> confirmCheckedOut(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.confirmCheckedOut(id, user.getId()), "Đã xác nhận trả phòng"));
    }
}
