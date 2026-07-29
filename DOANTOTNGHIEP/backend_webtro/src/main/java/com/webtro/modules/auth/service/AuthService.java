package com.webtro.modules.auth.service;

import com.webtro.modules.auth.dto.request.ChangePasswordRequest;
import com.webtro.modules.auth.dto.request.ForgotPasswordRequest;
import com.webtro.modules.auth.dto.request.LoginRequest;
import com.webtro.modules.auth.dto.request.RegisterRequest;
import com.webtro.modules.auth.dto.request.ResendVerificationRequest;
import com.webtro.modules.auth.dto.request.ResetPasswordRequest;
import com.webtro.modules.auth.dto.request.SendPhoneOtpRequest;
import com.webtro.modules.auth.dto.request.VerifyEmailRequest;
import com.webtro.modules.auth.dto.request.VerifyPhoneRequest;
import com.webtro.modules.auth.dto.response.LoginResponse;
import com.webtro.modules.auth.dto.response.PhoneOtpResponse;
import com.webtro.modules.auth.dto.response.RegisterResponse;
import com.webtro.modules.auth.dto.response.ResendVerificationResponse;
import com.webtro.modules.auth.dto.response.TokenResponse;
import com.webtro.modules.auth.dto.response.VerifyEmailResponse;
import com.webtro.modules.auth.dto.response.VerifyPhoneResponse;

/**
 * Nghiệp vụ xác thực và quản lý phiên (AUTH-01..08) — docs/03 mục 4.1.
 *
 * <p>Controller chỉ gọi interface này (canonical luật 1). Các tham số hạ tầng của tầng web
 * ({@code ipAddress}, {@code userAgent}, refresh token thô, access token thô) được controller trích
 * từ request/cookie/header rồi truyền vào — tầng service không phụ thuộc {@code HttpServletRequest}.
 */
public interface AuthService {

    /** Đăng ký tài khoản mới (status PENDING_VERIFY), gửi email xác thực. */
    RegisterResponse register(RegisterRequest request, String ipAddress);

    /** Đăng nhập, phát access + refresh token và trả token thô để controller đặt cookie. */
    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    /** Làm mới token: xoay vòng + phát hiện tái sử dụng. {@code refreshTokenRaw} lấy từ cookie/body. */
    TokenResponse refresh(String refreshTokenRaw, String ipAddress, String userAgent);

    /** Đăng xuất: thu hồi cả họ refresh token và đưa jti access token vào blacklist. Idempotent. */
    void logout(String refreshTokenRaw, String accessTokenRaw);

    /** Quên mật khẩu: sinh token đặt lại (TTL 30 phút) và gửi mail. Luôn "thành công" về phía client. */
    void forgotPassword(ForgotPasswordRequest request, String ipAddress);

    /** Đặt lại mật khẩu bằng token trong email; thu hồi toàn bộ refresh token của user. */
    void resetPassword(ResetPasswordRequest request, String ipAddress);

    /** Đổi mật khẩu khi đã đăng nhập; thu hồi refresh token của các thiết bị khác. */
    void changePassword(Long userId, String currentRefreshTokenRaw, ChangePasswordRequest request);

    /** Xác thực email bằng token; PENDING_VERIFY → ACTIVE. */
    VerifyEmailResponse verifyEmail(VerifyEmailRequest request, String ipAddress);

    /** Gửi lại email xác thực (vô hiệu hóa token cũ, phát token mới). */
    ResendVerificationResponse resendVerification(ResendVerificationRequest request, String ipAddress);

    /** Gửi OTP xác thực số điện thoại cho tài khoản đang đăng nhập. */
    PhoneOtpResponse sendPhoneOtp(Long userId, SendPhoneOtpRequest request);

    /** Xác thực số điện thoại bằng OTP. */
    VerifyPhoneResponse verifyPhone(Long userId, VerifyPhoneRequest request);
}
