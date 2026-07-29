package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.GenderRequirement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * Hồ sơ người thuê (mở rộng 1-1 của {@link User}) [§6.1] —
 * bảng {@code user_profiles} (mục 6 của V1 baseline).
 *
 * <p>{@code user_id} cùng module + UNIQUE → map {@code @OneToOne(LAZY)}.
 * {@code province_id}/{@code district_id} trỏ sang module {@code catalog} nên chỉ giữ
 * {@link Long} (canonical luật 8). FK tới provinces/districts được thêm bằng ALTER TABLE ở cuối
 * V1 (forward reference) nên không khai báo {@code foreignKey} phía entity.
 */
@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id"),
        indexes = {
                @Index(name = "idx_user_profiles_province_id", columnList = "province_id"),
                @Index(name = "idx_user_profiles_district_id", columnList = "district_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class UserProfile extends AuditableEntity {

    /** Người dùng sở hữu hồ sơ. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Ngày sinh. */
    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    /** Giới thiệu bản thân. */
    @Column(name = "bio", length = 500)
    private String bio;

    /** Nghề nghiệp. */
    @Column(name = "occupation", length = 100)
    private String occupation;

    /** Tỉnh/thành thường trú (provinces.id, chéo module → giữ Long). */
    @Column(name = "province_id")
    private Long provinceId;

    /** Quận/huyện thường trú (districts.id, chéo module → giữ Long). */
    @Column(name = "district_id")
    private Long districtId;

    /** Địa chỉ chi tiết. */
    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    /** Yêu cầu giới tính khi ở ghép (tùy chọn). */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_gender_requirement", length = 15)
    private GenderRequirement preferredGenderRequirement;
}
