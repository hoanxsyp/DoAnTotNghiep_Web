package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO trả về cho một tỉnh/thành phố ({@code provinces}) — mục 4.3.2 của {@code docs/03}.
 *
 * <p>Trường {@code supported} ánh xạ trực tiếp cột {@code is_active}: {@code true} nghĩa là khu
 * vực hệ thống đang hỗ trợ đăng tin {@code [§3.3]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ProvinceResponse", description = "Tỉnh/thành phố")
public class ProvinceResponse {

    @Schema(description = "Định danh", example = "79")
    private Long id;

    @Schema(description = "Mã hành chính", example = "79")
    private String code;

    @Schema(description = "Tên đầy đủ", example = "Thành phố Hồ Chí Minh")
    private String name;

    @Schema(description = "Tên rút gọn", example = "Hồ Chí Minh")
    private String shortName;

    @Schema(description = "Slug dùng cho URL", example = "ho-chi-minh")
    private String slug;

    @Schema(description = "Loại đơn vị", example = "THANH_PHO_TRUNG_UONG")
    private String type;

    @Schema(description = "Khu vực hệ thống có hỗ trợ đăng tin không", example = "true")
    private Boolean supported;

    @Schema(description = "Số tin đăng trong tỉnh/thành", example = "2431")
    private Integer listingCount;
}
