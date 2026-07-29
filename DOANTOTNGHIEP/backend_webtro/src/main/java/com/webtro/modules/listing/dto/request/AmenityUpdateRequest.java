package com.webtro.modules.listing.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Yêu cầu thay thế toàn bộ tiện ích của tin (LIST-12, docs/03 mục 4.4.19). Cho phép mảng rỗng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AmenityUpdateRequest {

    @NotNull(message = "Danh sách tiện ích không được null")
    @Size(max = 30, message = "Chỉ được chọn tối đa 30 tiện ích")
    private List<Long> amenityIds;
}
