package com.webtro.modules.interaction.service.impl;

import com.webtro.common.enums.ListingStatus;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.interaction.dto.request.CreateFavoriteRequest;
import com.webtro.modules.interaction.entity.Favorite;
import com.webtro.modules.interaction.mapper.FavoriteMapper;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.spi.ListingGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT lưu/bỏ lưu/xem tin đã lưu của người thuê (UT-FAV-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoriteServiceImplTest {

    private static final long USER_ID = 30L;
    private static final long LISTING_ID = 20L;

    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private ListingGateway listingGateway;

    private FavoriteServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FavoriteServiceImpl(favoriteRepository, listingGateway, new FavoriteMapper());
        when(favoriteRepository.save(any(Favorite.class))).thenAnswer(inv -> {
            Favorite f = inv.getArgument(0);
            f.setId(1L);
            return f;
        });
    }

    @Test
    @DisplayName("UT-FAV-01 Lưu tin công khai → lưu, ghi chú bị lọc HTML, đồng bộ favorite_count")
    void addFavoriteOnPublicListing() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(brief(ListingStatus.ACTIVE, true));
        when(favoriteRepository.countByListingIdAndDeletedAtIsNull(LISTING_ID)).thenReturn(7L);

        var response = service.addFavorite(request("<b>gần trường</b>"), USER_ID);

        ArgumentCaptor<Favorite> saved = ArgumentCaptor.forClass(Favorite.class);
        verify(favoriteRepository).save(saved.capture());
        assertEquals("gần trường", saved.getValue().getNote());
        assertTrue(response.getFavorited());
        assertEquals(7, response.getFavoriteCount());
        verify(listingGateway).setFavoriteCount(LISTING_ID, 7);
    }

    @Test
    @DisplayName("UT-FAV-02 Lưu trùng → FAVORITE_ALREADY_EXISTS, không lưu thêm")
    void addDuplicateFavorite() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(brief(ListingStatus.ACTIVE, true));
        when(favoriteRepository.existsByUserIdAndListingIdAndDeletedAtIsNull(USER_ID, LISTING_ID)).thenReturn(true);

        BusinessException ex = assertThrows(ConflictException.class,
                () -> service.addFavorite(request(null), USER_ID));

        assertEquals(ErrorCode.FAVORITE_ALREADY_EXISTS, ex.getErrorCode());
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FAV-03 Lưu tin không tồn tại → LISTING_NOT_FOUND")
    void addFavoriteOnMissingListing() {
        when(listingGateway.getBrief(LISTING_ID))
                .thenThrow(new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));

        assertThrows(ResourceNotFoundException.class, () -> service.addFavorite(request(null), USER_ID));
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FAV-04 [BUG-03] Lưu tin KHÔNG công khai (nháp/ẩn/khóa của người khác) → phải bị chặn LISTING_NOT_ACTIVE")
    @org.junit.jupiter.api.Disabled("BUG-03: FavoriteServiceImpl.addFavorite chỉ kiểm tra tin còn sống, không kiểm tra public "
            + "(docs/03 mục 4.5.1 yêu cầu 422 LISTING_NOT_ACTIVE). Bỏ @Disabled sau khi fix.")
    void addFavoriteOnNonPublicListingMustBeRejected() {
        when(listingGateway.getBrief(LISTING_ID)).thenReturn(brief(ListingStatus.DRAFT, false));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.addFavorite(request(null), USER_ID));

        assertEquals(ErrorCode.LISTING_NOT_ACTIVE, ex.getErrorCode());
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    @DisplayName("UT-FAV-05 Bỏ lưu → xóa cứng (để lưu lại không đụng unique key) và đồng bộ lại số đếm")
    void removeFavoriteHardDeletes() {
        Favorite f = Favorite.builder().id(1L).userId(USER_ID).listingId(LISTING_ID).build();
        when(favoriteRepository.findByUserIdAndListingIdAndDeletedAtIsNull(USER_ID, LISTING_ID))
                .thenReturn(Optional.of(f));
        when(favoriteRepository.countByListingIdAndDeletedAtIsNull(LISTING_ID)).thenReturn(0L);

        service.removeFavorite(LISTING_ID, USER_ID);

        verify(favoriteRepository).delete(f);
        verify(listingGateway).setFavoriteCount(LISTING_ID, 0);
    }

    @Test
    @DisplayName("UT-FAV-06 Bỏ lưu tin chưa lưu → FAVORITE_NOT_FOUND")
    void removeFavoriteNotSaved() {
        when(favoriteRepository.findByUserIdAndListingIdAndDeletedAtIsNull(USER_ID, LISTING_ID))
                .thenReturn(Optional.empty());

        BusinessException ex = assertThrows(ResourceNotFoundException.class,
                () -> service.removeFavorite(LISTING_ID, USER_ID));

        assertEquals(ErrorCode.FAVORITE_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("UT-FAV-07 Danh sách đã lưu: tin không còn công khai được gắn nhãn notAvailable")
    void listFavoritesMarksUnavailableListings() {
        Favorite active = Favorite.builder().id(1L).userId(USER_ID).listingId(LISTING_ID).build();
        Favorite expired = Favorite.builder().id(2L).userId(USER_ID).listingId(21L).build();
        Favorite gone = Favorite.builder().id(3L).userId(USER_ID).listingId(22L).build();
        var pageable = PageRequest.of(0, 20);
        when(favoriteRepository.findByUserIdAndDeletedAtIsNull(USER_ID, pageable))
                .thenReturn(new PageImpl<>(List.of(active, expired, gone), pageable, 3));
        when(listingGateway.getBriefs(anyCollection())).thenReturn(Map.of(
                LISTING_ID, brief(ListingStatus.ACTIVE, true),
                21L, brief(ListingStatus.EXPIRED, false)));

        var page = service.listFavorites(USER_ID, pageable);

        assertFalse(page.getItems().get(0).getNotAvailable());
        assertTrue(page.getItems().get(1).getNotAvailable());
        assertEquals("Tin đã hết hạn hiển thị", page.getItems().get(1).getNotAvailableLabel());
        assertTrue(page.getItems().get(2).getNotAvailable());
        assertEquals("Tin đã bị gỡ", page.getItems().get(2).getNotAvailableLabel());
    }

    private CreateFavoriteRequest request(String note) {
        CreateFavoriteRequest r = new CreateFavoriteRequest();
        r.setListingId(LISTING_ID);
        r.setNote(note);
        return r;
    }

    private ListingGateway.ListingBrief brief(ListingStatus status, boolean publiclyVisible) {
        return new ListingGateway.ListingBrief(LISTING_ID, 40L, "Phòng test", "phong-test", null,
                BigDecimal.valueOf(3_000_000), BigDecimal.valueOf(25), "Hà Nội", status, publiclyVisible,
                BigDecimal.ZERO, 0, 900L, null);
    }
}
