package com.webtro.modules.listing.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.listing.dto.request.AmenityUpdateRequest;
import com.webtro.modules.listing.dto.request.CloseListingRequest;
import com.webtro.modules.listing.dto.request.ImageOrderRequest;
import com.webtro.modules.listing.dto.request.ListingCreateRequest;
import com.webtro.modules.listing.dto.request.ListingUpdateRequest;
import com.webtro.modules.listing.dto.request.RenewListingRequest;
import com.webtro.modules.listing.dto.response.ImageUploadResponse;
import com.webtro.modules.listing.dto.response.ListingActionResponse;
import com.webtro.modules.listing.dto.response.ListingAmenityResponse;
import com.webtro.modules.listing.dto.response.ListingCreateResponse;
import com.webtro.modules.listing.dto.response.ListingDetailResponse;
import com.webtro.modules.listing.dto.response.ListingStatsResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.listing.dto.response.ModerationImpactResponse;
import com.webtro.modules.listing.dto.response.RenewResponse;
import com.webtro.modules.listing.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * API tin đăng (docs/03 mục 4.4, LIST-01..12). Controller chỉ nhận/trả DTO và ủy quyền toàn bộ
 * nghiệp vụ cho {@link ListingService}; kiểm quyền permission bằng {@code @PreAuthorize}, kiểm
 * quyền sở hữu thực hiện trong service.
 */
@RestController
@RequestMapping("/api/listings")
@RequiredArgsConstructor
@Tag(name = "Listing", description = "Quản lý tin đăng phòng trọ")
public class ListingController {

    private final ListingService listingService;

    @PostMapping
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Tạo tin đăng mới (LIST-01/LIST-02)",
            description = "Luôn tạo ở trạng thái DRAFT; submitImmediately=true sẽ gửi duyệt ngay.")
    public ResponseEntity<ApiResponse<ListingCreateResponse>> create(
            @Valid @RequestBody ListingCreateRequest request) {
        ListingCreateResponse data = listingService.create(request);
        return ResponseEntity.created(URI.create("/api/listings/" + data.getId()))
                .body(ApiResponse.success(data, "Đã lưu tin nháp thành công"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Sửa tin đăng (LIST-03)",
            description = "Thay đổi nhạy cảm trên tin ACTIVE sẽ đưa tin về PENDING để duyệt lại.")
    public ResponseEntity<ApiResponse<ListingDetailResponse>> update(
            @PathVariable Long id, @Valid @RequestBody ListingUpdateRequest request) {
        ListingDetailResponse data = listingService.update(id, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật tin thành công"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Xóa mềm tin đăng (LIST-08)")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        listingService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Gửi duyệt tin (LIST-04)")
    public ResponseEntity<ApiResponse<ListingActionResponse>> submit(@PathVariable Long id) {
        ListingActionResponse data = listingService.submit(id);
        return ResponseEntity.ok(ApiResponse.success(data,
                "Đã gửi tin chờ duyệt. Tin sẽ được kiểm duyệt trong vòng 24 giờ."));
    }

    @PostMapping("/{id}/hide")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Ẩn tin (LIST-06)")
    public ResponseEntity<ApiResponse<ListingActionResponse>> hide(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(listingService.hide(id), "Đã ẩn tin"));
    }

    @PostMapping("/{id}/unhide")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Hiển thị lại tin")
    public ResponseEntity<ApiResponse<ListingActionResponse>> unhide(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(listingService.unhide(id), "Tin đã được hiển thị lại"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Đóng tin (LIST-07)")
    public ResponseEntity<ApiResponse<ListingActionResponse>> close(
            @PathVariable Long id, @Valid @RequestBody CloseListingRequest request) {
        return ResponseEntity.ok(ApiResponse.success(listingService.close(id, request),
                "Đã đóng tin. Cảm ơn bạn đã cho thuê thành công qua Webtro!"));
    }

    @PostMapping("/{id}/renew")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Gia hạn tin (LIST-09)")
    public ResponseEntity<ApiResponse<RenewResponse>> renew(
            @PathVariable Long id, @Valid @RequestBody(required = false) RenewListingRequest request) {
        RenewListingRequest body = request == null ? new RenewListingRequest() : request;
        RenewResponse data = listingService.renew(id, body);
        String message = Boolean.TRUE.equals(data.getPaymentRequired())
                ? "Bạn đã dùng hết lượt gia hạn miễn phí. Vui lòng thanh toán để tiếp tục."
                : "Đã gia hạn tin thành công.";
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Xem chi tiết tin đăng",
            description = "Tin công khai ai cũng xem được; tin non-public chỉ chủ tin hoặc admin.")
    public ResponseEntity<ApiResponse<ListingDetailResponse>> getDetail(
            @PathVariable Long id,
            @RequestParam(name = "countView", defaultValue = "true") boolean countView,
            HttpServletRequest httpRequest) {
        ListingDetailResponse data = listingService.getDetail(id, countView, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy chi tiết tin đăng thành công"));
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Danh sách tin của tôi", description = "Lọc theo trạng thái, từ khóa, danh mục.")
    public ResponseEntity<ApiResponse<PageResponse<ListingSummaryResponse>>> getMyListings(
            @RequestParam(name = "status", required = false) List<ListingStatus> statuses,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "expiringWithinDays", required = false) Integer expiringWithinDays,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        PageResponse<ListingSummaryResponse> data = listingService.getMyListings(
                statuses, keyword, categoryId, expiringWithinDays, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách tin của bạn thành công"));
    }

    @GetMapping("/{id}/stats")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Thống kê tin đăng (LIST-10)")
    public ResponseEntity<ApiResponse<ListingStatsResponse>> getStats(
            @PathVariable Long id,
            @RequestParam(name = "from", required = false) LocalDate from,
            @RequestParam(name = "to", required = false) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(listingService.getStats(id, from, to),
                "Lấy thống kê tin đăng thành công"));
    }

    @PostMapping(path = "/{id}/images", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Upload ảnh tin đăng (LIST-11)")
    public ResponseEntity<ApiResponse<ImageUploadResponse>> uploadImages(
            @PathVariable Long id,
            @RequestPart("files") List<MultipartFile> files,
            @RequestParam(name = "setPrimaryIndex", required = false) Integer setPrimaryIndex) {
        ImageUploadResponse data = listingService.uploadImages(id, files, setPrimaryIndex);
        return ResponseEntity.created(URI.create("/api/listings/" + id + "/images"))
                .body(ApiResponse.success(data, "Đã tải lên " + data.getUploaded().size() + " ảnh thành công"));
    }

    @DeleteMapping("/{id}/images/{imageId}")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Xóa ảnh tin đăng")
    public ResponseEntity<Void> deleteImage(
            @PathVariable Long id, @PathVariable Long imageId,
            @RequestParam(name = "newPrimaryImageId", required = false) Long newPrimaryImageId) {
        listingService.deleteImage(id, imageId, newPrimaryImageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/images/{imageId}/primary")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Đặt ảnh đại diện")
    public ResponseEntity<ApiResponse<ModerationImpactResponse>> setPrimaryImage(
            @PathVariable Long id, @PathVariable Long imageId) {
        return ResponseEntity.ok(ApiResponse.success(listingService.setPrimaryImage(id, imageId),
                "Đã đặt ảnh đại diện"));
    }

    @PutMapping("/{id}/images/order")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Sắp xếp thứ tự ảnh")
    public ResponseEntity<ApiResponse<List<ListingService.ListingImageResponseOrder>>> reorderImages(
            @PathVariable Long id, @Valid @RequestBody ImageOrderRequest request) {
        return ResponseEntity.ok(ApiResponse.success(listingService.reorderImages(id, request),
                "Đã cập nhật thứ tự ảnh"));
    }

    @PutMapping("/{id}/amenities")
    @PreAuthorize("hasAnyRole('TENANT','LANDLORD','ADMIN')")
    @Operation(summary = "Cập nhật tiện ích của tin (LIST-12)")
    public ResponseEntity<ApiResponse<List<ListingAmenityResponse>>> updateAmenities(
            @PathVariable Long id, @Valid @RequestBody AmenityUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(listingService.updateAmenities(id, request),
                "Đã cập nhật tiện ích của tin"));
    }

    /** Lấy IP client, ưu tiên {@code X-Forwarded-For} khi đứng sau proxy. */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
