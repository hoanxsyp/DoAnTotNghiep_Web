package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.GenderRequirement;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
 */
@Entity
@Table(
        name = "user_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id")
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

    /** Địa chỉ chi tiết. */
    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    /** Yêu cầu giới tính khi ở ghép (tùy chọn). */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_gender_requirement", length = 15)
    private GenderRequirement preferredGenderRequirement;
}
