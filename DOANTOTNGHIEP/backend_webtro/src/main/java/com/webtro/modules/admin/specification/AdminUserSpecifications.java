package com.webtro.modules.admin.specification;

import com.webtro.common.enums.UserStatus;
import com.webtro.modules.user.entity.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification lọc động cho màn quản trị người dùng (canonical 4.13.1).
 *
 * <p>Tham số {@code roleCodes} là BỘ LỌC nhiều vai trò (admin muốn xem cùng lúc chủ trọ và người
 * thuê), không phải "một người nhiều vai trò": mỗi người dùng vẫn chỉ mang đúng một vai trò
 * ({@code users.role_id}), nên chỉ cần so khớp trực tiếp, không cần subquery.
 */
public final class AdminUserSpecifications {

    private AdminUserSpecifications() {
    }

    public static Specification<User> filter(String keyword, List<String> roleCodes,
                                             List<UserStatus> statuses, Boolean verified,
                                             Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();

            // Không bao giờ hiện tài khoản đã xóa mềm.
            ps.add(cb.isNull(root.get("deletedAt")));

            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim().toLowerCase() + "%";
                ps.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("phone")), like)));
            }

            if (statuses != null && !statuses.isEmpty()) {
                ps.add(root.get("status").in(statuses));
            } else {
                // Mặc định: tất cả trừ DELETED (canonical 4.13.1).
                ps.add(cb.notEqual(root.get("status"), UserStatus.DELETED));
            }

            if (verified != null) {
                ps.add(verified
                        ? cb.isNotNull(root.get("emailVerifiedAt"))
                        : cb.isNull(root.get("emailVerifiedAt")));
            }

            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }

            if (roleCodes != null && !roleCodes.isEmpty()) {
                ps.add(root.get("role").get("code").in(roleCodes));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
