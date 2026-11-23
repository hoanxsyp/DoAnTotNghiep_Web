package com.roomfinder.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomfinder.chat.config.ChatProperties;
import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.ChatContext;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.normalizer.UtilityNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Quản lý ngữ cảnh hội thoại trong Redis — §4. Áp dụng ba phép toán
 * MERGE / OVERRIDE / RESET (§4.2). Key: chat:session:{id}, TTL 30' refresh mỗi lượt.
 */
@Service
public class ContextService {

    private static final Logger log = LoggerFactory.getLogger(ContextService.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public ContextService(StringRedisTemplate redis, ObjectMapper mapper, ChatProperties props) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = Duration.ofMinutes(props.getContextTtlMinutes());
    }

    public ChatContext load(String sessionId) {
        try {
            String raw = redis.opsForValue().get(key(sessionId));
            return raw == null ? ChatContext.empty(sessionId)
                               : mapper.readValue(raw, ChatContext.class);
        } catch (Exception e) {
            log.warn("Đọc context lỗi ({}), tạo mới", e.getMessage());
            return ChatContext.empty(sessionId);
        }
    }

    public void save(ChatContext ctx) {
        try {
            ctx.touch();
            redis.opsForValue().set(key(ctx.getSessionId()),
                    mapper.writeValueAsString(ctx), ttl);
        } catch (Exception e) {
            log.warn("Lưu context lỗi: {}", e.getMessage());
        }
    }

    public void delete(String sessionId) {
        redis.delete(key(sessionId));
    }

    /**
     * Áp dụng MERGE / OVERRIDE / RESET (§4.2) rồi hợp nhất entity vào active_filters.
     * - RESET: search_room + từ khóa khởi tạo → xóa filter cũ.
     * - MERGE (refine_search) & OVERRIDE (search_room) đều dùng mergeNonNull:
     *   scalar được ghi đè, utilities được union — đúng ngữ nghĩa cả hai phép.
     * - Tiện ích bị PHỦ ĐỊNH trong câu ("không cần ban công", "có điều hòa thì tốt, không có cũng
     *   được") không được thêm, và bị gỡ nếu lượt trước đã có — NER vẫn gắn nhãn UTILITY cho cụm
     *   bị phủ định (như dữ liệu train), nên phải lọc ở đây.
     */
    public ChatContext apply(ChatContext ctx, NluResult nlu, String rawMsg) {
        if (nlu.getIntent() == Intent.SEARCH_ROOM && isRestart(rawMsg)) {
            ctx.getActiveFilters().clear();
            log.debug("RESET active_filters cho session {}", ctx.getSessionId());
        }
        List<String> negated = UtilityNormalizer.detectNegated(rawMsg);
        if (nlu.getEntities() != null && nlu.getEntities().getUtilities() != null) {
            nlu.getEntities().getUtilities().removeAll(negated);
        }
        // Chỉ câu TÌM/LỌC mới đổi bộ lọc. Câu hỏi về phòng ("có vskk k", "phòng 1 có máy giặt không",
        // "đặt lịch xem phòng 2") nhắc tiện ích/giá để HỎI, không phải để lọc — gộp vào sẽ âm thầm
        // thu hẹp lần tìm sau. Các handler đó đọc entity của lượt hiện tại, không đọc active_filters.
        boolean searchTurn = nlu.getIntent() == Intent.SEARCH_ROOM || nlu.getIntent() == Intent.REFINE_SEARCH;
        if (!searchTurn) {
            ctx.getActiveFilters().getUtilities().removeAll(negated);   // "thôi không cần ban công" vẫn gỡ
            ctx.setTurnCount(ctx.getTurnCount() + 1);
            return ctx;
        }
        ctx.getActiveFilters().mergeNonNull(nlu.getEntities());
        if (!negated.isEmpty()) {
            ctx.getActiveFilters().getUtilities().removeAll(negated);
            log.debug("Bỏ tiện ích bị phủ định {} cho session {}", negated, ctx.getSessionId());
        }
        ctx.setTurnCount(ctx.getTurnCount() + 1);
        return ctx;
    }

    private boolean isRestart(String m) {
        String s = m == null ? "" : m.toLowerCase();
        return s.contains("tìm phòng khác") || s.contains("bắt đầu lại")
            || s.contains("bỏ qua điều kiện") || s.contains("làm lại");
    }

    private String key(String id) { return "chat:session:" + id; }
}
