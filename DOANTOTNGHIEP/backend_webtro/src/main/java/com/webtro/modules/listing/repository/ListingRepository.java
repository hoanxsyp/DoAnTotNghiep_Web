package com.webtro.modules.listing.repository;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.listing.entity.Listing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Repository cho {@link Listing}.
 *
 * <p>Xóa mềm KHÔNG dùng {@code @SQLRestriction}; điều kiện {@code deleted_at IS NULL} viết
 * tường minh. Trong service nghiệp vụ, KHÔNG gọi {@code findById} kế thừa — chỉ dùng
 * {@link #findAliveById} (luồng công khai/chủ sở hữu) hoặc {@link #findAnyById} (Admin/Moderator).
 */
@Repository
public interface ListingRepository
        extends JpaRepository<Listing, Long>, JpaSpecificationExecutor<Listing> {

    /** Dùng cho MỌI luồng công khai và luồng chủ sở hữu (bỏ qua tin đã xóa mềm). */
    @Query("SELECT l FROM Listing l WHERE l.id = :id AND l.deletedAt IS NULL")
    Optional<Listing> findAliveById(@Param("id") Long id);

    /** CHỈ dùng cho Admin/Moderator có LISTING_VIEW_ANY — thấy cả tin đã xóa mềm [§3.6]. */
    @Query("SELECT l FROM Listing l WHERE l.id = :id")
    Optional<Listing> findAnyById(@Param("id") Long id);

    /** Lấy tin theo slug (trang chi tiết công khai). */
    @Query("SELECT l FROM Listing l WHERE l.slug = :slug AND l.deletedAt IS NULL")
    Optional<Listing> findBySlugAlive(@Param("slug") String slug);

    /** Kiểm tra slug đã tồn tại (chưa xóa mềm) để đảm bảo duy nhất khi tạo/sửa tin. */
    boolean existsBySlugAndDeletedAtIsNull(String slug);

    /**
     * Đếm số tin đang bị {@code LOCKED} của một chủ trọ kể từ mốc {@code since} — phục vụ ngưỡng
     * kiểm duyệt §5.4 ({@code moderation.threshold.locked_listing_*}). Không có cột {@code locked_at}
     * riêng nên dùng {@code updated_at} làm mốc thời điểm chuyển sang LOCKED (lần sửa gần nhất).
     */
    @Query("SELECT COUNT(l) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.status = com.webtro.common.enums.ListingStatus.LOCKED "
            + "AND l.updatedAt >= :since AND l.deletedAt IS NULL")
    long countLockedListingsSince(@Param("ownerId") Long ownerId, @Param("since") Instant since);

    // ==================================================================
    //  Tổng hợp theo chủ trọ (canonical 4.4) — thêm cho dashboard chủ trọ
    // ==================================================================

    /** Số tin theo từng trạng thái của một chủ trọ (bỏ tin đã xóa mềm): {@code [status, count]}. */
    @Query("SELECT l.status, COUNT(l) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.deletedAt IS NULL GROUP BY l.status")
    List<Object[]> countByStatusForOwner(@Param("ownerId") Long ownerId);

    /** Tổng số tin của một chủ trọ (bỏ tin đã xóa mềm). */
    @Query("SELECT COUNT(l) FROM Listing l WHERE l.ownerId = :ownerId AND l.deletedAt IS NULL")
    long countByOwner(@Param("ownerId") Long ownerId);

    /** Tổng lượt xem tất cả tin của một chủ trọ (bỏ tin đã xóa mềm). */
    @Query("SELECT COALESCE(SUM(l.viewCount), 0) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.deletedAt IS NULL")
    long sumViewCountForOwner(@Param("ownerId") Long ownerId);

    /** Tổng lượt lưu tất cả tin của một chủ trọ (bỏ tin đã xóa mềm). */
    @Query("SELECT COALESCE(SUM(l.favoriteCount), 0) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.deletedAt IS NULL")
    long sumFavoriteCountForOwner(@Param("ownerId") Long ownerId);

    /** Tổng lượt liên hệ tất cả tin của một chủ trọ (bỏ tin đã xóa mềm). */
    @Query("SELECT COALESCE(SUM(l.contactCount), 0) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.deletedAt IS NULL")
    long sumContactCountForOwner(@Param("ownerId") Long ownerId);

    /** So tin theo owner/status, bo qua xoa mem. */
    @Query("SELECT COUNT(l) FROM Listing l WHERE l.ownerId = :ownerId AND l.status = :status "
            + "AND l.deletedAt IS NULL")
    long countByOwnerAndStatus(@Param("ownerId") Long ownerId, @Param("status") ListingStatus status);

    /** So tin active sap het han trong cua so chi dinh. */
    @Query("SELECT COUNT(l) FROM Listing l WHERE l.ownerId = :ownerId "
            + "AND l.status = com.webtro.common.enums.ListingStatus.ACTIVE "
            + "AND l.expiredAt IS NOT NULL AND l.expiredAt > :now AND l.expiredAt <= :threshold "
            + "AND l.deletedAt IS NULL")
    long countExpiringSoonForOwner(@Param("ownerId") Long ownerId,
                                   @Param("now") Instant now,
                                   @Param("threshold") Instant threshold);

    /** Top tin cua chu tro theo luot xem/lien he. */
    @Query("SELECT l FROM Listing l WHERE l.ownerId = :ownerId AND l.status IN :statuses "
            + "AND l.deletedAt IS NULL ORDER BY l.viewCount DESC, l.contactCount DESC, l.updatedAt DESC")
    List<Listing> findTopForLandlordDashboard(@Param("ownerId") Long ownerId,
                                              @Param("statuses") Collection<ListingStatus> statuses,
                                              Pageable pageable);

    // ==================================================================
    //  Truy vấn phục vụ job nền (canonical mục 11) — thêm cho scheduler
    // ==================================================================

    /**
     * Tin thuộc {@code statuses} đã quá {@code expired_at} (ứng viên cho {@code ListingExpiryJob}).
     * Sắp theo {@code expired_at} tăng dần để xử lý tin quá hạn lâu nhất trước; phân trang để không
     * giữ transaction quá lâu.
     */
    @Query("SELECT l FROM Listing l WHERE l.status IN :statuses AND l.expiredAt IS NOT NULL "
            + "AND l.expiredAt < :now AND l.deletedAt IS NULL ORDER BY l.expiredAt ASC")
    List<Listing> findExpiredCandidates(@Param("statuses") Collection<ListingStatus> statuses,
                                        @Param("now") Instant now, Pageable pageable);

    /**
     * Tin sắp hết hạn cần nhắc chủ trọ ({@code ListingExpiryReminderJob}): còn hiệu lực, còn hạn
     * trong khoảng {@code (now, maxThreshold]} và chưa nhắc trong ngày hôm nay
     * ({@code expiry_reminder_sent_at} null hoặc trước {@code dayStart}). Việc xác định đúng mốc
     * "3 ngày"/"1 ngày" do job lọc lại theo số ngày còn lại.
     */
    @Query("SELECT l FROM Listing l WHERE l.status IN :statuses AND l.deletedAt IS NULL "
            + "AND l.expiredAt IS NOT NULL AND l.expiredAt > :now AND l.expiredAt <= :maxThreshold "
            + "AND (l.expiryReminderSentAt IS NULL OR l.expiryReminderSentAt < :dayStart) "
            + "ORDER BY l.expiredAt ASC")
    List<Listing> findExpiryReminderCandidates(@Param("statuses") Collection<ListingStatus> statuses,
                                               @Param("now") Instant now,
                                               @Param("maxThreshold") Instant maxThreshold,
                                               @Param("dayStart") Instant dayStart, Pageable pageable);

    /** Id các tin ở {@code statuses} (đang hiển thị) — {@code TrustScoreRecalcJob} tính lại điểm. */
    @Query("SELECT l.id FROM Listing l WHERE l.status IN :statuses AND l.deletedAt IS NULL "
            + "ORDER BY l.id ASC")
    List<Long> findIdsByStatusIn(@Param("statuses") Collection<ListingStatus> statuses, Pageable pageable);

    /** Id chủ trọ (distinct) có tin ở {@code statuses} — {@code TrustScoreRecalcJob} tính lại điểm chủ trọ. */
    @Query("SELECT DISTINCT l.ownerId FROM Listing l WHERE l.status IN :statuses AND l.deletedAt IS NULL")
    List<Long> findDistinctOwnerIdsByStatusIn(@Param("statuses") Collection<ListingStatus> statuses);

    /**
     * Tin còn cờ đẩy ({@code is_promoted = true}) nhưng đã quá {@code promoted_until} — dùng cho
     * {@code PromotionExpiryJob} gỡ cờ (phòng trường hợp lượt đẩy không còn khớp bản ghi subscription).
     */
    @Query("SELECT l FROM Listing l WHERE l.isPromoted = true AND l.promotedUntil IS NOT NULL "
            + "AND l.promotedUntil < :now AND l.deletedAt IS NULL")
    List<Listing> findExpiredPromotedListings(@Param("now") Instant now, Pageable pageable);

    /**
     * Tin mới xuất bản ({@code ACTIVE}) kể từ {@code since} — nguồn ứng viên cho
     * {@code NewMatchingListingNotifyJob}. Chỉ lấy tin còn hiệu lực, chưa xóa mềm.
     */
    @Query("SELECT l FROM Listing l WHERE l.status = com.webtro.common.enums.ListingStatus.ACTIVE "
            + "AND l.deletedAt IS NULL AND l.publishedAt IS NOT NULL AND l.publishedAt >= :since "
            + "ORDER BY l.publishedAt DESC")
    List<Listing> findNewActiveListingsSince(@Param("since") Instant since, Pageable pageable);

    // ==================================================================
    //  Truy vấn phục vụ màn quản trị AI (canonical 4.19) — thêm cho AdminAiController
    // ==================================================================

    /**
     * Tin bị AI đánh dấu cần kiểm tra do cảm xúc tiêu cực ({@code need_review_count > 0}) — nguồn cho
     * danh sách cảnh báo sentiment ở màn quản trị AI ({@code AI_LOG_VIEW}). Bỏ qua tin đã xóa mềm;
     * thứ tự sắp xếp do {@link Pageable} quyết định (mặc định theo {@code last_need_review_at} gần nhất).
     */
    @Query("SELECT l FROM Listing l WHERE l.needReviewCount > 0 AND l.deletedAt IS NULL")
    Page<Listing> findSentimentAlerts(Pageable pageable);

    /**
     * Tin bị AI đánh cờ lệch giá ({@code price_deviation_flag = true}) — nguồn cho danh sách tin
     * nghi ngờ lệch giá ở màn quản trị AI ({@code AI_LOG_VIEW}). Bỏ qua tin đã xóa mềm.
     */
    @Query("SELECT l FROM Listing l WHERE l.priceDeviationFlag = true AND l.deletedAt IS NULL")
    Page<Listing> findPriceDeviationFlagged(Pageable pageable);
}
