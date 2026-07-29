package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.Permission;
import com.webtro.modules.user.entity.RolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Repository cho bảng nối {@link RolePermission}. Điều kiện {@code deleted_at IS NULL} viết tường minh.
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

    /** Các bản ghi gán quyền còn hiệu lực của một vai trò. */
    List<RolePermission> findByRole_IdAndDeletedAtIsNull(Long roleId);

    /** Kiểm tra một vai trò đã có một quyền cụ thể chưa (còn hiệu lực). */
    boolean existsByRole_IdAndPermission_IdAndDeletedAtIsNull(Long roleId, Long permissionId);

    /**
     * Tập quyền (phân biệt) của một nhóm vai trò — dùng để nạp {@code permissions[]} vào JWT [§8].
     */
    @Query("""
            SELECT DISTINCT rp.permission FROM RolePermission rp
            WHERE rp.role.id IN :roleIds
              AND rp.deletedAt IS NULL
              AND rp.permission.deletedAt IS NULL
            """)
    List<Permission> findPermissionsByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
