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
 * <p>Khối dự đoán giá ({@code pricePrediction}) do module AI cung cấp — khi AI chưa sẵn sàng thì
 * {@code available = false}, tin vẫn được tạo bình thường {@code [§9.4]}.
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
