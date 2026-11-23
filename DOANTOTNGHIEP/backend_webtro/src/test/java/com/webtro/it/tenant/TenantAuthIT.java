package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** IT tài khoản người thuê: đăng ký → xác thực → đăng nhập → phiên → đăng xuất (IT-AUTH-xx). */
class TenantAuthIT extends TenantItSupport {

    @Test
    @DisplayName("IT-AUTH-01 Đăng ký TENANT → OTP email → xác thực → đăng nhập → /users/me đúng vai trò")
    void registerVerifyLoginFlow() throws Exception {
        String email = "flow." + uniq() + "@it.local";
        String phone = randomPhone();

        MvcResult reg = register("TENANT", email, phone, randomIp());
        JsonNode regBody = assertStatus(reg, 201);
        assertEquals("ROLE_TENANT", regBody.at("/data/role").asText());
        assertEquals("PENDING_VERIFY", regBody.at("/data/status").asText());

        // Chưa xác thực thì chưa đăng nhập được.
        JsonNode notVerified = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("emailOrPhone", email, "password", PASSWORD), 403);
        assertEquals("ACCOUNT_NOT_VERIFIED", errorCode(notVerified));

        call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("email", email, "otp", lastOtpFor(email)), 200);
        // Đăng nhập được bằng cả email lẫn số điện thoại.
        Actor byEmail = login(email, PASSWORD);
        login(phone, PASSWORD);

        JsonNode me = call(HttpMethod.GET, "/api/users/me", byEmail, null, 200);
        assertEquals(email, me.at("/data/email").asText());
        assertEquals("ROLE_TENANT", me.at("/data/role").asText());
        assertEquals(true, me.at("/data/landlordProfile").isMissingNode() || me.at("/data/landlordProfile").isNull());

        // Thông báo chào mừng được tạo.
        JsonNode noti = call(HttpMethod.GET, "/api/notifications", byEmail, null, 200);
        assertEquals(true, noti.toString().contains("ACCOUNT_REGISTERED"));
    }

    @Test
    @DisplayName("IT-AUTH-02 Đăng ký trùng email → 409 EMAIL_ALREADY_EXISTS; dữ liệu sai → 400 VALIDATION_FAILED")
    void registerValidation() throws Exception {
        Actor t = newTenant();
        JsonNode dup = assertStatus(register("TENANT", t.email().toUpperCase(), randomPhone(), randomIp()), 409);
        assertEquals("EMAIL_ALREADY_EXISTS", errorCode(dup));

        JsonNode badPhone = assertStatus(register("TENANT", "x" + uniq() + "@it.local", "12345", randomIp()), 400);
        assertEquals("VALIDATION_FAILED", errorCode(badPhone));
    }

    @Test
    @DisplayName("IT-AUTH-03 Gửi lại mã xác thực → OTP cũ vô hiệu, OTP mới dùng được")
    void resendVerification() throws Exception {
        String email = "resend." + uniq() + "@it.local";
        assertStatus(register("TENANT", email, randomPhone(), randomIp()), 201);
        String oldOtp = lastOtpFor(email);

        call(HttpMethod.POST, "/api/auth/resend-verification", null, Map.of("email", email), 200);
        String newOtp = lastOtpFor(email);
        assertNotEquals(oldOtp, newOtp);

        JsonNode old = call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("email", email, "otp", oldOtp), 400);
        assertEquals("OTP_INVALID", errorCode(old));
        call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("email", email, "otp", newOtp), 200);
    }

    @Test
    @DisplayName("IT-AUTH-04 Số lần nhập sai OTP phải được LƯU vào DB (verifications.attempt_count)")
    void wrongOtpAttemptsArePersisted() throws Exception {
        String email = "otp." + uniq() + "@it.local";
        assertStatus(register("TENANT", email, randomPhone(), randomIp()), 201);
        String otp = lastOtpFor(email);
        String wrong = otp.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("email", email, "otp", wrong), 400);
        }
        Integer attempts = jdbc.queryForObject("""
                select v.attempt_count from verifications v join users u on u.id = v.user_id
                where u.email = ? and v.type = 'EMAIL' and v.status = 'PENDING'""", Integer.class, email);
        assertEquals(5, attempts);

        JsonNode locked = call(HttpMethod.POST, "/api/auth/verify-email", null, Map.of("email", email, "otp", otp), 429);
        assertEquals("OTP_ATTEMPT_EXCEEDED", errorCode(locked));
    }

    @Test
    @DisplayName("IT-AUTH-05 Làm mới token: xoay vòng refresh token; dùng lại refresh cũ bị từ chối")
    void refreshRotation() throws Exception {
        Actor t = newTenant();
        JsonNode refreshed = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 200);
        String newRefresh = refreshed.at("/data/refreshToken").asText();
        assertNotEquals(t.refreshToken(), newRefresh);

        JsonNode reuse = call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 401);
        assertEquals(true, errorCode(reuse).startsWith("REFRESH_TOKEN_"));

        String newAccess = refreshed.at("/data/accessToken").asText();
        call(HttpMethod.GET, "/api/users/me", new Actor(t.id(), t.email(), null, newAccess, newRefresh), null, 200);
    }

    @Test
    @DisplayName("IT-AUTH-06 Đăng xuất → access token cũ bị thu hồi (401), refresh token cũ không dùng được")
    void logoutRevokesSession() throws Exception {
        Actor t = newTenant();
        call(HttpMethod.GET, "/api/users/me", t, null, 200);

        call(HttpMethod.POST, "/api/auth/logout", t, Map.of("refreshToken", t.refreshToken()), 204);

        call(HttpMethod.GET, "/api/users/me", t, null, 401);
        call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", t.refreshToken()), 401);
    }

    @Test
    @DisplayName("IT-AUTH-07 Đổi mật khẩu: sai mật khẩu cũ → 400; đúng → mật khẩu cũ hết dùng, mật khẩu mới đăng nhập được")
    void changePassword() throws Exception {
        Actor t = newTenant();
        JsonNode wrong = call(HttpMethod.POST, "/api/auth/change-password", t, Map.of(
                "oldPassword", "SaiMatKhau9", "newPassword", "MatKhauMoi9", "confirmPassword", "MatKhauMoi9"), 400);
        assertEquals("OLD_PASSWORD_INCORRECT", errorCode(wrong));

        call(HttpMethod.POST, "/api/auth/change-password", t, Map.of(
                "oldPassword", PASSWORD, "newPassword", "MatKhauMoi9", "confirmPassword", "MatKhauMoi9",
                "refreshToken", t.refreshToken()), 200);

        JsonNode old = call(HttpMethod.POST, "/api/auth/login", null, Map.of("emailOrPhone", t.email(), "password", PASSWORD), 401);
        assertEquals("INVALID_CREDENTIALS", errorCode(old));
        login(t.email(), "MatKhauMoi9");
    }

    @Test
    @DisplayName("IT-AUTH-08 Token giả/hỏng → 401 UNAUTHORIZED, không lộ stacktrace")
    void forgedToken() throws Exception {
        JsonNode res = assertStatus(rawWithAuth(HttpMethod.GET, "/api/users/me", "Bearer abc.def.ghi", null), 401);
        assertEquals(false, res.toString().contains("Exception"));
    }
}
