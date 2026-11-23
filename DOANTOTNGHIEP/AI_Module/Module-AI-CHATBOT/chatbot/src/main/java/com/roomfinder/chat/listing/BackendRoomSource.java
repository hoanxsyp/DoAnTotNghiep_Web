package com.roomfinder.chat.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.roomfinder.chat.config.ListingSourceProperties;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Tin đăng THẬT của web qua API công khai của backend_webtro:
 * <ol>
 *   <li>{@code GET /api/search/listings} — lọc cứng (giá, khu vực, tiện ích, loại tin, bán kính).
 *       Backend tự áp quy tắc tin công khai ({@code publicStatuses()}, canonical §5.2).</li>
 *   <li>{@code GET /api/listings/{id}?countView=false} — lấy mô tả + tiện ích cho NLG / guardrail /
 *       semantic rerank. {@code countView=false}: chatbot đọc tin KHÔNG được cộng lượt xem.</li>
 * </ol>
 * Chi tiết được cache theo id ({@code detail-cache-seconds}). Tọa độ: API ẩn tọa độ tin với khách
 * nên lấy tâm phường từ {@link WebtroCatalog} (tin trên web hiện đều dùng tâm phường — V23).
 */
@Component
@ConditionalOnProperty(prefix = "roomfinder.listing", name = "source", havingValue = "backend")
public class BackendRoomSource implements RoomSource {

    private static final Logger log = LoggerFactory.getLogger(BackendRoomSource.class);

    /** room_type của chatbot → categoryCode của web. */
    private static final Map<String, List<String>> CATEGORY_BY_ROOM_TYPE = Map.of(
            "PHONG_TRO", List.of("BOARDING_HOUSE"),
            "CHUNG_CU_MINI", List.of("MINI_APARTMENT", "APARTMENT"),
            "NHA_NGUYEN_CAN", List.of("WHOLE_HOUSE"));

    /**
     * Không nói rõ loại hình → mọi loại để Ở (bỏ SMALL_PREMISES = mặt bằng kinh doanh: chatbot tìm
     * phòng trọ, đưa mặt bằng vào kết quả là sai ý người dùng).
     */
    private static final List<String> RESIDENTIAL_CATEGORIES = List.of(
            "BOARDING_HOUSE", "MINI_APARTMENT", "APARTMENT", "WHOLE_HOUSE", "HOMESTAY", "ROOMMATE");

    private static final Map<String, String> ROOM_TYPE_BY_CATEGORY = Map.of(
            "BOARDING_HOUSE", "PHONG_TRO",
            "ROOMMATE", "PHONG_TRO",
            "MINI_APARTMENT", "CHUNG_CU_MINI",
            "APARTMENT", "CHUNG_CU_MINI",
            "WHOLE_HOUSE", "NHA_NGUYEN_CAN");

    /** Key tiện ích (UtilityNormalizer) → mã tiện ích của web — lọc "có ĐỦ" qua {@code amenityIds}. */
    private static final Map<String, String> AMENITY_CODE_BY_UTILITY = Map.ofEntries(
            Map.entry("air_conditioner", "AIR_CONDITIONER"),
            Map.entry("water_heater", "WATER_HEATER"),
            Map.entry("washing_machine", "WASHING_MACHINE"),
            Map.entry("fridge", "FRIDGE"),
            Map.entry("wifi", "WIFI"),
            Map.entry("elevator", "ELEVATOR"),
            Map.entry("balcony", "BALCONY"),
            Map.entry("window", "WINDOW"),
            Map.entry("mezzanine", "MEZZANINE"),
            Map.entry("private_kitchen", "PRIVATE_KITCHEN"),
            Map.entry("kitchen_cabinet", "KITCHEN_CABINET"),
            Map.entry("wardrobe", "WARDROBE"),
            Map.entry("bed", "BED"),
            Map.entry("desk", "DESK"),
            Map.entry("tv", "TV"),
            Map.entry("security_guard", "SECURITY_GUARD"),
            Map.entry("cctv", "CCTV"),
            Map.entry("fingerprint_lock", "FINGERPRINT_LOCK"),
            Map.entry("fire_alarm", "FIRE_ALARM"),
            Map.entry("private_entrance", "PRIVATE_ENTRANCE"),
            Map.entry("laundry_area", "LAUNDRY_AREA"),
            Map.entry("electric_meter", "ELECTRIC_METER"),
            Map.entry("car_parking", "CAR_PARKING"));

    /** Key tiện ích là CỘT riêng của tin (không phải bảng amenities) → tham số tìm kiếm. */
    private static final Map<String, String[]> COLUMN_PARAM_BY_UTILITY = Map.of(
            "parking", new String[]{"parkingAvailable", "true"},
            "pet", new String[]{"petAllowed", "true"},
            "private_bathroom", new String[]{"toiletType", "PRIVATE"},
            "free_hours", new String[]{"curfewType", "FREE"},
            "full_furniture", new String[]{"furnitureStatus", "FULL"});

    private record Cached(JsonNode detail, Instant expiresAt) {}

    private final WebtroApiClient api;
    private final WebtroCatalog catalog;
    private final ListingSourceProperties props;
    private final Map<Long, Cached> detailCache = new ConcurrentHashMap<>();

    public BackendRoomSource(WebtroApiClient api, WebtroCatalog catalog, ListingSourceProperties props) {
        this.api = api;
        this.catalog = catalog;
        this.props = props;
    }

    @Override
    public List<Room> search(Filters f, Double centerLat, Double centerLng, double radiusM, int limit) {
        boolean geo = centerLat != null && centerLng != null;
        // Lấy rộng rồi tự sắp ở đây: backend luôn đưa tin ĐẨY (promoted) lên đầu trước tiêu chí
        // sắp xếp, và theo bán kính chỉ lọc khung chữ nhật, chưa sắp theo khoảng cách
        // (DISTANCE_ASC rơi về ngày đăng). Chatbot giữ thứ tự gốc: giá tăng dần / gần nhất.
        UriComponentsBuilder q = UriComponentsBuilder.fromPath("/api/search/listings")
                .queryParam("page", 0)
                .queryParam("size", props.getPoolSize())
                .queryParam("sort", geo ? "publishedAt,desc" : "price,asc");

        if (f.getPriceMin() != null) q.queryParam("priceFrom", f.getPriceMin());
        if (f.getPriceMax() != null) q.queryParam("priceTo", f.getPriceMax());
        if (f.getAreaMin() != null) q.queryParam("areaFrom", f.getAreaMin());
        if (geo) {
            q.queryParam("latitude", centerLat)
                    .queryParam("longitude", centerLng)
                    .queryParam("radiusKm", radiusM / 1000.0);
        } else if (f.getLocation() != null) {
            WebtroCatalog.Area area = catalog.resolveArea(f.getLocation());
            if (area != null) {
                q.queryParam("provinceId", area.provinceId()).queryParam("districtId", area.districtId());
                if (area.wardId() != null) q.queryParam("wardId", area.wardId());
            } else {
                q.queryParam("keyword", f.getLocation());   // tên đường/khu không có trong danh mục
            }
        }
        applyUtilities(q, f);
        List<String> categories = f.getRoomType() == null ? null : CATEGORY_BY_ROOM_TYPE.get(f.getRoomType());
        (categories != null ? categories : RESIDENTIAL_CATEGORIES).forEach(c -> q.queryParam("categoryCode", c));

        JsonNode page = api.getData(q.encode().build().toUri());
        List<JsonNode> items = new ArrayList<>();
        if (page != null) page.path("items").forEach(items::add);

        if (!geo) {
            // Sắp theo giá trên bản tóm tắt rồi chỉ lấy chi tiết của `limit` tin đầu.
            List<Long> ids = items.stream()
                    .sorted(Comparator.comparingDouble((JsonNode it) -> it.path("price").asDouble(Double.MAX_VALUE))
                            .thenComparingLong(it -> it.get("id").asLong()))
                    .limit(limit)
                    .map(it -> it.get("id").asLong())
                    .toList();
            return loadInOrder(ids);
        }

        // Theo bán kính: cần tọa độ từng tin (tâm phường lấy theo wardId trong chi tiết).
        List<Room> rooms = loadInOrder(items.stream().map(it -> it.get("id").asLong()).toList());

        for (Room r : rooms) {
            if (r.getLatitude() != null && r.getLongitude() != null) {
                r.setDistanceM(haversine(centerLat, centerLng,
                        r.getLatitude().doubleValue(), r.getLongitude().doubleValue()));
            }
        }
        return rooms.stream()
                .filter(r -> r.getDistanceM() != null && r.getDistanceM() <= radiusM)
                .sorted(Comparator.comparingDouble(Room::getDistanceM))
                .limit(limit)
                .toList();
    }

    @Override
    public List<Room> findByIds(List<Long> ids) {
        return loadInOrder(ids);
    }

    // --- Helpers ---------------------------------------------------------

    private void applyUtilities(UriComponentsBuilder q, Filters f) {
        if (f.getUtilities() == null) return;
        for (String key : f.getUtilities()) {
            String[] column = COLUMN_PARAM_BY_UTILITY.get(key);
            if (column != null) {
                q.queryParam(column[0], column[1]);
                continue;
            }
            String code = AMENITY_CODE_BY_UTILITY.get(key);
            Long id = code == null ? null : catalog.amenityId(code);
            if (id != null) q.queryParam("amenityIds", id);
            else log.debug("Tiện ích '{}' chưa có trên web — bỏ qua khi lọc", key);
        }
    }

    /** Lấy chi tiết song song (virtual thread), giữ thứ tự {@code ids}, bỏ tin 404/không công khai. */
    private List<Room> loadInOrder(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        List<Long> unique = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
        List<Room> out = new ArrayList<>();
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<JsonNode>> futures = unique.stream()
                    .map(id -> pool.submit(() -> detail(id)))
                    .toList();
            for (Future<JsonNode> fu : futures) {
                JsonNode d = fu.get();
                if (d != null) out.add(toRoom(d));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ListingSourceException("Bị ngắt khi lấy chi tiết tin", e);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ListingSourceException lse) throw lse;
            throw new ListingSourceException("Lỗi lấy chi tiết tin: " + e.getCause(), e);
        }
        return out;
    }

    private JsonNode detail(Long id) {
        Cached c = detailCache.get(id);
        if (c != null && Instant.now().isBefore(c.expiresAt())) return c.detail();
        JsonNode d = api.getData(URI.create("/api/listings/" + id + "?countView=false"));
        if (d != null) {
            detailCache.put(id, new Cached(d, Instant.now().plus(Duration.ofSeconds(props.getDetailCacheSeconds()))));
        } else {
            detailCache.remove(id);
            log.debug("Tin {} không còn công khai (404) — bỏ qua", id);
        }
        return d;
    }

    /** Dựng Room MỚI mỗi lần (Room bị gán distanceM theo từng lượt — không dùng chung bản cache). */
    private Room toRoom(JsonNode d) {
        Room r = new Room();
        r.setId(d.get("id").asLong());
        r.setTitle(d.path("title").asText(null));
        r.setSlug(d.path("slug").asText(null));
        r.setPrice(d.hasNonNull("price") ? Math.round(d.get("price").asDouble()) : null);
        r.setArea(d.hasNonNull("area") ? d.get("area").decimalValue() : null);
        r.setDistrict(d.path("districtName").asText(null));
        r.setAddressText(d.path("fullAddress").asText(null));
        r.setDescription(d.path("description").asText(null));
        r.setStatus("AVAILABLE");
        r.setWardId(d.hasNonNull("wardId") ? d.get("wardId").asLong() : null);

        if (d.hasNonNull("latitude") && d.hasNonNull("longitude")) {
            r.setLatitude(d.get("latitude").decimalValue());
            r.setLongitude(d.get("longitude").decimalValue());
        } else {
            WebtroCatalog.Point p = catalog.wardPoint(r.getWardId());
            if (p != null) {
                r.setLatitude(BigDecimal.valueOf(p.lat()));
                r.setLongitude(BigDecimal.valueOf(p.lng()));
            }
        }

        Set<String> codes = new HashSet<>();
        List<String> names = new ArrayList<>();
        d.path("amenities").forEach(a -> {
            codes.add(a.path("code").asText());
            names.add(a.path("name").asText());
        });
        r.setAmenityNames(names);
        r.setHasAirConditioner(codes.contains("AIR_CONDITIONER"));
        r.setHasWifi(codes.contains("WIFI"));
        r.setHasWashingMachine(codes.contains("WASHING_MACHINE"));
        r.setHasParking(d.path("parkingAvailable").asBoolean(false)
                || codes.contains("MOTORBIKE_PARKING") || codes.contains("CAR_PARKING"));
        r.setIsPrivateBathroom("PRIVATE".equals(d.path("toiletType").asText(null)));

        // Cùng bảng ánh xạ với bộ lọc → "có X không" trả lời khớp đúng cái bộ lọc X sẽ lọc.
        Set<String> keys = new HashSet<>();
        AMENITY_CODE_BY_UTILITY.forEach((key, code) -> { if (codes.contains(code)) keys.add(key); });
        COLUMN_PARAM_BY_UTILITY.forEach((key, col) -> {
            if (col[1].equals(d.path(col[0]).asText(null))) keys.add(key);
        });
        if (r.getHasParking()) keys.add("parking");
        r.setUtilityKeys(keys);
        r.setRoomType(ROOM_TYPE_BY_CATEGORY.get(d.path("categoryCode").asText("")));
        r.setThumbnail(thumbnail(d.path("images")));
        return r;
    }

    private static String thumbnail(JsonNode images) {
        JsonNode chosen = null;
        for (JsonNode img : images) {
            if (chosen == null || img.path("primary").asBoolean(false)) chosen = img;
            if (img.path("primary").asBoolean(false)) break;
        }
        if (chosen == null) return null;
        String t = chosen.path("thumbnailUrl").asText(null);
        return Objects.requireNonNullElse(t, chosen.path("url").asText(null));
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6_371_000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
