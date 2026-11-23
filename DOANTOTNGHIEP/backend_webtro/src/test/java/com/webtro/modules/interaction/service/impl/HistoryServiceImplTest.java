package com.webtro.modules.interaction.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.constant.ConfigKey;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.entity.SearchHistory;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.mapper.HistoryMapper;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UT lịch sử xem tin/tìm kiếm của người thuê (UT-HIS-xx). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HistoryServiceImplTest {

    private static final long USER_ID = 30L;
    private static final long OTHER_USER_ID = 31L;
    private static final long LISTING_ID = 20L;

    @Mock
    private ViewHistoryRepository viewHistoryRepository;
    @Mock
    private SearchHistoryRepository searchHistoryRepository;
    @Mock
    private FavoriteRepository favoriteRepository;
    @Mock
    private ListingGateway listingGateway;
    @Mock
    private SystemConfigService systemConfig;

    private HistoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HistoryServiceImpl(viewHistoryRepository, searchHistoryRepository, favoriteRepository,
                listingGateway, new HistoryMapper(), systemConfig, new ObjectMapper());
        when(systemConfig.getInt(ConfigKey.VIEW_DEDUP_MINUTES)).thenReturn(30);
    }

    @Test
    @DisplayName("UT-HIS-01 Xem tin lần đầu trong cửa sổ khử trùng → ghi lịch sử, isCounted = true")
    void firstViewIsCounted() {
        when(viewHistoryRepository.existsByListingIdAndUserIdAndViewedAtAfter(eq(LISTING_ID), eq(USER_ID), any()))
                .thenReturn(false);

        boolean counted = service.recordView(LISTING_ID, USER_ID, "s", "1.1.1.1", "UA", null);

        ArgumentCaptor<ViewHistory> saved = ArgumentCaptor.forClass(ViewHistory.class);
        verify(viewHistoryRepository).save(saved.capture());
        assertTrue(counted);
        assertTrue(saved.getValue().getIsCounted());
    }

    @Test
    @DisplayName("UT-HIS-02 Xem lại trong cửa sổ khử trùng → vẫn ghi lịch sử nhưng isCounted = false")
    void repeatedViewNotCounted() {
        when(viewHistoryRepository.existsByListingIdAndUserIdAndViewedAtAfter(eq(LISTING_ID), eq(USER_ID), any()))
                .thenReturn(true);

        boolean counted = service.recordView(LISTING_ID, USER_ID, "s", "1.1.1.1", "UA", null);

        assertFalse(counted);
        verify(viewHistoryRepository).save(any());
    }

    @Test
    @DisplayName("UT-HIS-03 Xóa 1 mục lịch sử xem của người khác → FORBIDDEN (chống IDOR)")
    void deleteOthersView() {
        when(viewHistoryRepository.findById(5L)).thenReturn(Optional.of(
                ViewHistory.builder().id(5L).listingId(LISTING_ID).userId(OTHER_USER_ID).build()));

        assertThrows(ForbiddenException.class, () -> service.deleteView(5L, USER_ID));
        verify(viewHistoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("UT-HIS-04 Xóa mục lịch sử xem không tồn tại → RESOURCE_NOT_FOUND")
    void deleteMissingView() {
        when(viewHistoryRepository.findById(anyLong())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.deleteView(5L, USER_ID));
    }

    @Test
    @DisplayName("UT-HIS-05 Xóa toàn bộ lịch sử xem → xóa theo lô tới khi hết")
    void clearViewsInBatches() {
        Page<ViewHistory> batch = new PageImpl<>(List.of(ViewHistory.builder().id(1L).userId(USER_ID).build()));
        when(viewHistoryRepository.findByUserIdOrderByViewedAtDesc(eq(USER_ID), any()))
                .thenReturn(batch).thenReturn(batch).thenReturn(Page.empty());

        service.clearViews(USER_ID);

        verify(viewHistoryRepository, times(2)).deleteAllInBatch(any());
    }

    @Test
    @DisplayName("UT-HIS-06 Xóa 1 lịch sử tìm kiếm của người khác → FORBIDDEN; của mình → xóa")
    void deleteSearchHistory() {
        when(searchHistoryRepository.findById(7L)).thenReturn(Optional.of(
                SearchHistory.builder().id(7L).userId(OTHER_USER_ID).build()));
        SearchHistory mine = SearchHistory.builder().id(8L).userId(USER_ID).build();
        when(searchHistoryRepository.findById(8L)).thenReturn(Optional.of(mine));

        assertThrows(ForbiddenException.class, () -> service.deleteSearches(USER_ID, 7L));
        service.deleteSearches(USER_ID, 8L);

        verify(searchHistoryRepository).delete(mine);
    }

    @Test
    @DisplayName("UT-HIS-07 Lịch sử tìm kiếm: criteria JSON hỏng không làm lỗi cả trang")
    void listSearchesToleratesBadCriteria() {
        when(searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(
                        SearchHistory.builder().id(1L).userId(USER_ID).keyword("cầu giấy").criteria("{bad json").build())));

        var page = service.listSearches(USER_ID, org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, page.getItems().size());
    }
}
