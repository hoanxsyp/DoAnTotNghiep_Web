package com.webtro.modules.moderation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thông tin độ tin cậy của người báo cáo, giúp Moderator nhận diện người báo cáo lạm dụng
 * (canonical 4.16.1/4.16.2, {@code [§3.13]}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ReporterInfo", description = "Độ tin cậy người báo cáo")
public class ReporterInfo {

    private Long id;
    private String fullName;
    private String email;
    private long reportCount;
    private long validReportCount;
    private long rejectedReportCount;
    private Long accountAgeDays;
}
