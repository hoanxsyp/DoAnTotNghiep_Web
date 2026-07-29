package com.webtro.modules.catalog.dto.response;

import com.webtro.common.enums.AmenityGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * DTO trả về cho một tiện ích ({@code amenities}) — mục 4.3.5 và 4.17.12 của {@code docs/03}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AmenityResponse", description = "Tiện ích")
public class AmenityResponse {

    @Schema(description = "Định danh", example = "1")
    private Long id;

    @Schema(description = "Mã tiện ích", example = "AIR_CONDITIONER")
    private String code;

    @Schema(description = "Tên hiển thị", example = "Máy lạnh")
    private String name;

    @Schema(description = "Nhóm tiện ích", example = "FURNITURE")
    private AmenityGroup group;

    @Schema(description = "Icon (định danh hoặc URL)")
    private String iconUrl;

    @Schema(description = "Có dùng để lọc tìm kiếm không", example = "true")
    private Boolean filterable;

    @Schema(description = "Hệ số ảnh hưởng tới giá (-1..1)", example = "0.05")
    private BigDecimal priceImpactRatio;

    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer displayOrder;

    @Schema(description = "Còn hiển thị hay không", example = "true")
    private Boolean active;
}
