package com.webtro.modules.catalog.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Body cho {@code POST /api/admin/areas/import}. Import khu vực hành chính hàng loạt bằng JSON (KHÔNG
 * dùng CSV/Excel). Idempotent theo {@code code}: node đã tồn tại sẽ được cập nhật, node mới sẽ được
 * tạo. Cây lồng nhau tỉnh → huyện → xã; huyện/xã suy ra cha từ node bao ngoài (không cần id cha).
 */
@Getter
@Setter
@Schema(name = "AreaImportRequest", description = "Dữ liệu import khu vực hành chính (JSON)")
public class AreaImportRequest {

    @Schema(description = "Danh sách tỉnh/thành cần import")
    @NotEmpty(message = "Danh sách tỉnh/thành không được rỗng")
    @Valid
    private List<ProvinceImport> provinces;

    @Getter
    @Setter
    @Schema(name = "AreaProvinceImport", description = "Tỉnh/thành trong dữ liệu import")
    public static class ProvinceImport {
        @Schema(description = "Mã hành chính (khóa idempotent)", example = "79")
        private String code;
        @Schema(description = "Tên đầy đủ", example = "Thành phố Hồ Chí Minh")
        private String name;
        @Schema(description = "Tên rút gọn (tùy chọn)")
        private String shortName;
        @Schema(description = "Loại: TINH | THANH_PHO_TRUNG_UONG", example = "THANH_PHO_TRUNG_UONG")
        private String type;
        @Schema(description = "Hỗ trợ đăng tin không; mặc định false")
        private Boolean supported;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @Valid
        private List<DistrictImport> districts;
    }

    @Getter
    @Setter
    @Schema(name = "AreaDistrictImport", description = "Quận/huyện trong dữ liệu import")
    public static class DistrictImport {
        @Schema(description = "Mã hành chính (khóa idempotent)", example = "765")
        private String code;
        @Schema(description = "Tên đầy đủ", example = "Quận Bình Thạnh")
        private String name;
        @Schema(description = "Loại: QUAN | HUYEN | THI_XA | THANH_PHO_THUOC_TINH", example = "QUAN")
        private String type;
        private Boolean supported;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @Valid
        private List<WardImport> wards;
    }

    @Getter
    @Setter
    @Schema(name = "AreaWardImport", description = "Phường/xã trong dữ liệu import")
    public static class WardImport {
        @Schema(description = "Mã hành chính (khóa idempotent)", example = "26815")
        private String code;
        @Schema(description = "Tên đầy đủ", example = "Phường 25")
        private String name;
        @Schema(description = "Loại: PHUONG | XA | THI_TRAN", example = "PHUONG")
        private String type;
        private Boolean supported;
        private BigDecimal latitude;
        private BigDecimal longitude;
    }
}
