package com.webtro.modules.moderation.service.impl;

import com.webtro.common.enums.BannedKeywordScope;
import com.webtro.modules.interaction.spi.BannedKeywordGateway;
import com.webtro.modules.moderation.service.BannedKeywordService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bộ nối cung cấp {@link BannedKeywordGateway} (SPI do module {@code interaction} sở hữu) bằng
 * cách ủy quyền cho {@link BannedKeywordService} của module này — canonical luật 4.
 *
 * <p>Module {@code interaction} (bình luận/đánh giá/liên hệ/tin nhắn) gọi
 * {@code bannedKeywordGateway.scan(...)}; ở đây ta chỉ chuyển tiếp sang service thật và ánh xạ
 * kiểu {@code ScanResult} tương ứng.
 */
@Component
@RequiredArgsConstructor
public class BannedKeywordGatewayAdapter implements BannedKeywordGateway {

    private final BannedKeywordService bannedKeywordService;

    @Override
    public ScanResult scan(String text, BannedKeywordScope scope) {
        BannedKeywordService.ScanResult r = bannedKeywordService.scan(text, scope);
        return new ScanResult(r.severity(), r.matched());
    }
}
