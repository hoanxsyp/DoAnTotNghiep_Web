package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ListingSourceProperties;
import com.roomfinder.chat.config.SemanticProperties;
import com.roomfinder.chat.domain.ListingEmbedding;
import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.embedding.EmbeddingClient;
import com.roomfinder.chat.repository.ListingEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Semantic rerank — SPEC §12.1 (GĐ3). KHÔNG dùng Elasticsearch (xem README §8):
 * ở quy mô đồ án (≤100 phòng), so cosine brute-force trong Java trên vector đã
 * cache nhanh hơn round-trip tới 1 service ES riêng.
 *
 * Nguyên tắc vàng (§5.1) vẫn giữ nguyên: hàm này chỉ RERANK candidate ĐÃ được
 * lọc cứng bằng SQL (giá/diện tích/tiện ích) — không bao giờ dùng để filter.
 */
@Service
public class SemanticRerankService {

    private static final Logger log = LoggerFactory.getLogger(SemanticRerankService.class);

    private final EmbeddingClient embeddingClient;
    private final ListingEmbeddingRepository embeddingRepo;
    private final SemanticProperties props;
    private final ObjectMapper mapper;
    /** Tiền tố khóa cache theo nguồn phòng — id phòng demo và id tin của web không đè nhau. */
    private final String keyPrefix;

    public SemanticRerankService(EmbeddingClient embeddingClient, ListingEmbeddingRepository embeddingRepo,
                                  SemanticProperties props, ObjectMapper mapper,
                                  ListingSourceProperties listingProps) {
        this.embeddingClient = embeddingClient;
        this.embeddingRepo = embeddingRepo;
        this.props = props;
        this.mapper = mapper;
        this.keyPrefix = listingProps.getSource() + ":";
    }

    /**
     * Sắp lại {@code candidates} theo độ tương đồng ngữ nghĩa giữa câu hỏi gốc
     * và mô tả phòng. Trả {@code null} khi không rerank được (tắt tính năng,
     * embedding lỗi) — tầng gọi giữ nguyên thứ tự gốc.
     */
    public List<Room> rerank(String message, List<Room> candidates) {
        if (!props.isEnabled() || candidates == null || candidates.size() < 2) return null;
        float[] queryVec = embeddingClient.embed(message);
        if (queryVec == null) return null;

        Map<Long, float[]> vectors = new HashMap<>();
        for (Room r : candidates) {
            float[] v = vectorOf(r);
            if (v != null) vectors.put(r.getId(), v);
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble((Room r) -> -scoreOf(vectors.get(r.getId()), queryVec)))
                .toList();
    }

    /**
     * Vector mô tả từ cache {@code listing_embedding}; chưa có hoặc mô tả đã đổi (hash khác) thì
     * embed lại rồi lưu — mỗi phòng chỉ tính 1 lần cho tới khi chủ trọ sửa mô tả.
     */
    private float[] vectorOf(Room r) {
        if (r.getDescription() == null || r.getDescription().isBlank()) return null;
        String key = keyPrefix + r.getId();
        String hash = sha256(r.getDescription());
        ListingEmbedding cached = embeddingRepo.findById(key).orElse(null);
        if (cached != null && hash.equals(cached.getDescriptionHash())) {
            float[] v = parse(cached.getVector());
            if (v != null) return v;
        }
        float[] v = embeddingClient.embed(r.getDescription());
        if (v == null) return null;
        try {
            ListingEmbedding e = cached != null ? cached : new ListingEmbedding();
            e.setListingKey(key);
            e.setDescriptionHash(hash);
            e.setVector(mapper.writeValueAsString(v));
            e.setUpdatedAt(Instant.now());
            embeddingRepo.save(e);
        } catch (Exception ex) {
            log.warn("Lưu vector mô tả lỗi cho {}: {}", key, ex.getMessage());
        }
        return v;
    }

    private static double scoreOf(float[] v, float[] queryVec) {
        return v == null ? -1.0 : cosine(queryVec, v);
    }

    private static String sha256(String s) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    private float[] parse(String json) {
        if (json == null) return null;
        try {
            return mapper.readValue(json, float[].class);
        } catch (Exception e) {
            return null;
        }
    }

    private static double cosine(float[] a, float[] b) {
        if (a.length != b.length) return -1.0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return -1.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
