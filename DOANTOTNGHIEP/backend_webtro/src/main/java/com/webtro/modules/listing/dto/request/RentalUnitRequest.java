package com.webtro.modules.listing.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** Tạo hoặc sửa phòng ({@code POST/PUT /api/landlord/rental-units}). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalUnitRequest {

    @NotBlank(message = "Vui lòng nhập tên phòng")
    @Size(max = 150, message = "Tên phòng tối đa 150 ký tự")
    private String name;

    /** Số/mã phòng (P101, Tầng 2…) — phân biệt các phòng cùng địa chỉ. */
    @Size(max = 50, message = "Số phòng tối đa 50 ký tự")
    private String unitLabel;

    @NotNull(message = "Vui lòng chọn tỉnh/thành phố")
    private Long provinceId;

    @NotNull(message = "Vui lòng chọn quận/huyện")
    private Long districtId;

    @NotNull(message = "Vui lòng chọn phường/xã")
    private Long wardId;

    @NotBlank(message = "Vui lòng nhập địa chỉ chi tiết")
    @Size(min = 5, max = 255, message = "Địa chỉ chi tiết phải từ 5 đến 255 ký tự")
    private String addressDetail;

    @DecimalMin(value = "8.0", message = "Vĩ độ không thuộc lãnh thổ Việt Nam")
    @DecimalMax(value = "24.0", message = "Vĩ độ không thuộc lãnh thổ Việt Nam")
    private BigDecimal latitude;

    @DecimalMin(value = "102.0", message = "Kinh độ không thuộc lãnh thổ Việt Nam")
    @DecimalMax(value = "110.0", message = "Kinh độ không thuộc lãnh thổ Việt Nam")
    private BigDecimal longitude;
}
