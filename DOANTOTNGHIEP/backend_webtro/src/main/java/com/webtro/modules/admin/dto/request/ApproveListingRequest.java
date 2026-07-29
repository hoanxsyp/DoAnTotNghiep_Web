package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu duyệt tin (canonical 4.14.3).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApproveListingRequest", description = "Yêu cầu duyệt tin đăng")
public class ApproveListingRequest {

    @Schema(description = "Ghi chú nội bộ (≤500 ký tự)")
    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    private String note;

    @Schema(description = "Số ngày hiển thị (1–90); mặc định đọc listing.display_days", example = "30")
    @Min(value = 1, message = "Số ngày hiển thị tối thiểu 1")
    @Max(value = 90, message = "Số ngày hiển thị tối đa 90")
    private Integer displayDays;
}
