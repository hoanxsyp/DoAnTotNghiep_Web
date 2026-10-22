package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Kết quả dò trùng địa chỉ trước khi tạo phòng (RU-6). Wizard đăng tin dùng để cảnh báo sớm thay
 * vì đợi lỗi 409 lúc gửi.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalUnitDuplicateCheckResponse {
    /** Trùng cả địa chỉ lẫn số phòng với phòng của chính bạn → tạo phòng mới sẽ bị chặn. */
    private Boolean duplicate;
    /** Phòng trùng hẳn (nếu có) — gợi ý chọn phòng này. */
    private RentalUnitResponse duplicateUnit;
    /** Các phòng khác của bạn cùng địa chỉ (khác số phòng) — có thể bạn muốn chọn một trong số này. */
    private List<RentalUnitResponse> sameAddressUnits;
    /** Trùng với phòng của chủ trọ khác → vẫn cho tạo, tin sẽ được gắn cờ kiểm tra. */
    private Boolean otherOwnerMatch;
}
