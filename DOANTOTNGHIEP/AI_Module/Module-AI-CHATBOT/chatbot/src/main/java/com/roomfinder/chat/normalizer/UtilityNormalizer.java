package com.roomfinder.chat.normalizer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chuẩn hóa tiện ích về key chuẩn (§3.3): từ đồng nghĩa + bỏ dấu.
 * Ví dụ: "điều hoà"/"điều hòa"/"máy lạnh" → air_conditioner; "nóng lạnh" → water_heater.
 *
 * <p>Bộ key bám theo danh mục 29 tiện ích của web (bảng {@code amenities}) cộng vài thuộc tính
 * có cột riêng trên tin đăng (chỗ để xe, thú cưng, vệ sinh khép kín, giờ giấc, nội thất) — cách
 * dịch key sang tham số tìm kiếm nằm ở nguồn phòng ({@code BackendRoomSource}). Tiện ích gần
 * trường/chợ/bến xe KHÔNG có ở đây: đó là vị trí (POI), không phải tiện ích phòng.
 *
 * <p>Cố ý KHÔNG map (đo trên span UTILITY thật của ml/data): "chung chủ" (ngược nghĩa "không
 * chung chủ"), "đồ cơ bản"/"nội thất" (lọc BASIC sẽ loại oan phòng FULL), "vsc" (vệ sinh chung),
 * "máy sấy" (web không có tiện ích này).
 */
public final class UtilityNormalizer {

    /**
     * @param synonyms     biến thể (đã bỏ dấu, lowercase) — khớp theo RANH GIỚI TỪ ở bất kỳ đâu:
     *                     "ac" từng khớp substring trong "khác"/"các" khi chưa có ranh giới từ.
     * @param exactAliases viết tắt chỉ nhận khi CẢ span đúng bằng nó ("nl", "tl", "vskk") — quét
     *                     trong cả câu thì quá dễ khớp nhầm.
     */
    private record Utility(String key, String label, List<String> synonyms, List<String> exactAliases) {}

    private static final List<Utility> UTILITIES = List.of(
        u("air_conditioner", "điều hòa", List.of("dieu hoa", "dieu hoa nhiet do", "may lanh", "ac", "air conditioner"), "dh"),
        u("water_heater", "nóng lạnh", List.of("nong lanh", "binh nong lanh", "may nong lanh", "may nuoc nong",
                "binh nuoc nong", "nuoc nong"), "nl"),
        u("washing_machine", "máy giặt", List.of("may giat", "giat"), "mg"),
        u("fridge", "tủ lạnh", List.of("tu lanh"), "tl"),
        // "mạng" chỉ nhận khi đứng riêng: bỏ dấu thành "mang" → khớp nhầm "được mang xe điện".
        u("wifi", "wifi", List.of("wifi", "wi-fi", "internet"), "mang"),
        u("elevator", "thang máy", List.of("thang may")),
        u("balcony", "ban công", List.of("ban cong")),
        u("window", "cửa sổ", List.of("cua so")),
        u("mezzanine", "gác xép", List.of("gac xep", "gac lung")),
        u("private_kitchen", "bếp riêng", List.of("bep rieng", "khu bep rieng", "cho nau an rieng")),
        // "bếp"/"chỗ nấu ăn" chung chung: chỉ nhận cả span (có chỗ nấu ăn ≈ có kệ bếp trên web).
        u("kitchen_cabinet", "kệ bếp", List.of("ke bep", "ban bep", "tu bep"), "bep", "cho nau an"),
        // KHÔNG có "tủ đồ": bỏ dấu trùng "tự do" ("giờ giấc tự do").
        u("wardrobe", "tủ quần áo", List.of("tu quan ao", "tu dung do")),
        u("bed", "giường", List.of("giuong")),
        u("desk", "bàn làm việc", List.of("ban lam viec", "ban hoc")),
        u("tv", "tivi", List.of("tivi", "tv")),
        u("security_guard", "bảo vệ", List.of("bao ve", "an ninh")),
        u("cctv", "camera an ninh", List.of("camera")),
        u("fingerprint_lock", "khóa vân tay", List.of("khoa van tay")),
        u("fire_alarm", "PCCC", List.of("pccc", "bao chay", "phong chay", "thoat hiem", "loi thoat hiem")),
        u("private_entrance", "không chung chủ", List.of("khong chung chu", "rieng chu", "loi di rieng")),
        u("laundry_area", "sân phơi", List.of("san phoi", "cho phoi do")),
        u("electric_meter", "điện nước giá dân", List.of("dien nuoc gia dan", "dien gia dan", "gia dan", "dien dan")),
        // "xe điện"/"sạc xe điện": cần chỗ để xe điện — web chưa có tiện ích sạc, gần nhất là chỗ để xe.
        u("parking", "chỗ để xe", List.of("cho de xe", "de xe", "bai xe", "gui xe", "parking", "cho do xe",
                "cho gui xe", "de xe may", "xe dien", "sac xe dien")),
        u("car_parking", "chỗ để ô tô", List.of("o to", "oto", "ngo o to", "de o to", "do o to")),
        u("pet", "nuôi thú cưng", List.of("nuoi meo", "nuoi cho", "nuoi pet", "thu cung", "nuoi thu")),
        u("private_bathroom", "vệ sinh khép kín", List.of("ve sinh khep kin", "khep kin", "ve sinh rieng",
                // "vskk" không trùng chữ nào khác → nhận cả khi quét câu (NLU rule-based dự phòng);
                // "wc" thì không ("wc chung" = vệ sinh chung) → chỉ nhận khi đứng nguyên span.
                "wc rieng", "toilet rieng", "nha ve sinh rieng", "vskk"), "wc"),
        u("free_hours", "giờ giấc tự do", List.of("gio giac tu do", "gio tu do", "tu do gio giac", "khong chung gio")),
        u("full_furniture", "full nội thất", List.of("full do", "full noi that", "du do", "day du noi that",
                "noi that day du", "full do dac"))
    );

    private static final Map<String, Utility> BY_KEY = UTILITIES.stream()
            .collect(Collectors.toMap(Utility::key, x -> x, (a, b) -> a, LinkedHashMap::new));

    /** Biến thể → regex theo ranh giới từ, xếp biến thể DÀI trước (khớp "ve sinh khep kin" trước "khep kin"). */
    private static final Map<String, List<Pattern>> PATTERNS = UTILITIES.stream()
            .collect(Collectors.toMap(Utility::key, x -> x.synonyms().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .map(v -> Pattern.compile("\\b" + Pattern.quote(v) + "\\b"))
                    .toList(), (a, b) -> a, LinkedHashMap::new));

    // --- Phủ định: "không cần X", "bỏ X", "có X thì tốt, không có cũng được" ---------------

    /** Cụm phủ định đứng TRƯỚC tiện ích (cho phép xen tối đa 2 từ: "không cần cái ban công"). */
    private static final String NEG_BEFORE =
            "\\b(?:khong|ko|k|chang)\\s+(?:can|bat buoc|yeu cau|nhat thiet(?:\\s+phai)?(?:\\s+co)?)"
            // "bỏ" bỏ dấu thành "bo" — trùng "bổ sung" (= THÊM, nghĩa ngược lại) → loại riêng.
            + "|\\bkhoi(?:\\s+can)?|\\bthoi\\s+bo|\\bbo(?!\\s+sung\\b)(?:\\s+tieu chi)?";

    /** "không có (cũng / thì cũng)" — tiện ích chỉ là ưu tiên, không phải điều kiện. */
    private static final String OPTIONAL = "\\b(?:khong|ko|k)\\s+co\\s+(?:\\S+\\s+){0,3}?(?:cung|thi cung)\\b";

    private static final Map<String, List<Pattern>> NEGATION_PATTERNS = UTILITIES.stream()
            .collect(Collectors.toMap(Utility::key, x -> {
                String alt = x.synonyms().stream()
                        .sorted(Comparator.comparingInt(String::length).reversed())
                        .map(Pattern::quote)
                        .collect(Collectors.joining("|"));
                return List.of(
                        // không cần / khỏi / bỏ (tiêu chí) [≤2 từ] X
                        Pattern.compile("(?:" + NEG_BEFORE + ")\\s+(?:\\S+\\s+){0,2}?(?:" + alt + ")\\b"),
                        // không có X cũng được
                        Pattern.compile("\\b(?:khong|ko|k)\\s+co\\s+(?:" + alt + ")\\s+(?:cung|thi cung)\\b"),
                        // X thì tốt, không có cũng ko sao (cùng câu, cho phép qua 1 dấu phẩy)
                        Pattern.compile("\\b(?:" + alt + ")\\b[^.!?]{0,30}?" + OPTIONAL),
                        // tủ giường tự sắm được — người dùng tự lo, không phải điều kiện lọc
                        Pattern.compile("\\b(?:" + alt + ")\\b\\s+(?:\\S+\\s+){0,1}?tu\\s+(?:sam|mua|lo|trang bi)\\b"),
                        // phòng trống hay full đồ đều được — có hay không cũng được
                        Pattern.compile("\\b(?:" + alt + ")\\b[^.!?,]{0,15}?\\bdeu (?:duoc|dc|ok|oke)\\b"));
            }, (a, b) -> a, LinkedHashMap::new));

    private UtilityNormalizer() {}

    private static Utility u(String key, String label, List<String> synonyms, String... exactAliases) {
        return new Utility(key, label, synonyms, List.of(exactAliases));
    }

    private static boolean matches(String text, List<Pattern> variants) {
        for (Pattern p : variants) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    /**
     * Chuẩn hóa 1 span → key hoặc null nếu không nhận diện được. Span khớp nhiều tiện ích thì lấy
     * biến thể khớp DÀI NHẤT ("chỗ để ô tô": "o to" thắng "de"…), không phụ thuộc thứ tự khai báo.
     */
    public static String normalizeOne(String span) {
        if (span == null) return null;
        String raw = span.trim().toLowerCase();
        if (BY_KEY.containsKey(raw)) return raw;            // đã là key chuẩn
        String s = stripAccent(raw);
        for (Utility x : UTILITIES) {
            if (x.exactAliases().contains(s)) return x.key();
        }
        String best = null;
        int bestLen = 0;
        for (var e : PATTERNS.entrySet()) {
            for (Pattern p : e.getValue()) {
                var m = p.matcher(s);
                if (m.find() && m.end() - m.start() > bestLen) {
                    best = e.getKey();
                    bestLen = m.end() - m.start();
                }
            }
        }
        return best;
    }

    /** Quét cả câu, trả về mọi tiện ích xuất hiện (dùng cho NLU rule-based). */
    public static List<String> detectAll(String text) {
        if (text == null) return new ArrayList<>();
        String s = stripAccent(text.toLowerCase());
        Set<String> out = new LinkedHashSet<>();
        for (var e : PATTERNS.entrySet()) {
            if (matches(s, e.getValue())) out.add(e.getKey());
        }
        return new ArrayList<>(out);
    }

    /**
     * Tiện ích người dùng nói là KHÔNG cần / chỉ là ưu tiên trong câu này — phải bỏ khỏi bộ lọc,
     * không được thêm vào. "không cần ban công đâu, có cửa sổ là được" → [balcony].
     */
    public static List<String> detectNegated(String text) {
        if (text == null) return new ArrayList<>();
        String s = stripAccent(text.toLowerCase());
        Set<String> out = new LinkedHashSet<>();
        for (var e : NEGATION_PATTERNS.entrySet()) {
            if (matches(s, e.getValue())) out.add(e.getKey());
        }
        return new ArrayList<>(out);
    }

    /** Chuẩn hóa cả danh sách, loại trùng & phần tử không nhận diện được. */
    public static List<String> normalizeList(List<String> raw) {
        if (raw == null) return new ArrayList<>();
        Set<String> out = new LinkedHashSet<>();
        for (String r : raw) {
            String key = normalizeOne(r);
            if (key != null) out.add(key);
        }
        return new ArrayList<>(out);
    }

    /** Nhãn tiếng Việt của key để hiển thị ("có điều hòa, nóng lạnh"). */
    public static String label(String key) {
        Utility x = BY_KEY.get(key);
        return x != null ? x.label() : key;
    }

    private static String stripAccent(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD);
        return n.replaceAll("\\p{M}", "").replace("đ", "d");
    }
}
