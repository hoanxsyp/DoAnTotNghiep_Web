package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Permission}. Điều kiện {@code deleted_at IS NULL} viết tường minh.
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /** Tìm quyền còn hiệu lực theo mã (ví dụ {@code LISTING_CREATE}). */
    Optional<Permission> findByCodeAndDeletedAtIsNull(String code);

    /** Kiểm tra tồn tại quyền theo mã (còn hiệu lực). */
    boolean existsByCodeAndDeletedAtIsNull(String code);

    /** Danh sách quyền còn hiệu lực thuộc một module. */
    List<Permission> findByModuleAndDeletedAtIsNull(String module);

    /** Danh sách toàn bộ quyền còn hiệu lực. */
    List<Permission> findByDeletedAtIsNull();

    /**
     * Mã các quyền của một user, suy ra qua vai trò
     * (user_roles → roles → role_permissions → permissions). Dùng nạp authorities.
     */
    @Query("""
            SELECT DISTINCT p.code
            FROM UserRole ur
            JOIN ur.role r
            JOIN RolePermission rp ON rp.role = r
            JOIN rp.permission p
            WHERE ur.user.id = :userId
              AND r.deletedAt IS NULL
              AND p.deletedAt IS NULL
              AND ur.deletedAt IS NULL
              AND rp.deletedAt IS NULL
            """)
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}
