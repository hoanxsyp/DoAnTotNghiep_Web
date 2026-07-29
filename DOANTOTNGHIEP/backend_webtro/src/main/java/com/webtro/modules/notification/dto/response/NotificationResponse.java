package com.webtro.modules.notification.dto.response;

import com.webtro.common.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Một thông báo trả về client (docs/03 mục 4.10.1).
 *
 * <p>{@code targetType}/{@code targetId}/{@code targetUrl} ánh xạ từ {@code ref_type}/{@code ref_id}/
 * {@code link} của entity; {@code read}/{@code readAt} từ {@code is_read}/{@code read_at}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "NotificationResponse", description = "Một thông báo của người dùng")
public class NotificationResponse {

    @Schema(description = "Id thông báo", example = "88201")
    private Long id;

    @Schema(description = "Loại thông báo", example = "LISTING_APPROVED")
    private NotificationType type;

    @Schema(description = "Nhãn tiếng Việt của loại", example = "Tin được duyệt")
    private String typeLabel;

    @Schema(description = "Tiêu đề", example = "Tin đăng của bạn đã được duyệt")
    private String title;

    @Schema(description = "Nội dung")
    private String content;

    @Schema(description = "Kiểu icon gợi ý cho FE", example = "SUCCESS",
            allowableValues = {"SUCCESS", "WARNING", "ERROR", "INFO"})
    private String iconType;

    @Schema(description = "Loại đối tượng liên quan (LISTING, PAYMENT, CONVERSATION...)", example = "LISTING")
    private String targetType;

    @Schema(description = "Id đối tượng liên quan", example = "1024")
    private Long targetId;

    @Schema(description = "Đường dẫn điều hướng khi bấm vào thông báo",
            example = "/tin/phong-tro-moi-xay-1024")
    private String targetUrl;

    @Schema(description = "Đã đọc hay chưa", example = "false")
    private boolean read;

    @Schema(description = "Thời điểm đọc (null nếu chưa đọc)")
    private Instant readAt;

    @Schema(description = "Thời điểm tạo thông báo (ISO-8601, UTC)")
    private Instant createdAt;
}
