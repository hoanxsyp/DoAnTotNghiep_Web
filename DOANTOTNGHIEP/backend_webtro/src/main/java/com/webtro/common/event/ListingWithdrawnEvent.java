package com.webtro.common.event;

/**
 * Tin đăng vừa bị chủ tin đóng hoặc xoá. Module rental nghe sự kiện này để kết thúc các yêu cầu
 * thuê còn đang chờ ({@code PENDING}/{@code APPROVED}) của tin — trước đây chúng bị treo mãi.
 *
 * <p>Phát trong cùng transaction với việc đóng/xoá tin; listener chạy đồng bộ nên lỗi ở listener
 * làm rollback cả thao tác đóng/xoá.
 *
 * @param listingId id tin
 * @param title     tiêu đề tin (dùng trong thông báo cho người thuê)
 * @param reason    lý do hiển thị cho người thuê
 */
public record ListingWithdrawnEvent(Long listingId, String title, String reason) {
}
