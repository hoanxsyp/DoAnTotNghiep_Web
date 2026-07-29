package com.webtro.modules.ai.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.CurfewType;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.ToiletType;
import com.webtro.constant.ConfigKey;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ForbiddenException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.ai.dto.request.PricePredictionRequest;
import com.webtro.modules.ai.dto.response.PricePredictionHistoryResponse;
import com.webtro.modules.ai.dto.response.PricePredictionResponse;
import com.webtro.modules.ai.engine.AiModuleSupport;
import com.webtro.modules.ai.engine.PriceEstimator;
import com.webtro.modules.ai.engine.PriceEstimator.HedonicWeights;
import com.webtro.modules.ai.engine.PriceEstimator.PriceEstimate;
import com.webtro.modules.ai.engine.PriceEstimator.PriceInput;
import com.webtro.modules.ai.entity.PredictionHistory;
import com.webtro.modules.ai.mapper.PriceMapper;
import com.webtro.modules.ai.repository.PredictionHistoryRepository;
import com.webtro.modules.ai.service.PriceEstimationService;
import com.webtro.modules.ai.spi.ListingDataGateway;
import com.webtro.modules.ai.spi.ListingDataGateway.ComparableListing;
import com.webtro.modules.ai.spi.ListingDataGateway.ComparableQuery;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.service.AmenityService;
import com.webtro.modules.catalog.service.CategoryService;
import com.webtro.modules.catalog.service.ProvinceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Hiện thực nghiệp vụ dự đoán giá (canonical mục 10.4, §9.4). Lưu {@code PredictionHistory} MỌI lần
 * (kể cả thiếu dữ liệu) để phục vụ báo cáo chất lượng AI; TUYỆT ĐỐI không chặn đăng tin khi lệch giá.
 *
 * <p>Không đặt {@code @Transactional} ở cấp lớp: mỗi lần lưu {@code PredictionHistory} là một
 * transaction độc lập (qua repository), nên vẫn lưu được lịch sử kể cả khi sau đó ném
 * {@code AI_INSUFFICIENT_DATA} (422).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PriceEstimationServiceImpl implements PriceEstimationService {

    private static final int COMPARABLE_FETCH_LIMIT = 300;

    private final PriceEstimator estimator;
    private final PredictionHistoryRepository predictionHistoryRepository;
    private final ListingDataGateway listingGateway;
    private final CategoryService categoryService;
    private final ProvinceService provinceService;
    private final AmenityService amenityService;
    private final SystemConfigService config;
    private final PriceMapper mapper;
    private final AiModuleSupport support;

    @Override
    public PricePredictionResponse predict(PricePredictionRequest request, Long userId) {
        support.ensureEnabled(ConfigKey.AI_PRICE_ENABLED);
        validateReferences(request);

        boolean hasElevator = false;
        boolean hasParking = false;
        if (request.getAmenityIds() != null && !request.getAmenityIds().isEmpty()) {
            List<AmenityResponse> amenities = amenityService.validateAndGet(request.getAmenityIds());
            for (AmenityResponse a : amenities) {
                if ("ELEVATOR".equalsIgnoreCase(a.getCode())) {
                    hasElevator = true;
                }
                if ("PARKING".equalsIgnoreCase(a.getCode())) {
                    hasParking = true;
                }
            }
        }

        PriceInput input = new PriceInput(
                request.getCategoryId(), request.getProvinceId(), request.getDistrictId(),
                request.getWardId(), request.getArea(), request.getRoomCount(), request.getToiletCount(),
                request.getFurnitureStatus() == null ? FurnitureStatus.NONE : request.getFurnitureStatus(),
                request.getToiletType() == null ? ToiletType.PRIVATE : request.getToiletType(),
                request.getAmenityIds(),
                Boolean.TRUE.equals(request.getIsStreetFront()),
                hasElevator, hasParking,
                request.getCurfewType() == null ? CurfewType.UNKNOWN : request.getCurfewType(),
                request.getInputPrice());

        int minSamples = config.getInt(ConfigKey.AI_PRICE_MIN_SAMPLES);
        int comparableDays = config.getInt(ConfigKey.AI_PRICE_COMPARABLE_DAYS);
        BigDecimal tolerance = config.getDecimal(ConfigKey.AI_PRICE_COMPARABLE_AREA_TOLERANCE);
        BigDecimal deviationFlagRatio = config.getDecimal(ConfigKey.AI_PRICE_DEVIATION_FLAG_RATIO);
        HedonicWeights weights = readHedonicWeights();

        BigDecimal areaMin = request.getArea().multiply(BigDecimal.ONE.subtract(tolerance));
        BigDecimal areaMax = request.getArea().multiply(BigDecimal.ONE.add(tolerance));
        Instant since = Instant.now().minus(comparableDays, ChronoUnit.DAYS);

        // Nới dần phạm vi WARD → DISTRICT → PROVINCE cho tới khi đủ mẫu.
        AdministrativeUnitType scopeUsed = AdministrativeUnitType.WARD;
        List<ComparableListing> comparables = fetchComparables(request, AdministrativeUnitType.WARD,
                areaMin, areaMax, since);
        if (comparables.size() < minSamples) {
            List<ComparableListing> byDistrict = fetchComparables(request, AdministrativeUnitType.DISTRICT,
                    areaMin, areaMax, since);
            if (byDistrict.size() >= comparables.size()) {
                comparables = byDistrict;
                scopeUsed = AdministrativeUnitType.DISTRICT;
            }
        }
        if (comparables.size() < minSamples) {
            List<ComparableListing> byProvince = fetchComparables(request, AdministrativeUnitType.PROVINCE,
                    areaMin, areaMax, since);
            if (byProvince.size() >= comparables.size()) {
                comparables = byProvince;
                scopeUsed = AdministrativeUnitType.PROVINCE;
            }
        }
        boolean scopeExpanded = scopeUsed != AdministrativeUnitType.WARD;

        final List<ComparableListing> comps = comparables;
        final AdministrativeUnitType scope = scopeUsed;
        PriceEstimate estimate = support.runWithTimeout("price", ConfigKey.AI_PRICE_TIMEOUT_MS,
                () -> estimator.estimate(input, comps, scope, minSamples, weights, deviationFlagRatio));

        // Lưu PredictionHistory MỌI lần (kể cả INSUFFICIENT_DATA).
        PredictionHistory saved = predictionHistoryRepository.save(
                mapper.toEntity(input, estimate, userId, request.getListingId(), estimator.version()));

        if (!estimate.available()) {
            throw new BusinessException(ErrorCode.AI_INSUFFICIENT_DATA,
                    "Chỉ tìm được " + estimate.sampleSize() + " tin tương đương, cần tối thiểu "
                            + minSamples + " tin");
        }

        // Ghi cờ lệch giá (nếu có tin), TUYỆT ĐỐI không chặn.
        if (request.getListingId() != null) {
            listingGateway.markPriceDeviation(request.getListingId(),
                    estimate.deviationFlagged(), saved.getId());
        }

        return mapper.toResponse(saved, estimate, scopeExpanded, comparableDays);
    }

    @Override
    public PageResponse<PricePredictionHistoryResponse> history(Long listingId, Long requesterId,
                                                                boolean hasLogView, Pageable pageable) {
        if (!hasLogView) {
            Long owner = listingGateway.ownerOf(listingId)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.LISTING_NOT_FOUND));
            if (!owner.equals(requesterId)) {
                throw new ForbiddenException(ErrorCode.LISTING_FORBIDDEN);
            }
        }
        List<PredictionHistory> all = predictionHistoryRepository.findByListingIdOrderByCreatedAtDesc(listingId);
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        List<PredictionHistory> pageContent = all.subList(from, to);
        Page<PredictionHistory> page = new PageImpl<>(pageContent, pageable, all.size());
        return PageResponse.from(page, mapper::toHistoryResponse);
    }

    // ------------------------------------------------------------------

    private void validateReferences(PricePredictionRequest r) {
        if (!categoryService.existsActiveCategory(r.getCategoryId())) {
            throw new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND);
        }
        if (!provinceService.existsProvince(r.getProvinceId())) {
            throw new ResourceNotFoundException(ErrorCode.PROVINCE_NOT_FOUND);
        }
        if (!provinceService.existsDistrict(r.getDistrictId())) {
            throw new ResourceNotFoundException(ErrorCode.DISTRICT_NOT_FOUND);
        }
        if (!provinceService.existsWard(r.getWardId())) {
            throw new ResourceNotFoundException(ErrorCode.WARD_NOT_FOUND);
        }
        if (!provinceService.isAreaSupported(r.getProvinceId(), r.getDistrictId(), r.getWardId())) {
            throw new BusinessException(ErrorCode.ADDRESS_HIERARCHY_MISMATCH);
        }
    }

    private List<ComparableListing> fetchComparables(PricePredictionRequest r, AdministrativeUnitType scope,
                                                     BigDecimal areaMin, BigDecimal areaMax, Instant since) {
        ComparableQuery query = new ComparableQuery(
                r.getCategoryId(), r.getProvinceId(), r.getDistrictId(), r.getWardId(),
                scope, areaMin, areaMax, since, COMPARABLE_FETCH_LIMIT);
        return listingGateway.findComparables(query);
    }

    private HedonicWeights readHedonicWeights() {
        return new HedonicWeights(
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_FURNITURE_FULL),
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_TOILET_PRIVATE),
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_ELEVATOR),
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_PARKING),
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_CURFEW_FREE),
                config.getDecimal(ConfigKey.AI_PRICE_HEDONIC_STREET_FRONT));
    }
}
