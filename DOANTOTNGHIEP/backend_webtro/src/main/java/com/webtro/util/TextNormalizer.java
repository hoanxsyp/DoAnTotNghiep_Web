package com.webtro.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Chuẩn hóa văn bản tiếng Việt: bỏ dấu, hạ chữ thường, gom khoảng trắng.
 *
 * <p>Dùng cho: sinh slug, tạo {@code search_name} (khớp "Quận 1" khi người dùng gõ "quan 1"),
 * và chuẩn hóa đầu vào cho module AI.
 */
public final class TextNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    /**
     * Bỏ dấu tiếng Việt và hạ chữ thường. "Quận Gò Vấp" → "quan go vap".
     * Xử lý riêng chữ 'đ/Đ' vì nó không tách được bằng {@link Normalizer}.
     */
    public static String removeAccents(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String lower = input.toLowerCase(Locale.forLanguageTag("vi"));
        String normalized = Normalizer.normalize(lower, Normalizer.Form.NFD);
        String noMarks = COMBINING_MARKS.matcher(normalized).replaceAll("");
        // 'đ' sau NFD vẫn là 'đ' (không có dấu tổ hợp) nên phải thay tay.
        noMarks = noMarks.replace('đ', 'd').replace('Đ', 'd');
        return MULTI_SPACE.matcher(noMarks).replaceAll(" ").trim();
    }

    /** Gom nhiều khoảng trắng thành một, cắt đầu/cuối. */
    public static String collapseWhitespace(String input) {
        if (input == null) {
            return "";
        }
        return MULTI_SPACE.matcher(input).replaceAll(" ").trim();
    }
}
