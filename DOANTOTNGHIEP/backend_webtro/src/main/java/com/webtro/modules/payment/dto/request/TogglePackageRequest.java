package com.webtro.modules.payment.dto.request;

import com.webtro.validator.NoHtml;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu bật/tắt bán một gói — {@code PUT /api/admin/promotion-packages/{id}/toggle} (canonical 4.18.4).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "TogglePackageRequest", description = "Yêu cầu bật/tắt gói dịch vụ")
public class TogglePackageRequest {

    @Schema(description = "Bật (true) hay tắt (false) bán gói", example = "false")
    @NotNull(message = "Vui lòng chọn trạng thái bật/tắt")
    private Boolean active;

    @Schema(description = "Lý do thay đổi (ghi audit)", example = "Ngừng bán gói này để thay bằng gói mới")
    @NotNull(message = "Vui lòng nhập lý do")
    @Size(min = 10, max = 255, message = "Lý do phải từ 10 đến 255 ký tự")
    @NoHtml
    private String reason;
}
