package com.webtro.modules.moderation.spi;

import com.webtro.common.enums.ListingStatus;
import com.webtro.common.enums.ReportSeverity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code moderation} cần từ module {@code listing} — canonical luật 4
 * (gọi module khác qua interface, không đụng repository của nó).
 *
 * <p><b>Quyết định kiến trúc:</b> tại thời điểm viết module moderation, module listing mới chỉ có
 * entity + repository + {@code ListingStateMachine} + {@code TrustScoreService}, chưa có
 * {@code ListingService}. Theo đúng quy ước đã áp dụng ở {@code interaction.spi.ListingGateway},
 * hợp đồng cross-module được khai báo dạng SPI <i>do bên tiêu dùng sở hữu</i> (đặt tại
 * {@code moderation.spi}). Module {@code listing} phải cung cấp một {@code @Component} hiện thực
 * interface này, bọc {@code ListingRepository} + {@code ListingStateMachine} (mọi đổi trạng thái
 * phải đi qua máy trạng thái, canonical mục 5.1).
 *
 * <p>Mọi phương thức đọc bỏ qua tin đã xóa mềm ({@code deleted_at IS NULL}).
 */
public interface ListingModerationGateway {

    /**
     * Tóm tắt tin phục vụ màn kiểm duyệt.
     *
     * @param id            id tin
     * @param ownerId       chủ tin (users.id)
     * @param title         tiêu đề
     * @param slug          slug SEO
     * @param status        trạng thái hiện tại
     * @param price         giá thuê (VND)
     * @param area          diện tích (m2)
     * @param shortAddress  địa chỉ rút gọn "Phường, Quận, Tỉnh"
     * @param thumbnailUrl  URL ảnh đại diện (có thể null)
     */
    record ListingRef(
            Long id,
            Long ownerId,
            String title,
            String slug,
            ListingStatus status,
            BigDecimal price,
            BigDecimal area,
            String shortAddress,
            String thumbnailUrl) {
    }

    /** Lấy tóm tắt tin còn sống; ném {@code ResourceNotFoundException(LISTING_NOT_FOUND)} nếu không có. */
    ListingRef getRef(Long listingId);

    /** Lấy tóm tắt tin còn sống nếu tồn tại (không ném). */
    Optional<ListingRef> findRef(Long listingId);

    /**
     * Gắn cờ tin cần kiểm tra khi report vượt ngưỡng (canonical 5.1
     * {@code FLAG_NEED_REVIEW}: {@code ACTIVE → NEED_REVIEW}, actor SYSTEM). Không lỗi nếu tin
     * không ở trạng thái cho phép (bên hiện thực bỏ qua theo máy trạng thái).
     */
    void flagNeedReview(Long listingId, String reason);

    /**
     * Gỡ cờ cần kiểm tra khi kết luận không vi phạm (canonical 5.1 {@code CLEAR_NEED_REVIEW}:
     * {@code NEED_REVIEW → ACTIVE}). Không làm gì nếu tin không ở {@code NEED_REVIEW}.
     */
    void clearNeedReview(Long listingId, Long moderatorId, String reason);

    /**
     * Ẩn tin do kiểm duyệt khi kết luận vi phạm mức trung bình (canonical 5.1
     * {@code AUTO_HIDE_BY_SYSTEM}: {@code ACTIVE/NEED_REVIEW → HIDDEN}, có ghi {@code auto_hidden_at}
     * để chủ trọ KHÔNG tự bật lại — chỉ Moderator/Admin gỡ được).
     *
     * @return trạng thái trước khi ẩn
     */
    ListingStatus hideByModeration(Long listingId, Long moderatorId, String reason);

    /**
     * Khóa tin khi kết luận vi phạm nặng (canonical 5.1 {@code LOCK} → {@code LOCKED}, bắt buộc lý
     * do + mức độ). Chỉ Admin (có {@code LISTING_LOCK}) được gọi — kiểm quyền ở controller/service.
     *
     * @return trạng thái trước khi khóa
     */
    ListingStatus lock(Long listingId, Long adminId, ReportSeverity severity, String reason);

    /**
     * Đếm số tin của một chủ trọ đang bị {@code LOCKED} kể từ mốc {@code since} — phục vụ ngưỡng
     * §5.4 ({@code moderation.threshold.locked_listing_*}, mặc định 5 tin/60 ngày).
     */
    long countLockedListingsSince(Long ownerId, Instant since);
}
