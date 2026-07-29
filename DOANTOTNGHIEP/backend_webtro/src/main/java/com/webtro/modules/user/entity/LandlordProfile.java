package com.webtro.modules.user.entity;

import com.webtro.common.AuditableEntity;
import com.webtro.common.enums.VerificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Hồ sơ chủ trọ (mở rộng 1-1 của {@link User}) [§5.7][§5.8][§6.1] —
 * bảng {@code landlord_profiles} (mục 7 của V1 baseline).
 *
 * <p>{@code user_id} cùng module + UNIQUE → {@code @OneToOne(LAZY)}.
 * {@code verified_by} là actor (moderator/admin duyệt) nên giữ {@link Long}.
 * Nhiều cột đếm sẵn (denormalized counters) phục vụ công thức uy tín {@code [§5.8]}.
 */
@Entity
@Table(
        name = "landlord_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uk_landlord_profiles_user_id", columnNames = "user_id"),
        indexes = {
                @Index(name = "idx_landlord_profiles_verification_status", columnList = "verification_status"),
                @Index(name = "idx_landlord_profiles_trust_score", columnList = "trust_score")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class LandlordProfile extends AuditableEntity {

    /** Người dùng sở hữu hồ sơ chủ trọ. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Tên hiển thị công khai. */
    @Column(name = "display_name", length = 100)
    private String displayName;

    /** Tên người liên hệ. */
    @Column(name = "contact_name", nullable = false, length = 100)
    private String contactName;

    /** Số điện thoại liên hệ. */
    @Column(name = "contact_phone", nullable = false, length = 15)
    private String contactPhone;

    /** Email liên hệ. */
    @Column(name = "contact_email", length = 190)
    private String contactEmail;

    /** Tên công ty/doanh nghiệp. */
    @Column(name = "company_name", length = 150)
    private String companyName;

    /** Địa chỉ. */
    @Column(name = "address", length = 255)
    private String address;

    /** Cho phép chat với người thuê. */
    @Builder.Default
    @Column(name = "allow_chat", nullable = false)
    private Boolean allowChat = true;

    /** Trạng thái xác minh chủ trọ. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 10)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    /** Thời điểm được xác minh. */
    @Column(name = "verified_at")
    private Instant verifiedAt;

    /** Người xác minh (users.id, actor → giữ Long). */
    @Column(name = "verified_by")
    private Long verifiedBy;

    /** Ghi chú xác minh. */
    @Column(name = "verification_note", length = 500)
    private String verificationNote;

    /** Điểm uy tín (0-100). */
    @Builder.Default
    @Column(name = "trust_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal trustScore = BigDecimal.valueOf(100.00);

    /** Tỷ lệ phản hồi (%) — null nếu chưa có dữ liệu. */
    @Column(name = "response_rate_percent")
    private Integer responseRatePercent;

    /** Thời gian phản hồi trung bình (phút). */
    @Column(name = "avg_response_minutes")
    private Integer avgResponseMinutes;

    /** Số hội thoại tính vào thống kê phản hồi. */
    @Builder.Default
    @Column(name = "response_conversation_count", nullable = false)
    private Integer responseConversationCount = 0;

    /** Điểm đánh giá trung bình (0-5). */
    @Builder.Default
    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.valueOf(0.00);

    /** Số lượt đánh giá. */
    @Builder.Default
    @Column(name = "review_count", nullable = false)
    private Integer reviewCount = 0;

    /** Tổng số tin đã đăng. */
    @Builder.Default
    @Column(name = "total_listings", nullable = false)
    private Integer totalListings = 0;

    /** Số tin đang hoạt động. */
    @Builder.Default
    @Column(name = "total_active_listings", nullable = false)
    private Integer totalActiveListings = 0;

    /** Số báo cáo hợp lệ đã nhận. */
    @Builder.Default
    @Column(name = "valid_report_count", nullable = false)
    private Integer validReportCount = 0;

    /** Số lần bị cảnh báo vi phạm. */
    @Builder.Default
    @Column(name = "warning_count", nullable = false)
    private Integer warningCount = 0;

    /** Số tin bị khóa. */
    @Builder.Default
    @Column(name = "locked_listing_count", nullable = false)
    private Integer lockedListingCount = 0;

    /** Số lượt gia hạn miễn phí đã dùng trong tháng. */
    @Builder.Default
    @Column(name = "free_renew_used_this_month", nullable = false)
    private Integer freeRenewUsedThisMonth = 0;

    /** Ngày reset bộ đếm gia hạn miễn phí. */
    @Column(name = "free_renew_reset_at")
    private LocalDate freeRenewResetAt;

    /** Hạn chế đăng tin đến thời điểm này. */
    @Column(name = "posting_restricted_until")
    private Instant postingRestrictedUntil;

    /** Lý do hạn chế đăng tin. */
    @Column(name = "restrict_reason", length = 500)
    private String restrictReason;
}
