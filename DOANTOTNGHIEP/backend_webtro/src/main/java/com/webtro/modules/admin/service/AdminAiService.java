package com.webtro.modules.admin.service;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AiModule;
import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.AiAlertResponse;
import com.webtro.modules.admin.dto.response.AiConfigResponse;
import com.webtro.modules.admin.dto.response.AiLogResponse;
import com.webtro.modules.admin.dto.response.AiPriceDeviationResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

/**
 * Quản trị AI (canonical 4.19): xem/sửa cấu hình AI ({@code AI_CONFIG_MANAGE}) và xem log AI
 * ({@code AI_LOG_VIEW}). Cấu hình AI là tập khóa {@code ai.*}/{@code trust.*} trong {@code system_configs}.
 */
public interface AdminAiService {

    /** Xem cấu hình AI gom theo phân hệ (canonical 4.19.5). */
    AiConfigResponse getConfig();

    /** Cập nhật cấu hình AI; audit {@code AI_CONFIG_CHANGE}; queue recalc điểm uy tín nếu đổi trọng số. */
    UpdateConfigResponse updateConfig(UpdateConfigRequest request, Long actorId);

    /** Xem log AI của một module (canonical 4.19.1); khoảng {@code from..to} tối đa 90 ngày. */
    AiLogResponse getLogs(AiModule module, Instant from, Instant to, Pageable pageable);

    /**
     * Danh sách tin bị AI đánh dấu cần kiểm tra do cảm xúc tiêu cực ({@code need_review_count > 0}),
     * kèm tỷ lệ tiêu cực — cần {@code AI_LOG_VIEW}.
     */
    PageResponse<AiAlertResponse> getSentimentAlerts(Pageable pageable);

    /**
     * Danh sách tin bị AI đánh cờ lệch giá ({@code price_deviation_flag = true}) — cần {@code AI_LOG_VIEW}.
     */
    PageResponse<AiPriceDeviationResponse> getPriceDeviations(Pageable pageable);
}
