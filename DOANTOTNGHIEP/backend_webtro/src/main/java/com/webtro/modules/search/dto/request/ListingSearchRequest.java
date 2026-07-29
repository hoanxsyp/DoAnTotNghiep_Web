package com.webtro.modules.search.dto.request;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.common.enums.ToiletType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Gom TẤT CẢ tham số lọc của endpoint tìm kiếm tin đăng (SRCH-01..08, {@code [§3.7]}, docs/03 mục 4.4.1).
 *
 * <p>Được Spring bind trực tiếp từ query string (POJO có setter). Các ràng buộc phụ thuộc lẫn nhau
 * (priceFrom &le; priceTo, districtId cần provinceId, độ dài keyword theo config, số amenity theo
 * config) được kiểm ở tầng service để đọc ngưỡng từ {@code SystemConfigService} thay vì hardcode.
 * Bean-validation ở đây chỉ chặn các biên trị hiển nhiên.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ListingSearchRequest", description = "Bộ lọc tìm kiếm tin đăng (SRCH-01..08)")
public class ListingSearchRequest {

    @Schema(description = "Từ khóa tìm trên tiêu đề/mô tả", example = "phong tro co gac")
    private String keyword;

    @Schema(description = "Tỉnh/thành", example = "79")
    private Long provinceId;

    @Schema(description = "Quận/huyện (phải kèm provinceId)", example = "765")
    private Long districtId;

    @Schema(description = "Phường/xã (phải kèm districtId)", example = "26815")
    private Long wardId;

    @Schema(description = "Loại tin theo id", example = "1")
    private Long categoryId;

    @Schema(description = "Loại tin theo mã (dùng cho URL SEO, thay cho categoryId)")
    private CategoryCode categoryCode;

    @DecimalMin(value = "0", message = "Giá từ không được âm")
    @DecimalMax(value = "999999999.99", message = "Giá từ vượt ngưỡng cho phép")
    @Schema(description = "Giá thuê tối thiểu (VND)", example = "2000000")
    private BigDecimal priceFrom;

    @DecimalMin(value = "0", message = "Giá đến không được âm")
    @DecimalMax(value = "999999999.99", message = "Giá đến vượt ngưỡng cho phép")
    @Schema(description = "Giá thuê tối đa (VND)", example = "4000000")
    private BigDecimal priceTo;

    @DecimalMin(value = "0", message = "Diện tích từ không được âm")
    @DecimalMax(value = "9999.99", message = "Diện tích từ vượt ngưỡng cho phép")
    @Schema(description = "Diện tích tối thiểu (m2)", example = "15")
    private BigDecimal areaFrom;

    @DecimalMin(value = "0", message = "Diện tích đến không được âm")
    @DecimalMax(value = "9999.99", message = "Diện tích đến vượt ngưỡng cho phép")
    @Schema(description = "Diện tích tối đa (m2)", example = "30")
    private BigDecimal areaTo;

    @Schema(description = "Danh sách id tiện ích - ngữ nghĩa AND (tin phải có tất cả)")
    private List<Long> amenityIds;

    @Min(value = 1, message = "Số người ở tối thiểu là 1")
    @Max(value = 20, message = "Số người ở tối đa là 20")
    @Schema(description = "Lọc tin có max_occupants >= giá trị này", example = "2")
    private Integer maxOccupants;

    @Schema(description = "Yêu cầu giới tính khi ở ghép")
    private GenderRequirement genderRequirement;

    @Schema(description = "Tình trạng nội thất")
    private FurnitureStatus furnitureStatus;

    @Schema(description = "true = chỉ tin cho nuôi thú cưng")
    private Boolean petAllowed;

    @Schema(description = "true = chỉ tin có chỗ để xe")
    private Boolean parkingAvailable;

    @Schema(description = "Giờ giấc ra vào")
    private CurfewType curfewType;

    @Schema(description = "Nhà vệ sinh riêng/chung")
    private ToiletType toiletType;

    @Schema(description = "true = tin có ban công (amenity BALCONY)")
    private Boolean hasBalcony;

    @Schema(description = "true = tin có máy lạnh (amenity AIR_CONDITIONER)")
    private Boolean hasAirConditioner;

    @Schema(description = "true = tin có máy giặt (amenity WASHING_MACHINE)")
    private Boolean hasWashingMachine;

    @Schema(description = "true = tin có thang máy (amenity ELEVATOR)")
    private Boolean hasElevator;

    @Min(value = 1, message = "Số phòng tối thiểu là 1")
    @Max(value = 20, message = "Số phòng tối đa là 20")
    @Schema(description = "Lọc tin có room_count >= giá trị này", example = "2")
    private Integer roomCountFrom;

    @Min(value = 1, message = "Số nhà vệ sinh tối thiểu là 1")
    @Max(value = 10, message = "Số nhà vệ sinh tối đa là 10")
    @Schema(description = "Lọc tin có toilet_count >= giá trị này", example = "1")
    private Integer toiletCountFrom;

    @DecimalMin(value = "0", message = "Tiền cọc tối đa không được âm")
    @Schema(description = "Lọc tin có deposit_amount <= giá trị này", example = "5000000")
    private BigDecimal depositMax;

    @Schema(description = "Lọc tin có thể vào ở từ ngày này trở về trước (available_from <= giá trị)")
    private LocalDate availableFrom;

    @Min(value = 0, message = "Điểm uy tín tối thiểu là 0")
    @Max(value = 100, message = "Điểm uy tín tối đa là 100")
    @Schema(description = "Lọc tin có trust_score >= giá trị này", example = "40")
    private Integer minTrustScore;

    @Schema(description = "Vĩ độ tâm tìm kiếm (đi cùng longitude + radiusKm)")
    private Double latitude;

    @Schema(description = "Kinh độ tâm tìm kiếm (đi cùng latitude + radiusKm)")
    private Double longitude;

    @DecimalMin(value = "0.5", message = "Bán kính tối thiểu 0.5 km")
    @DecimalMax(value = "50", message = "Bán kính tối đa 50 km")
    @Schema(description = "Bán kính tìm quanh tâm (km)", example = "5")
    private Double radiusKm;

    @Schema(description = "Sắp xếp: RELEVANCE | publishedAt,desc | price,asc | area,desc | viewCount,desc | trustScore,desc | distance,asc ...",
            example = "RELEVANCE")
    private String sort;
}
