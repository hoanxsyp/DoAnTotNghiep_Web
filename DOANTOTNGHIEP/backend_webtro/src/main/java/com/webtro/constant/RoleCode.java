package com.webtro.constant;

import java.util.Set;

/**
 * Mã 4 vai trò người dùng — canonical mục 4.1.
 *
 * <p>Quan hệ User↔Role là NHIỀU-MỘT: mỗi người dùng mang đúng MỘT vai trò
 * ({@code users.role_id}, từ migration V13). "Người cho ở ghép" và "Người cần ở ghép" KHÔNG phải
 * role riêng — chúng là ngữ cảnh sử dụng (canonical mục 4.1).
 *
 * <p>Tiền tố {@code ROLE_} bắt buộc để khớp quy ước của Spring Security
 * ({@code hasRole('TENANT')} kiểm tra authority {@code ROLE_TENANT}).
 */
public final class RoleCode {

    public static final String TENANT = "ROLE_TENANT";
    public static final String LANDLORD = "ROLE_LANDLORD";
    public static final String MODERATOR = "ROLE_MODERATOR";
    public static final String ADMIN = "ROLE_ADMIN";

    /** Toàn bộ mã vai trò hợp lệ — dùng để kiểm tra dữ liệu vào trước khi truy DB. */
    public static final Set<String> ALL = Set.of(TENANT, LANDLORD, MODERATOR, ADMIN);

    private RoleCode() {
    }
}
