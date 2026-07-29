package com.webtro.modules.ai.dto.response;

import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.modules.ai.dto.response.PricePredictionResponse.PriceRange;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một dòng lịch sử dự đoán giá của một tin (docs/03 mục 7.4.2).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PricePredictionHistoryResponse", description = "Lịch sử một lần dự đoán giá")
public class PricePredictionHistoryResponse {

    private Long id;
    private Long listingId;
    private BigDecimal suggestedPrice;
    private PriceRange priceRange;
    private PriceConfidence confidence;
    private Integer sampleSize;
    private AdministrativeUnitType comparableScope;
    private BigDecimal inputPrice;
    private BigDecimal deviationRatio;
    private Boolean deviationFlagged;
    private Boolean applied;
    private Instant createdAt;
}
