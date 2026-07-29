package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Bảng nối vai trò &lt;-&gt; quyền [§11.2] — bảng {@code role_permissions} (mục 3 của V1 baseline).
 *
 * <p>Cả {@link Role} và {@link Permission} đều cùng module {@code user} nên map
 * {@code @ManyToOne(LAZY)}. Ràng buộc duy nhất trên cặp (role_id, permission_id).
 */
@Entity
@Table(
        name = "role_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_role_permissions_role_permission",
                columnNames = {"role_id", "permission_id"}),
        indexes = @Index(name = "idx_role_permissions_permission_id", columnList = "permission_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RolePermission extends AuditableEntity {

    /** Vai trò. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Quyền được gán. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "permission_id", nullable = false)
    private Permission permission;
}
