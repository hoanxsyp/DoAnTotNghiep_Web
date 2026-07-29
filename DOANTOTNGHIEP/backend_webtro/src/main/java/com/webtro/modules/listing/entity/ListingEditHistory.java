package com.webtro.modules.listing.entity;

import com.webtro.common.enums.ListingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Lịch sử sửa tin [§3.4]. Bảng <b>append-only</b>: chỉ có {@code id} + {@code created_at}
 * (không có {@code updated_at}/xóa mềm), nên KHÔNG kế thừa BaseEntity/AuditableEntity mà
 * tự khai {@code id} và {@code created_at}.
 *
 * <p>{@code listing} cùng module → {@code @ManyToOne(LAZY)}. {@code editor_id} trỏ sang
 * {@code users} module khác nên chỉ giữ {@code Long} (canonical luật 8).
 */
@Entity
@Table(name = "listing_edit_histories")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ListingEditHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    /** users.id của người sửa (chéo module → chỉ giữ Long); NULL nếu user bị xóa. */
    @Column(name = "editor_id")
    private Long editorId;

    @Column(name = "field_name", nullable = false, length = 50)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "is_sensitive_change", nullable = false)
    @Builder.Default
    private Boolean isSensitiveChange = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before", length = 20)
    private ListingStatus statusBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", length = 20)
    private ListingStatus statusAfter;

    /** UUID nhóm các thay đổi trong cùng một lần sửa. */
    @Column(name = "edit_batch_id", nullable = false, columnDefinition = "CHAR(36)")
    private String editBatchId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
