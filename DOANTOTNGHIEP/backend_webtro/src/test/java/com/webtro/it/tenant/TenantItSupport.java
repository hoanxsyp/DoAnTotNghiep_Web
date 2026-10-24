package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.mail.MailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Nền cho Integration Test luồng người thuê (IT-xx trong docs/11_KIEM_THU_LUONG_TENANT.md).
 *
 * <p>Khởi động TOÀN BỘ ứng dụng Spring (security, validation, JPA/Flyway, Redis, listener) và gọi
 * API thật qua MockMvc. Hạ tầng thật: MySQL (database riêng {@code webtro_it}) + Redis (DB index
 * riêng, xóa trước mỗi test để reset rate limit). Chỉ {@link MailService} bị mock để đọc OTP thay
 * cho hộp thư.
 *
 * <p>Chỉ chạy khi có biến môi trường {@code WEBTRO_IT=true} (xem run-tenant-it.sh) để
 * {@code mvn test} thường không phụ thuộc DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Tag("integration")
@org.springframework.test.context.TestPropertySource(properties = "app.ai.chatbot-api.enabled=false")
@EnabledIfEnvironmentVariable(named = "WEBTRO_IT", matches = "true")
public abstract class TenantItSupport {

    protected static final String PASSWORD = "MatKhau123";
    private static final String ADMIN_EMAIL = "admin-it@webtro.local";
    private static final String ADMIN_PASSWORD = "AdminIt@12345";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper om;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected JdbcTemplate jdbc;
    @MockBean
    protected MailService mailService;
    @Autowired
    private org.springframework.transaction.PlatformTransactionManager txManager;

    private final Map<String, Long> categoryIds = new HashMap<>();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> env("IT_DB_URL",
                "jdbc:mysql://localhost:3307/webtro_it?useSSL=false&allowPublicKeyRetrieval=true"
                        + "&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true"
                        + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"));
        r.add("spring.datasource.username", () -> env("IT_DB_USERNAME", env("DB_USERNAME", "root")));
        r.add("spring.datasource.password", () -> env("IT_DB_PASSWORD", env("DB_PASSWORD", "root")));
        r.add("spring.datasource.hikari.minimum-idle", () -> "1");
        r.add("spring.datasource.hikari.maximum-pool-size", () -> "8");
        r.add("spring.data.redis.host", () -> env("IT_REDIS_HOST", "localhost"));
        r.add("spring.data.redis.port", () -> env("IT_REDIS_PORT", "6380"));
        r.add("spring.data.redis.password", () -> env("IT_REDIS_PASSWORD", env("REDIS_PASSWORD", "")));
        r.add("spring.data.redis.database", () -> env("IT_REDIS_DB", "9"));
        r.add("app.jwt.secret", () -> "integration-test-secret-key-0123456789-abcdefghijklmnop");
        r.add("app.seed.demo-enabled", () -> "false");
        r.add("app.scheduler.enabled", () -> "false");
        r.add("app.admin.seed-enabled", () -> "true");
        r.add("app.admin.email", () -> ADMIN_EMAIL);
        r.add("app.admin.password", () -> ADMIN_PASSWORD);
        r.add("app.admin.phone", () -> "0899999999");
        r.add("app.storage.root", () -> "target/it-storage");
        r.add("app.rate-limit.enabled", () -> "true");
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }

    @BeforeEach
    protected void resetRedis() {
        // Redis DB riêng của IT: xóa rate limit / blacklist / cache giữa các test cho tất định.
        redis.execute((org.springframework.data.redis.core.RedisCallback<Object>) c -> {
            c.serverCommands().flushDb();
            return null;
        });
    }

    /**
     * Chạy SQL dựng tình huống (có commit). Hikari đặt auto-commit=false nên {@code jdbc.update} trần
     * sẽ không được commit — luôn đi qua hàm này.
     */
    protected int sql(String statement, Object... args) {
        Integer n = new org.springframework.transaction.support.TransactionTemplate(txManager)
                .execute(s -> jdbc.update(statement, args));
        return n == null ? 0 : n;
    }

    // ================================================================== tài khoản

    /** Người dùng đã đăng nhập dùng cho test. */
    protected record Actor(long id, String email, String phone, String token, String refreshToken) {
        String bearer() {
            return "Bearer " + token;
        }
    }

    protected static String uniq() {
        return Long.toString(System.currentTimeMillis(), 36) + SEQ.incrementAndGet();
    }

    protected static String randomPhone() {
        return "09" + String.format("%08d", ThreadLocalRandom.current().nextInt(100_000_000));
    }

    protected static String randomIp() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        return "10." + r.nextInt(256) + "." + r.nextInt(256) + "." + (1 + r.nextInt(250));
    }

    /** Đăng ký qua API (chưa xác thực). Trả email. */
    protected MvcResult register(String role, String email, String phone, String ip) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("fullName", "Người Test " + ("LANDLORD".equals(role) ? "Chủ" : "Thuê"));
        body.put("email", email);
        body.put("phone", phone);
        body.put("password", PASSWORD);
        body.put("confirmPassword", PASSWORD);
        body.put("requestedRole", role);
        body.put("acceptTerms", true);
        if ("LANDLORD".equals(role)) {
            body.put("contactName", "Chủ Trọ Test");
            body.put("contactPhone", phone);
        }
        return mvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .with(req -> {
                            req.setRemoteAddr(ip);
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsBytes(body)))
                .andReturn();
    }

    /** OTP mới nhất đã "gửi" tới email (bắt từ MailService mock). */
    @SuppressWarnings("unchecked")
    protected String lastOtpFor(String email) {
        ArgumentCaptor<Map<String, Object>> vars = ArgumentCaptor.forClass(Map.class);
        verify(mailService, atLeastOnce()).sendHtml(eq(email), anyString(), eq("verify-email"), vars.capture());
        List<Map<String, Object>> all = vars.getAllValues();
        return String.valueOf(all.get(all.size() - 1).get("otp"));
    }

    protected Actor newTenant() throws Exception {
        return newUser("TENANT");
    }

    protected Actor newLandlord() throws Exception {
        return newUser("LANDLORD");
    }

    /** Đăng ký → xác thực OTP → đăng nhập, toàn bộ qua API thật. */
    protected Actor newUser(String role) throws Exception {
        String email = role.toLowerCase() + "." + uniq() + "@it.local";
        String phone = randomPhone();
        MvcResult reg = register(role, email, phone, randomIp());
        assertEquals(201, reg.getResponse().getStatus(), reg.getResponse().getContentAsString(StandardCharsets.UTF_8));
        JsonNode verify = call(HttpMethod.POST, "/api/auth/verify-email", null,
                Map.of("email", email, "otp", lastOtpFor(email)), 200);
        assertEquals("ACTIVE", verify.at("/data/status").asText());
        Actor a = login(email, PASSWORD);
        return new Actor(a.id(), email, phone, a.token(), a.refreshToken());
    }

    protected Actor login(String email, String password) throws Exception {
        JsonNode res = call(HttpMethod.POST, "/api/auth/login", null,
                Map.of("emailOrPhone", email, "password", password), 200);
        JsonNode d = res.get("data");
        return new Actor(d.at("/user/id").asLong(), email, null,
                d.get("accessToken").asText(), d.get("refreshToken").asText());
    }

    protected Actor admin() throws Exception {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    // ================================================================== tin đăng

    protected long categoryId(String code) throws Exception {
        if (categoryIds.isEmpty()) {
            JsonNode res = call(HttpMethod.GET, "/api/categories", null, null, 200);
            for (JsonNode c : res.get("data")) {
                categoryIds.put(c.get("code").asText(), c.get("id").asLong());
            }
        }
        return categoryIds.get(code);
    }

    protected Map<String, Object> listingBody(String categoryCode) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("categoryId", categoryId(categoryCode));
        b.put("title", "Phòng test tích hợp " + uniq());
        b.put("description", "Phòng sạch sẽ, gần trường đại học, an ninh tốt, giờ giấc tự do, có chỗ để xe.");
        b.put("price", 3_000_000);
        b.put("area", 25);
        b.put("provinceId", 1);
        b.put("districtId", 101);
        b.put("wardId", 10101);
        b.put("addressDetail", "Số " + ThreadLocalRandom.current().nextInt(1, 999) + " ngõ IT " + uniq());
        b.put("maxOccupants", 3);
        b.put("currentOccupants", 1);
        b.put("genderRequirement", "ANY");
        b.put("contactName", "Liên hệ Test");
        b.put("contactPhone", "0912345678");
        return b;
    }

    /** Tạo tin nháp + tải 1 ảnh, trả id tin. */
    protected long createDraftWithImage(Actor owner, String categoryCode) throws Exception {
        JsonNode created = call(HttpMethod.POST, "/api/listings", owner, listingBody(categoryCode), 201);
        long id = created.at("/data/id").asLong();
        uploadImage(owner, id, 201);
        return id;
    }

    protected JsonNode uploadImage(Actor owner, long listingId, int expectedStatus) throws Exception {
        MockMultipartFile file = new MockMultipartFile("files", "room.png", "image/png", pngBytes());
        MvcResult r = mvc.perform(multipart("/api/listings/" + listingId + "/images").file(file)
                        .header("Authorization", owner.bearer()))
                .andReturn();
        return assertStatus(r, expectedStatus);
    }

    /** Tạo tin → ảnh → gửi duyệt → Admin duyệt. Trả id tin đang ACTIVE. */
    protected long createActiveListing(Actor owner, String categoryCode) throws Exception {
        long id = createDraftWithImage(owner, categoryCode);
        call(HttpMethod.POST, "/api/listings/" + id + "/submit", owner, null, 200);
        approve(id);
        return id;
    }

    protected void approve(long listingId) throws Exception {
        call(HttpMethod.PUT, "/api/admin/listings/" + listingId + "/approve", admin(), Map.of(), 200);
    }

    protected static byte[] pngBytes() throws Exception {
        BufferedImage img = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(ThreadLocalRandom.current().nextInt(0xFFFFFF)));
        g.fillRect(0, 0, 64, 48);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    // ================================================================== HTTP

    /** Gọi API, kiểm tra mã HTTP và trả JSON (null nếu không có body). */
    protected JsonNode call(HttpMethod method, String url, Actor actor, Object body, int expectedStatus)
            throws Exception {
        return assertStatus(raw(method, url, actor, body), expectedStatus);
    }

    protected MvcResult raw(HttpMethod method, String url, Actor actor, Object body) throws Exception {
        return rawWithAuth(method, url, actor == null ? null : actor.bearer(), body);
    }

    protected MvcResult rawWithAuth(HttpMethod method, String url, String authHeader, Object body) throws Exception {
        MockHttpServletRequestBuilder rb = MockMvcRequestBuilders.request(method, url)
                .with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                })
                .accept(MediaType.APPLICATION_JSON);
        if (authHeader != null) {
            rb.header("Authorization", authHeader);
        }
        if (body != null) {
            rb.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsBytes(body));
        }
        return mvc.perform(rb).andReturn();
    }

    protected JsonNode assertStatus(MvcResult r, int expectedStatus) throws Exception {
        String content = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertEquals(expectedStatus, r.getResponse().getStatus(),
                () -> r.getRequest().getMethod() + " " + r.getRequest().getRequestURI() + " → " + content);
        return content.isBlank() ? null : om.readTree(content);
    }

    /** Mã lỗi nghiệp vụ trong body lỗi. */
    protected static String errorCode(JsonNode body) {
        return body == null || body.get("errorCode") == null ? null : body.get("errorCode").asText();
    }

    protected static String today(int plusDays) {
        return java.time.LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(plusDays).toString();
    }
}
