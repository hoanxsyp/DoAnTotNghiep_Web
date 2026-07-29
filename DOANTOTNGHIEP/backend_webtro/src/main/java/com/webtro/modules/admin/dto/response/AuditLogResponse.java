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
 * Một dòng nhật ký kiểm toán trả về cho Admin (canonical 4.20.3).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuditLogResponse", description = "Một bản ghi nhật ký kiểm toán")
public class AuditLogResponse {

    @Schema(description = "Id bản ghi audit", example = "44152")
    private Long id;

    @Schema(description = "Mã hành động (canonical §5)", example = "LISTING_LOCK")
    private String action;

    @Schema(description = "Nhãn hành động tiếng Việt", example = "Khóa tin đăng")
    private String actionLabel;

    @Schema(description = "users.id của người thực hiện", example = "1")
    private Long actorId;

    @Schema(description = "Email của actor tại thời điểm ghi", example = "admin@webtro.vn")
    private String actorEmail;

    @Schema(description = "Loại đối tượng bị tác động", example = "LISTING")
    private String targetType;

    @Schema(description = "Id đối tượng bị tác động", example = "877")
    private Long targetId;

    @Schema(description = "Nhãn đối tượng bị tác động (snapshot)")
    private String targetLabel;

    @Schema(description = "Các thay đổi field (suy từ old_value/new_value)")
    private List<ChangeEntry> changes;

    @Schema(description = "Lý do/ghi chú hành động")
    private String reason;

    @Schema(description = "IP của actor")
    private String ipAddress;

    @Schema(description = "User-Agent của actor")
    private String userAgent;

    @Schema(description = "Mã request (trace id) để đối chiếu log")
    private String requestId;

    @Schema(description = "Thời điểm ghi nhận (ISO-8601 UTC)")
    private Instant createdAt;

    /**
     * Một thay đổi field: dùng khi old/new value là JSON dạng {@code {"field": {"old":..,"new":..}}}
     * hoặc một cặp giá trị cấu hình đơn.
     */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "AuditChangeEntry", description = "Một thay đổi field trong audit log")
    public static class ChangeEntry {

        @Schema(description = "Tên field", example = "status")
        private String field;

        @Schema(description = "Giá trị cũ", example = "NEED_REVIEW")
        private String oldValue;

        @Schema(description = "Giá trị mới", example = "LOCKED")
        private String newValue;
    }
}
