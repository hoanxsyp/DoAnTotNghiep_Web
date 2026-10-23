package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** IT phân quyền & chống truy cập chéo (IDOR) cho người thuê (IT-SEC-xx). */
class TenantAccessControlIT extends TenantItSupport {

    @ParameterizedTest(name = "IT-SEC-01 Khách chưa đăng nhập: {0} {1} → 401")
    @CsvSource({
            "GET, /api/users/me",
            "GET, /api/favorites",
            "POST, /api/favorites",
            "POST, /api/rental-requests",
            "GET, /api/rental-requests/my",
            "GET, /api/conversations",
            "POST, /api/conversations",
            "GET, /api/notifications",
            "GET, /api/reviews/my",
            "GET, /api/history/views",
            "POST, /api/listings",
    })
    void anonymousIsRejected(String method, String url) throws Exception {
        JsonNode res = call(HttpMethod.valueOf(method), url, null, method.equals("POST") ? Map.of() : null, 401);
        assertEquals("UNAUTHORIZED", errorCode(res));
    }

    @ParameterizedTest(name = "IT-SEC-02 Tenant gọi API chủ trọ/thanh toán/quản trị: {0} {1} → 403")
    @CsvSource({
            "GET, /api/landlord/rental-requests",
            "PUT, /api/landlord/rental-requests/1/approve",
            "GET, /api/landlord/dashboard",
            "GET, /api/landlord/rental-units",
            "GET, /api/users/me/landlord-profile",
            "GET, /api/payments/my",
            "GET, /api/promotion-subscriptions/my",
            "GET, /api/admin/users",
            "GET, /api/admin/listings",
            "PUT, /api/admin/listings/1/approve",
            "GET, /api/admin/reports",
            "GET, /api/admin/dashboard",
            "GET, /api/admin/moderation-queue",
    })
    void tenantCannotUseOtherRoles(String method, String url) throws Exception {
        Actor tenant = newTenant();
        JsonNode res = call(HttpMethod.valueOf(method), url, tenant, method.equals("GET") ? null : Map.of(), 403);
        assertEquals("FORBIDDEN", errorCode(res));
    }

    @Test
    @DisplayName("IT-SEC-02b Tenant gọi API thanh toán/mã giảm giá/đẩy tin với body HỢP LỆ → 403 (không mua được gói)")
    void tenantCannotPay() throws Exception {
        Actor tenant = newTenant();
        Map<String, Object> pay = Map.of("listingId", 1, "packageId", 1, "returnUrl", "http://localhost/kq",
                "paymentMethod", "SANDBOX");
        assertEquals("FORBIDDEN", errorCode(call(HttpMethod.POST, "/api/payments", tenant, pay, 403)));
        assertEquals("FORBIDDEN", errorCode(call(HttpMethod.POST, "/api/listings/1/promote", tenant, pay, 403)));
        assertEquals("FORBIDDEN", errorCode(call(HttpMethod.POST, "/api/coupons/validate", tenant,
                Map.of("code", "GIAM10", "packageId", 1), 403)));
    }

    @Test
    @DisplayName("IT-SEC-03 Chủ trọ không dùng được API chỉ dành cho người thuê (gửi yêu cầu thuê) → 403")
    void landlordCannotUseTenantOnlyApi() throws Exception {
        Actor landlord = newLandlord();
        call(HttpMethod.POST, "/api/rental-requests", landlord, Map.of("listingId", 1, "desiredViewingDate", today(1)), 403);
        call(HttpMethod.GET, "/api/rental-requests/my", landlord, null, 403);
    }

    @Test
    @DisplayName("IT-SEC-04 Đổi ID để truy cập dữ liệu người khác: tin nháp (404), sửa/xóa/thống kê tin (403), "
            + "yêu cầu thuê của người khác không lộ trong /my")
    void idorOnListingsAndRequests() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor tenant2 = newTenant();
        long draft = call(HttpMethod.POST, "/api/listings", landlord, listingBody("BOARDING_HOUSE"), 201).at("/data/id").asLong();
        long active = createActiveListing(landlord, "BOARDING_HOUSE");

        call(HttpMethod.GET, "/api/listings/" + draft, tenant, null, 404);
        call(HttpMethod.PUT, "/api/listings/" + active, tenant, listingBody("ROOMMATE"), 403);
        call(HttpMethod.DELETE, "/api/listings/" + active, tenant, null, 403);
        call(HttpMethod.POST, "/api/listings/" + active + "/hide", tenant, null, 403);
        call(HttpMethod.GET, "/api/listings/" + active + "/stats", tenant, null, 403);
        uploadImage(tenant, active, 403);

        call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", active, "desiredViewingDate", today(2)), 201);
        assertEquals(0, call(HttpMethod.GET, "/api/rental-requests/my", tenant2, null, 200).at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-SEC-05 Dữ liệu cá nhân: /users/me luôn là của chính mình; hồ sơ công khai không lộ email")
    void ownDataOnly() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        assertEquals(tenant.email(), call(HttpMethod.GET, "/api/users/me", tenant, null, 200).at("/data/email").asText());

        JsonNode pub = call(HttpMethod.GET, "/api/users/" + landlord.id(), tenant, null, 200);
        assertFalse(pub.toString().contains(landlord.email()), () -> "Hồ sơ công khai lộ email: " + pub);
    }

    @Test
    @DisplayName("IT-SEC-06 [BUG-11] Admin khóa tài khoản tenant → access token đang dùng phải bị chặn ngay")
    @Disabled("BUG-11: khóa tài khoản chỉ thu hồi refresh token; JwtAuthenticationFilter không kiểm trạng thái user nên "
            + "access token cũ vẫn thao tác được tới khi hết hạn (mặc định 15 phút). Bỏ @Disabled sau khi fix.")
    void lockedTenantTokenStopsWorking() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        call(HttpMethod.PUT, "/api/admin/users/" + tenant.id() + "/lock", admin(),
                Map.of("reason", "Spam liên tục nhiều tin nhắn quảng cáo"), 200);

        JsonNode res = assertStatusIn(raw(HttpMethod.POST, "/api/favorites", tenant, Map.of("listingId", listingId)));
        assertEquals(true, res != null);
    }

    @Test
    @DisplayName("IT-SEC-07 Admin khóa tenant → refresh token bị thu hồi, đăng nhập lại bị 403 ACCOUNT_LOCKED")
    void lockedTenantCannotRefreshOrLogin() throws Exception {
        Actor tenant = newTenant();
        call(HttpMethod.PUT, "/api/admin/users/" + tenant.id() + "/lock", admin(),
                Map.of("reason", "Spam liên tục nhiều tin nhắn quảng cáo"), 200);

        call(HttpMethod.POST, "/api/auth/refresh", null, Map.of("refreshToken", tenant.refreshToken()), 401);
        JsonNode login = call(HttpMethod.POST, "/api/auth/login", null, Map.of("emailOrPhone", tenant.email(), "password", PASSWORD), 403);
        assertEquals("ACCOUNT_LOCKED", errorCode(login));
    }

    @Test
    @DisplayName("IT-SEC-08 [BUG-12] Khách chưa đăng nhập xem hồ sơ công khai chủ trọ (GET /api/users/{id}) → phải 200")
    @Disabled("BUG-12 (luồng khách): SecurityConfig chỉ mở '/api/users/*/public' nhưng frontend gọi '/api/users/{id}' → "
            + "khách nhận 401. Bỏ @Disabled sau khi fix.")
    void guestCanViewLandlordProfile() throws Exception {
        Actor landlord = newLandlord();
        call(HttpMethod.GET, "/api/users/" + landlord.id(), null, null, 200);
    }

    @Test
    @DisplayName("IT-SEC-09 Khách xem được tin công khai, danh sách tin + đánh giá của chủ trọ")
    void guestPublicEndpoints() throws Exception {
        Actor landlord = newLandlord();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        call(HttpMethod.GET, "/api/listings/" + listingId, null, null, 200);
        call(HttpMethod.GET, "/api/search/listings", null, null, 200);
        call(HttpMethod.GET, "/api/users/" + landlord.id() + "/listings", null, null, 200);
        call(HttpMethod.GET, "/api/users/" + landlord.id() + "/reviews", null, null, 200);
        call(HttpMethod.GET, "/api/listings/" + listingId + "/comments", null, null, 200);
    }

    /** Khóa xong thì mọi thao tác ghi phải bị từ chối (401 hoặc 403). */
    private JsonNode assertStatusIn(org.springframework.test.web.servlet.MvcResult r) throws Exception {
        int s = r.getResponse().getStatus();
        assertEquals(true, s == 401 || s == 403,
                () -> "Tài khoản đã khóa vẫn thao tác được, HTTP " + s);
        return om.readTree(r.getResponse().getContentAsString());
    }
}
