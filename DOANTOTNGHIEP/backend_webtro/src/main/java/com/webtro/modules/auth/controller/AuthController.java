package com.webtro.modules.auth.controller;

import com.webtro.common.ApiResponse;
import com.webtro.constant.AppConstant;
import com.webtro.modules.auth.dto.request.ChangePasswordRequest;
import com.webtro.modules.auth.dto.request.ForgotPasswordRequest;
import com.webtro.modules.auth.dto.request.LoginRequest;
import com.webtro.modules.auth.dto.request.LogoutRequest;
import com.webtro.modules.auth.dto.request.RefreshTokenRequest;
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
import com.webtro.modules.auth.service.AuthService;
import com.webtro.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;

/**
 * API xác thực và quản lý phiên (AUTH-01..08) — docs/03 mục 4.1.
 *
 * <p>Controller chỉ điều phối: trích thông tin hạ tầng (IP, user-agent, cookie, header) rồi gọi
 * {@link AuthService}; đặt/xóa cookie refresh token; trả đúng HTTP status + envelope. Không chứa
 * logic nghiệp vụ (canonical luật 1 & 6).
 *
 * <p>Refresh token đặt trong cookie {@code httpOnly; Secure; SameSite=Strict; Path=/api/auth}
 * (canonical mục 8), đồng thời trả trong body theo hợp đồng docs/03.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "01. Auth", description = "Đăng ký, đăng nhập, làm mới token, xác thực tài khoản")
public class AuthController {

    private final AuthService authService;

    // ==================================================================

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản",
            description = "Tạo tài khoản mới ở trạng thái PENDING_VERIFY và gửi email xác thực (AUTH-01).")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        RegisterResponse data = authService.register(request, clientIp(httpRequest));
        return ResponseEntity
                .created(URI.create("/api/users/" + data.getUserId()))
                .body(ApiResponse.success(data,
                        "Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản."));
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập",
            description = "Xác thực email/số điện thoại + mật khẩu, cấp access token và refresh token (AUTH-02).")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        LoginResponse data = authService.login(request, clientIp(httpRequest), userAgent(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.getRefreshToken(), data.getRefreshExpiresIn()).toString())
                .body(ApiResponse.success(data, "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token",
            description = "Xoay vòng refresh token (rotation) + phát hiện tái sử dụng. Ưu tiên đọc token từ cookie.")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @CookieValue(name = AppConstant.REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        String raw = firstNonBlank(cookieToken, request == null ? null : request.getRefreshToken());
        TokenResponse data = authService.refresh(raw, clientIp(httpRequest), userAgent(httpRequest));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, buildRefreshCookie(data.getRefreshToken(), data.getRefreshExpiresIn()).toString())
                .body(ApiResponse.success(data, "Làm mới phiên thành công"));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất",
            description = "Thu hồi refresh token trong DB, đưa access token vào blacklist và xóa cookie (AUTH-03).")
    public ResponseEntity<Void> logout(
            @CookieValue(name = AppConstant.REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) LogoutRequest request,
            HttpServletRequest httpRequest) {
        String raw = firstNonBlank(cookieToken, request == null ? null : request.getRefreshToken());
        authService.logout(raw, bearerToken(httpRequest));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie().toString())
                .build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Quên mật khẩu",
            description = "Gửi liên kết đặt lại mật khẩu (TTL 30 phút). Luôn trả 200 để chống dò tài khoản (AUTH-04).")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        authService.forgotPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(
                "Nếu email tồn tại trong hệ thống, chúng tôi đã gửi liên kết đặt lại mật khẩu."));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Đặt lại mật khẩu",
            description = "Đặt lại mật khẩu bằng token trong email và thu hồi toàn bộ refresh token (AUTH-04).")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request, HttpServletRequest httpRequest) {
        authService.resetPassword(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success("Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại."));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu",
            description = "Đổi mật khẩu khi đã đăng nhập; đăng xuất các thiết bị khác (AUTH-05).")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal CustomUserDetails principal,
            @CookieValue(name = AppConstant.REFRESH_TOKEN_COOKIE, required = false) String cookieToken,
            @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), cookieToken, request);
        return ResponseEntity.ok(ApiResponse.success("Đổi mật khẩu thành công. Các thiết bị khác đã bị đăng xuất."));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Xác thực email",
            description = "Kích hoạt tài khoản (PENDING_VERIFY → ACTIVE) bằng token trong email (AUTH-06).")
    public ResponseEntity<ApiResponse<VerifyEmailResponse>> verifyEmail(
            @Valid @RequestBody VerifyEmailRequest request, HttpServletRequest httpRequest) {
        VerifyEmailResponse data = authService.verifyEmail(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(data, "Xác thực email thành công. Bạn có thể đăng nhập ngay."));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Gửi lại email xác thực",
            description = "Vô hiệu hóa token cũ và gửi email xác thực mới. Luôn trả 200 nếu email không tồn tại.")
    public ResponseEntity<ApiResponse<ResendVerificationResponse>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request, HttpServletRequest httpRequest) {
        ResendVerificationResponse data = authService.resendVerification(request, clientIp(httpRequest));
        return ResponseEntity.ok(ApiResponse.success(data,
                "Nếu tài khoản chưa xác thực, email xác thực đã được gửi lại."));
    }

    @PostMapping("/send-phone-otp")
    @Operation(summary = "Gửi OTP số điện thoại",
            description = "Gửi OTP 6 chữ số (TTL 5 phút) để xác thực số điện thoại của tài khoản đang đăng nhập (AUTH-06).")
    public ResponseEntity<ApiResponse<PhoneOtpResponse>> sendPhoneOtp(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody SendPhoneOtpRequest request) {
        PhoneOtpResponse data = authService.sendPhoneOtp(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Mã OTP đã được gửi đến số " + data.getMaskedPhone()));
    }

    @PostMapping("/verify-phone")
    @Operation(summary = "Xác thực số điện thoại",
            description = "Xác thực số điện thoại bằng OTP 6 chữ số (AUTH-06).")
    public ResponseEntity<ApiResponse<VerifyPhoneResponse>> verifyPhone(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody VerifyPhoneRequest request) {
        VerifyPhoneResponse data = authService.verifyPhone(principal.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(data, "Xác thực số điện thoại thành công"));
    }

    // ==================================================================
    //  Helper hạ tầng web
    // ==================================================================

    /** Cookie refresh token theo canonical mục 8. */
    private ResponseCookie buildRefreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(AppConstant.REFRESH_TOKEN_COOKIE, token)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AppConstant.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(Duration.ofSeconds(Math.max(0, maxAgeSeconds)))
                .build();
    }

    /** Cookie rỗng hết hạn ngay — dùng khi đăng xuất. */
    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(AppConstant.REFRESH_TOKEN_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(AppConstant.REFRESH_TOKEN_COOKIE_PATH)
                .maxAge(0)
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String userAgent(HttpServletRequest request) {
        return request.getHeader(HttpHeaders.USER_AGENT);
    }

    /** Trích access token thô từ header Authorization (dùng để đưa jti vào blacklist khi logout). */
    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }
}
