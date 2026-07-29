package com.webtro.modules.user.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.search.service.ListingSearchService;
import com.webtro.modules.user.dto.request.LandlordVerificationRequest;
import com.webtro.modules.user.dto.request.UpdateContactRequest;
import com.webtro.modules.user.dto.request.UpdateLandlordProfileRequest;
import com.webtro.modules.user.dto.request.UpdateProfileRequest;
import com.webtro.modules.user.dto.response.AvatarResponse;
import com.webtro.modules.user.dto.response.ContactInfoUpdateResponse;
import com.webtro.modules.user.dto.response.LandlordPublicResponse;
import com.webtro.modules.user.dto.response.LandlordVerificationResponse;
import com.webtro.modules.user.dto.response.MyLandlordProfileResponse;
import com.webtro.modules.user.dto.response.UserProfileResponse;
import com.webtro.modules.user.service.UserService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;

/**
 * API hồ sơ người dùng (USER-01..06, docs/03 mục 4.2).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "02. User", description = "Hồ sơ người dùng, hồ sơ chủ trọ, ảnh đại diện")
public class UserController {

    private final UserService userService;
    private final ListingSearchService listingSearchService;

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xem hồ sơ cá nhân", description = "Trả về hồ sơ đầy đủ của người dùng đang đăng nhập (USER-01)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        UserProfileResponse data = userService.getMyProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy hồ sơ thành công"));
    }

    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Cập nhật họ tên, giới tính, ngày sinh, địa chỉ, giới thiệu (USER-02)")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateProfileRequest request) {
        UserProfileResponse data = userService.updateMyProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật hồ sơ thành công"));
    }

    @PatchMapping("/me/contact")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật nhanh thông tin liên hệ",
            description = "Cập nhật tên/số điện thoại liên hệ (đồng bộ hồ sơ chủ trọ nếu có và số điện thoại tài khoản).")
    public ResponseEntity<ApiResponse<ContactInfoUpdateResponse>> updateContact(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateContactRequest request) {
        ContactInfoUpdateResponse data = userService.updateContact(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật thông tin liên hệ thành công"));
    }

    @PostMapping(value = "/me/avatar", consumes = "multipart/form-data")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Cập nhật ảnh đại diện", description = "Tải lên ảnh JPG/PNG/WEBP làm ảnh đại diện (USER-02)")
    public ResponseEntity<ApiResponse<AvatarResponse>> updateAvatar(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @RequestParam("file") MultipartFile file) {
        AvatarResponse data = userService.updateAvatar(currentUser.getId(), file);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật ảnh đại diện thành công"));
    }

    @DeleteMapping("/me/avatar")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Xóa ảnh đại diện", description = "Đưa ảnh đại diện về mặc định (mục 4.2.13)")
    public ResponseEntity<Void> deleteAvatar(@AuthenticationPrincipal CustomUserDetails currentUser) {
        userService.deleteAvatar(currentUser.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/landlord-profile")
    @PreAuthorize("hasAuthority('LISTING_CREATE')")
    @Operation(summary = "Xem hồ sơ chủ trọ của tôi", description = "Thông tin liên hệ, uy tín, trạng thái xác minh của chủ trọ")
    public ResponseEntity<ApiResponse<MyLandlordProfileResponse>> getMyLandlordProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        MyLandlordProfileResponse data = userService.getMyLandlordProfile(currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy hồ sơ chủ trọ thành công"));
    }

    @PutMapping("/me/landlord-profile")
    @PreAuthorize("hasAuthority('LISTING_CREATE')")
    @Operation(summary = "Cập nhật hồ sơ chủ trọ", description = "Cập nhật thông tin liên hệ, cơ sở, bật/tắt chat (mục 4.2.11)")
    public ResponseEntity<ApiResponse<MyLandlordProfileResponse>> updateMyLandlordProfile(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody UpdateLandlordProfileRequest request) {
        MyLandlordProfileResponse data = userService.updateMyLandlordProfile(currentUser.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật hồ sơ chủ trọ thành công"));
    }

    @PostMapping("/me/landlord-verification")
    @PreAuthorize("hasAuthority('LISTING_CREATE')")
    @Operation(summary = "Gửi yêu cầu xác thực chủ trọ", description = "Gửi yêu cầu để Admin/Moderator xác minh thủ công (USER-06)")
    public ResponseEntity<ApiResponse<LandlordVerificationResponse>> submitLandlordVerification(
            @AuthenticationPrincipal CustomUserDetails currentUser,
            @Valid @RequestBody LandlordVerificationRequest request) {
        LandlordVerificationResponse data = userService.submitLandlordVerification(currentUser.getId(), request);
        return ResponseEntity.created(URI.create("/api/users/me/landlord-profile"))
                .body(ApiResponse.success(data,
                        "Đã gửi yêu cầu xác thực. Quản trị viên sẽ xem xét trong 1-2 ngày làm việc."));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem hồ sơ công khai", description = "Hồ sơ công khai của chủ trọ; che số điện thoại với khách chưa đăng nhập (USER-04)")
    public ResponseEntity<ApiResponse<LandlordPublicResponse>> getPublicProfile(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails currentUser) {
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        LandlordPublicResponse data = userService.getPublicProfile(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy hồ sơ công khai thành công"));
    }

    @GetMapping("/{id}/listings")
    @Operation(summary = "Tin đang hiển thị của một chủ trọ",
            description = "Danh sách tin công khai (ACTIVE, còn hạn) của chủ trọ cho trang hồ sơ công khai; phân trang.")
    public ResponseEntity<ApiResponse<PageResponse<ListingSummaryResponse>>> getPublicListings(
            @PathVariable Long id,
            @PageableDefault(size = 12, sort = "publishedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<ListingSummaryResponse> data = listingSearchService.listByOwner(id, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tin của chủ trọ thành công"));
    }
}
