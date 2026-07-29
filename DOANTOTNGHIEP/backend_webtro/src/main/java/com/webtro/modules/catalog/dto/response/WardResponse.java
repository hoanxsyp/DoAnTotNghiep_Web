package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO trả về cho một phường/xã ({@code wards}) — mục 4.3.4 của {@code docs/03}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WardResponse", description = "Phường/xã")
public class WardResponse {

    @Schema(description = "Định danh", example = "26815")
    private Long id;

    @Schema(description = "Mã hành chính", example = "26815")
    private String code;

    @Schema(description = "Id quận/huyện cha", example = "765")
    private Long districtId;

    @Schema(description = "Tên đầy đủ", example = "Phường 25")
    private String name;

    @Schema(description = "Slug", example = "phuong-25")
    private String slug;

    @Schema(description = "Loại đơn vị", example = "PHUONG")
    private String type;

    @Schema(description = "Khu vực có hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Số tin đăng trong phường/xã", example = "74")
    private Integer listingCount;
}
