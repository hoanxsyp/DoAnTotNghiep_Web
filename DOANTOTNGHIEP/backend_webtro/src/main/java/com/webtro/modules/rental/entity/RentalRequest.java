package com.webtro.modules.rental.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.RentalRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "rental_requests", indexes = {
        @Index(name = "idx_rental_requests_tenant_status_created", columnList = "tenant_id,status,created_at"),
        @Index(name = "idx_rental_requests_listing_status_created", columnList = "listing_id,status,created_at"),
        @Index(name = "idx_rental_requests_unit_tenant_status", columnList = "rental_unit_id,tenant_id,status")
})
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RentalRequest extends AuditableEntity {

    @Column(name = "listing_id", nullable = false)
    private Long listingId;

    /** Phòng của tin tại thời điểm gửi yêu cầu; dùng để xét quyền đánh giá theo phòng (RE-1). */
    @Column(name = "rental_unit_id", nullable = false)
    private Long rentalUnitId;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "desired_viewing_date", nullable = false)
    private LocalDate desiredViewingDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RentalRequestStatus status;

    @Column(name = "landlord_note", length = 500)
    private String landlordNote;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "rented_at")
    private Instant rentedAt;

    @Column(name = "checked_out_at")
    private Instant checkedOutAt;
}
