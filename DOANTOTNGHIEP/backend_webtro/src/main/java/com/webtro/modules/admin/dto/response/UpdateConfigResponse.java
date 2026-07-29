package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Kết quả cập nhật cấu hình (canonical 4.20.2 / 4.19.6). Dùng chung cho system-config và ai-config.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "UpdateConfigResponse", description = "Kết quả cập nhật cấu hình")
public class UpdateConfigResponse {

    @Schema(description = "Các cấu hình đã đổi (kèm giá trị cũ/mới)")
    private List<UpdatedItem> updated;

    @Schema(description = "Đã invalidate cache SystemConfigService chưa", example = "true")
    private boolean cacheInvalidated;

    @Schema(description = "Đã đưa job tính lại điểm uy tín vào hàng đợi chưa (chỉ khi đổi trọng số trust.*/sentiment)")
    private Boolean recalcJobQueued;

    @Schema(description = "Ghi chú (ví dụ: thay đổi không hồi tố, hoặc job recalc)")
    private String note;

    @Schema(description = "Id các bản ghi audit tương ứng")
    private List<Long> auditLogIds;

    @Schema(description = "Thời điểm cập nhật (ISO-8601 UTC)")
    private Instant updatedAt;

    /** Một khóa cấu hình đã cập nhật với giá trị cũ/mới (đã ép kiểu). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "UpdatedConfigItem", description = "Một khóa cấu hình đã cập nhật")
    public static class UpdatedItem {

        @Schema(description = "Khóa cấu hình", example = "listing.display_days")
        private String key;

        @Schema(description = "Giá trị cũ", example = "30")
        private Object oldValue;

        @Schema(description = "Giá trị mới", example = "45")
        private Object newValue;
    }
}
