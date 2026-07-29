package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO trả về cho một quận/huyện ({@code districts}) — mục 4.3.3 của {@code docs/03}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "DistrictResponse", description = "Quận/huyện")
public class DistrictResponse {

    @Schema(description = "Định danh", example = "765")
    private Long id;

    @Schema(description = "Mã hành chính", example = "765")
    private String code;

    @Schema(description = "Id tỉnh/thành cha", example = "79")
    private Long provinceId;

    @Schema(description = "Tên đầy đủ", example = "Quận Bình Thạnh")
    private String name;

    @Schema(description = "Slug", example = "quan-binh-thanh")
    private String slug;

    @Schema(description = "Loại đơn vị", example = "QUAN")
    private String type;

    @Schema(description = "Khu vực có hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Số tin đăng trong quận/huyện", example = "386")
    private Integer listingCount;
}
