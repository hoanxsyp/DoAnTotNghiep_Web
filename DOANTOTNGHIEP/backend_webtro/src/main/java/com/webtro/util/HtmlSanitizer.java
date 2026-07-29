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
     * Kiểm tra chuỗi có chứa dấu hiệu HTML/script không (dùng cho validator từ chối sớm).
     */
    public static boolean containsHtml(String input) {
        if (input == null) {
            return false;
        }
        return TAG.matcher(input).find() || JS_URI.matcher(input).find();
    }
}
