package com.webtro.modules.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
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
 * Body sửa phường/xã — {@code PUT /api/admin/wards/{id}} (mục 4.17.11). Không đổi {@code code}
 * và {@code districtId} (bất biến để không vỡ tin cũ).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateWardRequest", description = "Dữ liệu sửa phường/xã")
public class UpdateWardRequest {

    @NotNull(message = "Tên phường/xã không được để trống")
    @Size(min = 2, max = 100, message = "Tên phường/xã phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Phường 25")
    private String name;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "PHUONG|XA|THI_TRAN",
            message = "Loại đơn vị phải thuộc {PHUONG, XA, THI_TRAN}")
    @Schema(description = "Loại đơn vị", example = "PHUONG")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm")
    private BigDecimal longitude;
}
