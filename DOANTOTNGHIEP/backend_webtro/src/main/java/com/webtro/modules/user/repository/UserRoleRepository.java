package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.Role;
import com.webtro.modules.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository cho bảng nối {@link UserRole}. Điều kiện {@code deleted_at IS NULL} viết tường minh.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /** Các bản ghi gán vai trò còn hiệu lực của một người dùng. */
    List<UserRole> findByUser_IdAndDeletedAtIsNull(Long userId);

    /** Kiểm tra người dùng đã có một vai trò cụ thể chưa (còn hiệu lực). */
    boolean existsByUser_IdAndRole_IdAndDeletedAtIsNull(Long userId, Long roleId);

    /** Danh sách vai trò còn hiệu lực của một người dùng — dùng nạp {@code roles[]} vào JWT [§8]. */
    @Query("""
            SELECT ur.role FROM UserRole ur
            WHERE ur.user.id = :userId
              AND ur.deletedAt IS NULL
              AND ur.role.deletedAt IS NULL
            """)
    List<Role> findRolesByUserId(@Param("userId") Long userId);
}
