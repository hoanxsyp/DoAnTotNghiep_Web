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
 * Body tạo phường/xã — {@code POST /api/admin/wards} (mục 4.17.10).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateWardRequest", description = "Dữ liệu tạo phường/xã")
public class CreateWardRequest {

    @NotNull(message = "Quận/huyện cha không được để trống")
    @Schema(description = "Id quận/huyện cha", example = "765")
    private Long districtId;

    @NotNull(message = "Mã phường/xã không được để trống")
    @Size(min = 1, max = 10, message = "Mã phường/xã phải từ 1 đến 10 ký tự")
    @Schema(description = "Mã hành chính, unique", example = "26815")
    private String code;

    @NotNull(message = "Tên phường/xã không được để trống")
    @Size(min = 2, max = 100, message = "Tên phường/xã phải từ 2 đến 100 ký tự")
    @Schema(description = "Tên đầy đủ", example = "Phường 25")
    private String name;

    @NotNull(message = "Loại đơn vị không được để trống")
    @Pattern(regexp = "PHUONG|XA|THI_TRAN",
            message = "Loại đơn vị phải thuộc {PHUONG, XA, THI_TRAN}")
    @Schema(description = "Loại đơn vị", example = "PHUONG")
    private String type;

    @Schema(description = "Khu vực hỗ trợ đăng tin không; mặc định false", example = "true")
    private Boolean supported;

    @Schema(description = "Vĩ độ trung tâm")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ trung tâm")
    private BigDecimal longitude;
}
