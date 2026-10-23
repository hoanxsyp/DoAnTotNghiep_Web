package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Kết quả tạo tin nháp (docs/03 mục 4.4.7).
 *
 * <p>Dự đoán giá được frontend gọi qua module AI sau khi tạo để gắn lịch sử vào id của tin.
 * Module AI lỗi hoặc bị tắt không làm thất bại thao tác tạo tin {@code [§9.4]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingCreateResponse {

    private Long id;
    private String slug;
    private String title;
    private String status;
    private BigDecimal price;
    private BigDecimal area;
    private Integer imageCount;
    private Instant createdAt;
    private Instant expectedExpiredAt;
    private Integer displayDays;
    private List<String> nextSteps;
}
