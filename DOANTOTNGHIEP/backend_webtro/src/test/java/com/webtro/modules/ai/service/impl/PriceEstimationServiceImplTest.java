package com.webtro.modules.ai.service.impl;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.ForbiddenException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.dto.request.PricePredictionRequest;
import com.webtro.modules.ai.engine.AiModuleSupport;
import com.webtro.modules.ai.engine.PriceEstimator;
import com.webtro.modules.ai.mapper.PriceMapper;
import com.webtro.modules.ai.repository.PredictionHistoryRepository;
import com.webtro.modules.ai.spi.ListingDataGateway;
import com.webtro.modules.catalog.service.AmenityService;
import com.webtro.modules.catalog.service.CategoryService;
import com.webtro.modules.catalog.service.ProvinceService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PriceEstimationServiceImplTest {

    @Test
    void predictionCannotAttachHistoryToAnotherOwnersListing() {
        PriceEstimator estimator = mock(PriceEstimator.class);
        ListingDataGateway listingGateway = mock(ListingDataGateway.class);
        AiModuleSupport support = mock(AiModuleSupport.class);
        PriceEstimationServiceImpl service = new PriceEstimationServiceImpl(
                estimator,
                mock(PredictionHistoryRepository.class),
                listingGateway,
                mock(CategoryService.class),
                mock(ProvinceService.class),
                mock(AmenityService.class),
                mock(SystemConfigService.class),
                mock(PriceMapper.class),
                support);
        PricePredictionRequest request = PricePredictionRequest.builder().listingId(99L).build();
        when(listingGateway.ownerOf(99L)).thenReturn(Optional.of(2L));

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> service.predict(request, 1L));

        assertEquals(ErrorCode.LISTING_FORBIDDEN, exception.getErrorCode());
        verifyNoInteractions(estimator);
    }
}
