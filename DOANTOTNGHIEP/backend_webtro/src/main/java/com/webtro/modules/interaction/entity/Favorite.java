package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Tin đã lưu (yêu thích) của người dùng — bảng {@code favorites}.
 *
 * <p>Quan hệ chéo module: {@code userId} (module user) và {@code listingId} (module listing)
 * giữ dạng {@code Long}, KHÔNG map object (canonical mục 3, luật 8).
 */
@Entity
@Table(
        name = "favorites",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_favorites_user_listing",
                columnNames = {"user_id", "listing_id"}),
        indexes = {
                @Index(name = "idx_favorites_user_id_created_at", columnList = "user_id, created_at"),
                @Index(name = "idx_favorites_listing_id", columnList = "listing_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Favorite extends AuditableEntity {

    /** Người dùng lưu tin (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Tin được lưu (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Ghi chú riêng của người dùng cho tin đã lưu. */
    @Column(name = "note", length = 255)
    private String note;
}
