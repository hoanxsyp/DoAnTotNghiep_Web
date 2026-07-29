package com.webtro.modules.interaction.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.PageResponse;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.interaction.dto.response.SearchHistoryResponse;
import com.webtro.modules.interaction.dto.response.ViewHistoryResponse;
import com.webtro.modules.interaction.entity.SearchHistory;
import com.webtro.modules.interaction.entity.ViewHistory;
import com.webtro.modules.interaction.mapper.HistoryMapper;
import com.webtro.modules.interaction.repository.FavoriteRepository;
import com.webtro.modules.interaction.repository.SearchHistoryRepository;
import com.webtro.modules.interaction.repository.ViewHistoryRepository;
import com.webtro.modules.interaction.service.HistoryService;
import com.webtro.modules.interaction.spi.ListingGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Hiện thực nghiệp vụ lịch sử xem/tìm kiếm — canonical 4.5.4–4.5.8.
 *
 * <p><b>Ghi chú schema:</b> hai bảng {@code view_histories}/{@code search_histories} là append-only,
 * KHÔNG có cột {@code deleted_at} (xem Javadoc entity). Tài liệu 4.5.5/4.5.8 mô tả "xóa mềm" nhưng
 * schema không cho phép → ở đây xóa <i>cứng</i>. Đây là dữ liệu riêng tư, xóa cứng đúng tinh thần
 * quyền riêng tư người dùng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryServiceImpl implements HistoryService {

    private static final int PURGE_BATCH = 500;

    private final ViewHistoryRepository viewHistoryRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final FavoriteRepository favoriteRepository;
    private final ListingGateway listingGateway;
    private final HistoryMapper historyMapper;
    private final SystemConfigService systemConfig;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public boolean recordView(Long listingId, Long userId, String sessionId, String ipAddress,
                              String userAgent, String referrer) {
        int dedupMinutes = systemConfig.getInt(ConfigKey.VIEW_DEDUP_MINUTES);
        Instant since = Instant.now().minus(Duration.ofMinutes(dedupMinutes));

        boolean recentlyViewed;
        if (userId != null) {
            recentlyViewed = viewHistoryRepository
                    .existsByListingIdAndUserIdAndViewedAtAfter(listingId, userId, since);
        } else if (ipAddress != null) {
            recentlyViewed = viewHistoryRepository
                    .existsByListingIdAndIpAddressAndViewedAtAfter(listingId, ipAddress, since);
        } else {
            recentlyViewed = false;
        }
        boolean counted = !recentlyViewed;

        ViewHistory view = ViewHistory.builder()
                .listingId(listingId)
                .userId(userId)
                .sessionId(sessionId)
                .ipAddress(ipAddress)
                .userAgent(trim(userAgent, 255))
                .referrer(trim(referrer, 500))
                .isCounted(counted)
                .build();
        viewHistoryRepository.save(view);
        return counted;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ViewHistoryResponse> listViews(Long userId, LocalDate from, LocalDate to,
                                                       Pageable pageable) {
        // Ghi chú: ViewHistoryRepository (đã chốt) không có finder lọc theo khoảng ngày; tham số
        // from/to được nhận nhưng không lọc ở tầng DB. Cần bổ sung finder có @Query nếu muốn lọc.
        Page<ViewHistory> page = viewHistoryRepository.findByUserIdOrderByViewedAtDesc(userId, pageable);
        List<Long> listingIds = page.getContent().stream().map(ViewHistory::getListingId).toList();
        Map<Long, ListingGateway.ListingBrief> briefs = listingGateway.getBriefs(listingIds);
        return PageResponse.from(page, v -> {
            boolean favoritedByMe = favoriteRepository
                    .existsByUserIdAndListingIdAndDeletedAtIsNull(userId, v.getListingId());
            return historyMapper.toViewResponse(v, briefs.get(v.getListingId()), favoritedByMe);
        });
    }

    @Override
    @Transactional
    public void clearViews(Long userId) {
        Page<ViewHistory> batch;
        do {
            batch = viewHistoryRepository.findByUserIdOrderByViewedAtDesc(userId,
                    PageRequest.of(0, PURGE_BATCH));
            if (!batch.isEmpty()) {
                viewHistoryRepository.deleteAllInBatch(batch.getContent());
            }
        } while (!batch.isEmpty());
    }

    @Override
    @Transactional
    public void deleteView(Long id, Long userId) {
        ViewHistory view = viewHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!userId.equals(view.getUserId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        viewHistoryRepository.delete(view);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SearchHistoryResponse> listSearches(Long userId, Pageable pageable) {
        Page<SearchHistory> page = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return PageResponse.from(page, this::toSearchResponse);
    }

    @Override
    @Transactional
    public void deleteSearches(Long userId, Long id) {
        if (id == null) {
            Page<SearchHistory> batch;
            do {
                batch = searchHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId,
                        PageRequest.of(0, PURGE_BATCH));
                if (!batch.isEmpty()) {
                    searchHistoryRepository.deleteAllInBatch(batch.getContent());
                }
            } while (!batch.isEmpty());
            return;
        }
        SearchHistory history = searchHistoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!userId.equals(history.getUserId())) {
            throw new ForbiddenException(ErrorCode.FORBIDDEN);
        }
        searchHistoryRepository.delete(history);
    }

    private SearchHistoryResponse toSearchResponse(SearchHistory history) {
        JsonNode filters = null;
        try {
            if (history.getCriteria() != null && !history.getCriteria().isBlank()) {
                filters = objectMapper.readTree(history.getCriteria());
            }
        } catch (Exception e) {
            log.warn("Không parse được criteria của search_history id={}: {}", history.getId(), e.getMessage());
        }
        String summary = history.getKeyword() != null && !history.getKeyword().isBlank()
                ? history.getKeyword() : null;
        return historyMapper.toSearchResponse(history, filters, summary);
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
