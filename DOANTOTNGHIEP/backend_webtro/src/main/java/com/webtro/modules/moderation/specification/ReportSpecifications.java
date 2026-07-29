package com.webtro.modules.moderation.specification;

import com.webtro.common.enums.ReportReason;
import com.webtro.common.enums.ReportSeverity;
import com.webtro.common.enums.ReportStatus;
import com.webtro.common.enums.ReportTargetType;
import com.webtro.modules.moderation.entity.Report;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;

/**
 * Bộ {@link Specification} lọc động cho {@link Report} — dùng ở màn hình quản trị, đếm ngưỡng và
 * chống trùng. Tất cả đều ngầm loại bản ghi đã xóa mềm khi kết hợp với {@link #notDeleted()}.
 */
public final class ReportSpecifications {

    private ReportSpecifications() {
    }

    /** Chưa bị xóa mềm. */
    public static Specification<Report> notDeleted() {
        return (root, q, cb) -> cb.isNull(root.get("deletedAt"));
    }

    public static Specification<Report> reporter(Long reporterId) {
        return (root, q, cb) -> cb.equal(root.get("reporterId"), reporterId);
    }

    public static Specification<Report> statusesIn(List<ReportStatus> statuses) {
        return (root, q, cb) -> root.get("status").in(statuses);
    }

    public static Specification<Report> statusEq(ReportStatus status) {
        return (root, q, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Report> targetTypesIn(List<ReportTargetType> types) {
        return (root, q, cb) -> root.get("targetType").in(types);
    }

    public static Specification<Report> targetTypeEq(ReportTargetType type) {
        return (root, q, cb) -> cb.equal(root.get("targetType"), type);
    }

    public static Specification<Report> targetIdEq(Long targetId) {
        return (root, q, cb) -> cb.equal(root.get("targetId"), targetId);
    }

    public static Specification<Report> severitiesIn(List<ReportSeverity> severities) {
        return (root, q, cb) -> root.get("severity").in(severities);
    }

    public static Specification<Report> severityEq(ReportSeverity severity) {
        return (root, q, cb) -> cb.equal(root.get("severity"), severity);
    }

    public static Specification<Report> reasonEq(ReportReason reason) {
        return (root, q, cb) -> cb.equal(root.get("reason"), reason);
    }

    public static Specification<Report> isValidTrue() {
        return (root, q, cb) -> cb.isTrue(root.get("isValid"));
    }

    public static Specification<Report> createdFrom(Instant from) {
        return (root, q, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<Report> createdTo(Instant to) {
        return (root, q, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    /** Đúng một đối tượng bị báo cáo. */
    public static Specification<Report> target(ReportTargetType type, Long id) {
        return targetTypeEq(type).and(targetIdEq(id));
    }
}
