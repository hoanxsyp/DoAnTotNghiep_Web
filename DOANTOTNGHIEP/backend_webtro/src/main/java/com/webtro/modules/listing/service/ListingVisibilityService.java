package com.webtro.modules.listing.service;

import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.listing.entity.Listing;

import java.util.Set;

/**
 * Nơi DUY NHẤT quyết định "tin nào được hiển thị công khai" (canonical §5.2 — quy tắc tối quan
 * trọng). Mọi truy vấn công khai (search, chi tiết, chatbot, gợi ý) phải dùng
 * {@link #publicStatuses()} thay vì hardcode {@code status = 'ACTIVE'}.
 *
 * <p>{@code NEED_REVIEW} có nằm trong tập công khai hay không phụ thuộc cấu hình
 * {@code listing.need_review.publicly_visible} (mặc định {@code true}).
 */
public interface ListingVisibilityService {

    /** Tập trạng thái được coi là công khai tại thời điểm hiện tại (đọc từ cấu hình). */
    Set<ListingStatus> publicStatuses();

    /** {@code true} nếu tin đang ở trạng thái được hiển thị công khai (canonical §5.2). */
    boolean publiclyVisible(Listing listing);

    /** {@code true} nếu trạng thái này thuộc tập công khai. */
    boolean isPublicStatus(ListingStatus status);
}
