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
 * Body tạo quận/huyện — {@code POST /api/admin/districts} (mục 4.17.8).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateDistrictRequest", description = "Dữ liệu tạo quận/huyện")
public class CreateDistrictRequest {

    @NotNull(message = "Tỉnh/thành cha không được để trống")
    @Schema(description = "Id tỉnh/thành cha", example = "79")
    private Long provinceId;

    @NotNull(message = "Mã quận/huyện không được để trống")
    @Size(min = 1, max = 10, message = "Mã quận/huyện phải từ 1 đến 10 ký tự")
    @Schema(description = "Mã hành chính, unique", example = "765")
    private String code;

    @NotNull(message = "Tên quận/huyện không được để trống")
    @Size(min = 2, max = 100, message = "Tên quận/huyện phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Quận Bình Thạnh")
    private String name;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "QUAN|HUYEN|THI_XA|THANH_PHO_THUOC_TINH",
            message = "Loại đơn vị phải thuộc {QUAN, HUYEN, THI_XA, THANH_PHO_THUOC_TINH}")
    @Schema(description = "Loại đơn vị", example = "QUAN")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không; mặc định false", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm")
    private BigDecimal longitude;
}
