package com.webtro.modules.admin.specification;

import com.webtro.common.enums.UserStatus;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.entity.UserRole;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification lọc động cho màn quản trị người dùng (canonical 4.13.1).
 *
 * <p>Lọc vai trò dùng subquery trên {@link UserRole} (quan hệ nhiều-nhiều) — không nạp cả cụm
 * entity của module user vào truy vấn chính.
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
                Subquery<Long> sub = query.subquery(Long.class);
                var ur = sub.from(UserRole.class);
                sub.select(ur.get("user").get("id"))
                        .where(cb.and(
                                ur.get("role").get("code").in(roleCodes),
                                cb.isNull(ur.get("deletedAt"))));
                ps.add(root.get("id").in(sub));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
