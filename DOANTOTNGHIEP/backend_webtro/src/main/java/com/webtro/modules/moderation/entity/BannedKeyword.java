package com.webtro.modules.moderation.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Từ khóa cấm dùng để lọc nội dung tin đăng/bình luận.
 *
 * <p>Bảng nghiệp vụ (có xóa mềm). Khóa chuẩn hóa {@code normalized_keyword} là duy nhất để tránh
 * trùng lặp khi so khớp không phân biệt dấu/hoa-thường.
 */
@Entity
@Table(
        name = "banned_keywords",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_banned_keywords_normalized_keyword", columnNames = {"normalized_keyword"})
        },
        indexes = {
                @Index(name = "idx_banned_keywords_active_scope", columnList = "is_active, applies_to"),
                @Index(name = "idx_banned_keywords_severity", columnList = "severity")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class BannedKeyword extends AuditableEntity {

    /** Từ khóa gốc (giữ nguyên như admin nhập). */
    @Column(name = "keyword", nullable = false, length = 100)
    private String keyword;

    /** Từ khóa đã chuẩn hóa (bỏ dấu, thường hóa) dùng để so khớp; duy nhất. */
    @Column(name = "normalized_keyword", nullable = false, length = 100)
    private String normalizedKeyword;

    /** Mức độ nghiêm trọng. */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 10)
    private BannedKeywordSeverity severity;

    /** Phạm vi áp dụng (tin đăng / bình luận / cả hai). */
    @Enumerated(EnumType.STRING)
    @Column(name = "applies_to", nullable = false, length = 10)
    private BannedKeywordScope appliesTo;

    /** Từ khóa là biểu thức chính quy (regex) hay chuỗi thường. */
    @Column(name = "is_regex", nullable = false)
    private Boolean isRegex;

    /** Nhóm/phân loại từ khóa (chuỗi tự do, nullable). */
    @Column(name = "category", length = 30)
    private String category;

    /** Ghi chú của admin. */
    @Column(name = "note", length = 255)
    private String note;

    /** Đang bật hay tắt. */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Số lần từ khóa này khớp (INT UNSIGNED). */
    @Column(name = "hit_count", nullable = false)
    private Integer hitCount;
}
