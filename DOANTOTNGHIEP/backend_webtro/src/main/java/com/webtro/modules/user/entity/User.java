package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.Gender;
import com.webtro.common.enums.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Tài khoản người dùng [§3.1][§6.1] — bảng {@code users} (mục 4 của V1 baseline).
 *
 * <p>Bảng nghiệp vụ (audit + xóa mềm) → {@link AuditableEntity}.
 *
 * <p><b>Hai cột sinh {@code email_uk}/{@code phone_uk} KHÔNG được map</b>: chúng do MySQL tự sinh
 * ({@code GENERATED ALWAYS ... STORED}) để ép unique có điều kiện (ADR-02). Hibernate ở chế độ
 * {@code ddl-auto=validate} chỉ kiểm tra cột đã map có tồn tại trong bảng, không đòi map hết mọi
 * cột — nên bỏ qua chúng là an toàn và tránh việc Hibernate cố ghi vào cột sinh.
 *
 * <p>{@code locked_by} là tham chiếu <i>actor</i> (admin thực hiện khóa) nên giữ dạng {@link Long},
 * không map object (đồng nhất với quy ước cho các cột "người thực hiện").
 */
@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_status", columnList = "status"),
                @Index(name = "idx_users_role_id", columnList = "role_id"),
                @Index(name = "idx_users_full_name", columnList = "full_name"),
                @Index(name = "idx_users_created_at", columnList = "created_at"),
                @Index(name = "idx_users_email_lookup", columnList = "email"),
                @Index(name = "idx_users_phone_lookup", columnList = "phone")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class User extends AuditableEntity {

    /** Họ tên đầy đủ. */
    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Email đăng nhập. Uniqueness (case-insensitive, loại trừ tài khoản đã xóa) ép qua cột sinh email_uk. */
    @Column(name = "email", nullable = false, length = 190)
    private String email;

    /** Số điện thoại (tùy chọn). */
    @Column(name = "phone", length = 15)
    private String phone;

    /** Mật khẩu đã băm BCrypt (cost 12). */
    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    /** URL ảnh đại diện. */
    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /** Giới tính. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "gender", nullable = false, length = 10)
    private Gender gender = Gender.UNKNOWN;

    /** Trạng thái tài khoản. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status = UserStatus.PENDING_VERIFY;

    /**
     * Vai trò DUY NHẤT của người dùng (V13: quan hệ nhiều-một, bảng nối {@code user_roles} đã bỏ).
     *
     * <p>Toàn bộ phân quyền của người dùng suy ra trực tiếp từ đúng vai trò này, nên hai người cùng
     * vai trò luôn có bộ chức năng y hệt nhau.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /** Thời điểm xác thực email. */
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    /** Thời điểm xác thực số điện thoại. */
    @Column(name = "phone_verified_at")
    private Instant phoneVerifiedAt;

    /** Lần đăng nhập gần nhất. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Số lần đăng nhập sai liên tiếp. */
    @Builder.Default
    @Column(name = "failed_login_count", nullable = false)
    private Integer failedLoginCount = 0;

    /** Khóa tạm đến thời điểm này (khóa mềm do sai mật khẩu). */
    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Lý do khóa (bắt buộc khi status = LOCKED — CHECK ở DB). */
    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    /** Admin đã khóa tài khoản (users.id, actor → giữ Long). */
    @Column(name = "locked_by")
    private Long lockedBy;

    /** Thời điểm bị khóa. */
    @Column(name = "locked_at")
    private Instant lockedAt;

    /** Hạn chế bình luận đến thời điểm này. */
    @Column(name = "comment_restricted_until")
    private Instant commentRestrictedUntil;

    /** Hạn chế liên hệ đến thời điểm này. */
    @Column(name = "contact_restricted_until")
    private Instant contactRestrictedUntil;
}
