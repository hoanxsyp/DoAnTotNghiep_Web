package com.webtro.modules.catalog.dto.request;

import com.webtro.common.enums.CategoryCode;
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
 * Body tạo danh mục — {@code POST /api/admin/categories} (mục 4.17.2).
 *
 * <p>Lưu ý ràng buộc độ dài bám theo cột thực tế trong entity ({@code name} ≤ 50, {@code icon}
 * ≤ 50, {@code description} ≤ 255) thay vì con số minh họa ở docs/03 — entity là nguồn sự thật
 * để tránh lỗi ghi DB.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateCategoryRequest", description = "Dữ liệu tạo danh mục")
public class CreateCategoryRequest {

    @NotNull(message = "Mã danh mục không được để trống")
    @Schema(description = "Mã loại tin (∈ CategoryCode)", example = "BOARDING_HOUSE")
    private CategoryCode code;

    @NotNull(message = "Tên danh mục không được để trống")
    @Size(min = 2, max = 50, message = "Tên danh mục phải từ 2 đến 50 ký tự")
    @Schema(description = "Tên hiển thị", example = "Phòng trọ")
    private String name;

    @Size(max = 255, message = "Mô tả tối đa 255 ký tự")
    @Schema(description = "Mô tả ngắn")
    private String description;

    @Size(max = 50, message = "Icon tối đa 50 ký tự")
    @Schema(description = "Icon (định danh hoặc URL ngắn)")
    private String iconUrl;

    @Min(value = 0, message = "Thứ tự hiển thị nhỏ nhất là 0")
    @Max(value = 999, message = "Thứ tự hiển thị lớn nhất là 999")
    @Schema(description = "Thứ tự hiển thị; bỏ trống → MAX+1", example = "1")
    private Integer displayOrder;

    @Schema(description = "Danh sách trường bắt buộc theo loại tin")
    private List<@Size(max = 50) String> requiredFields;

    @Schema(description = "Danh sách trường tùy chọn theo loại tin")
    private List<@Size(max = 50) String> optionalFields;

    @Schema(description = "Có hiển thị ngay không; mặc định true", example = "true")
    private Boolean active;
}
