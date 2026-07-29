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
 * Body sửa quận/huyện — {@code PUT /api/admin/districts/{id}} (mục 4.17.9). Không đổi {@code code}
 * và {@code provinceId} (bất biến để không vỡ tin cũ).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateDistrictRequest", description = "Dữ liệu sửa quận/huyện")
public class UpdateDistrictRequest {

    @NotNull(message = "Tên quận/huyện không được để trống")
    @Size(min = 2, max = 100, message = "Tên quận/huyện phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Quận Bình Thạnh")
    private String name;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "QUAN|HUYEN|THI_XA|THANH_PHO_THUOC_TINH",
            message = "Loại đơn vị phải thuộc {QUAN, HUYEN, THI_XA, THANH_PHO_THUOC_TINH}")
    @Schema(description = "Loại đơn vị", example = "QUAN")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm")
    private BigDecimal longitude;
}
