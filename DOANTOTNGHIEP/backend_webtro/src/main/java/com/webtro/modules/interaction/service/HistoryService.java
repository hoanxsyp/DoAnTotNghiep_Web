package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.response.SearchHistoryResponse;
import com.webtro.modules.interaction.dto.response.ViewHistoryResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Nghiệp vụ lịch sử xem tin và lịch sử tìm kiếm — HIST, canonical mục 4.5, {@code [§2.5][§3.7]}.
 */
public interface HistoryService {

    /**
     * Ghi một lượt xem tin (HIST-01) — <b>được module listing gọi</b> khi phục vụ
     * {@code GET /api/listings/{id}}. Tự khử trùng lặp theo {@code view.dedup_minutes}.
     *
     * @param userId  người xem (null nếu khách ẩn danh)
     * @return {@code true} nếu lượt xem được TÍNH (ngoài cửa sổ khử trùng) — module listing dùng
     *         giá trị này để quyết định tăng {@code listings.view_count} (tránh phụ thuộc vòng).
     */
    boolean recordView(Long listingId, Long userId, String sessionId, String ipAddress,
                       String userAgent, String referrer);

    /** Lịch sử xem tin của người dùng (phân trang), lọc theo khoảng ngày tùy chọn. */
    PageResponse<ViewHistoryResponse> listViews(Long userId, LocalDate from, LocalDate to, Pageable pageable);

    /** Xóa toàn bộ lịch sử xem của người dùng (quyền riêng tư). */
    void clearViews(Long userId);

    /** Xóa một mục lịch sử xem của người dùng. */
    void deleteView(Long id, Long userId);

    /** Lịch sử tìm kiếm của người dùng (phân trang). */
    PageResponse<SearchHistoryResponse> listSearches(Long userId, Pageable pageable);

    /** Xóa lịch sử tìm kiếm: {@code id == null} → xóa toàn bộ; ngược lại xóa một mục. */
    void deleteSearches(Long userId, Long id);

    /** Chuyển {@link Instant} tiện dụng cho controller (chặn null). */
    static Instant atStartOfDay(LocalDate d) {
        return d == null ? null : d.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
    }
}
