package com.webtro.modules.listing.dto.request;

import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ToiletType;
import com.webtro.validator.ValidPhone;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Yêu cầu sửa tin đăng (LIST-03, docs/03 mục 4.4.8). Cùng cấu trúc {@link ListingCreateRequest}
 * nhưng bỏ {@code submitImmediately}, thêm {@code stillAvailable} và {@code editNote}.
 *
 * <p>Thay đổi các trường nhạy cảm (tiêu đề, mô tả, giá, địa chỉ, ảnh chính) trên tin đang
 * {@code ACTIVE} sẽ đưa tin về {@code PENDING} để duyệt lại (canonical §5.1 RESUBMIT_AFTER_EDIT).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingUpdateRequest {

    @NotNull(message = "Vui lòng chọn loại tin")
    private Long categoryId;

    @NotBlank(message = "Vui lòng nhập tiêu đề")
    @Size(max = 150, message = "Tiêu đề tối đa 150 ký tự")
    private String title;

    @NotBlank(message = "Vui lòng nhập mô tả")
    @Size(max = 3000, message = "Mô tả tối đa 3000 ký tự")
    private String description;

    @NotNull(message = "Vui lòng nhập giá thuê")
    @DecimalMin(value = "0.01", message = "Giá thuê phải lớn hơn 0")
    @DecimalMax(value = "999999999.99", message = "Giá thuê vượt quá giới hạn cho phép")
    @Digits(integer = 15, fraction = 2, message = "Giá thuê không hợp lệ")
    private BigDecimal price;

    @NotNull(message = "Vui lòng nhập diện tích")
    @DecimalMin(value = "0.01", message = "Diện tích phải lớn hơn 0")
    @DecimalMax(value = "9999.99", message = "Diện tích vượt quá giới hạn cho phép")
    @Digits(integer = 8, fraction = 2, message = "Diện tích không hợp lệ")
    private BigDecimal area;

    @DecimalMin(value = "0.0", message = "Tiền cọc không được nhỏ hơn 0")
    @Digits(integer = 15, fraction = 2, message = "Tiền cọc không hợp lệ")
    private BigDecimal depositAmount;

    @DecimalMin(value = "0.0", message = "Giá điện không được nhỏ hơn 0")
    @Digits(integer = 15, fraction = 2, message = "Giá điện không hợp lệ")
    private BigDecimal electricityPrice;

    @DecimalMin(value = "0.0", message = "Giá nước không được nhỏ hơn 0")
    @Digits(integer = 15, fraction = 2, message = "Giá nước không hợp lệ")
    private BigDecimal waterPrice;

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

    @Min(value = 1, message = "Số phòng phải từ 1 đến 20")
    @Max(value = 20, message = "Số phòng phải từ 1 đến 20")
    private Integer roomCount;

    @Min(value = 1, message = "Số nhà vệ sinh phải từ 1 đến 10")
    @Max(value = 10, message = "Số nhà vệ sinh phải từ 1 đến 10")
    private Integer toiletCount;

    private ToiletType toiletType;

    @NotNull(message = "Vui lòng nhập số người ở tối đa")
    @Min(value = 1, message = "Số người ở tối đa phải từ 1 đến 20")
    @Max(value = 20, message = "Số người ở tối đa phải từ 1 đến 20")
    private Integer maxOccupants;

    @Min(value = 0, message = "Số người hiện tại không được nhỏ hơn 0")
    private Integer currentOccupants;

    private GenderRequirement genderRequirement;

    private Boolean petAllowed;

    private Boolean parkingAvailable;

    private CurfewType curfewType;

    private FurnitureStatus furnitureStatus;

    private LocalDate availableFrom;

    @Min(value = 1, message = "Số tầng phải lớn hơn 0")
    private Integer floorCount;

    @Size(max = 30, message = "Chỉ được chọn tối đa 30 tiện ích")
    private List<Long> amenityIds;

    @NotBlank(message = "Vui lòng nhập tên người liên hệ")
    @Size(min = 2, max = 100, message = "Tên người liên hệ phải từ 2 đến 100 ký tự")
    private String contactName;

    @NotBlank(message = "Vui lòng nhập số điện thoại liên hệ")
    @ValidPhone
    private String contactPhone;

    /** Trạng thái còn phòng/hết phòng — thay đổi không cần kiểm duyệt lại {@code [§3.4]}. */
    private Boolean stillAvailable;

    @Size(max = 255, message = "Ghi chú chỉnh sửa tối đa 255 ký tự")
    private String editNote;
}
