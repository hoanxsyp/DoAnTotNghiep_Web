package com.webtro.util;

import java.util.regex.Pattern;

/**
 * Sinh slug thân thiện SEO từ tiêu đề tiếng Việt {@code [§11.8]}.
 * "Phòng trọ Quận 1 giá rẻ" → "phong-tro-quan-1-gia-re".
 *
 * <p>URL chi tiết tin có dạng {@code /tin/<slug>-<id>} (canonical mục 12) — slug chỉ để đẹp URL,
 * id mới là định danh, nên slug không cần unique.
 */
public final class SlugUtil {

    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern SPACES_DASHES = Pattern.compile("[\\s-]+");
    private static final int MAX_SLUG_LENGTH = 120;

    private SlugUtil() {
    }

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "tin";
        }
        String base = TextNormalizer.removeAccents(input);
        base = NON_SLUG.matcher(base).replaceAll("");
        base = SPACES_DASHES.matcher(base).replaceAll("-");
        base = base.replaceAll("^-+", "").replaceAll("-+$", "");
        if (base.length() > MAX_SLUG_LENGTH) {
            base = base.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }
        return base.isBlank() ? "tin" : base;
    }

    /** Ghép slug với id thành đuôi URL: "phong-tro-quan-1-123". */
    public static String toSlugWithId(String title, Long id) {
        return toSlug(title) + "-" + id;
    }

    /** Tách id ra khỏi đuôi URL dạng "...-123". Trả null nếu không có id hợp lệ. */
    public static Long extractId(String slugWithId) {
        if (slugWithId == null) {
            return null;
        }
        int lastDash = slugWithId.lastIndexOf('-');
        if (lastDash < 0 || lastDash == slugWithId.length() - 1) {
            return null;
        }
        try {
            return Long.parseLong(slugWithId.substring(lastDash + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
