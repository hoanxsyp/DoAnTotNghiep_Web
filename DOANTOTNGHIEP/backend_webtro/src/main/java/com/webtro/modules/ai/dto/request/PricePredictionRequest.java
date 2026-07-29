package com.webtro.modules.ai.dto.request;

import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.ToiletType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Yêu cầu dự đoán giá thuê (docs/03 mục 7.4.1, §3.16, §9.4). Đúng danh sách Input §9.4. Các trường
 * thiếu bắt buộc → 400 {@code AI_MISSING_INPUT_FIELD} (kiểm ở service để gom thông điệp tiếng Việt).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PricePredictionRequest", description = "Yêu cầu dự đoán giá thuê")
public class PricePredictionRequest {

    @NotNull(message = "Vui lòng chọn loại nhà")
    @Schema(description = "Loại nhà (categories.id)", example = "1")
    private Long categoryId;

    @NotNull(message = "Vui lòng chọn tỉnh/thành")
    @Schema(description = "Tỉnh/thành (provinces.id)", example = "79")
    private Long provinceId;

    @NotNull(message = "Vui lòng chọn quận/huyện")
    @Schema(description = "Quận/huyện (districts.id)", example = "765")
    private Long districtId;

    @NotNull(message = "Vui lòng chọn phường/xã")
    @Schema(description = "Phường/xã (wards.id)", example = "26815")
    private Long wardId;

    @NotNull(message = "Vui lòng nhập diện tích")
    @DecimalMin(value = "0.01", message = "Diện tích phải lớn hơn 0")
    @DecimalMax(value = "1000.00", message = "Diện tích tối đa 1000 m²")
    @Schema(description = "Diện tích (m²)", example = "22")
    private BigDecimal area;

    @Min(value = 1, message = "Số phòng tối thiểu là 1")
    @Max(value = 20, message = "Số phòng tối đa là 20")
    @Schema(description = "Số phòng", example = "1")
    private Integer roomCount;

    @Min(value = 1, message = "Số toilet tối thiểu là 1")
    @Max(value = 10, message = "Số toilet tối đa là 10")
    @Schema(description = "Số toilet", example = "1")
    private Integer toiletCount;

    @Schema(description = "Tình trạng nội thất (mặc định NONE)", example = "BASIC")
    private FurnitureStatus furnitureStatus;

    @Schema(description = "Nhà vệ sinh riêng/chung (mặc định PRIVATE)", example = "PRIVATE")
    private ToiletType toiletType;

    @Schema(description = "Giờ giấc (dùng cho điều chỉnh hedonic giờ tự do)", example = "FREE")
    private CurfewType curfewType;

    @Size(max = 30, message = "Tối đa 30 tiện ích")
    @Schema(description = "Danh sách tiện ích (amenities.id)")
    private List<Long> amenityIds;

    @Schema(description = "Mặt tiền/hẻm (true = mặt tiền)", example = "false")
    private Boolean isStreetFront;

    @DecimalMin(value = "-90", message = "Vĩ độ không hợp lệ")
    @DecimalMax(value = "90", message = "Vĩ độ không hợp lệ")
    @Schema(description = "Vĩ độ (khoảng cách trung tâm)", example = "10.801")
    private Double latitude;

    @DecimalMin(value = "-180", message = "Kinh độ không hợp lệ")
    @DecimalMax(value = "180", message = "Kinh độ không hợp lệ")
    @Schema(description = "Kinh độ (khoảng cách trung tâm)", example = "106.712")
    private Double longitude;

    @Schema(description = "Tin đang sửa (để ghi prediction_histories + gắn cờ)", example = "1024")
    private Long listingId;

    @DecimalMin(value = "0.01", message = "Giá nhập phải lớn hơn 0")
    @Schema(description = "Giá chủ trọ định nhập → tính deviationRatio", example = "3500000")
    private BigDecimal inputPrice;
}
