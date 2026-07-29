package com.webtro.modules.moderation.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.moderation.dto.request.BannedKeywordRequest;
import com.webtro.modules.moderation.dto.request.ToggleBannedKeywordRequest;
import com.webtro.modules.moderation.dto.response.BannedKeywordResponse;
import com.webtro.modules.moderation.dto.response.ToggleBannedKeywordResponse;
import com.webtro.modules.moderation.entity.BannedKeyword;
import com.webtro.modules.moderation.mapper.BannedKeywordMapper;
import com.webtro.modules.moderation.repository.BannedKeywordRepository;
import com.webtro.modules.moderation.service.BannedKeywordService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Hiện thực lọc từ khóa cấm + CRUD quản trị (canonical 4.20.4–4.20.8, {@code [§3.3][§5.3]}).
 *
 * <p>So khớp sau khi chuẩn hóa ({@link TextNormalizer#removeAccents}) để chống né bằng viết
 * hoa/bỏ dấu ({@code [§5.4]}). Nguồn dữ liệu lấy qua {@link BannedKeywordCache} (có cache Redis).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannedKeywordServiceImpl implements BannedKeywordService {

    private final BannedKeywordRepository repository;
    private final BannedKeywordMapper mapper;
    private final BannedKeywordCache cache;

    // ============================ Quét ============================

    @Override
    public ScanResult scan(String text, BannedKeywordScope scope) {
        if (text == null || text.isBlank()) {
            return new ScanResult(Optional.empty(), List.of());
        }
        String normalized = TextNormalizer.removeAccents(text);
        List<String> matched = new ArrayList<>();
        BannedKeywordSeverity highest = null;

        for (BannedKeywordCache.Snapshot s : cache.activeSnapshots()) {
            if (!appliesTo(s.appliesTo(), scope)) {
                continue;
            }
            if (hits(s, normalized)) {
                matched.add(s.keyword());
                highest = higher(highest, s.severity());
            }
        }
        return new ScanResult(Optional.ofNullable(highest), matched);
    }

    @Override
    public boolean containsBanned(String text, BannedKeywordScope scope) {
        return !scan(text, scope).isClean();
    }

    /** Từ khóa có áp dụng cho phạm vi đang quét không. */
    private boolean appliesTo(BannedKeywordScope keywordScope, BannedKeywordScope callerScope) {
        if (keywordScope == BannedKeywordScope.BOTH || callerScope == BannedKeywordScope.BOTH) {
            return true;
        }
        return keywordScope == callerScope;
    }

    /** Kiểm tra một từ khóa (đã chuẩn hóa) có khớp văn bản đã chuẩn hóa không. */
    private boolean hits(BannedKeywordCache.Snapshot s, String normalizedText) {
        String needle = s.normalizedKeyword();
        if (needle == null || needle.isBlank()) {
            return false;
        }
        if (s.regex()) {
            try {
                return Pattern.compile(needle, Pattern.CASE_INSENSITIVE).matcher(normalizedText).find();
            } catch (PatternSyntaxException e) {
                log.warn("Từ khóa cấm regex không hợp lệ, bỏ qua: {}", needle);
                return false;
            }
        }
        return normalizedText.contains(needle);
    }

    private BannedKeywordSeverity higher(BannedKeywordSeverity current, BannedKeywordSeverity candidate) {
        if (current == BannedKeywordSeverity.SEVERE || candidate == BannedKeywordSeverity.SEVERE) {
            return BannedKeywordSeverity.SEVERE;
        }
        return candidate != null ? candidate : current;
    }

    // ============================ CRUD ============================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<BannedKeywordResponse> list(String keyword, List<BannedKeywordSeverity> severities,
                                                    List<BannedKeywordScope> scopes, boolean activeOnly,
                                                    Pageable pageable) {
        // BannedKeywordRepository (đã chốt) không có Specification/paged-filter cho tập "chưa xóa mềm,
        // gồm cả bản ghi tắt". Bảng từ khóa cấm nhỏ nên lọc + phân trang trong bộ nhớ trên tập chưa
        // xóa mềm là chấp nhận được; xem "điểm quyết định" ở báo cáo bàn giao.
        String needle = keyword == null ? null : TextNormalizer.removeAccents(keyword);

        List<BannedKeyword> all = repository.findAll().stream()
                .filter(k -> !k.isDeleted())
                .filter(k -> !activeOnly || Boolean.TRUE.equals(k.getIsActive()))
                .filter(k -> severities == null || severities.isEmpty() || severities.contains(k.getSeverity()))
                .filter(k -> scopes == null || scopes.isEmpty() || scopes.contains(k.getAppliesTo()))
                .filter(k -> needle == null || needle.isBlank()
                        || k.getNormalizedKeyword().contains(needle))
                .sorted(Comparator.comparing(BannedKeyword::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int total = all.size();
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
        int to = (int) Math.min((long) from + pageable.getPageSize(), total);
        List<BannedKeywordResponse> items = all.subList(from, to).stream().map(mapper::toResponse).toList();

        int totalPages = pageable.getPageSize() == 0 ? 0
                : (int) Math.ceil((double) total / pageable.getPageSize());
        return PageResponse.<BannedKeywordResponse>builder()
                .items(items)
                .page(pageable.getPageNumber())
                .size(pageable.getPageSize())
                .totalElements(total)
                .totalPages(totalPages)
                .first(pageable.getPageNumber() == 0)
                .last(to >= total)
                .build();
    }

    @Override
    @Transactional
    public BannedKeywordResponse create(BannedKeywordRequest request, Long actorId) {
        String normalized = normalize(request.getKeyword());
        if (repository.existsByNormalizedKeywordAndDeletedAtIsNull(normalized)) {
            throw new ConflictException(ErrorCode.BANNED_KEYWORD_DUPLICATE);
        }
        BannedKeyword entity = BannedKeyword.builder()
                .keyword(request.getKeyword().trim())
                .normalizedKeyword(normalized)
                .severity(request.getSeverity())
                .appliesTo(request.getAppliesTo())
                .isRegex(Boolean.TRUE.equals(request.getIsRegex()))
                .category(sanitizeShort(request.getCategory()))
                .note(sanitizeShort(request.getNote()))
                .isActive(request.getActive() == null || request.getActive())
                .hitCount(0)
                .build();
        entity = repository.save(entity);
        cache.invalidate();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public BannedKeywordResponse update(Long id, BannedKeywordRequest request, Long actorId) {
        BannedKeyword entity = getAlive(id);
        String normalized = normalize(request.getKeyword());
        // Nếu đổi từ khóa sang một chuẩn hóa đã tồn tại ở bản ghi khác -> trùng.
        if (!normalized.equals(entity.getNormalizedKeyword())
                && repository.existsByNormalizedKeywordAndDeletedAtIsNull(normalized)) {
            throw new ConflictException(ErrorCode.BANNED_KEYWORD_DUPLICATE);
        }
        entity.setKeyword(request.getKeyword().trim());
        entity.setNormalizedKeyword(normalized);
        entity.setSeverity(request.getSeverity());
        entity.setAppliesTo(request.getAppliesTo());
        entity.setIsRegex(Boolean.TRUE.equals(request.getIsRegex()));
        entity.setCategory(sanitizeShort(request.getCategory()));
        entity.setNote(sanitizeShort(request.getNote()));
        if (request.getActive() != null) {
            entity.setIsActive(request.getActive());
        }
        entity = repository.save(entity);
        cache.invalidate();
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional
    public void delete(Long id, Long actorId) {
        BannedKeyword entity = getAlive(id);
        entity.softDelete();
        repository.save(entity);
        cache.invalidate();
    }

    @Override
    @Transactional
    public ToggleBannedKeywordResponse toggle(Long id, ToggleBannedKeywordRequest request, Long actorId) {
        BannedKeyword entity = getAlive(id);
        boolean previous = Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(request.getActive());
        entity = repository.save(entity);
        cache.invalidate();
        return ToggleBannedKeywordResponse.builder()
                .id(entity.getId())
                .keyword(entity.getKeyword())
                .active(Boolean.TRUE.equals(entity.getIsActive()))
                .previousActive(previous)
                .hitCount(entity.getHitCount() == null ? 0 : entity.getHitCount())
                .cacheInvalidated(List.of(com.webtro.constant.CacheName.BANNED_KEYWORDS))
                .updatedAt(entity.getUpdatedAt() == null ? Instant.now() : entity.getUpdatedAt())
                .build();
    }

    // ============================ Helper ============================

    private BannedKeyword getAlive(Long id) {
        return repository.findById(id)
                .filter(k -> !k.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BANNED_KEYWORD_NOT_FOUND));
    }

    private String normalize(String keyword) {
        return TextNormalizer.removeAccents(keyword);
    }

    private String sanitizeShort(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return HtmlSanitizer.stripAllHtml(value);
    }
}
