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
 * Kết quả đăng ký (AUTH-01). Không chứa token — người dùng phải xác thực email rồi mới đăng nhập.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "RegisterResponse", description = "Kết quả đăng ký tài khoản")
public class RegisterResponse {

    @Schema(description = "Id người dùng vừa tạo", example = "103")
    private Long userId;

    @Schema(description = "Email đã đăng ký", example = "nguyen.van.an@gmail.com")
    private String email;

    @Schema(description = "Họ tên", example = "Nguyễn Văn An")
    private String fullName;

    @Schema(description = "Trạng thái tài khoản", example = "PENDING_VERIFY")
    private String status;

    @Schema(description = "Danh sách vai trò được gán", example = "[\"ROLE_TENANT\",\"ROLE_LANDLORD\"]")
    private List<String> roles;

    @Schema(description = "Đã gửi email xác thực hay chưa", example = "true")
    private boolean verificationEmailSent;

    @Schema(description = "Thời điểm tạo tài khoản")
    private Instant createdAt;
}
