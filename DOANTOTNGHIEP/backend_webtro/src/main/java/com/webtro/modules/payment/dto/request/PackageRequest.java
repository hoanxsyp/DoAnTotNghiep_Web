package com.webtro.modules.payment.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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
 * Yêu cầu tạo/sửa gói dịch vụ (canonical 4.18.2, 4.18.3). Khi sửa, {@code code} bất biến (bỏ qua).
 * Trần {@code priority} theo {@code promotion.max_priority} được kiểm tại service (đọc từ config).
 *
 * <p>Chỉ chứa các field có cột tương ứng trong {@code promotion_packages}. Các field
 * {@code purpose}/{@code features}/{@code highlighted} trong đặc tả 03 KHÔNG có cột nên không nhận
 * ở đây (xem ghi chú bàn giao của module payment).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PackageRequest", description = "Yêu cầu tạo/sửa gói dịch vụ")
public class PackageRequest {

    @Schema(description = "Mã gói (duy nhất, bất biến khi sửa)", example = "PUSH_TOP_7D")
    @NotBlank(message = "Vui lòng nhập mã gói")
    @Size(min = 2, max = 50, message = "Mã gói phải từ 2 đến 50 ký tự")
    @Pattern(regexp = "^[A-Z0-9_]+$", message = "Mã gói chỉ gồm chữ HOA, số và gạch dưới")
    private String code;

    @Schema(description = "Tên hiển thị", example = "Đẩy tin lên đầu 7 ngày")
    @NotBlank(message = "Vui lòng nhập tên gói")
    @Size(min = 2, max = 100, message = "Tên gói phải từ 2 đến 100 ký tự")
    @NoHtml
    private String name;

    @Schema(description = "Mô tả gói")
    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    @NoHtml
    private String description;

    @Schema(description = "Giá gói (VND)", example = "99000.00")
    @NotNull(message = "Vui lòng nhập giá gói")
    @DecimalMin(value = "0.0", inclusive = false, message = "Giá gói phải lớn hơn 0")
    @DecimalMax(value = "99999999.99", message = "Giá gói vượt quá giới hạn cho phép")
    private BigDecimal price;

    @Schema(description = "Số ngày hiệu lực (1..365)", example = "7")
    @NotNull(message = "Vui lòng nhập thời hạn gói")
    @Min(value = 1, message = "Thời hạn gói phải từ 1 đến 365 ngày")
    @Max(value = 365, message = "Thời hạn gói phải từ 1 đến 365 ngày")
    private Integer durationDays;

    @Schema(description = "Mức ưu tiên (0..promotion.max_priority)", example = "80")
    @NotNull(message = "Vui lòng nhập mức ưu tiên")
    @Min(value = 0, message = "Mức ưu tiên không được âm")
    private Integer priority;

    @Schema(description = "Nhãn huy hiệu hiển thị trên tin", example = "Tin nổi bật")
    @Size(max = 30, message = "Nhãn huy hiệu tối đa 30 ký tự")
    @NoHtml
    private String badgeLabel;

    @Schema(description = "Màu huy hiệu", example = "#FF5722")
    @Size(max = 20, message = "Màu huy hiệu tối đa 20 ký tự")
    private String badgeColor;

    @Schema(description = "Thứ tự hiển thị (0..999)", example = "1")
    @Min(value = 0, message = "Thứ tự hiển thị không được âm")
    @Max(value = 999, message = "Thứ tự hiển thị tối đa 999")
    private Integer displayOrder;

    @Schema(description = "Gói còn được bán không (mặc định true)", example = "true")
    private Boolean active;
}
