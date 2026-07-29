package com.webtro.modules.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu kèm lý do dùng chung cho các thao tác kiểm duyệt tin không bắt buộc lý do: ẩn
 * (4.14, /hide), bỏ ẩn (/unhide), đánh dấu cần kiểm tra (/flag-need-review) và bỏ đánh dấu
 * (/clear-need-review). {@code reason} không bắt buộc; khi bỏ trống service dùng lý do mặc định
 * theo từng thao tác.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ListingModerationReasonRequest", description = "Lý do thao tác kiểm duyệt tin (không bắt buộc)")
public class ListingModerationReasonRequest {

    @Schema(description = "Lý do thao tác (tối đa 500 ký tự; bỏ trống sẽ dùng lý do mặc định)")
    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    private String reason;
}
