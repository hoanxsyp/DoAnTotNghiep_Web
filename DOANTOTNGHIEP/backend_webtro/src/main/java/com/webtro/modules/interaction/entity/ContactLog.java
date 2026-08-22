package com.webtro.modules.interaction.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.ContactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Nhật ký liên hệ giữa người thuê và chủ tin — bảng {@code contact_logs}.
 *
 * <p>Ghi lại mỗi lần xem SĐT / gửi form / khởi tạo chat ({@link ContactType}). Chống trùng lặp
 * theo cửa sổ {@code contact.dedup_minutes} qua {@code isCounted}.
 * FK chéo module ({@code listingId}, {@code userId}) giữ dạng {@code Long}; owner suy ra từ listing.
 */
@Entity
@Table(
        name = "contact_logs",
        indexes = {
                @Index(name = "idx_contact_logs_listing_id_created_at", columnList = "listing_id, created_at"),
                @Index(name = "idx_contact_logs_dedup", columnList = "listing_id, user_id, created_at"),
                @Index(name = "idx_contact_logs_user_id_created_at", columnList = "user_id, created_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class ContactLog extends AuditableEntity {

    /** Tin được liên hệ (FK listings.id — chéo module, giữ Long). */
    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Người thực hiện liên hệ (FK users.id — chéo module, giữ Long). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Chủ tin nhận liên hệ (FK users.id — chéo module, giữ Long). */

    /** Hình thức liên hệ. */
    @Enumerated(EnumType.STRING)
    @Column(name = "contact_type", nullable = false, length = 15)
    private ContactType contactType;

    /** Nội dung tin nhắn khi gửi form. */
    @Column(name = "message", length = 1000)
    private String message;

    /** Tên liên hệ do người dùng cung cấp trong form. */
    @Column(name = "contact_name", length = 100)
    private String contactName;

    /** SĐT liên hệ do người dùng cung cấp trong form. */
    @Column(name = "contact_phone", length = 15)
    private String contactPhone;

    /** Có tính vào {@code listings.contact_count} không (đã khử trùng lặp). */
    @Builder.Default
    @Column(name = "is_counted", nullable = false)
    private Boolean isCounted = true;

    /** Chủ tin đã đọc liên hệ này chưa. */
    @Builder.Default
    @Column(name = "is_read_by_owner", nullable = false)
    private Boolean isReadByOwner = false;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;
}
