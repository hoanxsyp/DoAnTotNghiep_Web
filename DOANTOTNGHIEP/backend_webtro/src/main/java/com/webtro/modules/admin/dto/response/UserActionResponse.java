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
 * Kết quả các thao tác quản trị người dùng: khóa (4.13.3), mở khóa (4.13.4), đổi vai trò (4.13.5).
 * Các field không liên quan tới thao tác hiện tại bị bỏ khỏi JSON ({@code NON_NULL}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "UserActionResponse", description = "Kết quả thao tác quản trị người dùng")
public class UserActionResponse {

    private Long userId;
    private String status;
    private String previousStatus;
    private String reason;

    private List<String> previousRoles;
    private List<String> roles;
    private Boolean landlordProfileCreated;

    private Integer lockedListingCount;
    private Integer unlockedListingCount;
    private Integer revokedSessionCount;
    private Boolean userNotified;

    private Long auditLogId;
    private Instant at;
}
