package com.webtro.modules.interaction.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Trang danh sách người liên hệ của chủ trọ kèm thống kê tổng (canonical 4.6.3).
 * Dùng {@link JsonUnwrapped} để phẳng các trường phân trang vào cùng cấp với {@code summary},
 * đúng hình dạng JSON canonical mục 7.1.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "LandlordContactPageResponse", description = "Trang người liên hệ + thống kê")
public class LandlordContactPageResponse {

    @JsonUnwrapped
    private PageResponse<LandlordContactResponse> page;

    private ContactSummary summary;

    /** Thống kê số lượt liên hệ theo hình thức. */
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "ContactSummary", description = "Thống kê lượt liên hệ")
    public static class ContactSummary {
        private long totalContacts;
        private long viewPhone;
        private long sendForm;
        private long startChat;
    }
}
