package com.webtro.modules.rental.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.RentalRequestStatus;
import com.webtro.modules.rental.dto.request.CreateRentalRequest;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/rental-requests")
@RequiredArgsConstructor
@PreAuthorize("hasRole('TENANT')")
public class RentalRequestController {

    private final RentalRequestService rentalRequestService;

    @PostMapping
    public ResponseEntity<ApiResponse<RentalRequestResponse>> create(
            @Valid @RequestBody CreateRentalRequest request,
            @AuthenticationPrincipal CustomUserDetails user) {
        RentalRequestResponse data = rentalRequestService.create(request, user.getId());
        return ResponseEntity.created(URI.create("/api/rental-requests/" + data.getId()))
                .body(ApiResponse.success(data, "Đã gửi yêu cầu thuê phòng"));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<RentalRequestResponse>>> listMine(
            @RequestParam(required = false) RentalRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.listMine(user.getId(), status, pageable),
                "Lấy danh sách yêu cầu thuê phòng thành công"));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<RentalRequestResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails user) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalRequestService.cancel(id, user.getId()), "Đã hủy yêu cầu thuê phòng"));
    }
}
