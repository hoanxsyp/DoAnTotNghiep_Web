package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Quyền chi tiết (RBAC tầng 2) [§11.2] — bảng {@code permissions} (mục 2 của V1 baseline).
 *
 * <p>Bảng nghiệp vụ (audit + xóa mềm) → {@link AuditableEntity}. Cột {@code code} là chuỗi mã
 * quyền (ví dụ {@code LISTING_CREATE}); dùng hằng số {@code constant.PermissionCode}, không có
 * enum riêng nên map {@link String}.
 */
@Entity
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_permissions_code", columnNames = "code"),
        indexes = @Index(name = "idx_permissions_module", columnList = "module")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Permission extends AuditableEntity {

    /** Mã quyền (VARCHAR). */
    @Column(name = "code", nullable = false, length = 40)
    private String code;

    /** Tên hiển thị. */
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** Nhóm module của quyền (ví dụ LISTING, USER, PAYMENT). */
    @Column(name = "module", nullable = false, length = 30)
    private String module;

    /** Mô tả ngắn. */
    @Column(name = "description", length = 255)
    private String description;
}
