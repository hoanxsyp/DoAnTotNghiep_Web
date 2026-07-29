package com.webtro.modules.admin.service;

import com.webtro.modules.admin.dto.request.UpdateConfigRequest;
import com.webtro.modules.admin.dto.response.SystemConfigResponse;
import com.webtro.modules.admin.dto.response.UpdateConfigResponse;

/**
 * Quản trị cấu hình hệ thống (canonical 4.20.1–4.20.2) — quyền {@code SYSTEM_CONFIG_MANAGE}. Không
 * đụng nhóm {@code ai.*}/{@code trust.*} (thuộc {@code /api/admin/ai/config}, quyền
 * {@code AI_CONFIG_MANAGE}).
 */
public interface AdminSystemConfigService {

    /** Xem cấu hình hệ thống gom nhóm; {@code group} null/"ALL" trả toàn bộ (trừ nhóm ai và trust). */
    SystemConfigResponse getConfigs(String group);

    /** Cập nhật một hoặc nhiều cấu hình hệ thống; ghi audit {@code SYSTEM_CONFIG_CHANGE}. */
    UpdateConfigResponse updateConfigs(UpdateConfigRequest request, Long actorId);
}
