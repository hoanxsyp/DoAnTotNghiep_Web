package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Vai trò người dùng (RBAC tầng 1) [§1.1][§6.1] — bảng {@code roles} (mục 1 của V1 baseline).
 *
 * <p>Quan hệ với {@link User} là MỘT-NHIỀU: một vai trò có nhiều người dùng, nhưng mỗi người dùng
 * chỉ mang đúng một vai trò ({@code users.role_id}, từ V13). Phân quyền dựa trực tiếp trên mã role,
 * không còn bảng permission hay cơ chế cấp quyền riêng cho từng người dùng.
 *
 * <p>Bảng nghiệp vụ (có audit + xóa mềm) nên kế thừa {@link AuditableEntity}.
 * Cột {@code code} lưu VARCHAR với ràng buộc CHECK ở DB
 * ({@code ROLE_TENANT}/{@code ROLE_LANDLORD}/{@code ROLE_MODERATOR}/{@code ROLE_ADMIN});
 * chưa có enum riêng trong {@code common.enums} nên map dạng {@link String}
 * (dùng hằng số {@code constant.RoleCode}).
 */
@Entity
@Table(
        name = "roles",
        uniqueConstraints = @UniqueConstraint(name = "uk_roles_code", columnNames = "code"),
        indexes = @Index(name = "idx_roles_is_system", columnList = "is_system")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Role extends AuditableEntity {

    /** Mã vai trò (VARCHAR, CHECK ở DB). */
    @Column(name = "code", nullable = false, length = 30)
    private String code;

    /** Tên hiển thị. */
    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** Mô tả ngắn. */
    @Column(name = "description", length = 255)
    private String description;

    /** Vai trò do hệ thống định nghĩa (không cho xóa/sửa mã). */
    @Builder.Default
    @Column(name = "is_system", nullable = false)
    private Boolean isSystem = true;

    /** Thứ tự hiển thị. */
    @Builder.Default
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder = 0;
}
