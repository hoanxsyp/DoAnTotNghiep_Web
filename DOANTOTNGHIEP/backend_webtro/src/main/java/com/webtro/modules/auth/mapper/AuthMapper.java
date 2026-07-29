package com.webtro.modules.auth.mapper;

import com.webtro.modules.auth.dto.response.AuthUserResponse;
import com.webtro.modules.auth.dto.response.RegisterResponse;
import com.webtro.modules.auth.dto.response.VerifyEmailResponse;
import com.webtro.modules.user.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Mapper thủ công entity ↔ DTO cho module Auth (canonical luật 3: mapper là nơi duy nhất chuyển
 * đổi, dùng Builder, KHÔNG MapStruct). Chỉ chuyển đổi dữ liệu — không chứa logic nghiệp vụ.
 */
@Component
public class AuthMapper {

    /** Dựng kết quả đăng ký từ {@link User} vừa tạo. */
    public RegisterResponse toRegisterResponse(User user, List<String> roles, boolean emailSent) {
        return RegisterResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .status(user.getStatus() == null ? null : user.getStatus().name())
                .roles(roles)
                .verificationEmailSent(emailSent)
                .createdAt(user.getCreatedAt())
                .build();
    }

    /**
     * Dựng thông tin người dùng cho response đăng nhập.
     *
     * @param landlordVerified {@code null} nếu người dùng không phải chủ trọ; ngược lại là trạng thái
     *                         đã xác minh chủ trọ.
     */
    public AuthUserResponse toAuthUserResponse(User user, List<String> roles, List<String> permissions,
                                               Boolean landlordVerified) {
        return AuthUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus() == null ? null : user.getStatus().name())
                .roles(roles)
                .permissions(permissions)
                .landlordVerified(landlordVerified)
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    /** Dựng kết quả xác thực email. */
    public VerifyEmailResponse toVerifyEmailResponse(User user, Instant verifiedAt) {
        return VerifyEmailResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .status(user.getStatus() == null ? null : user.getStatus().name())
                .verifiedAt(verifiedAt)
                .build();
    }
}
