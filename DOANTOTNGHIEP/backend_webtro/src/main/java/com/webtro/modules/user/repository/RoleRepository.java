package com.webtro.modules.user.repository;

import com.webtro.modules.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Role}. Điều kiện {@code deleted_at IS NULL} viết tường minh (không dùng
 * {@code @SQLRestriction}).
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /** Tìm vai trò còn hiệu lực theo mã (ví dụ {@code ROLE_TENANT}). */
    Optional<Role> findByCodeAndDeletedAtIsNull(String code);

    /** Kiểm tra tồn tại vai trò theo mã (còn hiệu lực). */
    boolean existsByCodeAndDeletedAtIsNull(String code);

    /** Danh sách vai trò còn hiệu lực, sắp theo thứ tự hiển thị. */
    List<Role> findByDeletedAtIsNullOrderByDisplayOrderAsc();

    /** Mã các vai trò của một user (dùng khi nạp authorities cho JWT/UserDetails). */
    @Query("""
            SELECT r.code FROM UserRole ur JOIN ur.role r
            WHERE ur.user.id = :userId AND r.deletedAt IS NULL AND ur.deletedAt IS NULL
            """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
