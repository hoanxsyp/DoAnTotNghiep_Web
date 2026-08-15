package com.webtro.modules.admin.repository;

import com.webtro.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository chỉ-đọc do module {@code admin} sở hữu để đếm người dùng theo vai trò cho Dashboard
 * (canonical §10.1). Trỏ tới entity {@link User} của module user nhưng đặt ở admin để không sửa
 * file module khác.
 *
 * <p>Từ V13 mỗi user có đúng một vai trò nên không cần {@code DISTINCT}: tổng số người dùng của
 * bốn vai trò đúng bằng tổng số người dùng còn sống.
 */
@Repository
public interface AdminUserRoleMetricsRepository extends JpaRepository<User, Long> {

    /** Số người dùng còn sống mang một vai trò cụ thể. */
    @Query("""
            SELECT COUNT(u.id) FROM User u
            WHERE u.role.code = :code
              AND u.deletedAt IS NULL
            """)
    long countUsersByRole(@Param("code") String code);
}
