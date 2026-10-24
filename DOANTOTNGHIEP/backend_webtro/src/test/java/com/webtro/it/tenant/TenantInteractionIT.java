package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * IT tương tác của người thuê: lưu tin, theo dõi chủ trọ, bình luận, nhắn tin, báo cáo, thông báo,
 * lịch sử, hồ sơ cá nhân (IT-FAV/FOL/CMT/CHAT/RPT/NOTI/HIS/PRO-xx).
 */
class TenantInteractionIT extends TenantItSupport {

    // ================================================================== lưu tin

    @Test
    @DisplayName("IT-FAV-01 Lưu tin → có trong danh sách + favoritedByMe; lưu trùng 409; bỏ lưu → 204; bỏ lần 2 → 404; lưu lại được")
    void favoriteLifecycle() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode added = call(HttpMethod.POST, "/api/favorites", tenant, Map.of("listingId", listingId, "note", "Gần trường"), 201);
        assertEquals(1, added.at("/data/favoriteCount").asInt());
        call(HttpMethod.POST, "/api/favorites", tenant, Map.of("listingId", listingId), 409);

        JsonNode list = call(HttpMethod.GET, "/api/favorites", tenant, null, 200);
        assertEquals(listingId, list.at("/data/items/0/listingId").asLong());
        assertTrue(call(HttpMethod.GET, "/api/listings/" + listingId, tenant, null, 200).at("/data/favoritedByMe").asBoolean());

        call(HttpMethod.DELETE, "/api/favorites/" + listingId, tenant, null, 204);
        call(HttpMethod.DELETE, "/api/favorites/" + listingId, tenant, null, 404);
        call(HttpMethod.POST, "/api/favorites", tenant, Map.of("listingId", listingId), 201);
    }

    @Test
    @DisplayName("IT-FAV-02 [BUG-03] Lưu tin chưa công khai (nháp của người khác) → phải 422 LISTING_NOT_ACTIVE")
    void favoriteDraftOfOthersRejected() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long draftId = call(HttpMethod.POST, "/api/listings", landlord, listingBody("BOARDING_HOUSE"), 201).at("/data/id").asLong();

        JsonNode res = call(HttpMethod.POST, "/api/favorites", tenant, Map.of("listingId", draftId), 422);
        assertEquals("LISTING_NOT_ACTIVE", errorCode(res));
    }

    // ================================================================== theo dõi

    @Test
    @DisplayName("IT-FOL-01 Theo dõi chủ trọ → danh sách đang theo dõi; theo dõi tenant khác → 422; bỏ theo dõi → 204, lần 2 → 409")
    void followLandlord() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor otherTenant = newTenant();

        JsonNode f = call(HttpMethod.POST, "/api/users/" + landlord.id() + "/follow", tenant, null, 201);
        assertTrue(f.at("/data/following").asBoolean());
        call(HttpMethod.POST, "/api/users/" + landlord.id() + "/follow", tenant, null, 409);
        JsonNode following = call(HttpMethod.GET, "/api/users/me/following", tenant, null, 200);
        assertEquals(landlord.id(), following.at("/data/items/0/landlordId").asLong());

        JsonNode notLandlord = call(HttpMethod.POST, "/api/users/" + otherTenant.id() + "/follow", tenant, null, 422);
        assertEquals("TARGET_NOT_LANDLORD", errorCode(notLandlord));
        call(HttpMethod.POST, "/api/users/" + tenant.id() + "/follow", tenant, null, 422);

        call(HttpMethod.DELETE, "/api/users/" + landlord.id() + "/follow", tenant, null, 204);
        call(HttpMethod.DELETE, "/api/users/" + landlord.id() + "/follow", tenant, null, 409);
    }

    @Test
    @DisplayName("IT-FOL-02 Đang theo dõi chủ trọ → chủ trọ có tin mới được duyệt thì tenant nhận thông báo")
    void followerNotifiedOnNewListing() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        call(HttpMethod.POST, "/api/users/" + landlord.id() + "/follow", tenant, null, 201);

        createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode noti = call(HttpMethod.GET, "/api/notifications?type=FOLLOWED_LANDLORD_NEW_LISTING", tenant, null, 200);
        assertEquals(1, noti.at("/data/totalElements").asInt(),
                () -> "Tenant theo dõi chủ trọ nhưng không nhận thông báo tin mới: " + noti);
    }

    @Test
    @DisplayName("IT-FOL-03 [BUG-06] Link của thông báo 'chủ trọ theo dõi có tin mới' phải mở được trang chi tiết (/tin/...)")
    @Disabled("BUG-06: FollowListener đặt link '/listings/{id}', frontend chỉ có route '/tin/:slugId' → bấm thông báo ra 404. "
            + "Bỏ @Disabled sau khi fix.")
    void followerNotificationLinkOpensListing() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        call(HttpMethod.POST, "/api/users/" + landlord.id() + "/follow", tenant, null, 201);
        createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode noti = call(HttpMethod.GET, "/api/notifications?type=FOLLOWED_LANDLORD_NEW_LISTING", tenant, null, 200);
        String url = noti.at("/data/items/0/targetUrl").asText();
        assertTrue(url.startsWith("/tin/"), () -> "targetUrl không khớp route frontend: " + url);
    }

    // ================================================================== bình luận

    @Test
    @DisplayName("IT-CMT-01 Bình luận → hiện công khai; chủ trọ nhận NEW_COMMENT; trả lời; sửa; xóa của mình; không sửa/xóa của người khác")
    void commentLifecycle() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor other = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        long rootId = call(HttpMethod.POST, "/api/listings/" + listingId + "/comments", tenant,
                Map.of("content", "Phòng còn trống không ạ?"), 201).at("/data/id").asLong();
        JsonNode list = call(HttpMethod.GET, "/api/listings/" + listingId + "/comments", null, null, 200);
        assertEquals(rootId, list.at("/data/items/0/id").asLong());
        assertTrue(call(HttpMethod.GET, "/api/notifications", landlord, null, 200).toString().contains("NEW_COMMENT"));

        long replyId = call(HttpMethod.POST, "/api/comments/" + rootId + "/reply", landlord,
                Map.of("content", "Còn em nhé"), 201).at("/data/id").asLong();
        JsonNode deep = call(HttpMethod.POST, "/api/comments/" + replyId + "/reply", tenant, Map.of("content", "Dạ em cảm ơn"), 422);
        assertEquals("COMMENT_NESTING_TOO_DEEP", errorCode(deep));

        JsonNode edited = call(HttpMethod.PUT, "/api/comments/" + rootId, tenant, Map.of("content", "Phòng còn trống không ạ? (sửa)"), 200);
        assertTrue(edited.at("/data/content").asText().endsWith("(sửa)"));
        call(HttpMethod.PUT, "/api/comments/" + rootId, other, Map.of("content", "Sửa trộm"), 403);
        call(HttpMethod.DELETE, "/api/comments/" + rootId, other, null, 403);

        call(HttpMethod.DELETE, "/api/comments/" + rootId, tenant, null, 204);
        JsonNode after = call(HttpMethod.GET, "/api/listings/" + listingId + "/comments", null, null, 200);
        assertEquals(0, after.at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-CMT-02 Bình luận chứa HTML/script → 400 VALIDATION_FAILED (chặn, không lưu); quá ngắn (<3) → 400")
    void commentSanitizeAndValidate() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode xss = call(HttpMethod.POST, "/api/listings/" + listingId + "/comments", tenant,
                Map.of("content", "<script>alert(1)</script>Giá có thương lượng không?"), 400);
        assertEquals("VALIDATION_FAILED", errorCode(xss));
        assertEquals("content", xss.at("/errors/0/field").asText());
        call(HttpMethod.POST, "/api/listings/" + listingId + "/comments", tenant, Map.of("content", "ok"), 400);
        assertEquals(0, call(HttpMethod.GET, "/api/listings/" + listingId + "/comments", null, null, 200)
                .at("/data/totalElements").asInt());
    }

    // ================================================================== nhắn tin

    @Test
    @DisplayName("IT-CHAT-01 Tenant nhắn chủ trọ → chủ trọ thấy chưa đọc; trả lời; tenant đánh dấu đã đọc; mở lại cùng tin → 200 dùng lại hội thoại")
    void chatFlow() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode conv = call(HttpMethod.POST, "/api/conversations", tenant,
                Map.of("listingId", listingId, "initialMessage", "Chào anh, phòng còn trống không ạ?"), 201);
        long convId = conv.at("/data/id").asLong();

        JsonNode landlordInbox = call(HttpMethod.GET, "/api/conversations", landlord, null, 200);
        assertEquals(1, landlordInbox.at("/data/totalUnreadConversations").asInt());

        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", landlord, Map.of("content", "Còn em, qua xem nhé"), 201);
        JsonNode unread = call(HttpMethod.GET, "/api/notifications/unread-count", tenant, null, 200);
        assertEquals(1, unread.at("/data/unreadMessageCount").asInt());

        call(HttpMethod.POST, "/api/conversations/" + convId + "/read", tenant, null, 204);
        assertEquals(0, call(HttpMethod.GET, "/api/notifications/unread-count", tenant, null, 200)
                .at("/data/unreadMessageCount").asInt());

        JsonNode msgs = call(HttpMethod.GET, "/api/conversations/" + convId + "/messages", tenant, null, 200);
        assertEquals(2, msgs.at("/data/totalElements").asInt());

        JsonNode again = call(HttpMethod.POST, "/api/conversations", tenant,
                Map.of("listingId", listingId, "initialMessage", "Em hỏi thêm: có chỗ để xe không ạ?"), 200);
        assertEquals(convId, again.at("/data/id").asLong());
        assertTrue(again.at("/data/alreadyExisted").asBoolean());
    }

    @Test
    @DisplayName("IT-CHAT-02 Người ngoài đọc/gửi vào hội thoại của người khác → 403; chủ trọ tắt chat → 422 CHAT_DISABLED_BY_LANDLORD")
    void chatAccessControl() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor stranger = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long convId = call(HttpMethod.POST, "/api/conversations", tenant,
                Map.of("listingId", listingId, "initialMessage", "Chào anh, phòng còn trống không ạ?"), 201).at("/data/id").asLong();

        call(HttpMethod.GET, "/api/conversations/" + convId + "/messages", stranger, null, 403);
        call(HttpMethod.POST, "/api/conversations/" + convId + "/messages", stranger, Map.of("content", "chen ngang"), 403);
        call(HttpMethod.GET, "/api/conversations/" + convId, stranger, null, 403);

        sql("update landlord_profiles set allow_chat = false where user_id = ?", landlord.id());
        JsonNode disabled = call(HttpMethod.POST, "/api/conversations", stranger,
                Map.of("listingId", listingId, "initialMessage", "Chào anh, phòng còn trống không ạ?"), 422);
        assertEquals("CHAT_DISABLED_BY_LANDLORD", errorCode(disabled));
    }

    // ================================================================== báo cáo

    @Test
    @DisplayName("IT-RPT-01 Báo cáo tin (multipart) → 201 PENDING; trùng → 409; 'Báo cáo của tôi' có bản ghi; OTHER không mô tả → 400")
    void reportListing() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode r = report(tenant, "LISTING", listingId, "SCAM", "Đòi đặt cọc trước khi xem phòng", 201);
        assertEquals("PENDING", r.at("/data/status").asText());
        assertEquals("REPORT_DUPLICATE", errorCode(report(tenant, "LISTING", listingId, "SCAM", null, 409)));
        assertEquals("REPORT_DESCRIPTION_REQUIRED", errorCode(report(tenant, "LISTING", listingId, "OTHER", null, 400)));

        JsonNode my = call(HttpMethod.GET, "/api/reports/my", tenant, null, 200);
        assertEquals(1, my.at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-RPT-02 Báo cáo chủ trọ (USER) và bình luận; không tự báo cáo mình (422)")
    void reportUserAndComment() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long commentId = call(HttpMethod.POST, "/api/listings/" + listingId + "/comments", tenant,
                Map.of("content", "Phòng còn không ạ?"), 201).at("/data/id").asLong();

        report(tenant, "USER", landlord.id(), "SPAM", null, 201);
        report(landlord, "COMMENT", commentId, "OFFENSIVE", null, 201);
        assertEquals("REPORT_SELF_FORBIDDEN", errorCode(report(tenant, "COMMENT", commentId, "SPAM", null, 422)));
    }

    // ================================================================== thông báo

    @Test
    @DisplayName("IT-NOTI-01 Xem/đánh dấu đọc/đọc tất cả/xóa thông báo; không thao tác được thông báo của người khác (403)")
    void notifications() throws Exception {
        Actor tenant = newTenant();
        Actor other = newTenant();
        JsonNode list = call(HttpMethod.GET, "/api/notifications", tenant, null, 200);
        long notiId = list.at("/data/items/0/id").asLong();
        assertTrue(list.at("/data/unreadCount").asLong() >= 1);

        call(HttpMethod.PUT, "/api/notifications/" + notiId + "/read", other, null, 403);
        call(HttpMethod.DELETE, "/api/notifications/" + notiId, other, null, 403);

        call(HttpMethod.PUT, "/api/notifications/" + notiId + "/read", tenant, null, 200);
        call(HttpMethod.PUT, "/api/notifications/read-all", tenant, null, 200);
        assertEquals(0, call(HttpMethod.GET, "/api/notifications/unread-count", tenant, null, 200).at("/data/unreadCount").asInt());
        call(HttpMethod.DELETE, "/api/notifications/" + notiId, tenant, null, 204);

        call(HttpMethod.GET, "/api/notifications/preferences", tenant, null, 200);
    }

    // ================================================================== lịch sử

    @Test
    @DisplayName("IT-HIS-01 [BUG-05] Tenant xem chi tiết tin → tin phải xuất hiện trong 'Lịch sử xem'")
    @Disabled("BUG-05: HistoryService.recordView() không được gọi ở đâu (GET /api/listings/{id} chỉ tăng view_count) → "
            + "lịch sử xem luôn rỗng, tín hiệu gợi ý & thống kê lượt xem duy nhất cũng mất. Bỏ @Disabled sau khi fix.")
    void viewDetailIsRecordedInHistory() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        call(HttpMethod.GET, "/api/listings/" + listingId, tenant, null, 200);

        JsonNode views = call(HttpMethod.GET, "/api/history/views", tenant, null, 200);
        assertEquals(listingId, views.at("/data/items/0/listingId").asLong());
    }

    @Test
    @DisplayName("IT-HIS-03 Lịch sử xem (dựng bằng SQL): xem danh sách; xóa 1 mục của người khác → 403; xóa 1 mục / xóa toàn bộ của mình")
    void viewHistoryManagement() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor other = newTenant();
        long l1 = createActiveListing(landlord, "BOARDING_HOUSE");
        long l2 = createActiveListing(landlord, "BOARDING_HOUSE");
        sql("insert into view_histories (listing_id, user_id, ip_address, is_counted, viewed_at, created_at) "
                + "values (?, ?, '127.0.0.1', true, now(6), now(6))", l1, tenant.id());
        sql("insert into view_histories (listing_id, user_id, ip_address, is_counted, viewed_at, created_at) "
                + "values (?, ?, '127.0.0.1', true, now(6), now(6))", l2, tenant.id());

        JsonNode views = call(HttpMethod.GET, "/api/history/views", tenant, null, 200);
        assertEquals(2, views.at("/data/totalElements").asInt());
        long viewId = views.at("/data/items/0/id").asLong();
        assertEquals(0, call(HttpMethod.GET, "/api/history/views", other, null, 200).at("/data/totalElements").asInt());

        call(HttpMethod.DELETE, "/api/history/views/" + viewId, other, null, 403);
        call(HttpMethod.DELETE, "/api/history/views/" + viewId, tenant, null, 204);
        assertEquals(1, call(HttpMethod.GET, "/api/history/views", tenant, null, 200).at("/data/totalElements").asInt());
        call(HttpMethod.DELETE, "/api/history/views", tenant, null, 204);
        assertEquals(0, call(HttpMethod.GET, "/api/history/views", tenant, null, 200).at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-HIS-02 Tìm kiếm khi đăng nhập → lưu lịch sử tìm kiếm; xóa toàn bộ được")
    void searchHistory() throws Exception {
        Actor tenant = newTenant();
        call(HttpMethod.GET, "/api/search/listings?keyword=cau%20giay", tenant, null, 200);
        call(HttpMethod.GET, "/api/search/histories", tenant, null, 200);
        call(HttpMethod.DELETE, "/api/search/histories", tenant, null, 204);
        assertEquals(0, call(HttpMethod.GET, "/api/search/histories", tenant, null, 200).at("/data/totalElements").asInt());
    }

    // ================================================================== hồ sơ

    @Test
    @DisplayName("IT-PRO-01 Cập nhật hồ sơ (lọc HTML ở bio bị chặn bởi @NoHtml → 400), cập nhật SĐT, avatar không phải ảnh → 400")
    void profile() throws Exception {
        Actor tenant = newTenant();
        JsonNode updated = call(HttpMethod.PUT, "/api/users/me", tenant,
                Map.of("fullName", "Nguyễn Văn Thuê", "gender", "MALE", "bio", "Sinh viên năm cuối"), 200);
        assertEquals("Nguyễn Văn Thuê", updated.at("/data/fullName").asText());
        call(HttpMethod.PUT, "/api/users/me", tenant, Map.of("fullName", "Thuê", "bio", "<b>xin chào</b>"), 400);

        String newPhone = randomPhone();
        JsonNode contact = call(HttpMethod.PATCH, "/api/users/me/contact", tenant, Map.of("contactPhone", newPhone), 200);
        assertEquals(newPhone, contact.at("/data/phone").asText());

        MvcResult avatar = mvc.perform(multipart("/api/users/me/avatar")
                .file(new MockMultipartFile("file", "x.png", "image/png", "not-an-image-at-all".getBytes()))
                .header("Authorization", tenant.bearer())).andReturn();
        assertStatus(avatar, 400);
        MvcResult okAvatar = mvc.perform(multipart("/api/users/me/avatar")
                .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes()))
                .header("Authorization", tenant.bearer())).andReturn();
        assertStatus(okAvatar, 200);
        call(HttpMethod.DELETE, "/api/users/me/avatar", tenant, null, 204);
        call(HttpMethod.DELETE, "/api/users/me/avatar", tenant, null, 404);
    }

    @Test
    @DisplayName("IT-PRO-02 [BUG-04] Đổi sang SĐT mới → phoneVerified phải về false")
    void changingPhoneResetsVerification() throws Exception {
        Actor tenant = newTenant();
        sql("update users set phone_verified_at = now() where id = ?", tenant.id());
        assertTrue(call(HttpMethod.GET, "/api/users/me", tenant, null, 200).at("/data/phoneVerified").asBoolean());

        call(HttpMethod.PATCH, "/api/users/me/contact", tenant, Map.of("contactPhone", randomPhone()), 200);

        assertFalse(call(HttpMethod.GET, "/api/users/me", tenant, null, 200).at("/data/phoneVerified").asBoolean());
    }

    // ================================================================== helper

    private JsonNode report(Actor actor, String type, long targetId, String reason, String description, int status)
            throws Exception {
        var rb = multipart("/api/reports")
                .param("targetType", type)
                .param("targetId", String.valueOf(targetId))
                .param("reason", reason)
                .header("Authorization", actor.bearer());
        if (description != null) {
            rb.param("description", description);
        }
        return assertStatus(mvc.perform(rb).andReturn(), status);
    }
}
