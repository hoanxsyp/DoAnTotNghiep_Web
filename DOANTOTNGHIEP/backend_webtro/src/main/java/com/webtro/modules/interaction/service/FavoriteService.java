package com.webtro.modules.interaction.service;

import com.webtro.common.PageResponse;
import com.webtro.modules.interaction.dto.request.CreateFavoriteRequest;
import com.webtro.modules.interaction.dto.response.FavoriteResponse;
import com.webtro.modules.interaction.dto.response.FavoriteToggleResponse;
import org.springframework.data.domain.Pageable;

/**
 * Nghiệp vụ lưu tin (yêu thích) — FAV, canonical mục 4.5, {@code [§3.9]}.
 */
public interface FavoriteService {

    /** Lưu một tin cho người dùng (1 user 1 tin 1 lần); cập nhật {@code listings.favorite_count}. */
    FavoriteToggleResponse addFavorite(CreateFavoriteRequest request, Long userId);

    /** Bỏ lưu tin (xóa mềm); cập nhật {@code listings.favorite_count}. */
    void removeFavorite(Long listingId, Long userId);

    /** Danh sách tin đã lưu của người dùng (phân trang). */
    PageResponse<FavoriteResponse> listFavorites(Long userId, Pageable pageable);
}
