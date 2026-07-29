package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Refresh token (opaque UUID, lưu hash SHA-256, xoay vòng theo họ token) [§8] —
 * bảng {@code refresh_tokens} (mục 9 của V1 baseline).
 *
 * <p>{@code user_id} cùng module → {@code @ManyToOne(LAZY)}. {@code parent_id} tự trỏ về
 * {@code refresh_tokens} (token cha trong chuỗi xoay vòng) nên map {@code @ManyToOne(LAZY)} tới
 * chính {@link RefreshToken}. Reuse detection thu hồi toàn bộ token cùng {@code family_id}.
 */
@Entity
@Table(
        name = "refresh_tokens",
        uniqueConstraints = @UniqueConstraint(name = "uk_refresh_tokens_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_refresh_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_refresh_tokens_family_id", columnList = "family_id"),
                @Index(name = "idx_refresh_tokens_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RefreshToken extends AuditableEntity {

    /** Người dùng sở hữu token. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Băm token (SHA-256 hex). */
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    /** Định danh họ token (UUID) — mỗi lần login sinh mới, token xoay vòng kế thừa. */
    @Column(name = "family_id", nullable = false, length = 36, columnDefinition = "CHAR(36)")
    private String familyId;

    /** Token cha trong chuỗi xoay vòng (tự trỏ, null nếu là token gốc của họ). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private RefreshToken parent;

    /** Thời điểm phát hành. */
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Thời điểm hết hạn. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Thời điểm đã dùng để xoay vòng. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Thời điểm bị thu hồi. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /** Lý do thu hồi. */
    @Column(name = "revoked_reason", length = 50)
    private String revokedReason;

    /** User-Agent lúc phát hành. */
    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /** Địa chỉ IP lúc phát hành. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
