package com.webtro.it.tenant;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** IT tin ở ghép (ROOMMATE) do người thuê đăng và quản lý (IT-RM-xx). */
class TenantRoommateListingIT extends TenantItSupport {

    @Test
    @DisplayName("IT-RM-01 Tạo tin ở ghép → DRAFT → tải ảnh → gửi duyệt PENDING → Admin duyệt ACTIVE → công khai, có trong 'Tin của tôi'")
    void roommateLifecycleToActive() throws Exception {
        Actor tenant = newTenant();
        JsonNode created = call(HttpMethod.POST, "/api/listings", tenant, listingBody("ROOMMATE"), 201);
        long id = created.at("/data/id").asLong();
        assertEquals("DRAFT", created.at("/data/status").asText());

        JsonNode noImage = call(HttpMethod.POST, "/api/listings/" + id + "/submit", tenant, null, 400);
        assertEquals("IMAGE_COUNT_MIN", errorCode(noImage));

        uploadImage(tenant, id, 201);
        assertEquals("PENDING", call(HttpMethod.POST, "/api/listings/" + id + "/submit", tenant, null, 200)
                .at("/data/status").asText());
        // Chưa duyệt thì người khác không xem được.
        Actor other = newTenant();
        call(HttpMethod.GET, "/api/listings/" + id, other, null, 404);

        approve(id);
        assertEquals("ACTIVE", call(HttpMethod.GET, "/api/listings/" + id, other, null, 200).at("/data/status").asText());
        assertTrue(notification(tenant).contains("LISTING_APPROVED"));

        JsonNode my = call(HttpMethod.GET, "/api/listings/my", tenant, null, 200);
        assertEquals(id, my.at("/data/items/0/id").asLong());
        assertEquals(1, my.at("/data/statusCounts/ACTIVE").asInt());
    }

    @Test
    @DisplayName("IT-RM-02 Tenant tạo/sửa sang loại chỉ dành cho chủ trọ → 403 LISTING_FORBIDDEN")
    void tenantRestrictedToRoommate() throws Exception {
        Actor tenant = newTenant();
        JsonNode res = call(HttpMethod.POST, "/api/listings", tenant, listingBody("BOARDING_HOUSE"), 403);
        assertEquals("LISTING_FORBIDDEN", errorCode(res));

        long id = call(HttpMethod.POST, "/api/listings", tenant, listingBody("ROOMMATE"), 201).at("/data/id").asLong();
        Map<String, Object> update = listingBody("APARTMENT");
        call(HttpMethod.PUT, "/api/listings/" + id, tenant, update, 403);
    }

    @Test
    @DisplayName("IT-RM-03 Thiếu thông tin ở ghép (giới tính/số người hiện tại) → 400 ROOMMATE_INFO_REQUIRED")
    void roommateInfoRequired() throws Exception {
        Actor tenant = newTenant();
        Map<String, Object> body = listingBody("ROOMMATE");
        body.remove("currentOccupants");
        JsonNode res = call(HttpMethod.POST, "/api/listings", tenant, body, 400);
        assertEquals("ROOMMATE_INFO_REQUIRED", errorCode(res));
    }

    @Test
    @DisplayName("IT-RM-04 Bị từ chối → tenant nhận LISTING_REJECTED → sửa → gửi duyệt lại → PENDING")
    void rejectedThenResubmit() throws Exception {
        Actor tenant = newTenant();
        long id = createDraftWithImage(tenant, "ROOMMATE");
        call(HttpMethod.POST, "/api/listings/" + id + "/submit", tenant, null, 200);
        call(HttpMethod.PUT, "/api/admin/listings/" + id + "/reject", admin(),
                Map.of("reason", "Thiếu thông tin giờ giấc và chi phí điện nước", "reasonCode", "MISSING_INFO"), 200);
        assertTrue(notification(tenant).contains("LISTING_REJECTED"));

        Map<String, Object> fix = listingBody("ROOMMATE");
        fix.put("description", "Đã bổ sung: điện 3.5k/số, nước 100k/người, giờ giấc tự do, khóa vân tay.");
        call(HttpMethod.PUT, "/api/listings/" + id, tenant, fix, 200);
        assertEquals("PENDING", call(HttpMethod.POST, "/api/listings/" + id + "/submit", tenant, null, 200)
                .at("/data/status").asText());
    }

    @Test
    @DisplayName("IT-RM-05 Sửa tiêu đề tin ACTIVE → quay lại PENDING (requiresReapproval=true); người khác sửa → 403")
    void editActiveRequiresReapproval() throws Exception {
        Actor tenant = newTenant();
        long id = createActiveListing(tenant, "ROOMMATE");
        Map<String, Object> body = listingBody("ROOMMATE");
        body.put("title", "Tìm bạn ở ghép tiêu đề mới " + uniq());

        Actor other = newTenant();
        call(HttpMethod.PUT, "/api/listings/" + id, other, body, 403);

        JsonNode res = call(HttpMethod.PUT, "/api/listings/" + id, tenant, body, 200);
        assertTrue(res.at("/data/moderationImpact/requiresReapproval").asBoolean());
        assertEquals("PENDING", res.at("/data/moderationImpact/newStatus").asText());
    }

    @Test
    @DisplayName("IT-RM-06 Ẩn → HIDDEN (người khác 404) → hiện lại → ACTIVE → đóng → CLOSED → xóa → 404")
    void hideUnhideCloseDelete() throws Exception {
        Actor tenant = newTenant();
        Actor other = newTenant();
        long id = createActiveListing(tenant, "ROOMMATE");

        assertEquals("HIDDEN", call(HttpMethod.POST, "/api/listings/" + id + "/hide", tenant, null, 200).at("/data/status").asText());
        call(HttpMethod.GET, "/api/listings/" + id, other, null, 404);
        call(HttpMethod.POST, "/api/listings/" + id + "/hide", tenant, null, 409);
        assertEquals("ACTIVE", call(HttpMethod.POST, "/api/listings/" + id + "/unhide", tenant, null, 200).at("/data/status").asText());

        call(HttpMethod.POST, "/api/listings/" + id + "/close", other, Map.of("reason", "RENTED_OUT"), 403);
        assertEquals("CLOSED", call(HttpMethod.POST, "/api/listings/" + id + "/close", tenant,
                Map.of("reason", "RENTED_OUT"), 200).at("/data/status").asText());

        call(HttpMethod.DELETE, "/api/listings/" + id, other, null, 403);
        call(HttpMethod.DELETE, "/api/listings/" + id, tenant, null, 204);
        call(HttpMethod.GET, "/api/listings/" + id, tenant, null, 404);
    }

    @Test
    @DisplayName("IT-RM-07 Quản lý ảnh/tiện ích: tải thêm ảnh, đặt ảnh chính, sắp xếp, cập nhật tiện ích")
    void imagesAndAmenities() throws Exception {
        Actor tenant = newTenant();
        long id = createDraftWithImage(tenant, "ROOMMATE");
        JsonNode second = uploadImage(tenant, id, 201);
        long img2 = second.at("/data/uploaded/0/id").asLong();
        assertEquals(2, second.at("/data/totalImages").asInt());

        call(HttpMethod.PUT, "/api/listings/" + id + "/images/" + img2 + "/primary", tenant, null, 200);
        JsonNode detail = call(HttpMethod.GET, "/api/listings/" + id, tenant, null, 200);
        long img1 = detail.at("/data/images/0/id").asLong() == img2
                ? detail.at("/data/images/1/id").asLong() : detail.at("/data/images/0/id").asLong();
        call(HttpMethod.PUT, "/api/listings/" + id + "/images/order", tenant, Map.of("imageIds", java.util.List.of(img2, img1)), 200);

        JsonNode amenities = call(HttpMethod.GET, "/api/amenities", null, null, 200);
        long amenityId = firstAmenityId(amenities);
        JsonNode updated = call(HttpMethod.PUT, "/api/listings/" + id + "/amenities", tenant,
                Map.of("amenityIds", java.util.List.of(amenityId)), 200);
        assertEquals(1, updated.at("/data").size());
        // Gỡ hết rồi gắn lại không lỗi trùng khóa.
        call(HttpMethod.PUT, "/api/listings/" + id + "/amenities", tenant, Map.of("amenityIds", java.util.List.of()), 200);
        call(HttpMethod.PUT, "/api/listings/" + id + "/amenities", tenant, Map.of("amenityIds", java.util.List.of(amenityId)), 200);
    }

    @Test
    @DisplayName("IT-RM-08 [BUG-01] Tenant gia hạn miễn phí tin ở ghép đang ACTIVE → phải 200 free=true")
    void tenantRenewRoommate() throws Exception {
        Actor tenant = newTenant();
        long id = createActiveListing(tenant, "ROOMMATE");

        JsonNode res = call(HttpMethod.POST, "/api/listings/" + id + "/renew", tenant, null, 200);
        assertTrue(res.at("/data/free").asBoolean());
    }

    @Test
    @DisplayName("IT-RM-09 [BUG-08] Tin ở ghép do tenant đăng phải hiển thị SĐT liên hệ (form bắt buộc nhập contactPhone)")
    void roommateListingShowsContactPhone() throws Exception {
        Actor poster = newTenant();
        Actor viewer = newTenant();
        long id = createActiveListing(poster, "ROOMMATE");

        JsonNode detail = call(HttpMethod.GET, "/api/listings/" + id, viewer, null, 200);
        assertFalse(detail.at("/data/contactPhone").asText("").isBlank(), () -> "Không có SĐT liên hệ: " + detail);
    }

    @Test
    @DisplayName("IT-RM-10 [BUG-09] Tenant khác nhắn tin cho người đăng tin ở ghép → phải tạo được hội thoại")
    void chatWithRoommatePoster() throws Exception {
        Actor poster = newTenant();
        Actor seeker = newTenant();
        long id = createActiveListing(poster, "ROOMMATE");

        call(HttpMethod.POST, "/api/conversations", seeker,
                Map.of("listingId", id, "initialMessage", "Chào bạn, mình muốn ở ghép, còn chỗ không?"), 201);
    }

    @Test
    @DisplayName("IT-RM-11 [BUG-10] Tin ở ghép của tenant không nhận yêu cầu thuê → 422, chi tiết tin báo rentalRequestEnabled=false")
    void rentalRequestOnRoommateIsRejected() throws Exception {
        Actor poster = newTenant();
        Actor seeker = newTenant();
        long id = createActiveListing(poster, "ROOMMATE");

        JsonNode detail = call(HttpMethod.GET, "/api/listings/" + id, seeker, null, 200);
        assertFalse(detail.at("/data/landlord/rentalRequestEnabled").asBoolean(true), () -> "Phải tắt thuê phòng: " + detail);

        JsonNode res = call(HttpMethod.POST, "/api/rental-requests", seeker,
                Map.of("listingId", id, "desiredViewingDate", today(2)), 422);
        assertEquals("RENTAL_NOT_SUPPORTED_FOR_ROOMMATE", errorCode(res));
    }

    @Test
    @DisplayName("IT-RM-12 [BUG-10] Tin ở ghép do chủ trọ đăng vẫn nhận yêu cầu thuê")
    void rentalRequestOnLandlordRoommateIsAllowed() throws Exception {
        Actor landlord = newLandlord();
        Actor seeker = newTenant();
        long id = createActiveListing(landlord, "ROOMMATE");

        JsonNode detail = call(HttpMethod.GET, "/api/listings/" + id, seeker, null, 200);
        assertTrue(detail.at("/data/landlord/rentalRequestEnabled").asBoolean(false), () -> "Phải bật thuê phòng: " + detail);

        call(HttpMethod.POST, "/api/rental-requests", seeker,
                Map.of("listingId", id, "desiredViewingDate", today(2)), 201);
    }

    private java.util.List<String> notificationTypesRaw(Actor a) throws Exception {
        java.util.List<String> out = new java.util.ArrayList<>();
        call(HttpMethod.GET, "/api/notifications?size=100", a, null, 200).at("/data/items")
                .forEach(n -> out.add(n.get("type").asText()));
        return out;
    }

    private java.util.List<String> notification(Actor a) throws Exception {
        return notificationTypesRaw(a);
    }

    private long firstAmenityId(JsonNode amenities) {
        JsonNode data = amenities.get("data");
        java.util.Deque<JsonNode> stack = new java.util.ArrayDeque<>();
        stack.push(data);
        while (!stack.isEmpty()) {
            JsonNode n = stack.pop();
            if (n.isObject() && n.has("id") && n.has("name") && !n.has("amenities")) {
                return n.get("id").asLong();
            }
            n.forEach(stack::push);
        }
        throw new IllegalStateException("Không có tiện ích nào: " + amenities);
    }
}
