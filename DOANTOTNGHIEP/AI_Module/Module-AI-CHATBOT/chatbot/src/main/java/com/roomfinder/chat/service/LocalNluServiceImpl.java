package com.roomfinder.chat.service;

import com.roomfinder.chat.config.NluProperties;
import com.roomfinder.chat.domain.Intent;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.model.NluResult;
import com.roomfinder.chat.normalizer.PriceNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * NLU bằng PhoBERT qua nlu-service (FastAPI) — GĐ2, §11 bước 2.4.
 * Class này KHÔNG phụ thuộc base model: hợp đồng JSON của nlu-service là span thô
 * + intent, nên thay model bên Python không phải sửa gì ở tầng Java.
 * @Primary thay cho LlmNluServiceImpl; chuỗi fallback 2 tầng:
 * nlu-service chết/timeout (NLU_TIMEOUT_MS, mặc định 800ms) → LLM → rule-based. Hệ thống không bao giờ
 * sập vì NLU. Tắt bằng roomfinder.nlu.enabled=false (NLU_ENABLED).
 *
 * nlu-service trả entity là SPAN THÔ ({label,text,start,end,score}) — class này
 * quy span về slot của {@link Filters}: giá qua PriceNormalizer ngay tại đây
 * (cần Long để so sánh), các span chữ (location/datetime/utility) giữ nguyên
 * cho {@link com.roomfinder.chat.normalizer.EntityNormalizer} xử lý như mọi
 * NluService khác — không nhân đôi luật chuẩn hóa.
 */
@Service
@Primary
public class LocalNluServiceImpl implements NluService {

    private static final Logger log = LoggerFactory.getLogger(LocalNluServiceImpl.class);

    private final RestClient http;
    private final NluService fallback;
    private final NluProperties props;

    public LocalNluServiceImpl(NluProperties props,
                                 @Qualifier("llmNluServiceImpl") NluService fallback) {
        this.props = props;
        this.fallback = fallback;
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(props.getTimeoutMs());
        f.setReadTimeout(props.getTimeoutMs());
        this.http = RestClient.builder().baseUrl(props.getUrl()).requestFactory(f).build();
    }

    /**
     * Khởi động nóng: lần gọi ĐẦU TIÊN từ JVM mới (nạp class Jackson/RestClient, mở kết nối)
     * vượt timeout 300ms → câu chat đầu tiên sau mỗi lần khởi động luôn rơi xuống LLM/rule-based.
     * Gọi thử vài lần lúc app sẵn sàng; lỗi (nlu-service chưa lên) thì bỏ qua.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        if (!props.isEnabled()) return;
        for (int i = 0; i < 3; i++) {
            try {
                http.post().uri("/nlu").header("Content-Type", "application/json")
                        .body(Map.of("text", "tìm phòng trọ")).retrieve().body(NluServiceResponse.class);
                log.info("Khởi động nóng nlu-service xong (lần {})", i + 1);
                return;
            } catch (Exception e) {
                log.debug("Khởi động nóng nlu-service lần {} lỗi: {}", i + 1, e.getMessage());
            }
        }
        log.warn("Không khởi động nóng được nlu-service — câu đầu tiên có thể rơi xuống fallback");
    }

    @Override
    public NluResult parse(String message) {
        if (!props.isEnabled()) return fallback.parse(message);
        try {
            NluServiceResponse r = http.post()
                    .uri("/nlu")
                    .header("Content-Type", "application/json")
                    .body(Map.of("text", message == null ? "" : message))
                    .retrieve()
                    .body(NluServiceResponse.class);
            if (r == null || r.intent() == null) {
                throw new IllegalStateException("nlu-service trả response rỗng");
            }
            return toNluResult(r, message == null ? "" : message);
        } catch (Exception e) {
            // Ghi NGUYÊN NHÂN GỐC: timeout khi đang đọc header bị Spring báo thành "content type
            // [application/octet-stream]" — dễ hiểu lầm là nlu-service trả sai định dạng.
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) root = root.getCause();
            log.warn("NLU self-host lỗi ({}: {}), fallback LLM", root.getClass().getSimpleName(), root.getMessage());
            return fallback.parse(message);
        }
    }

    // --- Hợp đồng JSON với nlu-service (xem nlu-service/README.md) --------

    record NluServiceResponse(String intent, double confidence, List<Span> entities) {}

    record Span(String label, String text, int start, int end, double score) {}

    // --- Span → Filters ---------------------------------------------------

    /** Từ đứng TRƯỚC giá → giá tối thiểu ("từ 4 triệu", "trên 3tr"). */
    private static final Pattern MIN_BEFORE = Pattern.compile(
            "(?:^|\\s)(?:tu|tren|hon|toi thieu|it nhat|>=?)\\s*$");
    /** "rẻ hơn 3 củ", "thấp hơn 3tr", "nhỏ hơn" → tối ĐA dù có chữ "hơn" (xét trước MIN_BEFORE). */
    private static final Pattern MAX_BEFORE = Pattern.compile(
            "(?:^|\\s)(?:re|thap|nho|it|kem|duoi)\\s+hon\\s*$");
    /** Từ đứng SAU giá: "4tr trở lên" → tối thiểu; "3tr đổ lại/trở xuống" → tối đa (thắng). */
    private static final Pattern MIN_AFTER = Pattern.compile("^\\s*(?:tro len|do len)\\b");
    private static final Pattern MAX_AFTER = Pattern.compile("^\\s*(?:do lai|do ve|tro xuong|do xuong|tro lai)\\b");

    /**
     * NER gán mọi mức giá là PRICE_MAX (như dữ liệu train) kể cả "từ 4 triệu" — xét từ đứng quanh
     * span để biết là giá TỐI THIỂU. Span là khoảng ("2tr5-3tr") thì giữ PRICE_MAX.
     */
    static boolean isMinPrice(String message, int start, int end, String span) {
        if (span.matches(".*\\d\\s*(?:-|–|~|đến|den)\\s*\\d.*")) return false;
        String before = strip(message.substring(Math.max(0, start - 12), Math.max(0, Math.min(start, message.length()))));
        String after = strip(message.substring(Math.min(end, message.length()), Math.min(message.length(), end + 12)));
        if (MAX_AFTER.matcher(after).find() || MAX_BEFORE.matcher(before).find()) return false;
        return MIN_BEFORE.matcher(before).find() || MIN_AFTER.matcher(after).find();
    }

    private NluResult toNluResult(NluServiceResponse r, String message) {
        NluResult out = new NluResult();
        out.setIntent(Intent.fromCode(r.intent()));
        out.setConfidence(r.confidence());

        Filters f = new Filters();
        for (Span s : r.entities() == null ? List.<Span>of() : r.entities()) {
            String text = s.text();
            if (text == null || text.isBlank()) continue;
            switch (s.label()) {
                case "PRICE_MAX" -> {
                    Long price = PriceNormalizer.normalize(text);
                    if (isMinPrice(message, s.start(), s.end(), text)) f.setPriceMin(price);
                    else f.setPriceMax(price);
                }
                case "PRICE_MIN" -> f.setPriceMin(PriceNormalizer.normalize(text));
                case "LOCATION"  -> f.setLocation(text);
                case "POI"       -> f.setPoi(text);
                case "RADIUS"    -> f.setRadiusM(parseRadiusM(text));
                case "AREA_MIN"  -> f.setAreaMin(parseFirstNumber(text));
                case "UTILITY"   -> f.getUtilities().add(text);
                case "ROOM_TYPE" -> {
                    f.setRoomType(mapRoomType(text));
                    // "phòng khép kín": NER gán cả cụm là ROOM_TYPE (như dữ liệu train), nhưng
                    // "khép kín" là yêu cầu vệ sinh khép kín — giữ lại thành tiện ích.
                    if (strip(text).contains("khep kin")) f.getUtilities().add("khép kín");
                }
                case "DATETIME"  -> f.setDatetime(text);
                case "ROOM_REF"  -> {
                    Integer ord = parseRoomRef(text);
                    if (ord != null && !f.getRoomRefs().contains(ord)) f.getRoomRefs().add(ord);
                }
                default -> log.debug("Bỏ qua entity label lạ: {}", s.label());
            }
        }
        // NER đôi khi bỏ sót "phòng 1" khi câu còn mốc giờ ("đặt lịch xem phòng 1 chiều mai lúc 3h")
        // → câu hỏi về 1 phòng cụ thể mà không có phòng: dò bằng regex của rule-based.
        if (f.getRoomRefs().isEmpty() && (out.getIntent() == Intent.ROOM_DETAIL
                || out.getIntent() == Intent.COMPARE_ROOMS || out.getIntent() == Intent.BOOK_APPOINTMENT)) {
            f.setRoomRefs(new java.util.ArrayList<>(RuleBasedNluService.detectRoomRefs(message.toLowerCase())));
        }
        out.setEntities(f);
        return out;
    }

    private static final Pattern RADIUS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(km|m)?");

    private Integer parseRadiusM(String span) {
        Matcher m = RADIUS.matcher(span.toLowerCase());
        if (!m.find()) return null;
        double v = Double.parseDouble(m.group(1).replace(',', '.'));
        return (int) Math.round("km".equals(m.group(2)) ? v * 1000 : v);
    }

    private Double parseFirstNumber(String span) {
        Matcher m = Pattern.compile("(\\d+(?:[.,]\\d+)?)").matcher(span);
        return m.find() ? Double.parseDouble(m.group(1).replace(',', '.')) : null;
    }

    /**
     * "phòng số 2"/"phong thu hai"/"cái đầu tiên" → ordinal 1-based.
     * Chỉ cần 1..5 (top-K = 5); số bằng chữ vượt 5 coi như không phân giải được.
     */
    private Integer parseRoomRef(String span) {
        String s = strip(span);
        Matcher m = Pattern.compile("(\\d{1,2})").matcher(s);
        if (m.find()) return Integer.parseInt(m.group(1));
        if (s.contains("dau") || s.contains("nhat")) return 1;
        if (s.contains("hai")) return 2;
        if (s.contains("ba")) return 3;
        if (s.contains("bon") || s.contains("tu")) return 4;
        if (s.contains("nam")) return 5;
        return null;
    }

    /**
     * Span → mã room_type trong DB. Chỉ map khi người dùng nói rõ LOẠI HÌNH;
     * "khép kín" là thuộc tính (trọ lẫn CCMN đều có) — map thành PHONG_TRO sẽ
     * lọc oan chung cư mini, nên trả null (không filter).
     */
    private String mapRoomType(String span) {
        String s = strip(span);
        if (s.contains("nguyen can")) return "NHA_NGUYEN_CAN";
        if (s.contains("chung cu") || s.contains("ccmn")) return "CHUNG_CU_MINI";
        if (s.contains("tro")) return "PHONG_TRO";
        return null;
    }

    private static String strip(String s) {
        String n = Normalizer.normalize(s.toLowerCase().trim(), Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
