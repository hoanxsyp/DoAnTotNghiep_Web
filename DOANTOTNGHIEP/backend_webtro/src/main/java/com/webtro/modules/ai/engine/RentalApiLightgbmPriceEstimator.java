package com.webtro.modules.ai.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.webtro.common.enums.AdministrativeUnitType;
import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.FurnitureStatus;
import com.webtro.common.enums.PriceConfidence;
import com.webtro.common.enums.ToiletType;
import com.webtro.config.properties.AppProperties;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.modules.ai.spi.ListingDataGateway.ComparableListing;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.repository.AmenityRepository;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Engine production cho website: gọi FastAPI rental-api, nơi đang phục vụ LightGBM tốt nhất
 * ({@code RENTAL_BACKEND=sklearn}).
 */
@Slf4j
@Primary
@Component
public class RentalApiLightgbmPriceEstimator implements PriceEstimator {

    private static final String ESTIMATOR_VERSION = "lgbm-api-1.0";
    private static final BigDecimal ONE_MILLION = new BigDecimal("1000000");
    private static final Set<String> FURNITURE_AMENITIES = Set.of(
            "BED", "WARDROBE", "DESK", "FRIDGE", "TV", "KITCHEN_CABINET");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final CategoryRepository categoryRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final AmenityRepository amenityRepository;
    private final HttpClient httpClient;

    public RentalApiLightgbmPriceEstimator(AppProperties appProperties,
                                           ObjectMapper objectMapper,
                                           CategoryRepository categoryRepository,
                                           DistrictRepository districtRepository,
                                           WardRepository wardRepository,
                                           AmenityRepository amenityRepository) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.categoryRepository = categoryRepository;
        this.districtRepository = districtRepository;
        this.wardRepository = wardRepository;
        this.amenityRepository = amenityRepository;
        Duration connectTimeout = appProperties.getAi().getRentalPrice().getConnectTimeout();
        // Uvicorn không hỗ trợ HTTP/2 cleartext upgrade (h2c). Java HttpClient mặc định thử
        // nâng cấp kết nối HTTP, khiến Uvicorn bỏ body của POST /predict và trả 422 body missing.
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
    }

    @Override
    public String version() {
        return ESTIMATOR_VERSION;
    }

    @Override
    public boolean usesComparables() {
        return false;
    }

    @Override
    public PriceEstimate estimate(PriceInput input, List<ComparableListing> comparables,
                                  AdministrativeUnitType scopeUsed, int minSamples,
                                  HedonicWeights weights, BigDecimal deviationFlagRatio) {
        Category category = categoryRepository.findById(input.categoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
        District district = districtRepository.findById(input.districtId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DISTRICT_NOT_FOUND));
        Ward ward = wardRepository.findById(input.wardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.WARD_NOT_FOUND));

        RentalApiResult result = callRentalApi(input, category, district, ward);
        BigDecimal suggested = millionToVnd(result.predictedPriceMillion());
        BigDecimal low = millionToVnd(result.priceLowMillion());
        BigDecimal high = millionToVnd(result.priceHighMillion());
        BigDecimal median = suggested;
        BigDecimal pricePerSqm = result.marketUnitPriceMillionM2() != null
                ? millionToVnd(result.marketUnitPriceMillionM2())
                : suggested.divide(input.area(), 0, RoundingMode.HALF_UP);
        BigDecimal basePrice = result.marketPriceMillion() != null
                ? millionToVnd(result.marketPriceMillion())
                : suggested;
        int sampleSize = result.marketSampleCount();
        PriceConfidence confidence = confidenceOf(sampleSize, result.mapePct(), minSamples);
        BigDecimal confidenceScore = confidenceScoreOf(confidence, result.mapePct(), sampleSize, minSamples);
        BigDecimal dispersionRatio = result.mapePct() == null ? null
                : BigDecimal.valueOf(result.mapePct())
                .divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        BigDecimal deviationRatio = deviationRatio(input.inputPrice(), suggested);

        boolean deviationFlagged = deviationRatio != null
                && deviationRatio.abs().compareTo(deviationFlagRatio) > 0;

        return new PriceEstimate(true, confidence, confidenceScore, suggested,
                low, median, high, pricePerSqm, sampleSize, scopeOf(result.marketScopeLevel()),
                dispersionRatio, basePrice, List.of(), deviationRatio, deviationFlagged,
                explanation(result, suggested));
    }

    private RentalApiResult callRentalApi(PriceInput input, Category category, District district, Ward ward) {
        try {
            String body = objectMapper.writeValueAsString(buildPayload(input, category, district, ward));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl() + "/predict"))
                    .timeout(appProperties.getAi().getRentalPrice().getRequestTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                log.warn("Rental API trả {}: {}", response.statusCode(), response.body());
                if (response.statusCode() < 500) {
                    throw new BusinessException(ErrorCode.AI_PRICE_INPUT_INVALID,
                            "Thông tin đầu vào chưa khớp dữ liệu model dự đoán giá");
                }
                throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                        "Dịch vụ dự đoán giá rental-api đang lỗi, vui lòng thử lại sau");
            }
            JsonNode node = objectMapper.readTree(response.body());
            JsonNode range = node.path("price_range");
            double predicted = node.path("predicted_price_million").asDouble();
            double low = range.isArray() && range.size() > 0 ? range.get(0).asDouble() : predicted;
            double high = range.isArray() && range.size() > 1 ? range.get(1).asDouble() : predicted;
            return new RentalApiResult(
                    predicted,
                    low,
                    high,
                    nullableDouble(node, "market_unit_price_million_m2"),
                    nullableDouble(node, "market_price_million"),
                    node.path("market_sample_count").asInt(0),
                    nullableDouble(node, "market_frecency_score"),
                    nullableDouble(node, "market_freshness_days"),
                    node.path("market_scope_level").asInt(0),
                    text(node, "model_type"),
                    nullableDouble(node, "mape_pct"));
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Không gọi được rental-api để dự đoán giá", e);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Không gọi được rental-api: {}", e.getMessage());
            throw new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "Không gọi được rental-api để dự đoán giá", e);
        }
    }

    private Map<String, Object> buildPayload(PriceInput input, Category category, District district, Ward ward) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("district", stripAdministrativePrefix(district.getName()));
        payload.put("ward", stripAdministrativePrefix(ward.getName()));
        payload.put("area_m2", input.area().doubleValue());
        payload.put("room_type", roomTypeOf(category));
        payload.put("amenities", amenitiesOf(input));
        payload.put("floor", null);
        payload.put("latitude", input.latitude() != null ? input.latitude() : decimalToDouble(ward.getLatitude()));
        payload.put("longitude", input.longitude() != null ? input.longitude() : decimalToDouble(ward.getLongitude()));
        return payload;
    }

    private String roomTypeOf(Category category) {
        CategoryCode code = category.getCode();
        return switch (code) {
            case BOARDING_HOUSE, HOMESTAY -> "phong_tro";
            case MINI_APARTMENT -> "can_ho_mini";
            case APARTMENT -> "can_ho";
            case WHOLE_HOUSE, SMALL_PREMISES -> "nha_nguyen_can";
            case ROOMMATE -> "o_ghep";
        };
    }

    private List<String> amenitiesOf(PriceInput input) {
        Set<String> out = new LinkedHashSet<>();
        if (input.toiletType() == ToiletType.PRIVATE) {
            out.add("khep_kin");
        }
        if (input.furnitureStatus() == FurnitureStatus.FULL) {
            out.add("full_do");
        }
        int furnitureCount = 0;
        if (input.amenityIds() != null && !input.amenityIds().isEmpty()) {
            for (Amenity amenity : amenityRepository.findAllById(input.amenityIds())) {
                String code = amenity.getCode() == null ? "" : amenity.getCode().toUpperCase();
                if (FURNITURE_AMENITIES.contains(code)) {
                    furnitureCount++;
                }
                switch (code) {
                    case "AIR_CONDITIONER" -> out.add("dieu_hoa");
                    case "WATER_HEATER" -> out.add("nong_lanh");
                    case "WASHING_MACHINE" -> out.add("may_giat");
                    case "ELEVATOR" -> out.add("thang_may");
                    case "BALCONY" -> out.add("ban_cong");
                    case "MEZZANINE" -> out.add("gac");
                    case "WIFI" -> out.add("wifi");
                    case "MOTORBIKE_PARKING", "CAR_PARKING" -> out.add("de_xe");
                    default -> {
                    }
                }
            }
        }
        if (furnitureCount >= 3) {
            out.add("full_do");
        }
        return new ArrayList<>(out);
    }

    private String baseUrl() {
        String raw = appProperties.getAi().getRentalPrice().getBaseUrl();
        String value = raw == null || raw.isBlank() ? "http://localhost:8002" : raw.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String stripAdministrativePrefix(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        String out = name.trim();
        for (String prefix : List.of("Thành phố", "Quận", "Huyện", "Thị xã", "Phường", "Xã", "Thị trấn")) {
            if (out.regionMatches(true, 0, prefix, 0, prefix.length())
                    && out.length() > prefix.length()
                    && Character.isWhitespace(out.charAt(prefix.length()))) {
                return out.substring(prefix.length()).trim();
            }
        }
        return out;
    }

    private BigDecimal millionToVnd(Double million) {
        return BigDecimal.valueOf(million == null ? 0.0 : million)
                .multiply(ONE_MILLION)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private BigDecimal deviationRatio(BigDecimal inputPrice, BigDecimal suggested) {
        if (inputPrice == null || suggested == null || suggested.signum() <= 0) {
            return null;
        }
        return inputPrice.subtract(suggested).divide(suggested, 4, RoundingMode.HALF_UP);
    }

    private PriceConfidence confidenceOf(int sampleSize, Double mapePct, int minSamples) {
        double mape = mapePct == null ? 30.0 : mapePct;
        if (sampleSize >= 20 && mape <= 18.0) {
            return PriceConfidence.HIGH;
        }
        if (sampleSize >= minSamples && mape <= 25.0) {
            return PriceConfidence.MEDIUM;
        }
        return PriceConfidence.LOW;
    }

    private BigDecimal confidenceScoreOf(PriceConfidence confidence, Double mapePct, int sampleSize, int minSamples) {
        double mapeScore = 1.0 - Math.min(mapePct == null ? 30.0 : mapePct, 60.0) / 100.0;
        double sampleScore = Math.min(1.0, sampleSize / (double) Math.max(minSamples, 1));
        double value = switch (confidence) {
            case HIGH -> Math.max(0.80, mapeScore);
            case MEDIUM -> Math.max(0.55, Math.min(0.79, (mapeScore + sampleScore) / 2.0));
            case LOW -> Math.max(0.30, Math.min(0.54, (mapeScore + sampleScore) / 2.0));
            case INSUFFICIENT_DATA -> 0.0;
        };
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private AdministrativeUnitType scopeOf(int scopeLevel) {
        if (scopeLevel >= 3) {
            return AdministrativeUnitType.WARD;
        }
        if (scopeLevel >= 1) {
            return AdministrativeUnitType.DISTRICT;
        }
        return AdministrativeUnitType.PROVINCE;
    }

    private String explanation(RentalApiResult result, BigDecimal suggested) {
        String text = "Mô hình " + result.modelType()
                + " dự đoán " + suggested.divide(ONE_MILLION, 2, RoundingMode.HALF_UP)
                + " triệu/tháng; benchmark " + scopeLabel(result.marketScopeLevel())
                + ", " + result.marketSampleCount() + " mẫu"
                + (result.marketFreshnessDays() == null ? "" : ", độ mới "
                + BigDecimal.valueOf(result.marketFreshnessDays()).setScale(0, RoundingMode.HALF_UP) + " ngày")
                + ".";
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private String scopeLabel(int scopeLevel) {
        return switch (scopeLevel) {
            case 4 -> "khớp quận + phường + loại phòng";
            case 3 -> "khớp quận + phường";
            case 2 -> "khớp quận + loại phòng";
            case 1 -> "khớp quận";
            default -> "fallback toàn bộ dữ liệu";
        };
    }

    private Double nullableDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asDouble();
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "LightGBM" : value.asText();
    }

    private Double decimalToDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private record RentalApiResult(
            double predictedPriceMillion,
            double priceLowMillion,
            double priceHighMillion,
            Double marketUnitPriceMillionM2,
            Double marketPriceMillion,
            int marketSampleCount,
            Double marketFrecencyScore,
            Double marketFreshnessDays,
            int marketScopeLevel,
            String modelType,
            Double mapePct) {
    }
}
