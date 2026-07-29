package com.webtro.modules.moderation.dto.request;

import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;

/**
 * Yêu cầu tạo báo cáo vi phạm (canonical 4.8.1, {@code [§3.13]}).
 *
 * <p>Nhận qua {@code multipart/form-data} để kèm ảnh bằng chứng ({@link #evidenceImage}). Ràng
 * buộc "mô tả bắt buộc khi lý do = OTHER" ép ở tầng service (mã lỗi
 * {@code REPORT_DESCRIPTION_REQUIRED}), không đặt annotation vì là ràng buộc liên trường.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "CreateReportRequest", description = "Dữ liệu tạo báo cáo vi phạm")
public class CreateReportRequest {

    @NotNull(message = "Vui lòng chọn loại đối tượng bị báo cáo")
    @Schema(description = "Loại đối tượng bị báo cáo", example = "LISTING")
    private ReportTargetType targetType;

    @NotNull(message = "Thiếu định danh đối tượng bị báo cáo")
    @Positive(message = "Định danh đối tượng không hợp lệ")
    @Schema(description = "Id đối tượng bị báo cáo (theo targetType)", example = "877")
    private Long targetId;

    @NotNull(message = "Vui lòng chọn lý do báo cáo")
    @Schema(description = "Lý do báo cáo", example = "FAKE_IMAGE")
    private ReportReason reason;

    @Size(max = 1000, message = "Mô tả tối đa 1000 ký tự")
    @Schema(description = "Mô tả chi tiết (bắt buộc khi lý do = OTHER)", example = "Ảnh trong tin lấy từ tin khác.")
    private String description;

    @Schema(description = "Ảnh bằng chứng (JPG/PNG/WEBP, ≤5MB) — tùy chọn", format = "binary")
    private MultipartFile evidenceImage;
}
