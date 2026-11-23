package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Khối "phòng" gắn kèm tin đăng: đánh giá thuộc về phòng nên điểm ở đây là điểm của phòng qua mọi
 * lần đăng tin (RE-2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RentalUnitSummaryResponse {
    private Long id;
    private String name;
    private String unitLabel;
    private BigDecimal averageRating;
    private Integer reviewCount;
    /** Phòng đã có đánh giá nên không đổi được địa chỉ/số phòng (RU-4). */
    private Boolean addressLocked;
}
