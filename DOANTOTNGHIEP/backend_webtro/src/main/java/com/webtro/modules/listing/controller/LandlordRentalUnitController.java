package com.webtro.modules.listing.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.common.enums.RentalUnitStatus;
import com.webtro.modules.listing.dto.request.RentalUnitRequest;
import com.webtro.modules.listing.dto.response.ListingCreateResponse;
import com.webtro.modules.listing.dto.response.RentalUnitDuplicateCheckResponse;
import com.webtro.modules.listing.dto.response.RentalUnitResponse;
import com.webtro.modules.listing.service.RentalUnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Quản lý Phòng của chủ trọ (màn "Phòng của tôi") — docs/DANH_GIA_THEO_PHONG_DOI_CHIEU.md mục 4.
 * Mỗi tin đăng thuộc một phòng; đánh giá gắn vào phòng nên đóng tin rồi đăng lại vẫn giữ đánh giá.
 */
@RestController
@RequestMapping("/api/landlord/rental-units")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('LANDLORD','ADMIN')")
@Tag(name = "Rental unit", description = "Phòng của chủ trọ")
public class LandlordRentalUnitController {

    private final RentalUnitService rentalUnitService;

    @GetMapping
    @Operation(summary = "Danh sách phòng của tôi")
    public ResponseEntity<ApiResponse<PageResponse<RentalUnitResponse>>> list(
            @RequestParam(name = "status", required = false) RentalUnitStatus status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(rentalUnitService.listMine(status, keyword, pageable),
                "Lấy danh sách phòng thành công"));
    }

    @GetMapping("/duplicate-check")
    @Operation(summary = "Dò trùng địa chỉ trước khi tạo phòng (RU-6)")
    public ResponseEntity<ApiResponse<RentalUnitDuplicateCheckResponse>> duplicateCheck(
            @RequestParam("wardId") Long wardId,
            @RequestParam("addressDetail") String addressDetail,
            @RequestParam(name = "unitLabel", required = false) String unitLabel,
            @RequestParam(name = "excludeUnitId", required = false) Long excludeUnitId) {
        return ResponseEntity.ok(ApiResponse.success(
                rentalUnitService.checkDuplicate(wardId, addressDetail, unitLabel, excludeUnitId),
                "Đã kiểm tra trùng địa chỉ"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phòng (kèm lịch sử tin đăng)")
    public ResponseEntity<ApiResponse<RentalUnitResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(rentalUnitService.get(id), "Lấy thông tin phòng thành công"));
    }

    @PostMapping
    @Operation(summary = "Tạo phòng")
    public ResponseEntity<ApiResponse<RentalUnitResponse>> create(@Valid @RequestBody RentalUnitRequest request) {
        RentalUnitResponse data = rentalUnitService.create(request);
        return ResponseEntity.created(URI.create("/api/landlord/rental-units/" + data.getId()))
                .body(ApiResponse.success(data, "Đã tạo phòng"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Sửa phòng",
            description = "Phòng đã có đánh giá thì không đổi được địa chỉ và số phòng (RU-4). "
                    + "Đổi địa chỉ sẽ đồng bộ sang các tin của phòng; tin đang hiển thị phải duyệt lại.")
    public ResponseEntity<ApiResponse<RentalUnitResponse>> update(
            @PathVariable Long id, @Valid @RequestBody RentalUnitRequest request) {
        return ResponseEntity.ok(ApiResponse.success(rentalUnitService.update(id, request), "Đã cập nhật phòng"));
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "Lưu trữ phòng (thay cho xoá, RU-5)")
    public ResponseEntity<ApiResponse<RentalUnitResponse>> archive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(rentalUnitService.archive(id), "Đã lưu trữ phòng"));
    }

    @PostMapping("/{id}/unarchive")
    @Operation(summary = "Khôi phục phòng đã lưu trữ")
    public ResponseEntity<ApiResponse<RentalUnitResponse>> unarchive(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(rentalUnitService.unarchive(id), "Đã khôi phục phòng"));
    }

    @PostMapping("/{id}/relist")
    @Operation(summary = "Đăng lại phòng",
            description = "Tạo tin nháp mới từ tin gần nhất của phòng (nội dung, tiện ích, ảnh). "
                    + "Đánh giá của phòng tự hiện trên tin mới.")
    public ResponseEntity<ApiResponse<ListingCreateResponse>> relist(@PathVariable Long id) {
        ListingCreateResponse data = rentalUnitService.relist(id);
        return ResponseEntity.created(URI.create("/api/listings/" + data.getId()))
                .body(ApiResponse.success(data, "Đã tạo tin nháp mới cho phòng"));
    }
}
