package com.webtro.modules.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Body sửa danh mục — {@code PUT /api/admin/categories/{id}} (mục 4.17.3). Không cho đổi
 * {@code code} (bất biến — enum gắn với logic nghiệp vụ như ROOMMATE).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateCategoryRequest", description = "Dữ liệu sửa danh mục")
public class UpdateCategoryRequest {

    @NotNull(message = "Tên danh mục không được để trống")
    @Size(min = 2, max = 50, message = "Tên danh mục phải từ 2 đến 50 ký tự")
    @Schema(description = "Tên hiển thị", example = "Phòng trọ")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    @Schema(description = "Mô tả ngắn")
    private String description;

    @Size(max = 50, message = "Icon tối đa 50 ký tự")
    @Schema(description = "Icon")
    private String iconUrl;

    @Min(value = 0, message = "Thứ tự hiển thị nhỏ nhất là 0")
    @Max(value = 999, message = "Thứ tự hiển thị lớn nhất là 999")
    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer displayOrder;

    @Schema(description = "Danh sách trường bắt buộc theo loại tin")
    private List<@Size(max = 50) String> requiredFields;

    @Schema(description = "Danh sách trường tùy chọn theo loại tin")
    private List<@Size(max = 50) String> optionalFields;

    @Schema(description = "Trạng thái hiển thị", example = "true")
    private Boolean active;
}
