package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Kết quả import khu vực hành chính ({@code POST /api/admin/areas/import}): số node tạo mới/cập nhật
 * theo từng cấp + danh sách cảnh báo (node bị bỏ qua do thiếu dữ liệu).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AreaImportResponse", description = "Kết quả import khu vực hành chính")
public class AreaImportResponse {

    private int provincesCreated;
    private int provincesUpdated;
    private int districtsCreated;
    private int districtsUpdated;
    private int wardsCreated;
    private int wardsUpdated;

    /** Cảnh báo cho các node bị bỏ qua (thiếu code/tên...). */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
}
