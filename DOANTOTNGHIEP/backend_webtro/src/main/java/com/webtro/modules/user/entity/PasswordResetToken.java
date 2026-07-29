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
 * Token đặt lại mật khẩu [§2.1] (AUTH-04) —
 * bảng {@code password_reset_tokens} (mục 10 của V1 baseline).
 *
 * <p>{@code user_id} cùng module → {@code @ManyToOne(LAZY)}. Lưu hash SHA-256 của token, dùng
 * một lần ({@code used_at}) và có hạn ({@code expires_at}).
 */
@Entity
@Table(
        name = "password_reset_tokens",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_password_reset_tokens_token_hash", columnNames = "token_hash"),
        indexes = {
                @Index(name = "idx_password_reset_tokens_user_id", columnList = "user_id"),
                @Index(name = "idx_password_reset_tokens_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class PasswordResetToken extends AuditableEntity {

    /** Người dùng yêu cầu đặt lại mật khẩu. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Băm token (SHA-256 hex). */
    @Column(name = "token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String tokenHash;

    /** Thời điểm hết hạn. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Thời điểm token đã được sử dụng (null = chưa dùng). */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Địa chỉ IP yêu cầu. */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
