package com.webtro.modules.moderation.service.impl;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.constant.CacheName;
import com.webtro.modules.moderation.repository.BannedKeywordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bộ nạp có cache cho danh sách từ khóa cấm đang bật ({@code [§11.11]}).
 *
 * <p>Tách khỏi {@code BannedKeywordServiceImpl} để {@code @Cacheable}/{@code @CacheEvict} đi qua
 * proxy Spring (self-invocation trong cùng bean sẽ vô hiệu hóa cache). Chỉ nạp bản ghi
 * {@code is_active = true} và chưa xóa mềm — tắt qua {@code /toggle} hoặc xóa mềm có hiệu lực ngay
 * sau khi cache được invalidate, không cần restart.
 *
 * <p>Cache dữ liệu → fail-open (canonical mục 8): Redis lỗi thì đọc thẳng DB.
 */
@Component
@RequiredArgsConstructor
public class BannedKeywordCache {

    private final BannedKeywordRepository repository;

    /**
     * Ảnh chụp một từ khóa cấm ở dạng dữ liệu thuần (serialize được cho Redis, không mang Pattern
     * hay entity JPA).
     *
     * @param keyword           từ khóa gốc (để trả về trong thông báo)
     * @param normalizedKeyword từ khóa đã chuẩn hóa (bỏ dấu, lowercase) để so khớp
     * @param severity          mức độ
     * @param appliesTo         phạm vi áp dụng
     * @param regex             có phải regex không
     */
    public record Snapshot(String keyword, String normalizedKeyword, BannedKeywordSeverity severity,
                           BannedKeywordScope appliesTo, boolean regex) {
    }

    /** Danh sách từ khóa đang bật (có cache). */
    @Cacheable(CacheName.BANNED_KEYWORDS)
    public List<Snapshot> activeSnapshots() {
        return repository.findByIsActiveTrueAndDeletedAtIsNull().stream()
                .map(k -> new Snapshot(
                        k.getKeyword(),
                        k.getNormalizedKeyword(),
                        k.getSeverity(),
                        k.getAppliesTo(),
                        Boolean.TRUE.equals(k.getIsRegex())))
                .toList();
    }

    /** Xóa cache khi Admin thêm/sửa/xóa/bật-tắt từ khóa. */
    @CacheEvict(value = CacheName.BANNED_KEYWORDS, allEntries = true)
    public void invalidate() {
        // Chỉ để kích hoạt @CacheEvict; không có thân xử lý.
    }
}
