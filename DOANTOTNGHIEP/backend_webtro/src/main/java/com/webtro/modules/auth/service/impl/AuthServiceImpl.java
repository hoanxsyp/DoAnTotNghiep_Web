package com.webtro.modules.auth.service.impl;

import com.webtro.common.enums.NotificationType;
import com.webtro.common.enums.UserStatus;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import com.webtro.common.mail.MailService;
import com.webtro.config.properties.AppProperties;
import com.webtro.config.properties.JwtProperties;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.constant.RoleCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.auth.dto.request.ChangePasswordRequest;
import com.webtro.modules.auth.dto.request.ForgotPasswordRequest;
import com.webtro.modules.auth.dto.request.LoginRequest;
import com.webtro.modules.auth.dto.request.RegisterRequest;
import com.webtro.modules.auth.dto.request.ResendVerificationRequest;
import com.webtro.modules.auth.dto.request.ResetPasswordRequest;
import com.webtro.modules.auth.dto.request.SendPhoneOtpRequest;
import com.webtro.modules.auth.dto.request.VerifyEmailRequest;
import com.webtro.modules.auth.dto.request.VerifyPhoneRequest;
import com.webtro.modules.auth.dto.response.AuthUserResponse;
import com.webtro.modules.auth.dto.response.LoginResponse;
import com.webtro.modules.auth.dto.response.PhoneOtpResponse;
import com.webtro.modules.auth.dto.response.RegisterResponse;
import com.webtro.modules.auth.dto.response.ResendVerificationResponse;
import com.webtro.modules.auth.dto.response.TokenResponse;
import com.webtro.modules.auth.dto.response.VerifyEmailResponse;
import com.webtro.modules.auth.dto.response.VerifyPhoneResponse;
import com.webtro.modules.auth.mapper.AuthMapper;
import com.webtro.modules.auth.service.AuthService;
import com.webtro.modules.user.entity.LandlordProfile;
import com.webtro.modules.user.entity.PasswordResetToken;
import com.webtro.modules.user.entity.RefreshToken;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserRole;
import com.webtro.modules.user.entity.Verification;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.PasswordResetTokenRepository;
import com.webtro.modules.user.repository.PermissionRepository;
import com.webtro.modules.user.repository.RefreshTokenRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.UserRoleRepository;
import com.webtro.modules.user.repository.VerificationRepository;
import com.webtro.security.TokenBlacklistService;
import com.webtro.security.RateLimitService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.MaskUtil;
import com.webtro.util.PhoneUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Cài đặt nghiệp vụ xác thực (AUTH-01..08) — docs/03 mục 4.1, canonical mục 8.
 *
 * <p>Điểm mấu chốt bảo mật:
 * <ul>
 *   <li>Refresh token opaque UUID, DB chỉ lưu SHA-256; xoay vòng mỗi lần refresh; phát hiện tái sử
 *       dụng → thu hồi cả họ token ({@code family_id}).</li>
 *   <li>Không phân biệt "sai định danh" và "sai mật khẩu" (đều {@code INVALID_CREDENTIALS}) để chống
 *       dò tài khoản.</li>
 *   <li>Quên mật khẩu / gửi lại xác thực luôn "thành công" về phía client dù email không tồn tại.</li>
 * </ul>
 *
 * <p><b>Ngưỡng nghiệp vụ đọc từ {@link SystemConfigService}</b> (số lần đăng nhập/đăng ký, cửa sổ
 * khóa...). Một số giới hạn tần suất theo từng endpoint (quên mật khẩu 3/giờ, refresh 20/phút...) mà
 * canonical mục 9 chưa cấp config key được giữ ở hằng {@code *_MAX}/{@code *_WINDOW} bên dưới — đây
 * là ngưỡng KỸ THUẬT chống lạm dụng, không phải tham số nghiệp vụ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    // ---- Giới hạn tần suất kỹ thuật theo endpoint (canonical mục 9 chưa cấp config key) ----
    private static final Duration REGISTER_WINDOW = Duration.ofHours(1);
    private static final int FORGOT_PASSWORD_MAX = 3;
    private static final Duration FORGOT_PASSWORD_WINDOW = Duration.ofHours(1);
    private static final int RESET_PASSWORD_MAX = 5;
    private static final Duration RESET_PASSWORD_WINDOW = Duration.ofHours(1);
    private static final int CHANGE_PASSWORD_MAX = 5;
    private static final Duration CHANGE_PASSWORD_WINDOW = Duration.ofHours(1);
    private static final int VERIFY_EMAIL_MAX = 10;
    private static final Duration VERIFY_EMAIL_WINDOW = Duration.ofHours(1);
    private static final int REFRESH_MAX = 20;
    private static final Duration REFRESH_WINDOW = Duration.ofMinutes(1);
    private static final int RESEND_DAILY_MAX = 5;
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration RESEND_DAILY_WINDOW = Duration.ofDays(1);
    private static final int PHONE_OTP_DAILY_MAX = 5;
    private static final Duration PHONE_OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration PHONE_OTP_DAILY_WINDOW = Duration.ofDays(1);
    private static final int VERIFY_PHONE_MAX = 10;
    private static final Duration VERIFY_PHONE_WINDOW = Duration.ofHours(1);
    private static final int OTP_MAX_ATTEMPTS = 5;

    // ---- Thời hạn token ----
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final Duration PHONE_OTP_TTL = Duration.ofMinutes(5);
    private static final int TOKEN_BYTES = 32; // 32 byte -> 64 ký tự hex

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final LandlordProfileRepository landlordProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationRepository verificationRepository;
    private final PermissionRepository permissionRepository;

    private final PasswordEncoder passwordEncoder;
    private final com.webtro.security.JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final TokenBlacklistService tokenBlacklistService;
    private final RateLimitService rateLimitService;
    private final SystemConfigService systemConfigService;
    private final com.webtro.modules.notification.service.NotificationService notificationService;
    private final MailService mailService;
    private final AppProperties appProperties;
    private final AuthMapper authMapper;

    // ==================================================================
    //  AUTH-01 — Đăng ký
    // ==================================================================

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request, String ipAddress) {
        // 1) Rate limit đăng ký: 3/giờ/IP (số lần từ config, cửa sổ kỹ thuật 1 giờ).
        int registerMax = systemConfigService.getInt(ConfigKey.SECURITY_REGISTER_RATE);
        if (rateLimitService.currentCount("register", safeId(ipAddress)) >= registerMax) {
            throw new RateLimitException(ErrorCode.REGISTER_RATE_LIMIT, REGISTER_WINDOW.getSeconds());
        }

        // 2) Xác nhận mật khẩu khớp.
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        String fullName = HtmlSanitizer.stripAllHtml(request.getFullName());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone = PhoneUtil.normalize(request.getPhone());
        boolean asLandlord = request.getRequestedRole() == RegisterRequest.RequestedRole.LANDLORD;

        // 3) Chủ trọ bắt buộc bổ sung thông tin liên hệ [§3.1].
        String contactName = null;
        String contactPhone = null;
        if (asLandlord) {
            contactName = request.getContactName() == null ? "" : HtmlSanitizer.stripAllHtml(request.getContactName());
            contactPhone = PhoneUtil.normalize(request.getContactPhone());
            if (contactName.isBlank() || contactPhone.isBlank() || !PhoneUtil.isValid(contactPhone)) {
                throw new BusinessException(ErrorCode.LANDLORD_CONTACT_REQUIRED);
            }
        }

        // 4) Duy nhất email/số điện thoại (loại trừ tài khoản đã xóa mềm).
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        if (userRepository.existsByPhoneAndDeletedAtIsNull(phone)) {
            throw new ConflictException(ErrorCode.PHONE_ALREADY_EXISTS);
        }

        // 5) Tạo user PENDING_VERIFY, hash BCrypt cost 12.
        User user = User.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .status(UserStatus.PENDING_VERIFY)
                .failedLoginCount(0)
                .build();
        user = userRepository.save(user);

        // 6) Gán vai trò: luôn TENANT; nếu chọn chủ trọ thì thêm LANDLORD (canonical §4.1).
        List<String> roleCodes = new ArrayList<>();
        assignRole(user, RoleCode.TENANT);
        roleCodes.add(RoleCode.TENANT);
        if (asLandlord) {
            assignRole(user, RoleCode.LANDLORD);
            roleCodes.add(RoleCode.LANDLORD);
            createLandlordProfile(user, fullName, contactName, contactPhone, email);
        }

        // 7) Sinh Verification EMAIL (token 64 ký tự, TTL 24 giờ) và gửi email xác thực.
        String rawToken = createEmailVerification(user, email);
        boolean emailSent = sendVerificationEmail(user, rawToken);

        // 8) Thông báo ACCOUNT_REGISTERED (in-app + email theo [§5.6]).
        notificationService.notifyUser(user.getId(), NotificationType.ACCOUNT_REGISTERED,
                "Chào mừng bạn đến với Webtro",
                "Tài khoản của bạn đã được tạo. Vui lòng xác thực email để kích hoạt.");

        // 9) Ghi nhận đã dùng một lượt rate limit đăng ký.
        rateLimitService.checkLimit("register", safeId(ipAddress), registerMax, REGISTER_WINDOW);

        return authMapper.toRegisterResponse(user, roleCodes, emailSent);
    }

    // ==================================================================
    //  AUTH-02 — Đăng nhập
    // ==================================================================

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        int maxAttempts = systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_MAX_ATTEMPTS);
        int captchaAfter = systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_CAPTCHA_AFTER_ATTEMPTS);
        int windowMinutes = systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_WINDOW_MINUTES);
        int lockMinutes = systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_LOCK_MINUTES);
        Duration window = Duration.ofMinutes(windowMinutes);

        String identifier = request.getEmailOrPhone().trim().toLowerCase(Locale.ROOT);
        String counterId = safeId(ipAddress) + ":" + sha256Hex(identifier);

        // 1) Đang bị khóa tạm do sai quá nhiều?
        long fails = rateLimitService.currentCount("login", counterId);
        if (fails >= maxAttempts) {
            throw new RateLimitException(ErrorCode.LOGIN_ATTEMPT_EXCEEDED, Duration.ofMinutes(lockMinutes).getSeconds());
        }
        // 2) Sau ngưỡng captcha, bắt buộc phải có captcha.
        if (fails >= captchaAfter && isBlank(request.getCaptchaToken())) {
            throw new BusinessException(ErrorCode.CAPTCHA_REQUIRED);
        }

        // 3) Tra cứu user theo email hoặc số điện thoại.
        Optional<User> userOpt = resolveByEmailOrPhone(identifier);

        // 4) Kiểm mật khẩu. Sai định danh hoặc sai mật khẩu → cùng INVALID_CREDENTIALS.
        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPasswordHash())) {
            recordLoginFailure(counterId, maxAttempts, window, userOpt.orElse(null));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = userOpt.get();

        // 5) Kiểm trạng thái tài khoản (sau khi mật khẩu đã đúng — chủ tài khoản được biết lý do).
        switch (user.getStatus()) {
            case DELETED -> throw new ForbiddenException(ErrorCode.ACCOUNT_DELETED);
            case LOCKED -> throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản của bạn đã bị khóa"
                            + (user.getLockReason() != null ? ". Lý do: " + user.getLockReason() : ""));
            case PENDING_VERIFY -> throw new ForbiddenException(ErrorCode.ACCOUNT_NOT_VERIFIED);
            case ACTIVE -> { /* tiếp tục */ }
        }

        // 6) Thành công: reset đếm sai, ghi last_login_at.
        rateLimitService.reset("login", counterId);
        Instant now = Instant.now();
        user.setLastLoginAt(now);
        user.setFailedLoginCount(0);

        // 7) Phát access token + refresh token (họ token mới).
        String familyId = java.util.UUID.randomUUID().toString();
        Instant refreshExpiry = now.plus(jwtProperties.getRefreshTokenTtl());
        String rawRefresh = createRefreshToken(user, familyId, null, now, refreshExpiry, ipAddress, userAgent);

        List<String> roles = roleRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = permissionRepository.findPermissionCodesByUserId(user.getId());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles, permissions);

        AuthUserResponse userInfo = authMapper.toAuthUserResponse(user, roles, permissions, resolveLandlordVerified(user.getId()));

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenTtl().getSeconds())
                .refreshExpiresIn(jwtProperties.getRefreshTokenTtl().getSeconds())
                .user(userInfo)
                .build();
    }

    // ==================================================================
    //  AUTH — Refresh (xoay vòng + reuse detection)
    // ==================================================================

    @Override
    @Transactional
    public TokenResponse refresh(String refreshTokenRaw, String ipAddress, String userAgent) {
        rateLimitService.checkLimit("refresh", safeId(ipAddress), REFRESH_MAX, REFRESH_WINDOW);

        if (isBlank(refreshTokenRaw)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        String hash = sha256Hex(refreshTokenRaw.trim());
        RefreshToken current = refreshTokenRepository.findByTokenHashAndDeletedAtIsNull(hash)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        Instant now = Instant.now();

        // Hết hạn theo họ token.
        if (current.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Phát hiện tái sử dụng: token đã bị thu hồi mà vẫn được dùng lại.
        if (current.getRevokedAt() != null) {
            int graceSeconds = systemConfigService.getInt(ConfigKey.SECURITY_REFRESH_GRACE_SECONDS);
            Instant usedAt = current.getUsedAt();
            if (usedAt != null && Duration.between(usedAt, now).getSeconds() <= graceSeconds) {
                // Trong cửa sổ ân hạn: coi như gửi trùng do mạng chập chờn, KHÔNG thu hồi cả họ.
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID,
                        "Phiên đang được làm mới, vui lòng thử lại");
            }
            revokeFamily(current.getFamilyId(), "REUSE_DETECTED", now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        User user = current.getUser();
        if (user.getStatus() == UserStatus.LOCKED || user.getStatus() == UserStatus.DELETED) {
            revokeFamily(current.getFamilyId(), "ACCOUNT_LOCKED", now);
            throw new ForbiddenException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản của bạn đã bị khóa"
                            + (user.getLockReason() != null ? ". Lý do: " + user.getLockReason() : ""));
        }

        // Xoay vòng: thu hồi token cũ, phát token mới cùng họ, giữ nguyên hạn của họ.
        current.setUsedAt(now);
        current.setRevokedAt(now);
        current.setRevokedReason("ROTATED");
        String rawRefresh = createRefreshToken(user, current.getFamilyId(), current, now,
                current.getExpiresAt(), ipAddress, userAgent);

        List<String> roles = roleRepository.findRoleCodesByUserId(user.getId());
        List<String> permissions = permissionRepository.findPermissionCodesByUserId(user.getId());
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles, permissions);

        long refreshRemaining = Math.max(0, current.getExpiresAt().getEpochSecond() - now.getEpochSecond());
        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefresh)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenTtl().getSeconds())
                .refreshExpiresIn(refreshRemaining)
                .build();
    }

    // ==================================================================
    //  AUTH-03 — Đăng xuất
    // ==================================================================

    @Override
    @Transactional
    public void logout(String refreshTokenRaw, String accessTokenRaw) {
        Instant now = Instant.now();
        // 1) Thu hồi cả họ refresh token (nếu token còn tra được). Idempotent.
        if (!isBlank(refreshTokenRaw)) {
            refreshTokenRepository.findByTokenHashAndDeletedAtIsNull(sha256Hex(refreshTokenRaw.trim()))
                    .ifPresent(rt -> revokeFamily(rt.getFamilyId(), "LOGOUT", now));
        }
        // 2) Đưa jti của access token vào blacklist với TTL = hạn còn lại.
        if (!isBlank(accessTokenRaw)) {
            Claims claims = jwtService.parseQuietly(accessTokenRaw.trim());
            if (claims != null) {
                tokenBlacklistService.blacklist(jwtService.extractJti(claims), jwtService.secondsUntilExpiry(claims));
            }
        }
    }

    // ==================================================================
    //  AUTH-04 — Quên mật khẩu / Đặt lại mật khẩu
    // ==================================================================

    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request, String ipAddress) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        rateLimitService.checkLimit("forgot-password", email, FORGOT_PASSWORD_MAX, FORGOT_PASSWORD_WINDOW);

        // Luôn "thành công" về phía client — không tiết lộ email có tồn tại hay không.
        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (userOpt.isEmpty() || userOpt.get().getStatus() == UserStatus.DELETED) {
            return;
        }
        User user = userOpt.get();
        Instant now = Instant.now();

        // Vô hiệu hóa mọi token đặt lại chưa dùng của user.
        passwordResetTokenRepository.findByUser_IdAndUsedAtIsNullAndDeletedAtIsNull(user.getId())
                .forEach(t -> t.setUsedAt(now));

        String rawToken = randomToken();
        PasswordResetToken prt = PasswordResetToken.builder()
                .user(user)
                .tokenHash(sha256Hex(rawToken))
                .expiresAt(now.plus(RESET_TOKEN_TTL))
                .ipAddress(ipAddress)
                .build();
        passwordResetTokenRepository.save(prt);

        String resetUrl = frontendUrl(appProperties.getFrontend().getResetPasswordPath(), rawToken);
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", user.getFullName());
        vars.put("resetUrl", resetUrl);
        vars.put("expiryMinutes", RESET_TOKEN_TTL.toMinutes());
        safeSendMail(user.getEmail(), "Đặt lại mật khẩu Webtro", "reset-password", vars);
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request, String ipAddress) {
        rateLimitService.checkLimit("reset-password", safeId(ipAddress), RESET_PASSWORD_MAX, RESET_PASSWORD_WINDOW);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        PasswordResetToken prt = passwordResetTokenRepository
                .findByTokenHashAndDeletedAtIsNull(sha256Hex(request.getToken().trim()))
                .orElseThrow(() -> new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID));

        if (prt.getUsedAt() != null) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_INVALID);
        }
        Instant now = Instant.now();
        if (prt.getExpiresAt().isBefore(now)) {
            throw new BusinessException(ErrorCode.PASSWORD_RESET_TOKEN_EXPIRED);
        }

        User user = prt.getUser();
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        prt.setUsedAt(now);
        // Thu hồi toàn bộ refresh token của user (mọi thiết bị).
        revokeAllUserTokens(user.getId(), "PASSWORD_RESET", now);
    }

    // ==================================================================
    //  AUTH-05 — Đổi mật khẩu
    // ==================================================================

    @Override
    @Transactional
    public void changePassword(Long userId, String currentRefreshTokenRaw, ChangePasswordRequest request) {
        rateLimitService.checkLimit("change-password", String.valueOf(userId), CHANGE_PASSWORD_MAX, CHANGE_PASSWORD_WINDOW);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // Thu hồi refresh token của các thiết bị KHÁC — giữ lại họ token hiện tại.
        Instant now = Instant.now();
        String keepFamilyId = null;
        if (!isBlank(currentRefreshTokenRaw)) {
            keepFamilyId = refreshTokenRepository
                    .findByTokenHashAndDeletedAtIsNull(sha256Hex(currentRefreshTokenRaw.trim()))
                    .map(RefreshToken::getFamilyId)
                    .orElse(null);
        }
        final String keep = keepFamilyId;
        for (RefreshToken rt : refreshTokenRepository.findByUser_IdAndDeletedAtIsNull(userId)) {
            if (rt.getRevokedAt() == null && !rt.getFamilyId().equals(keep)) {
                rt.setRevokedAt(now);
                rt.setRevokedReason("PASSWORD_CHANGE");
            }
        }
    }

    // ==================================================================
    //  AUTH-06 — Xác thực email / gửi lại
    // ==================================================================

    @Override
    @Transactional
    public VerifyEmailResponse verifyEmail(VerifyEmailRequest request, String ipAddress) {
        rateLimitService.checkLimit("verify-email", safeId(ipAddress), VERIFY_EMAIL_MAX, VERIFY_EMAIL_WINDOW);

        Verification v = verificationRepository.findByTokenHashAndDeletedAtIsNull(sha256Hex(request.getToken().trim()))
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

        if (v.getType() != VerificationType.EMAIL) {
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }
        if (v.getStatus() == VerificationStatus.VERIFIED) {
            throw new ConflictException(ErrorCode.VERIFICATION_ALREADY_DONE);
        }
        if (v.getStatus() != VerificationStatus.PENDING) {
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }
        Instant now = Instant.now();
        if (v.getExpiresAt().isBefore(now)) {
            v.setStatus(VerificationStatus.EXPIRED);
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }

        User user = v.getUser();
        if (user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null) {
            throw new ConflictException(ErrorCode.VERIFICATION_ALREADY_DONE);
        }

        v.setStatus(VerificationStatus.VERIFIED);
        v.setVerifiedAt(now);
        user.setEmailVerifiedAt(now);
        if (user.getStatus() == UserStatus.PENDING_VERIFY) {
            user.setStatus(UserStatus.ACTIVE);
        }

        return authMapper.toVerifyEmailResponse(user, now);
    }

    @Override
    @Transactional
    public ResendVerificationResponse resendVerification(ResendVerificationRequest request, String ipAddress) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        // Cooldown 1/60s + tối đa 5/ngày theo email.
        rateLimitService.checkLimit("resend-cooldown", email, 1, RESEND_COOLDOWN);
        rateLimitService.checkLimit("resend-daily", email, RESEND_DAILY_MAX, RESEND_DAILY_WINDOW);

        Optional<User> userOpt = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getStatus() == UserStatus.ACTIVE && user.getEmailVerifiedAt() != null) {
                throw new ConflictException(ErrorCode.VERIFICATION_ALREADY_DONE);
            }
            if (user.getStatus() == UserStatus.PENDING_VERIFY) {
                // Vô hiệu hóa token EMAIL cũ đang chờ, tạo token mới TTL 24 giờ.
                verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(
                                user.getId(), VerificationType.EMAIL, VerificationStatus.PENDING)
                        .forEach(old -> old.setStatus(VerificationStatus.EXPIRED));
                String rawToken = createEmailVerification(user, email);
                sendVerificationEmail(user, rawToken);
            }
        }
        // Không tiết lộ email có tồn tại hay không — luôn trả cooldown.
        return ResendVerificationResponse.builder().cooldownSeconds(RESEND_COOLDOWN.getSeconds()).build();
    }

    // ==================================================================
    //  AUTH-06 — OTP số điện thoại
    // ==================================================================

    @Override
    @Transactional
    public PhoneOtpResponse sendPhoneOtp(Long userId, SendPhoneOtpRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));

        String phone = PhoneUtil.normalize(request.getPhone());
        if (!PhoneUtil.isValid(phone)) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT);
        }
        // Phải trùng số điện thoại của chính tài khoản.
        if (!phone.equals(user.getPhone())) {
            throw new BusinessException(ErrorCode.INVALID_PHONE_FORMAT, "Số điện thoại không khớp với tài khoản");
        }
        if (user.getPhoneVerifiedAt() != null) {
            throw new ConflictException(ErrorCode.VERIFICATION_ALREADY_DONE);
        }
        // Không cho trùng số của tài khoản khác đang hoạt động.
        userRepository.findByPhoneAndDeletedAtIsNull(phone).ifPresent(other -> {
            if (!other.getId().equals(userId)) {
                throw new ConflictException(ErrorCode.PHONE_ALREADY_EXISTS);
            }
        });

        rateLimitService.checkLimit("phone-otp-cooldown", String.valueOf(userId), 1, PHONE_OTP_COOLDOWN);
        rateLimitService.checkLimit("phone-otp-daily", String.valueOf(userId), PHONE_OTP_DAILY_MAX, PHONE_OTP_DAILY_WINDOW);

        Instant now = Instant.now();
        // Vô hiệu hóa OTP cũ đang chờ.
        verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(
                        userId, VerificationType.PHONE, VerificationStatus.PENDING)
                .forEach(old -> old.setStatus(VerificationStatus.EXPIRED));

        String otp = randomOtp();
        Verification v = Verification.builder()
                .user(user)
                .type(VerificationType.PHONE)
                .status(VerificationStatus.PENDING)
                .targetValue(phone)
                .otpHash(sha256Hex(otp))
                .attemptCount(0)
                .expiresAt(now.plus(PHONE_OTP_TTL))
                .build();
        verificationRepository.save(v);

        // Dev: gửi OTP qua email (MailHog thay SMS) — canonical §1.1.
        Map<String, Object> vars = new HashMap<>();
        vars.put("title", "Mã OTP xác thực số điện thoại");
        vars.put("content", "Mã OTP của bạn là: " + otp + " (hiệu lực " + PHONE_OTP_TTL.toMinutes() + " phút).");
        safeSendMail(user.getEmail(), "Mã OTP xác thực số điện thoại", "notification", vars);

        return PhoneOtpResponse.builder()
                .maskedPhone(MaskUtil.maskPhone(phone))
                .expiresInSeconds(PHONE_OTP_TTL.getSeconds())
                .cooldownSeconds(PHONE_OTP_COOLDOWN.getSeconds())
                .build();
    }

    @Override
    @Transactional
    public VerifyPhoneResponse verifyPhone(Long userId, VerifyPhoneRequest request) {
        rateLimitService.checkLimit("verify-phone", String.valueOf(userId), VERIFY_PHONE_MAX, VERIFY_PHONE_WINDOW);

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (user.getPhoneVerifiedAt() != null) {
            throw new ConflictException(ErrorCode.VERIFICATION_ALREADY_DONE);
        }

        Verification v = verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(
                        userId, VerificationType.PHONE, VerificationStatus.PENDING).stream()
                .max(Comparator.comparing(Verification::getCreatedAt))
                .orElseThrow(() -> new BusinessException(ErrorCode.OTP_INVALID));

        Instant now = Instant.now();
        if (v.getExpiresAt().isBefore(now)) {
            v.setStatus(VerificationStatus.EXPIRED);
            throw new BusinessException(ErrorCode.OTP_EXPIRED);
        }
        if (v.getAttemptCount() >= OTP_MAX_ATTEMPTS) {
            throw new BusinessRuleException(ErrorCode.OTP_ATTEMPT_EXCEEDED);
        }
        if (!sha256Hex(request.getOtp().trim()).equals(v.getOtpHash())) {
            v.setAttemptCount(v.getAttemptCount() + 1);
            throw new BusinessException(ErrorCode.OTP_INVALID);
        }

        v.setStatus(VerificationStatus.VERIFIED);
        v.setVerifiedAt(now);
        user.setPhoneVerifiedAt(now);

        return VerifyPhoneResponse.builder()
                .phone(user.getPhone())
                .phoneVerified(true)
                .verifiedAt(now)
                .build();
    }

    // ==================================================================
    //  Helper riêng
    // ==================================================================

    private void assignRole(User user, String roleCode) {
        Role role = roleRepository.findByCodeAndDeletedAtIsNull(roleCode)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND,
                        "Chưa seed vai trò " + roleCode));
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .assignedAt(Instant.now())
                .build();
        userRoleRepository.save(userRole);
    }

    private void createLandlordProfile(User user, String fullName, String contactName, String contactPhone, String email) {
        LandlordProfile profile = LandlordProfile.builder()
                .user(user)
                .displayName(fullName)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .contactEmail(email)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        landlordProfileRepository.save(profile);
    }

    /** Tạo Verification EMAIL, trả về token THÔ (chỉ token thô mới gửi qua mail; DB lưu hash). */
    private String createEmailVerification(User user, String email) {
        String rawToken = randomToken();
        Verification v = Verification.builder()
                .user(user)
                .type(VerificationType.EMAIL)
                .status(VerificationStatus.PENDING)
                .targetValue(email)
                .tokenHash(sha256Hex(rawToken))
                .attemptCount(0)
                .expiresAt(Instant.now().plus(EMAIL_TOKEN_TTL))
                .build();
        verificationRepository.save(v);
        return rawToken;
    }

    private boolean sendVerificationEmail(User user, String rawToken) {
        String verifyUrl = frontendUrl(appProperties.getFrontend().getVerifyEmailPath(), rawToken);
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", user.getFullName());
        vars.put("verifyUrl", verifyUrl);
        vars.put("otp", ""); // Xác thực email dùng link, không dùng OTP số.
        return safeSendMail(user.getEmail(), "Xác thực email tài khoản Webtro", "verify-email", vars);
    }

    private Optional<User> resolveByEmailOrPhone(String identifier) {
        if (identifier.contains("@")) {
            return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identifier);
        }
        String phone = PhoneUtil.normalize(identifier);
        if (!phone.isBlank()) {
            Optional<User> byPhone = userRepository.findByPhoneAndDeletedAtIsNull(phone);
            if (byPhone.isPresent()) {
                return byPhone;
            }
        }
        // Dự phòng: định danh không có '@' nhưng vẫn có thể là email lỗi — thử tra email.
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identifier);
    }

    private void recordLoginFailure(String counterId, int maxAttempts, Duration window, User user) {
        // Tăng bộ đếm sai. Nếu lần này chạm trần, checkLimit sẽ ném 429 (đúng chế tài [§3.2]).
        rateLimitService.checkLimit("login", counterId, maxAttempts, window);
        if (user != null) {
            user.setFailedLoginCount(user.getFailedLoginCount() == null ? 1 : user.getFailedLoginCount() + 1);
        }
    }

    /** Tạo refresh token, lưu hash SHA-256, trả token THÔ để đặt cookie / trả body. */
    private String createRefreshToken(User user, String familyId, RefreshToken parent, Instant issuedAt,
                                      Instant expiresAt, String ipAddress, String userAgent) {
        String raw = java.util.UUID.randomUUID().toString();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(sha256Hex(raw))
                .familyId(familyId)
                .parent(parent)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .userAgent(truncate(userAgent, 255))
                .ipAddress(truncate(ipAddress, 45))
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    private void revokeFamily(String familyId, String reason, Instant now) {
        for (RefreshToken rt : refreshTokenRepository.findByFamilyIdAndDeletedAtIsNull(familyId)) {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(now);
                rt.setRevokedReason(reason);
            }
        }
    }

    private void revokeAllUserTokens(Long userId, String reason, Instant now) {
        for (RefreshToken rt : refreshTokenRepository.findByUser_IdAndDeletedAtIsNull(userId)) {
            if (rt.getRevokedAt() == null) {
                rt.setRevokedAt(now);
                rt.setRevokedReason(reason);
            }
        }
    }

    private Boolean resolveLandlordVerified(Long userId) {
        return landlordProfileRepository.findByUser_IdAndDeletedAtIsNull(userId)
                .map(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElse(null);
    }

    private String frontendUrl(String path, String rawToken) {
        String base = appProperties.getFrontend().getBaseUrl();
        String sep = path.contains("?") ? "&" : "?";
        return base + path + sep + "token=" + rawToken;
    }

    private boolean safeSendMail(String to, String subject, String template, Map<String, Object> vars) {
        try {
            mailService.sendHtml(to, subject, template, vars);
            return true;
        } catch (RuntimeException e) {
            // Mail gửi bất đồng bộ; nếu enqueue lỗi vẫn không chặn luồng chính.
            log.warn("Không gửi được email '{}' tới {}: {}", template, to, e.getMessage());
            return false;
        }
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String randomOtp() {
        return String.format(Locale.ROOT, "%06d", RANDOM.nextInt(1_000_000));
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Thiếu thuật toán SHA-256", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String safeId(String ip) {
        return isBlank(ip) ? "unknown" : ip;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
