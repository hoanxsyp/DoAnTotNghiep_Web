package com.webtro.modules.catalog.dto.request;

import com.webtro.common.enums.AmenityGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Body tạo tiện ích — {@code POST /api/admin/amenities} (mục 4.17.13).
 *
 * <p>Ràng buộc độ dài bám theo cột entity ({@code code} ≤ 30, {@code name} ≤ 60, {@code icon}
 * ≤ 50) thay vì con số minh họa ở docs/03.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateAmenityRequest", description = "Dữ liệu tạo tiện ích")
public class CreateAmenityRequest {

    @NotNull(message = "Mã tiện ích không được để trống")
    @Size(min = 2, max = 30, message = "Mã tiện ích phải từ 2 đến 30 ký tự")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Mã tiện ích chỉ gồm chữ hoa, số và dấu gạch dưới")
    @Schema(description = "Mã tiện ích", example = "AIR_CONDITIONER")
    private String code;

    @NotNull(message = "Tên tiện ích không được để trống")
    @Size(min = 2, max = 60, message = "Tên tiện ích phải từ 2 đến 60 ký tự")
    @Schema(description = "Tên hiển thị", example = "Máy lạnh")
    private String name;

    @NotNull(message = "Nhóm tiện ích không được để trống")
    @Schema(description = "Nhóm tiện ích (∈ AmenityGroup)", example = "FURNITURE")
    private AmenityGroup group;

    @Size(max = 50, message = "Icon tối đa 50 ký tự")
    @Schema(description = "Icon")
    private String iconUrl;

    @Min(value = 0, message = "Thứ tự hiển thị nhỏ nhất là 0")
    @Max(value = 999, message = "Thứ tự hiển thị lớn nhất là 999")
    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer displayOrder;

    @Schema(description = "Có dùng để lọc tìm kiếm không; mặc định true", example = "true")
    private Boolean filterable;

    @DecimalMin(value = "-1.0", message = "Hệ số ảnh hưởng giá nhỏ nhất là -1")
    @DecimalMax(value = "1.0", message = "Hệ số ảnh hưởng giá lớn nhất là 1")
    @Schema(description = "Hệ số ảnh hưởng tới giá (-1..1); mặc định 0", example = "0.05")
    private BigDecimal priceImpactRatio;

    @Schema(description = "Có hiển thị ngay không; mặc định true", example = "true")
    private Boolean active;
}
