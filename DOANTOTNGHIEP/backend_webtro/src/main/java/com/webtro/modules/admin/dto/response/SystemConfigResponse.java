package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Cấu hình hệ thống gom nhóm (canonical 4.20.1). Nhóm {@code TRUST}/{@code AI} không nằm ở đây —
 * chúng xem/sửa tại {@code /api/admin/ai/config} (ranh giới quyền {@code AI_CONFIG_MANAGE}).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "SystemConfigResponse", description = "Cấu hình hệ thống theo nhóm")
public class SystemConfigResponse {

    @Schema(description = "Các nhóm cấu hình")
    private List<Group> groups;

    @Schema(description = "Ghi chú")
    private String note;

    /** Một nhóm cấu hình (gom theo prefix/group_name). */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "SystemConfigGroup", description = "Một nhóm cấu hình")
    public static class Group {

        @Schema(description = "Mã nhóm", example = "LISTING")
        private String group;

        @Schema(description = "Nhãn nhóm", example = "Tin đăng")
        private String label;

        @Schema(description = "Các khóa cấu hình trong nhóm")
        private List<ConfigItemResponse> configs;
    }
}
