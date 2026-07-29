package com.webtro.modules.admin.repository;

import com.webtro.common.enums.AiModule;
import com.webtro.modules.admin.entity.AiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link AiConfig} (bảng nghiệp vụ, có xóa mềm).
 *
 * <p>Các truy vấn công khai đều lọc {@code deleted_at IS NULL} tường minh (canonical: không dùng
 * {@code @SQLRestriction}).
 */
@Repository
public interface AiConfigRepository extends JpaRepository<AiConfig, Long> {

    /** Tìm cấu hình theo module + khóa (chưa xóa). */
    Optional<AiConfig> findByModuleAndConfigKeyAndDeletedAtIsNull(AiModule module, String configKey);

    /** Danh sách cấu hình của một module (chưa xóa). */
    List<AiConfig> findByModuleAndDeletedAtIsNull(AiModule module);

    /** Danh sách cấu hình đang bật của một module (chưa xóa). */
    List<AiConfig> findByModuleAndIsEnabledTrueAndDeletedAtIsNull(AiModule module);

    /** Kiểm tra tồn tại theo module + khóa (chưa xóa). */
    boolean existsByModuleAndConfigKeyAndDeletedAtIsNull(AiModule module, String configKey);
}
