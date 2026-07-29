package com.webtro.modules.admin.repository;

import com.webtro.modules.admin.entity.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link SystemConfig} (bảng nghiệp vụ, có xóa mềm).
 *
 * <p>Các truy vấn công khai đều lọc {@code deleted_at IS NULL} tường minh (canonical: không dùng
 * {@code @SQLRestriction}).
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    /** Tìm cấu hình theo khóa (chưa xóa). */
    Optional<SystemConfig> findByConfigKeyAndDeletedAtIsNull(String configKey);

    /** Kiểm tra tồn tại theo khóa (chưa xóa). */
    boolean existsByConfigKeyAndDeletedAtIsNull(String configKey);

    /** Danh sách cấu hình theo nhóm, sắp theo thứ tự hiển thị (chưa xóa). */
    List<SystemConfig> findByGroupNameAndDeletedAtIsNullOrderByDisplayOrderAsc(String groupName);

    /** Toàn bộ cấu hình chưa xóa, sắp theo nhóm rồi thứ tự hiển thị. */
    List<SystemConfig> findByDeletedAtIsNullOrderByGroupNameAscDisplayOrderAsc();
}
