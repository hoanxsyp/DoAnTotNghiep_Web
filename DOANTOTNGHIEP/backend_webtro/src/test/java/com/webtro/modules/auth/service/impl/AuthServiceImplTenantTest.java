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
import com.webtro.exception.ConflictException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.RateLimitException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.auth.dto.request.LoginRequest;
import com.webtro.modules.auth.dto.request.RegisterRequest;
import com.webtro.modules.auth.dto.request.VerifyEmailRequest;
import com.webtro.modules.auth.mapper.AuthMapper;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.Verification;
import com.webtro.modules.user.repository.LandlordProfileRepository;
import com.webtro.modules.user.repository.PasswordResetTokenRepository;
import com.webtro.modules.user.repository.RefreshTokenRepository;
import com.webtro.modules.user.repository.RoleRepository;
import com.webtro.modules.user.repository.UserRepository;
import com.webtro.modules.user.repository.VerificationRepository;
import com.webtro.security.JwtService;
import com.webtro.security.RateLimitService;
import com.webtro.security.TokenBlacklistService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT đăng ký/đăng nhập/xác thực email của người thuê (UT-AUTH-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTenantTest {

    private static final String EMAIL = "tenant@test.local";
    private static final String PHONE = "0912345678";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private LandlordProfileRepository landlordProfileRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock
    private VerificationRepository verificationRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;
    @Spy
    private JwtProperties jwtProperties = new JwtProperties();
    @Mock
    private TokenBlacklistService tokenBlacklistService;
    @Mock
    private RateLimitService rateLimitService;
    @Mock
    private SystemConfigService systemConfigService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private MailService mailService;
    @Spy
    private AppProperties appProperties = new AppProperties();
    @Spy
    private AuthMapper authMapper = new AuthMapper();

    @InjectMocks
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        when(systemConfigService.getInt(ConfigKey.SECURITY_REGISTER_RATE)).thenReturn(3);
        when(systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_MAX_ATTEMPTS)).thenReturn(5);
        when(systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_CAPTCHA_AFTER_ATTEMPTS)).thenReturn(3);
        when(systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_WINDOW_MINUTES)).thenReturn(15);
        when(systemConfigService.getInt(ConfigKey.SECURITY_LOGIN_LOCK_MINUTES)).thenReturn(15);
        when(roleRepository.findByCodeAndDeletedAtIsNull(RoleCode.TENANT))
                .thenReturn(Optional.of(Role.builder().code(RoleCode.TENANT).build()));
        when(passwordEncoder.encode(anyString())).thenReturn("$hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(30L);
            return u;
        });
    }

    @Test
    @DisplayName("UT-AUTH-01 Đăng ký TENANT → PENDING_VERIFY, chuẩn hóa email, KHÔNG tạo hồ sơ chủ trọ, gửi mail OTP, thông báo chào mừng")
    void registerTenant() {
        var response = service.register(register("Tenant@Test.Local"), "1.2.3.4");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals(EMAIL, saved.getValue().getEmail());
        assertEquals(UserStatus.PENDING_VERIFY, saved.getValue().getStatus());
        assertEquals(RoleCode.TENANT, saved.getValue().getRole().getCode());
        verify(landlordProfileRepository, never()).save(any());
        ArgumentCaptor<Verification> v = ArgumentCaptor.forClass(Verification.class);
        verify(verificationRepository).save(v.capture());
        assertEquals(VerificationType.EMAIL, v.getValue().getType());
        assertNotNull(v.getValue().getOtpHash());
        verify(mailService).sendHtml(eq(EMAIL), anyString(), eq("verify-email"), any());
        verify(notificationService).notifyUser(eq(30L), eq(NotificationType.ACCOUNT_REGISTERED), anyString(), anyString());
        assertEquals(RoleCode.TENANT, response.getRole());
    }

    @Test
    @DisplayName("UT-AUTH-02 Mật khẩu xác nhận không khớp → PASSWORD_CONFIRM_MISMATCH")
    void registerPasswordMismatch() {
        RegisterRequest req = register(EMAIL);
        req.setConfirmPassword("Khac12345");

        BusinessException ex = assertThrows(BusinessException.class, () -> service.register(req, "1.2.3.4"));

        assertEquals(ErrorCode.PASSWORD_CONFIRM_MISMATCH, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-03 Email đã tồn tại (không phân biệt hoa thường) → EMAIL_ALREADY_EXISTS")
    void registerDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(true);

        BusinessException ex = assertThrows(ConflictException.class, () -> service.register(register("TENANT@test.local"), "ip"));

        assertEquals(ErrorCode.EMAIL_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-04 SĐT đã tồn tại (+84 được chuẩn hóa về 0) → PHONE_ALREADY_EXISTS")
    void registerDuplicatePhone() {
        when(userRepository.existsByPhoneAndDeletedAtIsNull(PHONE)).thenReturn(true);
        RegisterRequest req = register(EMAIL);
        req.setPhone("+84912345678");

        BusinessException ex = assertThrows(ConflictException.class, () -> service.register(req, "ip"));

        assertEquals(ErrorCode.PHONE_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-05 Quá 3 lần đăng ký/giờ/IP → REGISTER_RATE_LIMIT")
    void registerRateLimit() {
        when(rateLimitService.currentCount("register", "1.2.3.4")).thenReturn(3L);

        BusinessException ex = assertThrows(RateLimitException.class, () -> service.register(register(EMAIL), "1.2.3.4"));

        assertEquals(ErrorCode.REGISTER_RATE_LIMIT, ex.getErrorCode());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-AUTH-06 Đăng nhập khi chưa xác thực email → ACCOUNT_NOT_VERIFIED")
    void loginBeforeVerify() {
        User u = user(UserStatus.PENDING_VERIFY);
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("MatKhau123", "$hash")).thenReturn(true);

        BusinessException ex = assertThrows(ForbiddenException.class,
                () -> service.login(login("MatKhau123"), "ip", "UA"));

        assertEquals(ErrorCode.ACCOUNT_NOT_VERIFIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-07 Sai mật khẩu → INVALID_CREDENTIALS (không lộ email có tồn tại hay không)")
    void loginWrongPassword() {
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        BusinessException wrongPass = assertThrows(BusinessException.class,
                () -> service.login(login("SaiMatKhau1"), "ip", "UA"));
        BusinessException noUser = assertThrows(BusinessException.class,
                () -> service.login(LoginRequest.builder().emailOrPhone("khongco@test.local").password("x").build(), "ip", "UA"));

        assertEquals(ErrorCode.INVALID_CREDENTIALS, wrongPass.getErrorCode());
        assertEquals(ErrorCode.INVALID_CREDENTIALS, noUser.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-08 Tài khoản bị khóa nhập đúng mật khẩu → ACCOUNT_LOCKED kèm lý do")
    void loginLockedAccount() {
        User u = user(UserStatus.LOCKED);
        u.setLockReason("Spam");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        BusinessException ex = assertThrows(ForbiddenException.class, () -> service.login(login("MatKhau123"), "ip", "UA"));

        assertEquals(ErrorCode.ACCOUNT_LOCKED, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-AUTH-09 Xác thực email bằng OTP đúng → user ACTIVE, verification VERIFIED")
    void verifyEmailWithOtp() {
        User u = user(UserStatus.PENDING_VERIFY);
        Verification v = pendingEmailVerification(u, "123456");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(u));
        when(verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(30L, VerificationType.EMAIL,
                VerificationStatus.PENDING)).thenReturn(List.of(v));

        service.verifyEmail(VerifyEmailRequest.builder().email(EMAIL).otp("123456").build(), "ip");

        assertEquals(UserStatus.ACTIVE, u.getStatus());
        assertNotNull(u.getEmailVerifiedAt());
        assertEquals(VerificationStatus.VERIFIED, v.getStatus());
    }

    @Test
    @DisplayName("UT-AUTH-10 OTP sai → OTP_INVALID; sau 5 lần sai → OTP_ATTEMPT_EXCEEDED kể cả nhập đúng "
            + "(UT chỉ kiểm logic trong bộ nhớ — việc bộ đếm có được LƯU hay không kiểm ở IT-AUTH-05)")
    void verifyEmailOtpAttempts() {
        User u = user(UserStatus.PENDING_VERIFY);
        Verification v = pendingEmailVerification(u, "123456");
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(u));
        when(verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(30L, VerificationType.EMAIL,
                VerificationStatus.PENDING)).thenReturn(List.of(v));

        for (int i = 0; i < 5; i++) {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> service.verifyEmail(VerifyEmailRequest.builder().email(EMAIL).otp("000000").build(), "ip"));
            assertEquals(ErrorCode.OTP_INVALID, ex.getErrorCode());
        }
        BusinessException locked = assertThrows(BusinessException.class,
                () -> service.verifyEmail(VerifyEmailRequest.builder().email(EMAIL).otp("123456").build(), "ip"));

        assertEquals(ErrorCode.OTP_ATTEMPT_EXCEEDED, locked.getErrorCode());
        assertEquals(UserStatus.PENDING_VERIFY, u.getStatus());
    }

    @Test
    @DisplayName("UT-AUTH-11 OTP hết hạn → OTP_EXPIRED")
    void verifyEmailExpired() {
        User u = user(UserStatus.PENDING_VERIFY);
        Verification v = pendingEmailVerification(u, "123456");
        v.setExpiresAt(Instant.now().minus(Duration.ofMinutes(1)));
        when(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(EMAIL)).thenReturn(Optional.of(u));
        when(verificationRepository.findByUser_IdAndTypeAndStatusAndDeletedAtIsNull(30L, VerificationType.EMAIL,
                VerificationStatus.PENDING)).thenReturn(List.of(v));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.verifyEmail(VerifyEmailRequest.builder().email(EMAIL).otp("123456").build(), "ip"));

        assertEquals(ErrorCode.OTP_EXPIRED, ex.getErrorCode());
    }

    // ------------------------------------------------------------------ helper

    private RegisterRequest register(String email) {
        return RegisterRequest.builder()
                .fullName("Nguyễn Văn Thuê")
                .email(email)
                .phone(PHONE)
                .password("MatKhau123")
                .confirmPassword("MatKhau123")
                .requestedRole(RegisterRequest.RequestedRole.TENANT)
                .acceptTerms(true)
                .build();
    }

    private LoginRequest login(String password) {
        return LoginRequest.builder().emailOrPhone(EMAIL).password(password).build();
    }

    private User user(UserStatus status) {
        User u = User.builder().fullName("Thuê").email(EMAIL).phone(PHONE).passwordHash("$hash").status(status)
                .role(Role.builder().code(RoleCode.TENANT).build()).failedLoginCount(0).build();
        u.setId(30L);
        return u;
    }

    private Verification pendingEmailVerification(User u, String otp) {
        Verification v = Verification.builder()
                .user(u)
                .type(VerificationType.EMAIL)
                .status(VerificationStatus.PENDING)
                .targetValue(EMAIL)
                .otpHash(sha256(otp))
                .attemptCount(0)
                .expiresAt(Instant.now().plus(Duration.ofHours(1)))
                .build();
        v.setCreatedAt(Instant.now());
        return v;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unused")
    private static Map<String, Object> none() {
        return Map.of();
    }
}
