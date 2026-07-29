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
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Người thuê theo dõi chủ trọ [§2.5] (FOLLOW-01/02) —
 * bảng {@code follows} (mục 11 của V1 baseline).
 *
 * <p>Cả {@code follower_id} và {@code landlord_id} đều trỏ về {@code users} (cùng module) — hai FK
 * cùng đích nên đặt tên vai trò rõ ràng ({@code fk_follows_users_follower}/
 * {@code fk_follows_users_landlord}) và map hai {@code @ManyToOne(LAZY)} riêng. CHECK ở DB chặn
 * tự theo dõi chính mình.
 */
@Entity
@Table(
        name = "follows",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_follows_follower_landlord",
                columnNames = {"follower_id", "landlord_id"}),
        indexes = @Index(name = "idx_follows_landlord_id", columnList = "landlord_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Follow extends AuditableEntity {

    /** Người theo dõi (người thuê). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    /** Chủ trọ được theo dõi. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    /** Nhận thông báo khi chủ trọ đăng tin mới. */
    @Builder.Default
    @Column(name = "notify_new_listing", nullable = false)
    private Boolean notifyNewListing = true;
}
