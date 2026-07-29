package com.webtro.modules.admin.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.AiModule;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cấu hình 4 module AI (SENTIMENT/RECOMMENDATION/CHATBOT/PRICE) [§9]: mỗi bản ghi là một khóa cấu
 * hình JSON của một module, có version để theo dõi thay đổi.
 *
 * <p>Bảng nghiệp vụ (có audit + xóa mềm) -> kế thừa {@link AuditableEntity}. Duy nhất theo
 * (module, config_key).
 */
@Entity
@Table(
        name = "ai_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_ai_configs_module_config_key", columnNames = {"module", "config_key"})
        },
        indexes = {
                @Index(name = "idx_ai_configs_module_enabled", columnList = "module, is_enabled")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class AiConfig extends AuditableEntity {

    /** Module AI sở hữu cấu hình. */
    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 15)
    private AiModule module;

    /** Khóa cấu hình (duy nhất trong phạm vi module). */
    @Column(name = "config_key", nullable = false, length = 60)
    private String configKey;

    /** Giá trị cấu hình, lưu JSON thô. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_value", nullable = false, columnDefinition = "json")
    private String configValue;

    /** Tên schema/kiểu để validate config_value. */
    @Column(name = "value_schema", nullable = false, length = 30)
    private String valueSchema;

    /** Mô tả cấu hình. */
    @Column(name = "description", length = 500)
    private String description;

    /** Bật/tắt cấu hình. */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    /** Phiên bản cấu hình (tăng dần mỗi lần đổi, >= 1). */
    @Column(name = "version", nullable = false)
    private Integer version;
}
