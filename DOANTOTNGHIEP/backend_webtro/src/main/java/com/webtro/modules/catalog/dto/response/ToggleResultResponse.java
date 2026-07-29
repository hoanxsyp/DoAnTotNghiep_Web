package com.webtro.modules.catalog.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả bật/tắt hiển thị một tài nguyên catalog — mục 4.17.16–4.17.20 của {@code docs/03}.
 * Dùng chung cho danh mục, tiện ích, tỉnh/huyện/xã.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ToggleResultResponse", description = "Kết quả bật/tắt hiển thị")
public class ToggleResultResponse {

    @Schema(description = "Định danh tài nguyên", example = "6")
    private Long id;

    @Schema(description = "Mã tài nguyên", example = "ROOMMATE")
    private String code;

    @Schema(description = "Tên tài nguyên", example = "Ở ghép")
    private String name;

    @Schema(description = "Trạng thái hiển thị sau thao tác", example = "false")
    private Boolean active;

    @Schema(description = "Trạng thái hiển thị trước thao tác", example = "true")
    private Boolean previousActive;

    @Schema(description = "Số tin đăng bị ảnh hưởng (giữ nguyên hiển thị)", example = "287")
    private Integer affectedListingCount;

    @Schema(description = "Ghi chú giải thích ảnh hưởng")
    private String note;

    @Schema(description = "Danh sách cache đã được xóa")
    private List<String> cacheInvalidated;

    @Schema(description = "Thời điểm cập nhật")
    private Instant updatedAt;
}
