package com.webtro.modules.moderation.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;
import com.webtro.modules.moderation.dto.request.BannedKeywordRequest;
import com.webtro.modules.moderation.dto.request.ToggleBannedKeywordRequest;
import com.webtro.modules.moderation.dto.response.BannedKeywordResponse;
import com.webtro.modules.moderation.dto.response.ToggleBannedKeywordResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Lọc từ khóa cấm ({@code [§3.3][§5.3][§11.10]}) + CRUD quản trị.
 *
 * <p><b>API cho module khác</b> ({@link #scan}, {@link #containsBanned}): module listing/interaction
 * gọi để quét tin/bình luận/tin nhắn. Danh sách từ khóa được nạp và cache
 * ({@code CacheName.BANNED_KEYWORDS}), so khớp sau khi đã {@code TextNormalizer} chuẩn hóa (bỏ dấu,
 * lowercase) để chống né bằng viết hoa/bỏ dấu.
 */
public interface BannedKeywordService {

    /**
     * Kết quả quét: mức nghiêm trọng cao nhất tìm thấy và các từ khớp.
     *
     * @param severity {@code empty} nếu sạch; {@code MILD}/{@code SEVERE} nếu có từ cấm
     * @param matched  các từ khóa (gốc) khớp
     */
    record ScanResult(Optional<BannedKeywordSeverity> severity, List<String> matched) {

        public boolean isClean() {
            return severity.isEmpty();
        }

        public boolean isSevere() {
            return severity.filter(s -> s == BannedKeywordSeverity.SEVERE).isPresent();
        }

        public boolean isMild() {
            return severity.filter(s -> s == BannedKeywordSeverity.MILD).isPresent();
        }
    }

    // ======================= API cho module khác =======================

    /** Quét một đoạn văn bản theo phạm vi áp dụng (LISTING/COMMENT/BOTH). */
    ScanResult scan(String text, BannedKeywordScope scope);

    /**
     * Văn bản có chứa từ khóa cấm (bất kể mức độ) trong phạm vi cho trước không — tiện dụng khi
     * bên gọi chỉ cần true/false.
     */
    boolean containsBanned(String text, BannedKeywordScope scope);

    // ========================= CRUD quản trị ==========================

    /** Danh sách từ khóa cấm (lọc + phân trang). */
    PageResponse<BannedKeywordResponse> list(String keyword, List<BannedKeywordSeverity> severities,
                                             List<BannedKeywordScope> scopes, boolean activeOnly,
                                             Pageable pageable);

    /** Thêm từ khóa cấm; ném {@code BANNED_KEYWORD_DUPLICATE} nếu trùng sau chuẩn hóa. */
    BannedKeywordResponse create(BannedKeywordRequest request, Long actorId);

    /** Sửa từ khóa cấm. */
    BannedKeywordResponse update(Long id, BannedKeywordRequest request, Long actorId);

    /** Xóa mềm từ khóa cấm. */
    void delete(Long id, Long actorId);

    /** Tạm bật/tắt từ khóa cấm (không xóa). */
    ToggleBannedKeywordResponse toggle(Long id, ToggleBannedKeywordRequest request, Long actorId);
}
