package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Một khóa cấu hình trả về cho Admin (dùng chung cho {@code /system-configs} và {@code /ai/config}).
 * {@code value}/{@code defaultValue} đã ép kiểu đúng {@code type} để frontend không phải đoán.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ConfigItemResponse", description = "Một khóa cấu hình hệ thống")
public class ConfigItemResponse {

    @Schema(description = "Khóa cấu hình", example = "listing.display_days")
    private String key;

    @Schema(description = "Giá trị hiện tại (đã ép kiểu theo type)", example = "30")
    private Object value;

    @Schema(description = "Kiểu giá trị", example = "INT")
    private String type;

    @Schema(description = "Giá trị mặc định (đã ép kiểu)", example = "30")
    private Object defaultValue;

    @Schema(description = "Giá trị nhỏ nhất cho phép (INT/DECIMAL)", example = "1")
    private BigDecimal min;

    @Schema(description = "Giá trị lớn nhất cho phép (INT/DECIMAL)", example = "365")
    private BigDecimal max;

    @Schema(description = "Nhãn hiển thị", example = "Số ngày hiển thị tin mặc định")
    private String label;

    @Schema(description = "Mô tả/nguồn nghiệp vụ", example = "[§3.3][§5.2]")
    private String description;

    @Schema(description = "Có cho phép Admin sửa qua UI không", example = "true")
    private Boolean editable;
}
