package com.webtro.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

/**
 * Thông tin người dùng đính kèm khi đăng nhập (docs/03 mục 4.1.2) — dùng để frontend dựng phiên
 * làm việc và hiển thị quyền (ẩn/hiện nút; quyền thực thi vẫn kiểm ở backend).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthUserResponse", description = "Thông tin người dùng đăng nhập")
public class AuthUserResponse {

    @Schema(description = "Id người dùng", example = "42")
    private Long id;

    @Schema(description = "Họ tên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Email", example = "nguyen.van.an@gmail.com")
    private String email;

    @Schema(description = "URL ảnh đại diện", nullable = true)
    private String avatarUrl;

    @Schema(description = "Trạng thái tài khoản", example = "ACTIVE")
    private String status;

    @Schema(description = "Danh sách vai trò", example = "[\"ROLE_TENANT\",\"ROLE_LANDLORD\"]")
    private List<String> roles;

    @Schema(description = "Danh sách permission suy ra từ vai trò")
    private List<String> permissions;

    @Schema(description = "Chủ trọ đã được xác minh hay chưa (null nếu không phải chủ trọ)", nullable = true)
    private Boolean landlordVerified;

    @Schema(description = "Lần đăng nhập trước đó", nullable = true)
    private Instant lastLoginAt;
}
