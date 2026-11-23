package com.roomfinder.chat.listing;

import com.fasterxml.jackson.databind.JsonNode;
import com.roomfinder.chat.config.ListingSourceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Danh mục của web (quận, phường kèm tọa độ tâm, tiện ích) — nạp lười từ API công khai rồi cache.
 * Dùng để dịch bộ lọc của chatbot ("Cầu Giấy", "wifi") sang tham số tìm kiếm của backend
 * ({@code districtId}, {@code amenityIds}) và lấy tọa độ tâm phường tính khoảng cách (API chi
 * tiết tin ẩn tọa độ với khách).
 */
@Component
@ConditionalOnProperty(prefix = "roomfinder.listing", name = "source", havingValue = "backend")
public class WebtroCatalog {

    private static final Logger log = LoggerFactory.getLogger(WebtroCatalog.class);

    /**
     * Khu vực đã phân giải, kèm cấp cha — backend bắt buộc gửi đủ phân cấp
     * (districtId cần provinceId, wardId cần districtId). {@code wardId} null = cả quận.
     */
    public record Area(Long provinceId, Long districtId, Long wardId) {}

    public record Point(double lat, double lng) {}

    private record Snapshot(Map<String, Area> districtByKey, Map<String, Area> wardByKey,
                            Map<Long, Point> wardPoint, Map<String, Long> amenityIdByCode) {}

    private final WebtroApiClient api;
    private final Duration ttl;
    private volatile Snapshot snapshot;
    private volatile Instant loadedAt = Instant.EPOCH;

    public WebtroCatalog(WebtroApiClient api, ListingSourceProperties props) {
        this.api = api;
        this.ttl = Duration.ofMinutes(props.getCatalogCacheMinutes());
    }

    /** "Cầu Giấy", "quận cầu giấy", "Dịch Vọng" → quận/phường của web; không khớp → null. */
    public Area resolveArea(String location) {
        if (location == null || location.isBlank()) return null;
        Snapshot s = snapshot();
        String key = key(location);
        Area district = s.districtByKey().get(key);
        return district != null ? district : s.wardByKey().get(key);
    }

    public Point wardPoint(Long wardId) {
        return wardId == null ? null : snapshot().wardPoint().get(wardId);
    }

    /** Mã tiện ích của web (vd {@code WIFI}) → id; không có → null. */
    public Long amenityId(String code) {
        return snapshot().amenityIdByCode().get(code);
    }

    private Snapshot snapshot() {
        Snapshot s = snapshot;
        if (s != null && Instant.now().isBefore(loadedAt.plus(ttl))) return s;
        synchronized (this) {
            if (snapshot != null && Instant.now().isBefore(loadedAt.plus(ttl))) return snapshot;
            try {
                snapshot = load();
                loadedAt = Instant.now();
            } catch (ListingSourceException e) {
                // Giữ bản cũ nếu có — danh mục gần như không đổi, đừng làm hỏng cả lượt chat.
                if (snapshot == null) throw e;
                log.warn("Làm mới danh mục web lỗi, dùng bản cũ: {}", e.getMessage());
            }
            return snapshot;
        }
    }

    private Snapshot load() {
        Map<String, Area> districts = new HashMap<>();
        Map<String, Area> wards = new HashMap<>();
        Map<Long, Point> points = new HashMap<>();
        Map<String, Long> amenities = new HashMap<>();

        for (JsonNode p : list("/api/provinces")) {
            long provinceId = p.get("id").asLong();
            for (JsonNode d : list("/api/provinces/" + provinceId + "/districts")) {
                long districtId = d.get("id").asLong();
                districts.putIfAbsent(key(d.path("name").asText()), new Area(provinceId, districtId, null));
                for (JsonNode w : list("/api/districts/" + districtId + "/wards")) {
                    long wardId = w.get("id").asLong();
                    wards.putIfAbsent(key(w.path("name").asText()), new Area(provinceId, districtId, wardId));
                    if (w.hasNonNull("latitude") && w.hasNonNull("longitude")) {
                        points.put(wardId, new Point(w.get("latitude").asDouble(), w.get("longitude").asDouble()));
                    }
                }
            }
        }
        for (JsonNode a : list("/api/amenities")) {
            amenities.put(a.path("code").asText(), a.get("id").asLong());
        }
        log.info("Nạp danh mục web: {} quận, {} phường ({} có tọa độ), {} tiện ích",
                districts.size(), wards.size(), points.size(), amenities.size());
        return new Snapshot(Map.copyOf(districts), Map.copyOf(wards), Map.copyOf(points), Map.copyOf(amenities));
    }

    private Iterable<JsonNode> list(String path) {
        JsonNode data = api.getData(URI.create(path));
        return data != null && data.isArray() ? data : java.util.List.of();
    }

    /** Không dấu, chữ thường, bỏ tiền tố hành chính: "Quận Cầu Giấy" → "cau giay". */
    static String key(String s) {
        String n = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace("đ", "d");
        n = n.replaceFirst("^(quan|huyen|thi xa|thanh pho|phuong|xa|thi tran)\\s+", "");
        return n.replaceAll("\\s+", " ").trim();
    }
}
