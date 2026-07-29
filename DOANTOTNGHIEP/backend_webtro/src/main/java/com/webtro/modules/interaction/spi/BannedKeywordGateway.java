package com.webtro.modules.interaction.spi;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.common.enums.BannedKeywordSeverity;

import java.util.List;
import java.util.Optional;

/**
 * Cổng (SPI) mà module {@code interaction} cần từ module {@code moderation} để quét từ khóa cấm
 * trong bình luận / tin nhắn / nội dung liên hệ — canonical luật 4, {@code [§3.11][§5.3]}.
 *
 * <p>Task mô tả có validator {@code @NoBannedKeyword} nhưng hiện tại
 * {@code com.webtro.validator} chưa có nó (chỉ có {@code @NoHtml}, {@code @ValidPhone},
 * {@code @ValidPassword}). Việc quét từ khóa cấm phụ thuộc dữ liệu bảng {@code banned_keywords}
 * (do module moderation sở hữu) nên phải là một service, không thể là annotation thuần. Module
 * {@code moderation} phải cung cấp một {@code @Component} hiện thực interface này.
 */
public interface BannedKeywordGateway {

    /**
     * Kết quả quét: mức nghiêm trọng cao nhất tìm thấy và danh sách từ khớp.
     *
     * @param severity {@code empty} nếu sạch; {@code MILD} hoặc {@code SEVERE} nếu có từ cấm
     * @param matched  các từ khóa cấm khớp (để trả về message chi tiết)
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

    /** Quét một đoạn văn bản theo phạm vi áp dụng (LISTING/COMMENT/BOTH). */
    ScanResult scan(String text, BannedKeywordScope scope);
}
