package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * DTO trả về tỉnh/thành cho màn hình quản trị ({@code GET /api/admin/provinces}) — mục 4.17.5.
 * Bổ sung {@code districtCount}, {@code createdAt} so với {@link ProvinceResponse}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AdminProvinceResponse", description = "Tỉnh/thành (quản trị)")
public class AdminProvinceResponse {

    @Schema(description = "Định danh", example = "79")
    private Long id;

    @Schema(description = "Mã hành chính", example = "79")
    private String code;

    @Schema(description = "Tên đầy đủ", example = "Thành phố Hồ Chí Minh")
    private String name;

    @Schema(description = "Slug", example = "ho-chi-minh")
    private String slug;

    @Schema(description = "Loại đơn vị", example = "THANH_PHO_TRUNG_UONG")
    private String type;

    @Schema(description = "Khu vực có hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Số quận/huyện đang hoạt động", example = "22")
    private Integer districtCount;

    @Schema(description = "Số tin đăng trong tỉnh/thành", example = "2431")
    private Integer listingCount;

    @Schema(description = "Thời điểm tạo")
    private Instant createdAt;
}
