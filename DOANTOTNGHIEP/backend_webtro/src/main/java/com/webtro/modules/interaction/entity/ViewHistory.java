package com.webtro.modules.interaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Lịch sử xem tin — bảng {@code view_histories} (append-only, chỉ {@code id} + {@code created_at}).
 *
 * <p><b>Không kế thừa</b> {@link com.webtro.common.AuditableEntity}/{@link com.webtro.common.BaseEntity}
 * vì bảng ghi log chỉ có {@code id}, {@code viewed_at}, {@code created_at} — không có
 * {@code updated_at}/{@code updated_by}/{@code deleted_at}. Map thêm các cột đó sẽ làm
 * {@code ddl-auto=validate} fail. Đây là entity độc lập theo đúng schema V1.
 *
 * <p>{@code userId} nullable (khách ẩn danh); {@code listingId} chéo module giữ {@code Long}.
 */
@Entity
@Table(
        name = "view_histories",
        indexes = {
                @Index(name = "idx_view_histories_user_id_viewed_at", columnList = "user_id, viewed_at"),
                @Index(name = "idx_view_histories_listing_id_viewed_at", columnList = "listing_id, viewed_at"),
                @Index(name = "idx_view_histories_dedup", columnList = "listing_id, user_id, viewed_at"),
                @Index(name = "idx_view_histories_dedup_anon", columnList = "listing_id, ip_address, viewed_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViewHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    /** Tin được xem (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Người xem — {@code null} nếu khách ẩn danh (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id")
    private Long userId;

    /** Định danh phiên khách ẩn danh (UUID). */
    @Column(name = "session_id", length = 36)
    private String sessionId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "referrer", length = 500)
    private String referrer;

    /** Lượt xem này có được tính vào {@code listings.view_count} không (chống spam view). */
    @Builder.Default
    @Column(name = "is_counted", nullable = false)
    private Boolean isCounted = true;

    /** Thời điểm xem (UTC). */
    @CreationTimestamp
    @Column(name = "viewed_at", nullable = false, updatable = false)
    private Instant viewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
