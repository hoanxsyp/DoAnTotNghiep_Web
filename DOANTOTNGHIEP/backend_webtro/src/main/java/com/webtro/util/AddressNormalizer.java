package com.webtro.util;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Chuẩn hoá địa chỉ chi tiết và số phòng để dò phòng trùng (RU-6).
 *
 * <p>Quy tắc: bỏ dấu tiếng Việt (kể cả đ → d), chữ thường, thay mọi ký tự không phải chữ/số bằng
 * khoảng trắng, đổi chữ viết tắt không gây nhầm về từ đầy đủ ("ng" → ngõ, "ngh" → ngách,
 * "đ"/"đg" → đường), tách từ chỉ loại địa chỉ dính số ("ngõ62" → "ngõ 62"), bỏ các từ đệm không
 * mang nghĩa phân biệt ("số", "phòng"…). Ví dụ {@code "Số 12, Ngõ 5 Cầu Giấy"},
 * {@code "12 ng 5 cau giay"} và {@code "12 ngõ5 Cầu Giấy"} cho cùng một khoá; thứ tự giữ nguyên nên
 * {@code "ngõ 12 số 62"} vẫn khác {@code "số 12 ngõ 62"}.
 *
 * <p>Đây là so khớp chuỗi nên không chặn tuyệt đối: viết khác hẳn (đổi tên đường, thêm tên toà
 * nhà) vẫn ra khoá khác. Hạn chế này đã ghi trong tài liệu thiết kế.
 */
public final class AddressNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /** Từ đệm trong địa chỉ chi tiết. */
    private static final Set<String> ADDRESS_FILLERS = Set.of("so", "sn", "dia", "chi");

    /**
     * Chữ viết tắt (đã bỏ dấu) → từ đầy đủ. Chỉ gồm chữ tắt KHÔNG gây nhầm; cố ý không đổi "p"
     * (phố/phường/phòng), "h", "k". Giữ nguyên thứ tự và các từ ngõ/ngách/đường nên "ngõ 12 số 62"
     * vẫn khác "số 12 ngõ 62".
     */
    private static final Map<String, String> ADDRESS_ABBREVIATIONS = Map.of(
            "ng", "ngo",
            "ngh", "ngach",
            "d", "duong",
            "dg", "duong");

    /** Từ chỉ loại địa chỉ được tách khỏi số dính liền ("ng62" → "ng 62"); tên đường như "D2" không bị tách. */
    private static final Pattern TYPE_WORD_GLUED_TO_NUMBER =
            Pattern.compile("\\b(ngo|ng|ngach|ngh|hem|kiet|so|sn)(?=[0-9])");

    /** Từ đệm trong số phòng: "Phòng 101", "P.101", "Căn hộ 101" đều thành "101". */
    private static final Set<String> UNIT_FILLERS = Set.of("phong", "p", "room", "can", "ho", "so", "unit");

    private AddressNormalizer() {
    }

    /** Khoá so sánh của một phòng trong cùng phường: địa chỉ chi tiết + số phòng. */
    public static String unitKey(String addressDetail, String unitLabel) {
        return normalize(addressDetail, ADDRESS_FILLERS, ADDRESS_ABBREVIATIONS, true)
                + "|" + normalize(unitLabel, UNIT_FILLERS, Map.of(), false);
    }

    static String normalize(String raw, Set<String> fillers, Map<String, String> abbreviations,
                            boolean splitTypeWords) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.replace('đ', 'd').replace('Đ', 'D');
        s = MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
        s = NON_ALNUM.matcher(s.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (splitTypeWords) {
            s = TYPE_WORD_GLUED_TO_NUMBER.matcher(s).replaceAll("$1 ");
        }
        if (s.isEmpty()) {
            return "";
        }
        return Arrays.stream(s.split("\\s+"))
                .map(token -> abbreviations.getOrDefault(token, token))
                .filter(token -> !fillers.contains(token))
                .collect(Collectors.joining(" "));
    }
}
