package com.webtro.modules.admin.specification;

import com.webtro.common.enums.AuditAction;
import com.webtro.modules.admin.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Specification lọc động cho màn tra cứu nhật ký kiểm toán của Admin (canonical 4.20.3).
 */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    /**
     * Ghép các điều kiện lọc thành một Specification. Điều kiện null/rỗng bị bỏ qua.
     */
    public static Specification<AuditLog> filter(List<AuditAction> actions, Long actorId,
                                                 String targetType, Long targetId,
                                                 Instant from, Instant to) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (actions != null && !actions.isEmpty()) {
                ps.add(root.get("action").in(actions));
            }
            if (actorId != null) {
                ps.add(cb.equal(root.get("actorId"), actorId));
            }
            if (targetType != null && !targetType.isBlank()) {
                ps.add(cb.equal(root.get("targetType"), targetType));
            }
            if (targetId != null) {
                ps.add(cb.equal(root.get("targetId"), targetId));
            }
            if (from != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
