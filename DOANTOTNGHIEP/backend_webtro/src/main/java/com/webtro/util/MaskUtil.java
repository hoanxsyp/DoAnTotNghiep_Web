package com.webtro.util;

/**
 * Che thông tin nhạy cảm {@code [§3.8]}: khách chưa đăng nhập chỉ thấy một phần số điện thoại.
 */
public final class MaskUtil {

    private MaskUtil() {
    }

    /**
     * Che giữa số điện thoại: {@code 0901234567} → {@code 0901***567}.
     * Giữ 4 ký tự đầu và 3 ký tự cuối, phần giữa thay bằng dấu sao.
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 8) {
            return "***";
        }
        int keepHead = 4;
        int keepTail = 3;
        int maskLen = phone.length() - keepHead - keepTail;
        if (maskLen <= 0) {
            return phone.substring(0, Math.min(3, phone.length())) + "***";
        }
        return phone.substring(0, keepHead)
                + "*".repeat(maskLen)
                + phone.substring(phone.length() - keepTail);
    }

    /**
     * Che email: {@code nguyenvanan@gmail.com} → {@code ng****an@gmail.com}.
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "****" + local.charAt(local.length() - 1) + domain;
    }
}
