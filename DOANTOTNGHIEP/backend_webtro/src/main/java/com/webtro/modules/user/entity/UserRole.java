package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
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

import java.time.Instant;

/**
 * Bảng nối người dùng &lt;-&gt; vai trò (quan hệ nhiều-nhiều) [§1.1][§6.1] —
 * bảng {@code user_roles} (mục 5 của V1 baseline).
 *
 * <p>{@link User} và {@link Role} cùng module → map {@code @ManyToOne(LAZY)}.
 * {@code assigned_by} là actor (admin gán vai trò) nên giữ {@link Long}.
 */
@Entity
@Table(
        name = "user_roles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_roles_user_role",
                columnNames = {"user_id", "role_id"}),
        indexes = @Index(name = "idx_user_roles_role_id", columnList = "role_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserRole extends AuditableEntity {

    /** Người dùng. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Vai trò được gán. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Thời điểm gán vai trò. */
    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    /** Người gán vai trò (users.id, actor → giữ Long). */
    @Column(name = "assigned_by")
    private Long assignedBy;
}
