package com.webtro.util;

import java.util.regex.Pattern;

/**
 * Chuẩn hóa và kiểm tra số điện thoại Việt Nam {@code [§3.1]}.
 *
 * <p>Chấp nhận các dạng: {@code 0901234567}, {@code +84901234567}, {@code 84901234567},
 * có thể chứa khoảng trắng, dấu chấm hoặc gạch ngang. Chuẩn hóa về dạng {@code 0xxxxxxxxx}.
 */
public final class PhoneUtil {

    // Đầu số di động VN hợp lệ: 03x, 05x, 07x, 08x, 09x — tổng 10 chữ số bắt đầu bằng 0.
    private static final Pattern VN_MOBILE = Pattern.compile("^0(3[2-9]|5[2689]|7[06-9]|8[1-9]|9[0-9])\\d{7}$");
    private static final Pattern NON_DIGIT_PLUS = Pattern.compile("[^0-9+]");

    private PhoneUtil() {
    }

    /**
     * Chuẩn hóa về dạng nội địa {@code 0xxxxxxxxx}. Trả về chuỗi rỗng nếu không nhận dạng được.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String cleaned = NON_DIGIT_PLUS.matcher(raw).replaceAll("");
        if (cleaned.startsWith("+84")) {
            cleaned = "0" + cleaned.substring(3);
        } else if (cleaned.startsWith("84") && cleaned.length() == 11) {
            cleaned = "0" + cleaned.substring(2);
        }
        return cleaned;
    }

    public static boolean isValid(String raw) {
        return VN_MOBILE.matcher(normalize(raw)).matches();
    }
}
