package com.webtro.modules.interaction.spi;

import com.webtro.common.enums.ListingStatus;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code interaction} cần từ module {@code listing} — canonical luật 4
 * (gọi module khác qua interface, không đụng repository của nó).
 *
 * <p><b>Quyết định kiến trúc:</b> tại thời điểm viết module interaction, module listing mới chỉ
 * có entity + repository, chưa có {@code ListingService}. Để module interaction tự chứa và biên
 * dịch được, hợp đồng cross-module được khai báo dạng SPI <i>do bên tiêu dùng sở hữu</i> (đặt tại
 * {@code interaction.spi}). Module {@code listing} phải cung cấp một {@code @Component}
 * hiện thực interface này (ví dụ {@code ListingGatewayImpl} bọc {@code ListingRepository} +
 * {@code ListingImageRepository} + {@code ListingVisibilityService}).
 *
 * <p>Mọi phương thức đọc phải bỏ qua tin đã xóa mềm ({@code deleted_at IS NULL}); tính hiển thị
 * công khai phải dùng {@code ListingVisibilityService.publicStatuses()} (canonical mục 5.2),
 * KHÔNG so cứng {@code status = ACTIVE}.
 */
public interface ListingGateway {

    /**
     * Thông tin tóm tắt của tin phục vụ hiển thị và kiểm tra nghiệp vụ ở module interaction.
     *
     * @param id               id tin
     * @param ownerId          chủ tin (users.id)
     * @param title            tiêu đề
     * @param slug             slug SEO
     * @param thumbnailUrl     URL ảnh đại diện (ảnh primary, kích thước thumb) — có thể null
     * @param price            giá thuê (VND)
     * @param area             diện tích (m2)
     * @param shortAddress     địa chỉ rút gọn "Phường, Quận, Tỉnh"
     * @param status           trạng thái tin
     * @param publiclyVisible  tin có đang hiển thị công khai không (theo publicStatuses)
     * @param averageRating    điểm đánh giá trung bình hiện lưu (denormalized)
     * @param reviewCount      số đánh giá hiển thị hiện lưu (denormalized)
     * @param contactCount     số lượt liên hệ hiện lưu (denormalized)
     */
    record ListingBrief(
            Long id,
            Long ownerId,
            String title,
            String slug,
            String thumbnailUrl,
            BigDecimal price,
            BigDecimal area,
            String shortAddress,
            ListingStatus status,
            boolean publiclyVisible,
            BigDecimal averageRating,
            int reviewCount,
            int contactCount) {
    }

    /** Lấy tóm tắt tin còn sống; ném {@code ResourceNotFoundException(LISTING_NOT_FOUND)} nếu không có. */
    ListingBrief getBrief(Long listingId);

    /** Lấy tóm tắt tin còn sống nếu tồn tại (không ném). */
    Optional<ListingBrief> findBrief(Long listingId);

    /** Nạp hàng loạt (tránh N+1 cho danh sách favorites / lịch sử / đánh giá của tôi). */
    Map<Long, ListingBrief> getBriefs(Collection<Long> listingIds);

    /** Tin có đang hiển thị công khai không (dùng publicStatuses, canonical mục 5.2). */
    boolean isPubliclyVisible(Long listingId);

    /** Đặt lại {@code listings.favorite_count} theo số đếm chính xác từ module interaction. */
    void setFavoriteCount(Long listingId, int count);

    /** Tăng {@code listings.contact_count} thêm 1 (sau khi đã khử trùng lặp). */
    void incrementContactCount(Long listingId);

    /** Đặt lại {@code listings.comment_count} theo số đếm chính xác từ module interaction. */
    void setCommentCount(Long listingId, int count);

    /** Cập nhật {@code listings.average_rating} + {@code listings.review_count} (canonical 4.7.8 quy tắc 5). */
    void updateReviewAggregate(Long listingId, BigDecimal averageRating, int reviewCount);
}
