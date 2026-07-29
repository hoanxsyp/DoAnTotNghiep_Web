package com.webtro.modules.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Cấu hình AI gom theo phân hệ (canonical 4.19.5). Tất cả các khóa là {@code ai.*}/{@code trust.*}
 * trong {@code system_configs}; endpoint này chỉ trình bày lại theo nhóm cho Admin.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AiConfigResponse", description = "Cấu hình AI theo phân hệ")
public class AiConfigResponse {

    @Schema(description = "Bật/tắt từng module AI (khóa *.enabled)")
    private List<ConfigItemResponse> modules;

    @Schema(description = "Ngưỡng phân tích cảm xúc (ai.sentiment.*)")
    private List<ConfigItemResponse> sentiment;

    @Schema(description = "Cấu hình gợi ý tin (ai.recommendation.*)")
    private List<ConfigItemResponse> recommendation;

    @Schema(description = "Cấu hình dự đoán giá (ai.price.*)")
    private List<ConfigItemResponse> price;

    @Schema(description = "Cấu hình chatbot (ai.chatbot.*)")
    private List<ConfigItemResponse> chatbot;

    @Schema(description = "Trọng số điểm uy tín (trust.*)")
    private List<ConfigItemResponse> trustWeights;
}
