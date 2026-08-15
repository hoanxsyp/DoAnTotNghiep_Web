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

    /**
     * Mã vai trò DUY NHẤT của một user (dùng khi nạp authorities cho JWT/UserDetails).
     *
     * <p>Trả {@link Optional} chứ không phải danh sách: từ V13 mỗi user có đúng một vai trò
     * ({@code users.role_id}). {@code empty} chỉ xảy ra khi user không tồn tại / đã xóa mềm.
     */
    @Query("""
            SELECT r.code FROM User u JOIN u.role r
            WHERE u.id = :userId AND u.deletedAt IS NULL AND r.deletedAt IS NULL
            """)
    Optional<String> findRoleCodeByUserId(@Param("userId") Long userId);
}
