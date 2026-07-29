package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.VerificationStatus;
import com.webtro.common.enums.VerificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Yêu cầu xác thực email/phone/chủ trọ [§6.1] —
 * bảng {@code verifications} (mục 8 của V1 baseline).
 *
 * <p>{@code user_id} cùng module → {@code @ManyToOne(LAZY)}. {@code reviewed_by} là actor
 * (moderator duyệt xác thực chủ trọ) nên giữ {@link Long}.
 * {@code token_hash}/{@code otp_hash} là CHAR(64) (SHA-256 hex).
 */
@Entity
@Table(
        name = "verifications",
        uniqueConstraints = @UniqueConstraint(name = "uk_verifications_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_verifications_user_type_status", columnList = "user_id, type, status"),
                @Index(name = "idx_verifications_status_expires_at", columnList = "status, expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Verification extends AuditableEntity {

    /** Người dùng yêu cầu xác thực. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Loại xác thực. */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private VerificationType type;

    /** Trạng thái xác thực. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private VerificationStatus status = VerificationStatus.PENDING;

    /** Giá trị đích cần xác thực (email/số điện thoại). */
    @Column(name = "target_value", length = 190)
    private String targetValue;

    /** Băm token xác thực (SHA-256 hex). */
    @Column(name = "token_hash", length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    /** Băm mã OTP (SHA-256 hex). */
    @Column(name = "otp_hash", length = 64, columnDefinition = "CHAR(64)")
    private String otpHash;

    /** Số lần thử. */
    @Builder.Default
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    /** URL minh chứng (xác thực chủ trọ). */
    @Column(name = "evidence_url", length = 500)
    private String evidenceUrl;

    /** Thời điểm hết hạn. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Thời điểm xác thực thành công. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Người duyệt (users.id, actor → giữ Long). */
    @Column(name = "reviewed_by")
    private Long reviewedBy;

    /** Thời điểm duyệt. */
    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Lý do từ chối (bắt buộc khi status = REJECTED — CHECK ở DB). */
    @Column(name = "reject_reason", length = 500)
    private String rejectReason;
}
