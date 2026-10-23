package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IT luồng chính: tìm phòng → xem chi tiết/SĐT → gửi yêu cầu thuê → chủ trọ duyệt → xác nhận thuê →
 * đánh giá → trả phòng → thuê lại (IT-RENT-xx, IT-REV-xx).
 */
class TenantRentalFlowIT extends TenantItSupport {

    @Test
    @DisplayName("IT-RENT-01 Luồng đầy đủ: tìm thấy tin → SĐT hiển thị → yêu cầu thuê → duyệt → xác nhận thuê → "
            + "đánh giá → trả phòng → tin mở lại")
    void fullRentalLifecycle() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        // 1. Tin công khai: tìm thấy + xem chi tiết có SĐT liên hệ.
        JsonNode detail = call(HttpMethod.GET, "/api/listings/" + listingId, tenant, null, 200);
        assertEquals("ACTIVE", detail.at("/data/status").asText());
        assertFalse(detail.at("/data/contactPhone").asText("").isBlank(), "Tin chủ trọ phải có SĐT liên hệ");

        // 2. Gửi yêu cầu thuê → PENDING, chủ trọ nhận thông báo.
        JsonNode created = call(HttpMethod.POST, "/api/rental-requests", tenant,
                Map.of("listingId", listingId, "desiredViewingDate", today(3)), 201);
        long requestId = created.at("/data/id").asLong();
        assertEquals("PENDING", created.at("/data/status").asText());
        assertTrue(notificationTypes(landlord).contains("RENTAL_REQUEST_CREATED"));

        JsonNode mine = call(HttpMethod.GET, "/api/rental-requests/my", tenant, null, 200);
        assertEquals(requestId, mine.at("/data/items/0/id").asLong());

        // 3. Chủ trọ duyệt → tenant nhận RENTAL_REQUEST_APPROVED.
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + requestId + "/approve", landlord,
                Map.of("note", "Hẹn em 7h tối"), 200);
        assertTrue(notificationTypes(tenant).contains("RENTAL_REQUEST_APPROVED"));
        JsonNode approved = call(HttpMethod.GET, "/api/rental-requests/my?status=APPROVED", tenant, null, 200);
        assertEquals("Hẹn em 7h tối", approved.at("/data/items/0/landlordNote").asText());

        // Chưa thuê thì chưa đánh giá được.
        JsonNode notYet = call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews/eligibility", tenant, null, 200);
        assertEquals("NOT_RENTER", notYet.at("/data/reason").asText());

        // 4. Xác nhận thuê → tin RENTED (ẩn với người khác, người thuê vẫn xem được).
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + requestId + "/confirm-rented", landlord, null, 200);
        assertTrue(notificationTypes(tenant).contains("RENTAL_CONFIRMED"));
        assertEquals("RENTED", call(HttpMethod.GET, "/api/listings/" + listingId, tenant, null, 200)
                .at("/data/status").asText());
        Actor stranger = newTenant();
        call(HttpMethod.GET, "/api/listings/" + listingId, stranger, null, 404);

        // 5. Đánh giá khi đang thuê.
        JsonNode eligible = call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews/eligibility", tenant, null, 200);
        assertTrue(eligible.at("/data/eligible").asBoolean());
        JsonNode review = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant,
                Map.of("rating", 4, "content", "Phòng sạch, chủ nhà nhiệt tình"), 201);
        long reviewId = review.at("/data/id").asLong();
        assertTrue(notificationTypes(landlord).contains("NEW_REVIEW"));
        JsonNode reviews = call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews", null, null, 200);
        assertEquals(1, reviews.at("/data/summary/totalReviews").asInt());
        assertEquals(0, reviews.at("/data/summary/averageRating").decimalValue().compareTo(new java.math.BigDecimal("4")));

        // 6. Trả phòng → tin ACTIVE trở lại, tenant nhận RENTAL_CHECKED_OUT, vẫn sửa được đánh giá.
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + requestId + "/confirm-checked-out", landlord, null, 200);
        assertTrue(notificationTypes(tenant).contains("RENTAL_CHECKED_OUT"));
        assertEquals("ACTIVE", call(HttpMethod.GET, "/api/listings/" + listingId, stranger, null, 200)
                .at("/data/status").asText());
        call(HttpMethod.PUT, "/api/reviews/" + reviewId, tenant, Map.of("rating", 5, "content", "Ở lâu thấy rất ổn"), 200);
        JsonNode after = call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews", null, null, 200);
        assertEquals(0, after.at("/data/summary/averageRating").decimalValue().compareTo(new java.math.BigDecimal("5")));

        JsonNode history = call(HttpMethod.GET, "/api/rental-requests/my?status=CHECKED_OUT", tenant, null, 200);
        assertEquals(1, history.at("/data/totalElements").asInt());
    }

    @Test
    @DisplayName("IT-RENT-02 Không gửi trùng yêu cầu đang mở (409); ngày xem quá khứ (400); tin bị ẩn (409)")
    void createRequestGuards() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(1)), 201);
        JsonNode dup = call(HttpMethod.POST, "/api/rental-requests", tenant,
                Map.of("listingId", listingId, "desiredViewingDate", today(2)), 409);
        assertEquals("RENTAL_REQUEST_ALREADY_OPEN", errorCode(dup));

        Actor tenant2 = newTenant();
        JsonNode past = call(HttpMethod.POST, "/api/rental-requests", tenant2,
                Map.of("listingId", listingId, "desiredViewingDate", today(-1)), 400);
        assertEquals("VALIDATION_FAILED", errorCode(past));

        call(HttpMethod.POST, "/api/listings/" + listingId + "/hide", landlord, null, 200);
        JsonNode hidden = call(HttpMethod.POST, "/api/rental-requests", tenant2,
                Map.of("listingId", listingId, "desiredViewingDate", today(1)), 409);
        assertEquals("RENTAL_LISTING_UNAVAILABLE", errorCode(hidden));

        call(HttpMethod.POST, "/api/rental-requests", tenant2,
                Map.of("listingId", 999_999_999L, "desiredViewingDate", today(1)), 404);
    }

    @Test
    @DisplayName("IT-RENT-03 Đổi ngày xem khi PENDING; sau khi duyệt không đổi được (422); hủy APPROVED sát ngày (422)")
    void viewingDateAndCancelRules() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long requestId = call(HttpMethod.POST, "/api/rental-requests", tenant,
                Map.of("listingId", listingId, "desiredViewingDate", today(1)), 201).at("/data/id").asLong();

        JsonNode changed = call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/viewing-date", tenant,
                Map.of("desiredViewingDate", today(1)), 200);
        assertEquals(today(1), changed.at("/data/desiredViewingDate").asText());

        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + requestId + "/approve", landlord, null, 200);
        JsonNode cannotChange = call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/viewing-date", tenant,
                Map.of("desiredViewingDate", today(5)), 422);
        assertEquals("RENTAL_REQUEST_INVALID_STATUS", errorCode(cannotChange));

        JsonNode tooLate = call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/cancel", tenant, null, 422);
        assertEquals("RENTAL_CANCELLATION_DEADLINE_PASSED", errorCode(tooLate));
    }

    @Test
    @DisplayName("IT-RENT-04 Hủy yêu cầu PENDING → CANCELLED + báo chủ trọ; người khác không hủy được (403); gửi lại sau khi hủy được")
    void cancelPending() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        Actor other = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long requestId = call(HttpMethod.POST, "/api/rental-requests", tenant,
                Map.of("listingId", listingId, "desiredViewingDate", today(4)), 201).at("/data/id").asLong();

        JsonNode forbidden = call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/cancel", other, null, 403);
        assertEquals("RENTAL_REQUEST_FORBIDDEN", errorCode(forbidden));

        JsonNode cancelled = call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/cancel", tenant, null, 200);
        assertEquals("CANCELLED", cancelled.at("/data/status").asText());
        assertTrue(notificationTypes(landlord).contains("RENTAL_REQUEST_CANCELLED"));

        call(HttpMethod.PUT, "/api/rental-requests/" + requestId + "/cancel", tenant, null, 422);
        call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(5)), 201);
    }

    @Test
    @DisplayName("IT-RENT-05 Hai tenant cùng xin 1 phòng: xác nhận A → yêu cầu của B tự REJECTED + B được báo, B không xem được tin")
    void competingRequestsClosed() throws Exception {
        Actor landlord = newLandlord();
        Actor a = newTenant();
        Actor b = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long ra = call(HttpMethod.POST, "/api/rental-requests", a, Map.of("listingId", listingId, "desiredViewingDate", today(2)), 201)
                .at("/data/id").asLong();
        long rb = call(HttpMethod.POST, "/api/rental-requests", b, Map.of("listingId", listingId, "desiredViewingDate", today(2)), 201)
                .at("/data/id").asLong();

        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + ra + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + rb + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + ra + "/confirm-rented", landlord, null, 200);

        JsonNode bReq = call(HttpMethod.GET, "/api/rental-requests/my", b, null, 200);
        assertEquals("REJECTED", bReq.at("/data/items/0/status").asText());
        assertTrue(notificationTypes(b).contains("RENTAL_REQUEST_REJECTED"));
        call(HttpMethod.GET, "/api/listings/" + listingId, b, null, 404);

        JsonNode second = call(HttpMethod.PUT, "/api/landlord/rental-requests/" + rb + "/confirm-rented", landlord, null, 422);
        assertEquals("RENTAL_REQUEST_INVALID_STATUS", errorCode(second));
    }

    @Test
    @DisplayName("IT-RENT-06 Tenant đang thuê phòng A thì không thể được xác nhận thuê phòng B (409)")
    void tenantCannotRentTwoRooms() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long l1 = createActiveListing(landlord, "BOARDING_HOUSE");
        long l2 = createActiveListing(landlord, "BOARDING_HOUSE");
        long r1 = call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", l1, "desiredViewingDate", today(2)), 201)
                .at("/data/id").asLong();
        long r2 = call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", l2, "desiredViewingDate", today(2)), 201)
                .at("/data/id").asLong();
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r1 + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r2 + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r1 + "/confirm-rented", landlord, null, 200);

        // Yêu cầu chờ còn lại của tenant đã bị hủy tự động khi được xác nhận phòng 1.
        JsonNode list = call(HttpMethod.GET, "/api/rental-requests/my?status=CANCELLED", tenant, null, 200);
        assertEquals(r2, list.at("/data/items/0/id").asLong());
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r2 + "/confirm-rented", landlord, null, 422);
    }

    @Test
    @DisplayName("IT-RENT-07 Chủ trọ đóng tin → yêu cầu đang chờ của tenant tự REJECTED + thông báo")
    void closingListingEndsPendingRequests() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(2)), 201);

        call(HttpMethod.POST, "/api/listings/" + listingId + "/close", landlord, Map.of("reason", "NO_LONGER_AVAILABLE"), 200);

        JsonNode mine = call(HttpMethod.GET, "/api/rental-requests/my", tenant, null, 200);
        assertEquals("REJECTED", mine.at("/data/items/0/status").asText());
        assertTrue(notificationTypes(tenant).contains("RENTAL_REQUEST_REJECTED"));
    }

    @Test
    @DisplayName("IT-RENT-08 Sau khi trả phòng, tenant gửi yêu cầu thuê lại cùng phòng được")
    void rentAgainAfterCheckout() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        long r = call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(1)), 201)
                .at("/data/id").asLong();
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/confirm-rented", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/confirm-checked-out", landlord, null, 200);

        call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(3)), 201);
    }

    // ================================================================== đánh giá

    @Test
    @DisplayName("IT-REV-01 Chưa từng thuê → không đánh giá được (422 REVIEW_RENTAL_REQUIRED)")
    void reviewRequiresRental() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");

        JsonNode res = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant,
                Map.of("rating", 1, "content", "Chưa ở nhưng đánh giá thử"), 422);
        assertEquals("REVIEW_RENTAL_REQUIRED", errorCode(res));
    }

    @Test
    @DisplayName("IT-REV-02 Đánh giá: 1 người 1 đánh giá/phòng (409); xóa rồi tạo lại được; người khác không sửa/xóa được (403); "
            + "danh sách 'đánh giá của tôi'")
    void reviewOwnershipAndRecreate() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        rentAndCheckout(landlord, tenant, listingId);

        long reviewId = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant,
                Map.of("rating", 2, "content", "Hơi ồn vào buổi tối"), 201).at("/data/id").asLong();
        JsonNode dup = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant, Map.of("rating", 5), 409);
        assertEquals("REVIEW_ALREADY_EXISTS", errorCode(dup));

        Actor other = newTenant();
        call(HttpMethod.PUT, "/api/reviews/" + reviewId, other, Map.of("rating", 1, "content", "Sửa trộm đánh giá"), 403);
        call(HttpMethod.DELETE, "/api/reviews/" + reviewId, other, null, 403);

        JsonNode my = call(HttpMethod.GET, "/api/reviews/my", tenant, null, 200);
        assertEquals(reviewId, my.at("/data/items/0/id").asLong());

        call(HttpMethod.DELETE, "/api/reviews/" + reviewId, tenant, null, 204);
        JsonNode summary = call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews", null, null, 200);
        assertEquals(0, summary.at("/data/summary/totalReviews").asInt());

        call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant, Map.of("rating", 4, "content", "Đánh giá lại"), 201);
        assertEquals(1, call(HttpMethod.GET, "/api/listings/" + listingId + "/reviews", null, null, 200)
                .at("/data/summary/totalReviews").asInt());
    }

    @Test
    @DisplayName("IT-REV-03 Chấm ≤2 sao không có nội dung → 400 REVIEW_CONTENT_REQUIRED; số sao 6 → 400 VALIDATION_FAILED")
    void reviewValidation() throws Exception {
        Actor landlord = newLandlord();
        Actor tenant = newTenant();
        long listingId = createActiveListing(landlord, "BOARDING_HOUSE");
        rentAndCheckout(landlord, tenant, listingId);

        JsonNode noContent = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant, Map.of("rating", 2), 400);
        assertEquals("REVIEW_CONTENT_REQUIRED", errorCode(noContent));
        JsonNode badRating = call(HttpMethod.POST, "/api/listings/" + listingId + "/reviews", tenant, Map.of("rating", 6), 400);
        assertEquals("VALIDATION_FAILED", errorCode(badRating));
    }

    // ================================================================== helper

    private void rentAndCheckout(Actor landlord, Actor tenant, long listingId) throws Exception {
        long r = call(HttpMethod.POST, "/api/rental-requests", tenant, Map.of("listingId", listingId, "desiredViewingDate", today(1)), 201)
                .at("/data/id").asLong();
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/approve", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/confirm-rented", landlord, null, 200);
        call(HttpMethod.PUT, "/api/landlord/rental-requests/" + r + "/confirm-checked-out", landlord, null, 200);
    }

    private List<String> notificationTypes(Actor actor) throws Exception {
        JsonNode res = call(HttpMethod.GET, "/api/notifications?size=100", actor, null, 200);
        List<String> types = new ArrayList<>();
        res.at("/data/items").forEach(n -> types.add(n.get("type").asText()));
        return types;
    }
}
