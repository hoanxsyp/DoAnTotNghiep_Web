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
 * Body tạo tỉnh/thành — {@code POST /api/admin/provinces} (mục 4.17.6).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateProvinceRequest", description = "Dữ liệu tạo tỉnh/thành")
public class CreateProvinceRequest {

    @NotNull(message = "Mã tỉnh/thành không được để trống")
    @Size(min = 1, max = 10, message = "Mã tỉnh/thành phải từ 1 đến 10 ký tự")
    @Schema(description = "Mã hành chính, unique", example = "79")
    private String code;

    @NotNull(message = "Tên tỉnh/thành không được để trống")
    @Size(min = 2, max = 100, message = "Tên tỉnh/thành phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Thành phố Hồ Chí Minh")
    private String name;

    @Size(max = 50, message = "Tên rút gọn tối đa 50 ký tự")
    @Schema(description = "Tên rút gọn; bỏ trống sẽ tự suy ra từ tên", example = "Hồ Chí Minh")
    private String shortName;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "TINH|THANH_PHO_TRUNG_UONG",
            message = "Loại đơn vị phải là TINH hoặc THANH_PHO_TRUNG_UONG")
    @Schema(description = "Loại đơn vị", example = "THANH_PHO_TRUNG_UONG")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không; mặc định false", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm", example = "10.7769")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm", example = "106.7009")
    private BigDecimal longitude;
}
