package com.webtro.modules.listing.dto.request;

import com.webtro.common.enums.CloseReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Yêu cầu đóng tin (LIST-07, docs/03 mục 4.4.13).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CloseListingRequest {

    @NotNull(message = "Vui lòng chọn lý do đóng tin")
    private CloseReason reason;

    @Size(max = 255, message = "Ghi chú tối đa 255 ký tự")
    private String note;
}
