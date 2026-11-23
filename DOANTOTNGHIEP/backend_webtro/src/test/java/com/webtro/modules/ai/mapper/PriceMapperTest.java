package com.webtro.modules.ai.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.modules.ai.dto.response.PricePredictionResponse;
import com.webtro.modules.ai.engine.PriceEstimator.PriceEstimate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PriceMapperTest {

    @Test
    void responseIncludesConfiguredDeviationThresholdAndNeverBlocksPosting() {
        PriceMapper mapper = new PriceMapper(new ObjectMapper());
        PriceEstimate estimate = new PriceEstimate(
                true,
                PriceConfidence.MEDIUM,
                new BigDecimal("0.70"),
                new BigDecimal("3000000"),
                new BigDecimal("2500000"),
                new BigDecimal("3000000"),
                new BigDecimal("3500000"),
                new BigDecimal("150000"),
                20,
                AdministrativeUnitType.WARD,
                new BigDecimal("0.15"),
                new BigDecimal("3000000"),
                List.of(),
                new BigDecimal("0.50"),
                true,
                "Kết quả kiểm thử");

        PricePredictionResponse response = mapper.toResponse(
                null, estimate, false, 180, new BigDecimal("0.35"));

        assertEquals(new BigDecimal("0.35"), response.getComparison().getThresholdRatio());
        assertFalse(response.getComparison().getBlocksPosting());
    }
}
