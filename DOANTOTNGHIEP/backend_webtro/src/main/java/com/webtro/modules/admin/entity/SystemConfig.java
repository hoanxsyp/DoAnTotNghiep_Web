package com.webtro.modules.admin.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ConfigValueType;
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

import java.math.BigDecimal;

/**
 * Cấu hình hệ thống (105 config key) [§10]: mỗi bản ghi là một khóa cấu hình có kiểu, nhóm, nhãn
 * và ràng buộc min/max để {@code SystemConfigService} ép kiểu và kiểm tra hợp lệ.
 *
 * <p>Bảng nghiệp vụ (có audit + xóa mềm) -> kế thừa {@link AuditableEntity}.
 */
@Entity
@Table(
        name = "system_configs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_system_configs_config_key", columnNames = {"config_key"})
        },
        indexes = {
                @Index(name = "idx_system_configs_group_name_display_order", columnList = "group_name, display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class SystemConfig extends AuditableEntity {

    /** Khóa cấu hình (duy nhất). */
    @Column(name = "config_key", nullable = false, length = 80)
    private String configKey;

    /** Giá trị hiện tại (chuỗi thô, ép kiểu theo value_type). */
    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    /** Giá trị mặc định (dùng khi reset). */
    @Column(name = "default_value", nullable = false, columnDefinition = "TEXT")
    private String defaultValue;

    /** Kiểu giá trị (STRING/INT/DECIMAL/BOOLEAN/JSON). */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 10)
    private ConfigValueType valueType;

    /** Nhóm cấu hình (để gom nhóm hiển thị). */
    @Column(name = "group_name", nullable = false, length = 30)
    private String groupName;

    /** Nhãn hiển thị. */
    @Column(name = "label", nullable = false, length = 150)
    private String label;

    /** Mô tả cấu hình. */
    @Column(name = "description", length = 500)
    private String description;

    /** Giá trị nhỏ nhất cho phép (áp dụng cho INT/DECIMAL). */
    @Column(name = "min_value", precision = 15, scale = 4)
    private BigDecimal minValue;

    /** Giá trị lớn nhất cho phép (áp dụng cho INT/DECIMAL). */
    @Column(name = "max_value", precision = 15, scale = 4)
    private BigDecimal maxValue;

    /** Cho phép admin chỉnh sửa qua UI. */
    @Column(name = "is_editable", nullable = false)
    private Boolean isEditable;

    /** Thứ tự hiển thị trong nhóm. */
    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;
}
