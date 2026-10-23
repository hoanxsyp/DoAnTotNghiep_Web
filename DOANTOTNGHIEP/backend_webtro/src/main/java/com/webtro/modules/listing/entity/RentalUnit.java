package com.webtro.modules.listing.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.LocationSource;
import com.webtro.common.enums.RentalUnitStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Phòng thật của chủ trọ ({@code rental_units}). Một phòng có nhiều tin đăng theo thời gian nhưng
 * tối đa một tin "sống" (RU-2); đánh giá gắn vào phòng nên đóng tin rồi đăng lại không làm mất
 * đánh giá.
 *
 * <p><b>Điểm đánh giá và cờ khoá địa chỉ</b> chỉ được ghi bởi
 * {@code ReviewRatingRecalculator} thông qua câu lệnh UPDATE riêng
 * ({@code RentalUnitRepository.applyRating}). Vì vậy 3 cột này đặt
 * {@code insertable = false, updatable = false} và không có setter: lưu entity vì lý do khác
 * (đổi tên, đổi địa chỉ…) không bao giờ ghi đè điểm.
 */
@Entity
@Table(name = "rental_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class RentalUnit extends AuditableEntity {

    /** Chủ phòng (users.id). */
    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Tên hiển thị, ví dụ "Phòng 101 - Nhà trọ Hồng Loan". */
    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /** Số/mã phòng trong toà nhà (P101, Tầng 2…); dùng để phân biệt các phòng cùng địa chỉ. */
    @Column(name = "unit_label", length = 50)
    private String unitLabel;

    @Column(name = "ward_id", nullable = false)
    private Long wardId;

    @Column(name = "address_detail", nullable = false, length = 255)
    private String addressDetail;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 10)
    private LocationSource locationSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private RentalUnitStatus status = RentalUnitStatus.ACTIVE;

    @Column(name = "archived_at")
    private Instant archivedAt;

    // ----- Chỉ ReviewRatingRecalculator được ghi (qua RentalUnitRepository.applyRating) -----

    @Setter(AccessLevel.NONE)
    @Column(name = "address_locked", nullable = false, insertable = false, updatable = false)
    @Builder.Default
    private Boolean addressLocked = false;

    @Setter(AccessLevel.NONE)
    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2,
            insertable = false, updatable = false)
    @Builder.Default
    private BigDecimal averageRating = new BigDecimal("0.00");

    @Setter(AccessLevel.NONE)
    @Column(name = "review_count", nullable = false, insertable = false, updatable = false)
    @Builder.Default
    private Integer reviewCount = 0;

    public boolean isArchived() {
        return status == RentalUnitStatus.ARCHIVED;
    }

    public boolean isAddressLocked() {
        return Boolean.TRUE.equals(addressLocked);
    }
}
