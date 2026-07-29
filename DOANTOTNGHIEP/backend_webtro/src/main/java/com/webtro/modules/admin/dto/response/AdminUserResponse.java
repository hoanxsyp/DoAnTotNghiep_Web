package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Một người dùng trong màn quản trị (canonical 4.13.1). Các chỉ số uy tín/vi phạm lấy từ hồ sơ chủ
 * trọ (nếu có).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminUserResponse", description = "Người dùng (góc nhìn quản trị)")
public class AdminUserResponse {

    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private List<String> roles;
    private String status;
    private String statusLabel;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private Boolean landlordVerified;
    private Integer trustScore;
    private Integer listingCount;
    private Integer activeListingCount;
    private Integer validReportCount;
    private Integer warningCount;
    private String lockReason;
    private Instant lockedAt;
    private Long lockedBy;
    private Instant createdAt;
    private Instant lastLoginAt;
}
