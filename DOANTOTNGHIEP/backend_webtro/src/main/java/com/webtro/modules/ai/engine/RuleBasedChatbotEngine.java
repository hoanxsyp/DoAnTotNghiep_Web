package com.webtro.modules.ai.engine;

import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.ChatbotIntent;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.GenderRequirement;
import com.webtro.util.TextNormalizer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chatbot theo luật (canonical mục 10.3, §9.3): phân loại 8 intent + slot filling 11 trường.
 *
 * <p>Ràng buộc cứng thi hành ở tầng service (chỉ trả tin public, không bịa, kèm disclaimer). Engine
 * chỉ hiểu câu hỏi. Nhận diện nhạy cảm được ưu tiên cao nhất để từ chối lịch sự.
 */
@Component
public class RuleBasedChatbotEngine implements ChatbotEngine {

    private static final String ENGINE_VERSION = "chatbot-rule-1.0";

    /** Từ điển thuật ngữ (không dấu → định nghĩa) phục vụ intent GLOSSARY §3.15. */
    private static final Map<String, String[]> GLOSSARY = new LinkedHashMap<>();
    private static final List<String> SENSITIVE_TERMS = List.of(
            "sex", "khieu dam", "ma tuy", "cave", "mai dam", "vu khi", "sung", "danh nhau",
            "chinh tri", "phan dong", "lo de", "ca do", "hack");

    static {
        GLOSSARY.put("chung cu mini", new String[]{"chung cư mini",
                "\"Chung cư mini\" là loại căn hộ nhỏ nằm trong một tòa nhà do cá nhân hoặc hộ gia đình "
                        + "xây để cho thuê, thường 20–40 m², có gác lửng hoặc phòng ngủ riêng, khu bếp và "
                        + "vệ sinh khép kín; một số tòa có thang máy và bảo vệ."});
        GLOSSARY.put("coc", new String[]{"cọc",
                "\"Cọc\" (tiền đặt cọc) là khoản tiền người thuê đặt trước để giữ phòng và bảo đảm hợp "
                        + "đồng, thường bằng 1 tháng tiền phòng; được hoàn lại khi trả phòng đúng thỏa thuận."});
        GLOSSARY.put("gio giac tu do", new String[]{"giờ giấc tự do",
                "\"Giờ giấc tự do\" nghĩa là bạn được ra vào bất kỳ lúc nào, không bị khóa cửa theo giờ "
                        + "giới nghiêm."});
        GLOSSARY.put("o ghep", new String[]{"ở ghép",
                "\"Ở ghép\" là hình thức nhiều người cùng thuê chung một phòng/căn để chia tiền, thường "
                        + "có yêu cầu về giới tính và số người ở."});
        GLOSSARY.put("nha nguyen can", new String[]{"nhà nguyên căn",
                "\"Nhà nguyên căn\" là thuê trọn một căn nhà riêng, không ở chung với chủ hay hộ khác."});
    }

    // ------------------------- Regex slot -------------------------
    private static final Pattern PRICE_UNDER = Pattern.compile("(?:duoi|toi da|tối đa|<=?|khong qua)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|cu|nghin|ngan|k)?");
    private static final Pattern PRICE_OVER = Pattern.compile("(?:tren|từ|tu|>=?|it nhat)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|cu|nghin|ngan|k)?");
    private static final Pattern PRICE_RANGE = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:-|den|đến|toi|tới)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|cu|nghin|ngan|k)?");
    private static final Pattern PRICE_ABOUT = Pattern.compile("(?:khoang|tam|~)\\s*(\\d+(?:[.,]\\d+)?)\\s*(trieu|tr|cu)?\\s*(ruoi)?");
    private static final Pattern AREA_OVER = Pattern.compile("(?:tren|>=?|rong hon|từ|tu)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m2|met|met vuong)");
    private static final Pattern AREA_UNDER = Pattern.compile("(?:duoi|<=?|nho hon)\\s*(\\d+(?:[.,]\\d+)?)\\s*(?:m2|met|met vuong)");
    private static final Pattern AREA_PLAIN = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(?:m2|met vuong)");
    private static final Pattern OCCUPANTS = Pattern.compile("(?:o|ở|cho)\\s*(\\d+)\\s*nguoi");
    private static final Pattern LOCATION = Pattern.compile("(?:quan|quận|huyen|huyện|phuong|phường|tp|thanh pho|o|ở|gan|gần|tai|tại)\\s+([\\p{L}\\d]+(?:\\s+[\\p{L}\\d]+)?)");

    @Override
    public String version() {
        return ENGINE_VERSION;
    }

    @Override
    public ChatbotResult interpret(String message, ChatSlots prior, boolean resetContext) {
        String raw = message == null ? "" : message.trim();
        String noAccent = TextNormalizer.collapseWhitespace(TextNormalizer.removeAccents(raw.toLowerCase()));

        ChatSlots base = (resetContext || prior == null) ? ChatSlots.empty() : prior;

        // 1) Nhạy cảm — ưu tiên tuyệt đối (§9.3).
        if (containsAny(noAccent, SENSITIVE_TERMS)) {
            return new ChatbotResult(ChatbotIntent.SENSITIVE, bd("0.93"), base, List.of(),
                    "Xin lỗi, mình không thể hỗ trợ nội dung này. Mình là trợ lý tìm phòng trọ — "
                            + "bạn cần tìm phòng ở khu vực nào ạ?", null, true);
        }

        Map<ChatbotIntent, Double> scores = scoreIntents(noAccent);
        ChatbotIntent intent = argMax(scores);
        double confidence = confidenceOf(scores, intent);

        // 2) GLOSSARY.
        if (intent == ChatbotIntent.GLOSSARY) {
            String[] g = lookupGlossary(noAccent);
            if (g != null) {
                return new ChatbotResult(ChatbotIntent.GLOSSARY, bd(String.valueOf(confidence)), base,
                        List.of(), g[1], g[0], false);
            }
            // Không tra được → coi như UNKNOWN nhẹ nhàng.
            intent = ChatbotIntent.UNKNOWN;
        }

        // 3) Câu trả lời sẵn cho intent không phải tìm phòng.
        String canned = cannedReply(intent);
        if (canned != null) {
            return new ChatbotResult(intent, bd(String.valueOf(confidence)), base, List.of(),
                    canned, null, false);
        }

        // 4) FIND_ROOM: trích slot, gộp với slot cũ, tính slot thiếu.
        ChatSlots merged = extractSlots(raw, noAccent, base);
        List<String> missing = missingImportant(merged);
        return new ChatbotResult(ChatbotIntent.FIND_ROOM, bd(String.valueOf(Math.max(confidence, 0.6))),
                merged, missing, null, null, false);
    }

    // ------------------------------------------------------------------
    //  Intent
    // ------------------------------------------------------------------

    private Map<ChatbotIntent, Double> scoreIntents(String t) {
        Map<ChatbotIntent, Double> s = new LinkedHashMap<>();
        s.put(ChatbotIntent.FIND_ROOM, keyword(t, 1.0,
                "tim phong", "thue phong", "phong tro", "can thue", "muon thue", "o ghep",
                "chung cu mini", "nha nguyen can", "can ho", "tim tro", "co phong nao")
                + (PRICE_UNDER.matcher(t).find() || PRICE_RANGE.matcher(t).find() ? 0.6 : 0)
                + (LOCATION.matcher(t).find() ? 0.4 : 0));
        s.put(ChatbotIntent.HOW_TO_POST, keyword(t, 1.0,
                "dang tin", "lam sao de dang", "post tin", "cho thue", "huong dan dang",
                "dang bai", "toi la chu tro"));
        s.put(ChatbotIntent.GLOSSARY, keyword(t, 1.0, "la gi", "nghia la", "co nghia")
                + (lookupGlossary(t) != null ? 0.8 : 0));
        s.put(ChatbotIntent.FAQ, keyword(t, 1.0,
                "phi", "thanh toan", "bao cao", "quy dinh", "chinh sach", "lien he", "ho tro",
                "tai khoan", "mat khau", "xac thuc"));
        s.put(ChatbotIntent.GREETING, keyword(t, 1.0, "xin chao", "chao", "hello", "hi ", "alo", "hey"));
        s.put(ChatbotIntent.OUT_OF_SCOPE, keyword(t, 1.0,
                "hop dong", "phap ly", "luat", "kien", "tranh chap", "thoi tiet", "bong da", "vay von"));
        double max = s.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
        if (max <= 0) {
            s.put(ChatbotIntent.UNKNOWN, 0.5);
        }
        return s;
    }

    private double keyword(String text, double perHit, String... keys) {
        double score = 0;
        for (String k : keys) {
            if (text.contains(k)) {
                score += perHit;
            }
        }
        return score;
    }

    private ChatbotIntent argMax(Map<ChatbotIntent, Double> scores) {
        ChatbotIntent best = ChatbotIntent.UNKNOWN;
        double bestVal = 0;
        for (Map.Entry<ChatbotIntent, Double> e : scores.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    private double confidenceOf(Map<ChatbotIntent, Double> scores, ChatbotIntent intent) {
        double total = scores.values().stream().mapToDouble(Double::doubleValue).sum();
        double val = scores.getOrDefault(intent, 0.0);
        if (total <= 0) {
            return 0.5;
        }
        double ratio = val / total;
        return Math.max(0.5, Math.min(0.98, 0.5 + 0.5 * ratio + Math.min(0.3, val * 0.1)));
    }

    private String cannedReply(ChatbotIntent intent) {
        return switch (intent) {
            case GREETING -> "Chào bạn! Mình là trợ lý tìm phòng trọ. Bạn muốn tìm phòng ở khu vực nào, "
                    + "khoảng giá bao nhiêu ạ?";
            case HOW_TO_POST -> "Để đăng tin cho thuê: đăng nhập bằng tài khoản chủ trọ, vào mục "
                    + "\"Quản lý → Tin đăng → Tạo tin\", điền thông tin phòng, tải tối thiểu 1 ảnh rồi bấm "
                    + "\"Gửi duyệt\". Tin sẽ hiển thị sau khi được kiểm duyệt. Mình chỉ hướng dẫn, không "
                    + "đăng tin thay bạn nhé.";
            case FAQ -> "Mình có thể giúp giải thích cách dùng website: tìm phòng, lưu tin, liên hệ chủ "
                    + "trọ, báo cáo tin sai. Bạn muốn hỏi về việc gì ạ?";
            case OUT_OF_SCOPE -> "Mình chỉ hỗ trợ tìm phòng trọ, giải thích thuật ngữ và hướng dẫn sử "
                    + "dụng website thôi ạ. Về các vấn đề pháp lý hợp đồng thuê nhà, bạn nên tham khảo ý "
                    + "kiến luật sư hoặc cơ quan có thẩm quyền. Mình có thể giúp bạn tìm phòng không?";
            case UNKNOWN -> "Xin lỗi, mình chưa hiểu ý bạn. Bạn thử nói rõ khu vực và khoảng giá muốn "
                    + "tìm, ví dụ \"tìm phòng trọ Quận 1 dưới 4 triệu\" nhé.";
            default -> null; // FIND_ROOM / GLOSSARY / SENSITIVE xử lý riêng
        };
    }

    private String[] lookupGlossary(String noAccent) {
        for (Map.Entry<String, String[]> e : GLOSSARY.entrySet()) {
            if (noAccent.contains(e.getKey())) {
                return e.getValue();
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    //  Slot filling
    // ------------------------------------------------------------------

    private ChatSlots extractSlots(String raw, String t, ChatSlots prior) {
        BigDecimal priceFrom = prior.priceFrom();
        BigDecimal priceTo = prior.priceTo();
        BigDecimal areaFrom = prior.areaFrom();
        BigDecimal areaTo = prior.areaTo();
        Integer maxOccupants = prior.maxOccupants();
        GenderRequirement gender = prior.genderRequirement();
        FurnitureStatus furniture = prior.furnitureStatus();
        CurfewType curfew = prior.curfewType();
        Boolean pet = prior.petAllowed();
        Boolean parking = prior.parkingAvailable();
        CategoryCode category = prior.categoryCode();
        String location = prior.locationKeyword();

        // Giá — thứ tự: range → under → over → about.
        Matcher mr = PRICE_RANGE.matcher(t);
        if (mr.find()) {
            priceFrom = money(mr.group(1), mr.group(3));
            priceTo = money(mr.group(2), mr.group(3));
        } else {
            Matcher mu = PRICE_UNDER.matcher(t);
            if (mu.find()) {
                priceTo = money(mu.group(1), mu.group(2));
            }
            Matcher mo = PRICE_OVER.matcher(t);
            if (mo.find()) {
                priceFrom = money(mo.group(1), mo.group(2));
            }
            Matcher ma = PRICE_ABOUT.matcher(t);
            if (ma.find() && priceTo == null && priceFrom == null) {
                BigDecimal about = money(ma.group(1), ma.group(2));
                if (ma.group(3) != null) { // "rưỡi"
                    about = about.add(new BigDecimal("500000"));
                }
                priceFrom = about.multiply(new BigDecimal("0.8")).setScale(0, java.math.RoundingMode.HALF_UP);
                priceTo = about.multiply(new BigDecimal("1.2")).setScale(0, java.math.RoundingMode.HALF_UP);
            }
        }

        // Diện tích.
        Matcher aOver = AREA_OVER.matcher(t);
        if (aOver.find()) {
            areaFrom = new BigDecimal(aOver.group(1).replace(',', '.'));
        }
        Matcher aUnder = AREA_UNDER.matcher(t);
        if (aUnder.find()) {
            areaTo = new BigDecimal(aUnder.group(1).replace(',', '.'));
        }
        if (areaFrom == null && areaTo == null) {
            Matcher aPlain = AREA_PLAIN.matcher(t);
            if (aPlain.find()) {
                areaFrom = new BigDecimal(aPlain.group(1).replace(',', '.'));
            }
        }

        // Số người ở.
        if (t.contains("o mot minh") || t.contains("o 1 minh") || t.contains("mot minh")) {
            maxOccupants = 1;
        } else {
            Matcher mocc = OCCUPANTS.matcher(t);
            if (mocc.find()) {
                maxOccupants = Integer.parseInt(mocc.group(1));
            }
        }

        // Giới tính ở ghép.
        if (t.contains("chi nu") || t.contains("nu ") || t.contains("gioi tinh nu")) {
            gender = GenderRequirement.FEMALE_ONLY;
        } else if (t.contains("chi nam") || t.contains("gioi tinh nam")) {
            gender = GenderRequirement.MALE_ONLY;
        }

        // Nội thất.
        if (t.contains("full noi that") || t.contains("day du noi that") || t.contains("noi that day du")) {
            furniture = FurnitureStatus.FULL;
        } else if (t.contains("noi that co ban") || t.contains("co ban")) {
            furniture = FurnitureStatus.BASIC;
        } else if (t.contains("khong noi that")) {
            furniture = FurnitureStatus.NONE;
        } else if (t.contains("co noi that")) {
            furniture = FurnitureStatus.BASIC;
        }

        // Giờ giấc.
        if (t.contains("gio giac tu do") || t.contains("khong gioi nghiem") || t.contains("tu do gio giac")) {
            curfew = CurfewType.FREE;
        } else if (t.contains("gioi nghiem")) {
            curfew = CurfewType.CURFEW;
        }

        // Thú cưng.
        if (t.contains("nuoi meo") || t.contains("nuoi cho") || t.contains("thu cung") || t.contains("nuoi pet")) {
            pet = Boolean.TRUE;
        }

        // Chỗ để xe.
        if (t.contains("cho de xe") || t.contains("de xe may") || t.contains("gui xe") || t.contains("bai xe")) {
            parking = Boolean.TRUE;
        }

        // Loại nhà/phòng.
        CategoryCode c = detectCategory(t);
        if (c != null) {
            category = c;
        }

        // Khu vực (keyword).
        Matcher ml = LOCATION.matcher(t);
        if (ml.find()) {
            String loc = ml.group(0).trim();
            if (loc.length() >= 3) {
                location = loc;
            }
        }

        return new ChatSlots(priceFrom, priceTo, areaFrom, areaTo, maxOccupants, gender,
                furniture, curfew, pet, parking, category, location);
    }

    private CategoryCode detectCategory(String t) {
        if (t.contains("chung cu mini") || t.contains("ccmini")) {
            return CategoryCode.MINI_APARTMENT;
        }
        if (t.contains("nha nguyen can") || t.contains("nguyen can")) {
            return CategoryCode.WHOLE_HOUSE;
        }
        if (t.contains("o ghep") || t.contains("tim nguoi o ghep")) {
            return CategoryCode.ROOMMATE;
        }
        if (t.contains("homestay")) {
            return CategoryCode.HOMESTAY;
        }
        if (t.contains("mat bang") || t.contains("mat tien kinh doanh")) {
            return CategoryCode.SMALL_PREMISES;
        }
        if (t.contains("can ho")) {
            return CategoryCode.APARTMENT;
        }
        if (t.contains("phong tro") || t.contains("nha tro")) {
            return CategoryCode.BOARDING_HOUSE;
        }
        return null;
    }

    private List<String> missingImportant(ChatSlots s) {
        List<String> missing = new ArrayList<>();
        if (s.locationKeyword() == null) {
            missing.add("location");
        }
        if (s.priceTo() == null && s.priceFrom() == null) {
            missing.add("priceTo");
        }
        if (s.maxOccupants() == null) {
            missing.add("maxOccupants");
        }
        return missing;
    }

    // ------------------------------------------------------------------
    //  Tiện ích
    // ------------------------------------------------------------------

    /** Chuyển số + đơn vị thành VND. Không đơn vị / "triệu"/"tr"/"củ" → triệu; "nghìn"/"k" → nghìn. */
    private BigDecimal money(String num, String unit) {
        BigDecimal n = new BigDecimal(num.replace(',', '.'));
        String u = unit == null ? "" : unit;
        if (u.startsWith("nghin") || u.startsWith("ngan") || u.equals("k")) {
            return n.multiply(new BigDecimal("1000")).setScale(0, java.math.RoundingMode.HALF_UP);
        }
        // Mặc định: triệu (đơn vị phổ biến nhất khi nói giá thuê).
        return n.multiply(new BigDecimal("1000000")).setScale(0, java.math.RoundingMode.HALF_UP);
    }

    private boolean containsAny(String text, List<String> keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    private BigDecimal bd(String v) {
        return new BigDecimal(v).setScale(3, java.math.RoundingMode.HALF_UP);
    }
}
