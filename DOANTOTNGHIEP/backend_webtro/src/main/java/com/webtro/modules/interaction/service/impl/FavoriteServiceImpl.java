package com.webtro.modules.interaction.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.dto.request.CreateFavoriteRequest;
import com.webtro.modules.interaction.dto.response.FavoriteResponse;
import com.webtro.modules.interaction.dto.response.FavoriteToggleResponse;
import com.webtro.modules.interaction.entity.Favorite;
import com.webtro.modules.interaction.mapper.FavoriteMapper;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.service.FavoriteService;
import com.webtro.modules.interaction.spi.ListingGateway;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Hiện thực nghiệp vụ lưu tin (FAV) — canonical 4.5.1–4.5.3.
 *
 * <p><b>Quyết định:</b> bỏ lưu tin dùng <i>xóa cứng</i> ({@code favoriteRepository.delete}) thay vì
 * xóa mềm. Lý do: ràng buộc {@code uk_favorites_user_listing (user_id, listing_id)} không kèm
 * {@code deleted_at}; nếu xóa mềm thì lần lưu lại (rất phổ biến với nút "thả tim") sẽ đụng khóa
 * duy nhất. Dữ liệu favorite là dữ liệu cá nhân giá trị thấp, xóa cứng an toàn và đúng UX bật/tắt.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ListingGateway listingGateway;
    private final FavoriteMapper favoriteMapper;

    @Override
    @Transactional
    public FavoriteToggleResponse addFavorite(CreateFavoriteRequest request, Long userId) {
        Long listingId = request.getListingId();
        // Tin phải tồn tại (còn sống). Ném LISTING_NOT_FOUND nếu không có.
        listingGateway.getBrief(listingId);

        if (favoriteRepository.existsByUserIdAndListingIdAndDeletedAtIsNull(userId, listingId)) {
            throw new ConflictException(ErrorCode.FAVORITE_ALREADY_EXISTS);
        }

        Favorite favorite = Favorite.builder()
                .userId(userId)
                .listingId(listingId)
                .note(HtmlSanitizer.stripAllHtml(request.getNote()))
                .build();
        favorite = favoriteRepository.save(favorite);

        int count = syncFavoriteCount(listingId);
        return favoriteMapper.toToggleResponse(favorite, count);
    }

    @Override
    @Transactional
    public void removeFavorite(Long listingId, Long userId) {
        Favorite favorite = favoriteRepository
                .findByUserIdAndListingIdAndDeletedAtIsNull(userId, listingId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.FAVORITE_NOT_FOUND));
        favoriteRepository.delete(favorite);
        syncFavoriteCount(listingId);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FavoriteResponse> listFavorites(Long userId, Pageable pageable) {
        Page<Favorite> page = favoriteRepository.findByUserIdAndDeletedAtIsNull(userId, pageable);
        List<Long> listingIds = page.getContent().stream().map(Favorite::getListingId).toList();
        Map<Long, ListingGateway.ListingBrief> briefs = listingGateway.getBriefs(listingIds);
        return PageResponse.from(page, f -> favoriteMapper.toResponse(f, briefs.get(f.getListingId())));
    }

    /** Đồng bộ {@code listings.favorite_count} theo số đếm chính xác; trả về số mới. */
    private int syncFavoriteCount(Long listingId) {
        int count = (int) favoriteRepository.countByListingIdAndDeletedAtIsNull(listingId);
        listingGateway.setFavoriteCount(listingId, count);
        return count;
    }
}
