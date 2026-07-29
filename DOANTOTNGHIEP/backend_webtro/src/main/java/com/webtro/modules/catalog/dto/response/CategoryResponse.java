package com.webtro.modules.catalog.dto.response;

import com.webtro.common.enums.CategoryCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * DTO trả về cho một loại tin đăng ({@code categories}) — mục 4.3.1 và 4.17.1 của {@code docs/03}.
 *
 * <p>{@code requiredFields}/{@code optionalFields} được frontend dùng để render form động và
 * backend dùng để validate {@code REQUIRED_FIELD_MISSING} {@code [§10.5]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CategoryResponse", description = "Loại tin đăng")
public class CategoryResponse {

    @Schema(description = "Định danh", example = "1")
    private Long id;

    @Schema(description = "Mã loại tin", example = "BOARDING_HOUSE")
    private CategoryCode code;

    @Schema(description = "Tên hiển thị", example = "Phòng trọ")
    private String name;

    @Schema(description = "Slug dùng cho URL", example = "phong-tro")
    private String slug;

    @Schema(description = "Mô tả ngắn")
    private String description;

    @Schema(description = "Icon (định danh hoặc URL)")
    private String iconUrl;

    @Schema(description = "Thứ tự hiển thị", example = "1")
    private Integer displayOrder;

    @Schema(description = "Còn hiển thị hay không", example = "true")
    private Boolean active;

    @Schema(description = "Số tin đăng thuộc loại này", example = "1842")
    private Integer listingCount;

    @Schema(description = "Danh sách trường bắt buộc theo loại tin")
    private List<String> requiredFields;

    @Schema(description = "Danh sách trường tùy chọn theo loại tin")
    private List<String> optionalFields;

    @Schema(description = "Thời điểm tạo (chỉ trả cho Admin)")
    private Instant createdAt;

    @Schema(description = "Thời điểm cập nhật gần nhất")
    private Instant updatedAt;
}
