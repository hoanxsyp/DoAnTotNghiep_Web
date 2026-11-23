package com.webtro.util;

import org.springframework.web.util.HtmlUtils;

import java.util.regex.Pattern;

/**
 * Chống XSS ở tầng đầu vào {@code [§11.1]}: loại bỏ mọi thẻ HTML/script khỏi văn bản người dùng
 * nhập (mô tả tin, bình luận, tin nhắn...).
 *
 * <p>Chính sách: <b>allowlist rỗng</b> — không cho phép bất kỳ HTML nào trong nội dung do người
 * dùng nhập. Frontend hiển thị bằng text thuần (React tự escape), tuyệt đối không dùng
 * {@code dangerouslySetInnerHTML} (canonical luật frontend F5). Đây là phòng thủ nhiều lớp: dù
 * frontend có sơ hở thì dữ liệu lưu vào DB cũng đã sạch.
 */
public final class HtmlSanitizer {

    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SCRIPT_BLOCK =
            Pattern.compile("(?is)<script.*?>.*?</script>");
    private static final Pattern EVENT_ATTR =
            Pattern.compile("(?i)on\\w+\\s*=\\s*\"[^\"]*\"");
    private static final Pattern JS_URI =
            Pattern.compile("(?i)javascript:");

    private HtmlSanitizer() {
    }

    /**
     * Loại bỏ toàn bộ HTML và trả về text thuần đã cắt khoảng trắng thừa.
     * Dùng cho nội dung sẽ lưu DB rồi hiển thị lại.
     */
    public static String stripAllHtml(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String cleaned = SCRIPT_BLOCK.matcher(input).replaceAll("");
        cleaned = TAG.matcher(cleaned).replaceAll("");
        cleaned = EVENT_ATTR.matcher(cleaned).replaceAll("");
        cleaned = JS_URI.matcher(cleaned).replaceAll("");
        // Giải mã entity để "&lt;script&gt;" nhập vào không lách được lớp lọc, rồi cắt khoảng trắng.
        cleaned = HtmlUtils.htmlUnescape(cleaned);
        // Sau khi unescape có thể lộ ra thẻ mới → lọc thêm một lượt.
        cleaned = TAG.matcher(cleaned).replaceAll("");
        return TextNormalizer.collapseWhitespace(cleaned);
    }

    /**
     * Giống {@link #stripAllHtml} nhưng GIỮ xuống dòng — dùng cho văn bản nhiều đoạn như mô tả tin
     * (frontend tách đoạn theo dấu xuống dòng). Trong mỗi dòng vẫn gộp khoảng trắng thừa; nhiều
     * dòng trống liền nhau gộp thành một; bỏ dòng trống ở đầu và cuối.
     */
    public static String stripAllHtmlKeepLines(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String cleaned = SCRIPT_BLOCK.matcher(input).replaceAll("");
        cleaned = TAG.matcher(cleaned).replaceAll("");
        cleaned = EVENT_ATTR.matcher(cleaned).replaceAll("");
        cleaned = JS_URI.matcher(cleaned).replaceAll("");
        cleaned = HtmlUtils.htmlUnescape(cleaned);
        cleaned = TAG.matcher(cleaned).replaceAll("");
        StringBuilder out = new StringBuilder();
        boolean previousBlank = true;
        for (String line : cleaned.split("\r?\n", -1)) {
            String normalized = TextNormalizer.collapseWhitespace(line);
            if (normalized.isEmpty()) {
                // Dòng trống đầu tiên sau một đoạn: giữ lại đúng một dòng trống ngăn đoạn.
                if (!previousBlank) {
                    out.append('\n');
                }
                previousBlank = true;
            } else {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(normalized);
                previousBlank = false;
            }
        }
        return out.toString().strip();
    }

    /**
     * Kiểm tra chuỗi có chứa dấu hiệu HTML/script không (dùng cho validator từ chối sớm).
     */
    public static boolean containsHtml(String input) {
        if (input == null) {
            return false;
        }
        return TAG.matcher(input).find() || JS_URI.matcher(input).find();
    }
}
