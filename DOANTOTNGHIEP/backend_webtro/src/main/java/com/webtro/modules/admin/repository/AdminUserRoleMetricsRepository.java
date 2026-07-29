package com.webtro.modules.admin.repository;

import com.webtro.modules.user.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository chỉ-đọc do module {@code admin} sở hữu để đếm người dùng theo vai trò cho Dashboard
 * (canonical §10.1). Trỏ tới entity {@link UserRole} của module user nhưng đặt ở admin để không sửa
 * file module khác.
 */
@Repository
public interface AdminUserRoleMetricsRepository extends JpaRepository<UserRole, Long> {

    /** Số người dùng còn sống có một vai trò cụ thể (đếm distinct để tránh trùng). */
    @Query("""
            SELECT COUNT(DISTINCT ur.user.id) FROM UserRole ur
            WHERE ur.role.code = :code
              AND ur.deletedAt IS NULL
              AND ur.user.deletedAt IS NULL
            """)
    long countUsersByRole(@Param("code") String code);
}
