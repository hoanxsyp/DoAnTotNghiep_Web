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
 * Body sửa tỉnh/thành — {@code PUT /api/admin/provinces/{id}} (mục 4.17.7). Không đổi {@code code}
 * (bất biến — mã hành chính nhà nước).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateProvinceRequest", description = "Dữ liệu sửa tỉnh/thành")
public class UpdateProvinceRequest {

    @NotNull(message = "Tên tỉnh/thành không được để trống")
    @Size(min = 2, max = 100, message = "Tên tỉnh/thành phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Thành phố Hồ Chí Minh")
    private String name;

    @Size(max = 50, message = "Tên rút gọn tối đa 50 ký tự")
    @Schema(description = "Tên rút gọn", example = "Hồ Chí Minh")
    private String shortName;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "TINH|THANH_PHO_TRUNG_UONG",
            message = "Loại đơn vị phải là TINH hoặc THANH_PHO_TRUNG_UONG")
    @Schema(description = "Loại đơn vị", example = "THANH_PHO_TRUNG_UONG")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm")
    private BigDecimal longitude;
}
