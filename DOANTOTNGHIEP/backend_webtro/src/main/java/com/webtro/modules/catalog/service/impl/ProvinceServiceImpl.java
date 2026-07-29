package com.webtro.modules.catalog.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.constant.CacheName;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.dto.request.AreaImportRequest;
import com.webtro.modules.catalog.dto.request.CreateDistrictRequest;
import com.webtro.modules.catalog.dto.request.CreateProvinceRequest;
import com.webtro.modules.catalog.dto.request.CreateWardRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateDistrictRequest;
import com.webtro.modules.catalog.dto.request.UpdateProvinceRequest;
import com.webtro.modules.catalog.dto.request.UpdateWardRequest;
import com.webtro.modules.catalog.dto.response.AdminProvinceResponse;
import com.webtro.modules.catalog.dto.response.AreaImportResponse;
import com.webtro.modules.catalog.dto.response.DistrictResponse;
import com.webtro.modules.catalog.dto.response.ProvinceResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.dto.response.WardResponse;
import com.webtro.modules.catalog.entity.District;
import com.webtro.modules.catalog.entity.Province;
import com.webtro.modules.catalog.entity.Ward;
import com.webtro.modules.catalog.mapper.DistrictMapper;
import com.webtro.modules.catalog.mapper.ProvinceMapper;
import com.webtro.modules.catalog.mapper.WardMapper;
import com.webtro.modules.catalog.repository.DistrictRepository;
import com.webtro.modules.catalog.repository.ProvinceRepository;
import com.webtro.modules.catalog.repository.WardRepository;
import com.webtro.modules.catalog.service.ProvinceService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.SlugUtil;
import com.webtro.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Cài đặt {@link ProvinceService} cho cả cây địa chính. Đọc công khai được cache trong
 * {@link CacheName#ADMINISTRATIVE_UNITS} với các key phân biệt {@code provinces:*}/{@code districts:*}/
 * {@code wards:*}; mọi thao tác ghi xóa toàn bộ cache đó (đơn giản, an toàn — dữ liệu địa chính đổi
 * rất hiếm).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProvinceServiceImpl implements ProvinceService {

    private final ProvinceRepository provinceRepository;
    private final DistrictRepository districtRepository;
    private final WardRepository wardRepository;
    private final ProvinceMapper provinceMapper;
    private final DistrictMapper districtMapper;
    private final WardMapper wardMapper;

    // ==================================================================
    //  Đọc công khai
    // ==================================================================

    @Override
    @Cacheable(cacheNames = CacheName.ADMINISTRATIVE_UNITS,
            key = "'provinces:' + #supportedOnly + ':' + (#keyword == null ? '' : #keyword)")
    @Transactional(readOnly = true)
    public List<ProvinceResponse> getProvinces(boolean supportedOnly, String keyword) {
        List<Province> provinces = supportedOnly
                ? provinceRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                : provinceRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
        String normalized = normalizeKeyword(keyword);
        return provinces.stream()
                .filter(p -> normalized == null || p.getSearchName().contains(normalized))
                .map(provinceMapper::toResponse)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CacheName.ADMINISTRATIVE_UNITS,
            key = "'districts:' + #provinceId + ':' + (#keyword == null ? '' : #keyword)")
    @Transactional(readOnly = true)
    public List<DistrictResponse> getDistricts(Long provinceId, String keyword) {
        if (!provinceRepository.existsById(provinceId)) {
            throw new ResourceNotFoundException(ErrorCode.PROVINCE_NOT_FOUND);
        }
        List<District> districts =
                districtRepository.findByProvince_IdAndIsActiveTrueOrderByDisplayOrderAsc(provinceId);
        String normalized = normalizeKeyword(keyword);
        return districts.stream()
                .filter(d -> normalized == null || d.getSearchName().contains(normalized))
                .map(districtMapper::toResponse)
                .toList();
    }

    @Override
    @Cacheable(cacheNames = CacheName.ADMINISTRATIVE_UNITS,
            key = "'wards:' + #districtId + ':' + (#keyword == null ? '' : #keyword)")
    @Transactional(readOnly = true)
    public List<WardResponse> getWards(Long districtId, String keyword) {
        if (!districtRepository.existsById(districtId)) {
            throw new ResourceNotFoundException(ErrorCode.DISTRICT_NOT_FOUND);
        }
        List<Ward> wards =
                wardRepository.findByDistrict_IdAndIsActiveTrueOrderByNameAsc(districtId);
        String normalized = normalizeKeyword(keyword);
        return wards.stream()
                .filter(w -> normalized == null || w.getSearchName().contains(normalized))
                .map(wardMapper::toResponse)
                .toList();
    }

    // ==================================================================
    //  Tra cứu cho module khác
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public boolean existsProvince(Long id) {
        return id != null && provinceRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsDistrict(Long id) {
        return id != null && districtRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsWard(Long id) {
        return id != null && wardRepository.existsById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isAreaSupported(Long provinceId, Long districtId, Long wardId) {
        Province province = getProvinceEntity(provinceId);
        if (!Boolean.TRUE.equals(province.getIsActive())) {
            return false;
        }
        if (districtId != null) {
            District district = getDistrictEntity(districtId);
            if (!Boolean.TRUE.equals(district.getIsActive())) {
                return false;
            }
        }
        if (wardId != null) {
            Ward ward = getWardEntity(wardId);
            return Boolean.TRUE.equals(ward.getIsActive());
        }
        return true;
    }

    // ==================================================================
    //  Admin — tỉnh/thành
    // ==================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminProvinceResponse> getProvincesForAdmin(String keyword, Boolean supportedOnly,
                                                                    Pageable pageable) {
        String normalized = normalizeKeyword(keyword);
        List<Province> filtered = provinceRepository.findAll(Sort.by(Sort.Direction.ASC, "code")).stream()
                .filter(p -> !Boolean.TRUE.equals(supportedOnly) || Boolean.TRUE.equals(p.getIsActive()))
                .filter(p -> normalized == null || p.getSearchName().contains(normalized))
                .toList();

        int total = filtered.size();
        int from = (int) Math.min((long) pageable.getPageNumber() * pageable.getPageSize(), total);
        int to = (int) Math.min((long) from + pageable.getPageSize(), total);
        List<AdminProvinceResponse> items = filtered.subList(from, to).stream()
                .map(p -> provinceMapper.toAdminResponse(p, activeDistrictCount(p.getId())))
                .toList();

        int pageSize = pageable.getPageSize();
        int totalPages = pageSize == 0 ? 0 : (int) Math.ceil((double) total / pageSize);
        return PageResponse.<AdminProvinceResponse>builder()
                .items(items)
                .page(pageable.getPageNumber())
                .size(pageSize)
                .totalElements(total)
                .totalPages(totalPages)
                .first(pageable.getPageNumber() == 0)
                .last(to >= total)
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public ProvinceResponse createProvince(CreateProvinceRequest request, Long actorId) {
        if (provinceRepository.existsByCode(request.getCode())) {
            throw new ConflictException(ErrorCode.PROVINCE_CODE_DUPLICATE);
        }
        String name = cleanRequired(request.getName(), "Tên tỉnh/thành");
        Province province = Province.builder()
                .code(request.getCode())
                .name(name)
                .shortName(shortName(request.getShortName(), name))
                .type(request.getType())
                .slug(uniqueProvinceSlug(name))
                .searchName(searchName(name))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .displayOrder(nextProvinceDisplayOrder())
                .isActive(Boolean.TRUE.equals(request.getSupported()))
                .listingCount(0)
                .build();
        province = provinceRepository.save(province);
        log.info("Admin {} tạo tỉnh/thành {} (id={})", actorId, province.getCode(), province.getId());
        return provinceMapper.toResponse(province);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public ProvinceResponse updateProvince(Long id, UpdateProvinceRequest request, Long actorId) {
        Province province = getProvinceEntity(id);
        String name = cleanRequired(request.getName(), "Tên tỉnh/thành");
        province.setName(name);
        province.setShortName(shortName(request.getShortName(), name));
        province.setType(request.getType());
        province.setSearchName(searchName(name));
        if (request.getLatitude() != null) {
            province.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            province.setLongitude(request.getLongitude());
        }
        if (request.getSupported() != null) {
            province.setIsActive(request.getSupported());
        }
        province = provinceRepository.save(province);
        log.info("Admin {} sửa tỉnh/thành id={}", actorId, id);
        return provinceMapper.toResponse(province);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public ToggleResultResponse toggleProvince(Long id, ToggleRequest request, Long actorId) {
        Province province = getProvinceEntity(id);
        boolean previous = Boolean.TRUE.equals(province.getIsActive());
        boolean target = Boolean.TRUE.equals(request.getActive());
        if (previous != target) {
            province.setIsActive(target);
            provinceRepository.save(province);
            log.info("Admin {} {} hỗ trợ tỉnh/thành id={} (lý do: {})",
                    actorId, target ? "bật" : "tắt", id, request.getReason());
        }
        int affected = province.getListingCount() != null ? province.getListingCount() : 0;
        return ToggleResultResponse.builder()
                .id(province.getId())
                .code(province.getCode())
                .name(province.getName())
                .active(target)
                .previousActive(previous)
                .affectedListingCount(affected)
                .note("Tắt hỗ trợ chỉ chặn tin mới ở khu vực này; tin cũ giữ nguyên hiển thị.")
                .cacheInvalidated(List.of(CacheName.ADMINISTRATIVE_UNITS))
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Admin — quận/huyện
    // ==================================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public DistrictResponse createDistrict(CreateDistrictRequest request, Long actorId) {
        Province province = getProvinceEntity(request.getProvinceId());
        if (districtRepository.existsByCode(request.getCode())) {
            throw new ConflictException(ErrorCode.PROVINCE_CODE_DUPLICATE, "Mã quận/huyện đã tồn tại");
        }
        String name = cleanRequired(request.getName(), "Tên quận/huyện");
        District district = District.builder()
                .province(province)
                .code(request.getCode())
                .name(name)
                .type(request.getType())
                .slug(uniqueDistrictSlug(name))
                .searchName(searchName(name))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .displayOrder(nextDistrictDisplayOrder())
                .isActive(Boolean.TRUE.equals(request.getSupported()))
                .listingCount(0)
                .build();
        district = districtRepository.save(district);
        log.info("Admin {} tạo quận/huyện {} (id={})", actorId, district.getCode(), district.getId());
        return districtMapper.toResponse(district);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public DistrictResponse updateDistrict(Long id, UpdateDistrictRequest request, Long actorId) {
        District district = getDistrictEntity(id);
        String name = cleanRequired(request.getName(), "Tên quận/huyện");
        district.setName(name);
        district.setType(request.getType());
        district.setSearchName(searchName(name));
        if (request.getLatitude() != null) {
            district.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            district.setLongitude(request.getLongitude());
        }
        if (request.getSupported() != null) {
            district.setIsActive(request.getSupported());
        }
        district = districtRepository.save(district);
        log.info("Admin {} sửa quận/huyện id={}", actorId, id);
        return districtMapper.toResponse(district);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public ToggleResultResponse toggleDistrict(Long id, ToggleRequest request, Long actorId) {
        District district = getDistrictEntity(id);
        boolean previous = Boolean.TRUE.equals(district.getIsActive());
        boolean target = Boolean.TRUE.equals(request.getActive());
        if (previous != target) {
            district.setIsActive(target);
            districtRepository.save(district);
            log.info("Admin {} {} hỗ trợ quận/huyện id={} (lý do: {})",
                    actorId, target ? "bật" : "tắt", id, request.getReason());
        }
        int affected = district.getListingCount() != null ? district.getListingCount() : 0;
        return ToggleResultResponse.builder()
                .id(district.getId())
                .code(district.getCode())
                .name(district.getName())
                .active(target)
                .previousActive(previous)
                .affectedListingCount(affected)
                .note("Tắt hỗ trợ chỉ chặn tin mới ở khu vực này; tin cũ giữ nguyên hiển thị.")
                .cacheInvalidated(List.of(CacheName.ADMINISTRATIVE_UNITS))
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Admin — phường/xã
    // ==================================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public WardResponse createWard(CreateWardRequest request, Long actorId) {
        District district = getDistrictEntity(request.getDistrictId());
        if (wardRepository.existsByCode(request.getCode())) {
            throw new ConflictException(ErrorCode.PROVINCE_CODE_DUPLICATE, "Mã phường/xã đã tồn tại");
        }
        String name = cleanRequired(request.getName(), "Tên phường/xã");
        Ward ward = Ward.builder()
                .district(district)
                .code(request.getCode())
                .name(name)
                .type(request.getType())
                .slug(uniqueWardSlug(name))
                .searchName(searchName(name))
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .isActive(Boolean.TRUE.equals(request.getSupported()))
                .listingCount(0)
                .build();
        ward = wardRepository.save(ward);
        log.info("Admin {} tạo phường/xã {} (id={})", actorId, ward.getCode(), ward.getId());
        return wardMapper.toResponse(ward);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public WardResponse updateWard(Long id, UpdateWardRequest request, Long actorId) {
        Ward ward = getWardEntity(id);
        String name = cleanRequired(request.getName(), "Tên phường/xã");
        ward.setName(name);
        ward.setType(request.getType());
        ward.setSearchName(searchName(name));
        if (request.getLatitude() != null) {
            ward.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            ward.setLongitude(request.getLongitude());
        }
        if (request.getSupported() != null) {
            ward.setIsActive(request.getSupported());
        }
        ward = wardRepository.save(ward);
        log.info("Admin {} sửa phường/xã id={}", actorId, id);
        return wardMapper.toResponse(ward);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public ToggleResultResponse toggleWard(Long id, ToggleRequest request, Long actorId) {
        Ward ward = getWardEntity(id);
        boolean previous = Boolean.TRUE.equals(ward.getIsActive());
        boolean target = Boolean.TRUE.equals(request.getActive());
        if (previous != target) {
            ward.setIsActive(target);
            wardRepository.save(ward);
            log.info("Admin {} {} hỗ trợ phường/xã id={} (lý do: {})",
                    actorId, target ? "bật" : "tắt", id, request.getReason());
        }
        int affected = ward.getListingCount() != null ? ward.getListingCount() : 0;
        return ToggleResultResponse.builder()
                .id(ward.getId())
                .code(ward.getCode())
                .name(ward.getName())
                .active(target)
                .previousActive(previous)
                .affectedListingCount(affected)
                .note("Tắt hỗ trợ chỉ chặn tin mới ở khu vực này; tin cũ giữ nguyên hiển thị.")
                .cacheInvalidated(List.of(CacheName.ADMINISTRATIVE_UNITS))
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Admin — import hàng loạt (JSON)
    // ==================================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.ADMINISTRATIVE_UNITS, allEntries = true)
    public AreaImportResponse importAreas(AreaImportRequest request, Long actorId) {
        AreaImportResponse result = AreaImportResponse.builder().build();
        if (request.getProvinces() == null) {
            return result;
        }
        for (AreaImportRequest.ProvinceImport p : request.getProvinces()) {
            if (isBlank(p.getCode()) || isBlank(p.getName()) || isBlank(p.getType())) {
                result.getWarnings().add("Bỏ qua tỉnh/thành thiếu code/name/type: code="
                        + safe(p.getCode()));
                continue;
            }
            Long provinceId;
            Province existing = provinceRepository.findByCode(p.getCode().trim()).orElse(null);
            if (existing != null) {
                UpdateProvinceRequest upd = UpdateProvinceRequest.builder()
                        .name(p.getName()).shortName(p.getShortName()).type(p.getType())
                        .supported(p.getSupported()).latitude(p.getLatitude()).longitude(p.getLongitude())
                        .build();
                provinceId = updateProvince(existing.getId(), upd, actorId).getId();
                result.setProvincesUpdated(result.getProvincesUpdated() + 1);
            } else {
                CreateProvinceRequest create = CreateProvinceRequest.builder()
                        .code(p.getCode().trim()).name(p.getName()).shortName(p.getShortName())
                        .type(p.getType()).supported(p.getSupported())
                        .latitude(p.getLatitude()).longitude(p.getLongitude())
                        .build();
                provinceId = createProvince(create, actorId).getId();
                result.setProvincesCreated(result.getProvincesCreated() + 1);
            }
            importDistricts(provinceId, p.getDistricts(), actorId, result);
        }
        return result;
    }

    private void importDistricts(Long provinceId, List<AreaImportRequest.DistrictImport> districts,
                                 Long actorId, AreaImportResponse result) {
        if (districts == null) {
            return;
        }
        for (AreaImportRequest.DistrictImport d : districts) {
            if (isBlank(d.getCode()) || isBlank(d.getName()) || isBlank(d.getType())) {
                result.getWarnings().add("Bỏ qua quận/huyện thiếu code/name/type: code="
                        + safe(d.getCode()));
                continue;
            }
            Long districtId;
            District existing = districtRepository.findByCode(d.getCode().trim()).orElse(null);
            if (existing != null) {
                UpdateDistrictRequest upd = UpdateDistrictRequest.builder()
                        .name(d.getName()).type(d.getType()).supported(d.getSupported())
                        .latitude(d.getLatitude()).longitude(d.getLongitude())
                        .build();
                districtId = updateDistrict(existing.getId(), upd, actorId).getId();
                result.setDistrictsUpdated(result.getDistrictsUpdated() + 1);
            } else {
                CreateDistrictRequest create = CreateDistrictRequest.builder()
                        .provinceId(provinceId).code(d.getCode().trim()).name(d.getName())
                        .type(d.getType()).supported(d.getSupported())
                        .latitude(d.getLatitude()).longitude(d.getLongitude())
                        .build();
                districtId = createDistrict(create, actorId).getId();
                result.setDistrictsCreated(result.getDistrictsCreated() + 1);
            }
            importWards(districtId, d.getWards(), actorId, result);
        }
    }

    private void importWards(Long districtId, List<AreaImportRequest.WardImport> wards,
                             Long actorId, AreaImportResponse result) {
        if (wards == null) {
            return;
        }
        for (AreaImportRequest.WardImport w : wards) {
            if (isBlank(w.getCode()) || isBlank(w.getName()) || isBlank(w.getType())) {
                result.getWarnings().add("Bỏ qua phường/xã thiếu code/name/type: code=" + safe(w.getCode()));
                continue;
            }
            Ward existing = wardRepository.findByCode(w.getCode().trim()).orElse(null);
            if (existing != null) {
                UpdateWardRequest upd = UpdateWardRequest.builder()
                        .name(w.getName()).type(w.getType()).supported(w.getSupported())
                        .latitude(w.getLatitude()).longitude(w.getLongitude())
                        .build();
                updateWard(existing.getId(), upd, actorId);
                result.setWardsUpdated(result.getWardsUpdated() + 1);
            } else {
                CreateWardRequest create = CreateWardRequest.builder()
                        .districtId(districtId).code(w.getCode().trim()).name(w.getName())
                        .type(w.getType()).supported(w.getSupported())
                        .latitude(w.getLatitude()).longitude(w.getLongitude())
                        .build();
                createWard(create, actorId);
                result.setWardsCreated(result.getWardsCreated() + 1);
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String safe(String s) {
        return s == null ? "(null)" : s;
    }

    // ==================================================================
    //  Nội bộ
    // ==================================================================

    private Province getProvinceEntity(Long id) {
        return provinceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PROVINCE_NOT_FOUND));
    }

    private District getDistrictEntity(Long id) {
        return districtRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DISTRICT_NOT_FOUND));
    }

    private Ward getWardEntity(Long id) {
        return wardRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.WARD_NOT_FOUND));
    }

    private int activeDistrictCount(Long provinceId) {
        return districtRepository
                .findByProvince_IdAndIsActiveTrueOrderByDisplayOrderAsc(provinceId).size();
    }

    private int nextProvinceDisplayOrder() {
        return provinceRepository.findAll().stream()
                .map(Province::getDisplayOrder).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private int nextDistrictDisplayOrder() {
        return districtRepository.findAll().stream()
                .map(District::getDisplayOrder).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0) + 1;
    }

    private String uniqueProvinceSlug(String name) {
        String base = SlugUtil.toSlug(name);
        String candidate = base;
        int suffix = 2;
        while (provinceRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String uniqueDistrictSlug(String name) {
        String base = SlugUtil.toSlug(name);
        String candidate = base;
        int suffix = 2;
        while (districtRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String uniqueWardSlug(String name) {
        String base = SlugUtil.toSlug(name);
        String candidate = base;
        int suffix = 2;
        while (wardRepository.findBySlug(candidate).isPresent()) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    /** Tên rút gọn: bỏ tiền tố "Thành phố "/"Tỉnh "; cắt còn ≤ 50 ký tự. */
    private String shortName(String provided, String fullName) {
        String base = (provided != null && !provided.isBlank())
                ? HtmlSanitizer.stripAllHtml(provided)
                : fullName.replaceFirst("^(?i)(Thành phố|Tỉnh)\\s+", "");
        return base.length() > 50 ? base.substring(0, 50) : base;
    }

    private String searchName(String name) {
        String normalized = TextNormalizer.removeAccents(name);
        return normalized.length() > 100 ? normalized.substring(0, 100) : normalized;
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return TextNormalizer.removeAccents(keyword);
    }

    private String cleanRequired(String input, String fieldLabel) {
        String cleaned = HtmlSanitizer.stripAllHtml(input);
        if (cleaned.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldLabel + " không hợp lệ");
        }
        return cleaned;
    }
}
