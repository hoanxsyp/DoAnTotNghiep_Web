package com.webtro.modules.search.controller;

import com.webtro.common.ApiResponse;
import com.webtro.common.PageResponse;
import com.webtro.modules.listing.dto.response.ListingSummaryResponse;
import com.webtro.modules.search.dto.request.ListingSearchRequest;
import com.webtro.modules.search.dto.response.RelatedListingsResponse;
import com.webtro.modules.search.service.ListingSearchService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API tìm kiếm &amp; khám phá tin đăng (module SEARCH, SRCH-01..09, {@code [§3.7]}).
 *
 * <p>Chỉ gọi {@link ListingSearchService} (canonical luật 1), nhận/trả DTO (luật 2). Toàn bộ
 * endpoint đều <b>công khai</b> (khách xem được); người đăng nhập được lưu lịch sử tìm kiếm và
 * thấy cờ {@code favoritedByMe}.
 *
 * <p>Không dùng prefix ở cấp class vì hai endpoint nằm ở hai cây đường dẫn khác nhau
 * ({@code /api/search/**} và {@code /api/listings/**}); mỗi method khai báo đường dẫn tuyệt đối.
 */
@RestController
@RequiredArgsConstructor
@Validated
@Tag(name = "05. Search", description = "Tìm kiếm, lọc và gợi ý tin đăng (SRCH-01..09)")
public class ListingSearchController {

    private final ListingSearchService listingSearchService;

    /**
     * SRCH-01..08 — Tìm kiếm &amp; lọc động tin đăng.
     *
     * <p>Đặt ở {@code /api/search/listings} (giữ hợp đồng {@code [§12.4]}). URL chuẩn phục vụ
     * SEO {@code /api/listings} do controller của module listing đảm nhiệm để tránh trùng ánh xạ.
     */
    @GetMapping("/api/search/listings")
    @Operation(summary = "Tìm kiếm & lọc tin đăng (SRCH-01..08) [§3.7]",
            description = "Lọc động theo từ khóa, khu vực, giá, diện tích, tiện ích, đặc điểm phòng; "
                    + "chỉ trả tin công khai; tin đẩy còn hạn ưu tiên trong phạm vi kết quả khớp.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Danh sách tin phù hợp (phân trang)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "Bộ lọc không hợp lệ (khoảng giá/diện tích, từ khóa, sắp xếp...)")
    })
    public ResponseEntity<ApiResponse<PageResponse<ListingSummaryResponse>>> searchListings(
            @Valid @ParameterObject ListingSearchRequest request,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails principal) {

        PageResponse<ListingSummaryResponse> data =
                listingSearchService.search(request, pageable, principal);
        String message = data.getTotalElements() == 0
                ? "Không tìm thấy tin đăng phù hợp"
                : "Tìm thấy " + data.getTotalElements() + " tin đăng phù hợp";
        return ResponseEntity.ok(ApiResponse.success(data, message));
    }

    /**
     * SRCH-09 — Tin liên quan tới một tin (cùng khu vực / loại / khoảng giá).
     *
     * <p>Đặt tại {@code /api/listings/{id}/related} theo đúng docs/03 mục 4.4.5. Module listing
     * KHÔNG định nghĩa lại đường dẫn này (tránh trùng ánh xạ) — xem ghi chú bàn giao.
     */
    @GetMapping("/api/listings/{id}/related")
    @Operation(summary = "Tin liên quan (SRCH-09) [§9.2]",
            description = "Gợi ý tin tương tự dựa trên cùng khu vực, cùng loại và khoảng giá gần nhau.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "Danh sách tin liên quan"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "Không tìm thấy tin gốc")
    })
    public ResponseEntity<ApiResponse<RelatedListingsResponse>> relatedListings(
            @Parameter(description = "Id tin gốc") @PathVariable("id") Long id,
            @RequestParam(value = "size", required = false)
            @Min(value = 1, message = "size tối thiểu 1")
            @Max(value = 24, message = "size tối đa 24") Integer size,
            @AuthenticationPrincipal CustomUserDetails principal) {

        RelatedListingsResponse data = listingSearchService.related(id, size, principal);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy tin liên quan thành công"));
    }
}
