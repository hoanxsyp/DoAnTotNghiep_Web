package com.webtro.constant;

/**
 * Mã 4 vai trò người dùng — canonical mục 4.1.
 *
 * <p>Quan hệ User↔Role là nhiều-nhiều {@code [§1.2][§6.1]}. "Người cho ở ghép" và "Người cần ở
 * ghép" KHÔNG phải role riêng — chúng là ngữ cảnh sử dụng (canonical mục 4.1).
 *
 * <p>Tiền tố {@code ROLE_} bắt buộc để khớp quy ước của Spring Security
 * ({@code hasRole('TENANT')} kiểm tra authority {@code ROLE_TENANT}).
 */
public final class RoleCode {

    public static final String TENANT = "ROLE_TENANT";
    public static final String LANDLORD = "ROLE_LANDLORD";
    public static final String MODERATOR = "ROLE_MODERATOR";
    public static final String ADMIN = "ROLE_ADMIN";

    private RoleCode() {
    }
}
