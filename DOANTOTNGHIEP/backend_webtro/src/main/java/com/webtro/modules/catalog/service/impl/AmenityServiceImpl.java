package com.webtro.modules.catalog.service.impl;

import com.webtro.common.enums.AmenityGroup;
import com.webtro.constant.CacheName;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.dto.request.CreateAmenityRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateAmenityRequest;
import com.webtro.modules.catalog.dto.response.AmenityGroupResponse;
import com.webtro.modules.catalog.dto.response.AmenityResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.entity.Amenity;
import com.webtro.modules.catalog.mapper.AmenityMapper;
import com.webtro.modules.catalog.repository.AmenityRepository;
import com.webtro.modules.catalog.service.AmenityService;
import com.webtro.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Cài đặt {@link AmenityService}. Đọc công khai được cache trong {@link CacheName#AMENITIES};
 * mọi thao tác ghi xóa toàn bộ cache đó.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AmenityServiceImpl implements AmenityService {

    private final AmenityRepository amenityRepository;
    private final AmenityMapper amenityMapper;

    // ==================================================================
    //  Đọc
    // ==================================================================

    @Override
    @Cacheable(cacheNames = CacheName.AMENITIES,
            key = "'list:' + (#group == null ? 'ALL' : #group) + ':' + #activeOnly")
    @Transactional(readOnly = true)
    public List<AmenityResponse> getAmenities(AmenityGroup group, boolean activeOnly) {
        List<Amenity> amenities;
        if (activeOnly && group != null) {
            amenities = amenityRepository.findByGroupCodeAndIsActiveTrueOrderByDisplayOrderAsc(group);
        } else if (activeOnly) {
            amenities = amenityRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
        } else {
            amenities = amenityRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
            if (group != null) {
                amenities = amenities.stream().filter(a -> a.getGroupCode() == group).toList();
            }
        }
        return amenityMapper.toResponseList(amenities);
    }

    @Override
    @Cacheable(cacheNames = CacheName.AMENITIES, key = "'grouped:' + #activeOnly")
    @Transactional(readOnly = true)
    public List<AmenityGroupResponse> getGroupedAmenities(boolean activeOnly) {
        List<Amenity> amenities = activeOnly
                ? amenityRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                : amenityRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return amenityMapper.toGroupedResponse(amenities);
    }

    @Override
    @Transactional(readOnly = true)
    public AmenityResponse getById(Long id) {
        return amenityMapper.toResponse(getEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveAmenity(Long id) {
        return amenityRepository.findById(id)
                .map(Amenity::getIsActive)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AmenityResponse> validateAndGet(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        // Bỏ trùng, giữ thứ tự yêu cầu.
        Collection<Long> distinct = new LinkedHashSet<>(ids);
        return distinct.stream()
                .map(id -> {
                    Amenity amenity = getEntity(id);
                    if (!Boolean.TRUE.equals(amenity.getIsActive())) {
                        throw new ResourceNotFoundException(ErrorCode.AMENITY_NOT_FOUND,
                                "Tiện ích đã bị ẩn: id = " + id);
                    }
                    return amenityMapper.toResponse(amenity);
                })
                .toList();
    }

    // ==================================================================
    //  Ghi (Admin)
    // ==================================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.AMENITIES, allEntries = true)
    public AmenityResponse create(CreateAmenityRequest request, Long actorId) {
        if (amenityRepository.existsByCode(request.getCode())) {
            throw new ConflictException(ErrorCode.AMENITY_CODE_DUPLICATE);
        }
        String name = cleanRequired(request.getName());
        Amenity amenity = Amenity.builder()
                .code(request.getCode())
                .name(name)
                .groupCode(request.getGroup())
                .icon(request.getIconUrl())
                .isFilterable(request.getFilterable() == null || request.getFilterable())
                .priceImpactRatio(request.getPriceImpactRatio() != null
                        ? request.getPriceImpactRatio() : BigDecimal.ZERO)
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : nextDisplayOrder())
                .isActive(request.getActive() == null || request.getActive())
                .build();
        amenity = amenityRepository.save(amenity);
        log.info("Admin {} tạo tiện ích {} (id={})", actorId, amenity.getCode(), amenity.getId());
        return amenityMapper.toResponse(amenity);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.AMENITIES, allEntries = true)
    public AmenityResponse update(Long id, UpdateAmenityRequest request, Long actorId) {
        Amenity amenity = getEntity(id);
        amenity.setName(cleanRequired(request.getName()));
        amenity.setGroupCode(request.getGroup());
        if (request.getIconUrl() != null) {
            amenity.setIcon(request.getIconUrl());
        }
        if (request.getFilterable() != null) {
            amenity.setIsFilterable(request.getFilterable());
        }
        if (request.getPriceImpactRatio() != null) {
            amenity.setPriceImpactRatio(request.getPriceImpactRatio());
        }
        if (request.getDisplayOrder() != null) {
            amenity.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            amenity.setIsActive(request.getActive());
        }
        amenity = amenityRepository.save(amenity);
        log.info("Admin {} sửa tiện ích id={}", actorId, id);
        return amenityMapper.toResponse(amenity);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.AMENITIES, allEntries = true)
    public void hide(Long id, Long actorId) {
        // Ẩn là thao tác KHÔNG phá hủy (chỉ đặt is_active=false, tin cũ vẫn giữ liên kết
        // listing_amenities). Không có cột đếm sẵn trên amenities và không được chạm repository
        // module listing (luật 4), nên không chặn theo AMENITY_IN_USE ở đây — xem điểm quyết định
        // trong báo cáo: cần listing module cung cấp phương thức đếm nếu muốn chặn cứng.
        Amenity amenity = getEntity(id);
        amenity.setIsActive(false);
        amenityRepository.save(amenity);
        log.info("Admin {} ẩn tiện ích id={}", actorId, id);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.AMENITIES, allEntries = true)
    public ToggleResultResponse toggle(Long id, ToggleRequest request, Long actorId) {
        Amenity amenity = getEntity(id);
        boolean previous = Boolean.TRUE.equals(amenity.getIsActive());
        boolean target = Boolean.TRUE.equals(request.getActive());
        if (previous != target) {
            amenity.setIsActive(target);
            amenityRepository.save(amenity);
            log.info("Admin {} {} tiện ích id={} (lý do: {})",
                    actorId, target ? "bật" : "tắt", id, request.getReason());
        }
        return ToggleResultResponse.builder()
                .id(amenity.getId())
                .code(amenity.getCode())
                .name(amenity.getName())
                .active(target)
                .previousActive(previous)
                .note("Tiện ích ẩn sẽ biến khỏi bộ lọc và form đăng tin; tin cũ vẫn giữ liên kết.")
                .cacheInvalidated(List.of(CacheName.AMENITIES))
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.AMENITIES, allEntries = true)
    public ReorderResultResponse reorder(ReorderRequest request, Long actorId) {
        CatalogReorderSupport.validateNoDuplicates(request);
        List<ReorderResultResponse.ReorderItemResponse> items = request.getItems().stream()
                .map(item -> {
                    Amenity amenity = getEntity(item.getId());
                    int previousOrder = amenity.getDisplayOrder() != null ? amenity.getDisplayOrder() : 0;
                    amenity.setDisplayOrder(item.getDisplayOrder());
                    amenityRepository.save(amenity);
                    return ReorderResultResponse.ReorderItemResponse.builder()
                            .id(amenity.getId())
                            .code(amenity.getCode())
                            .name(amenity.getName())
                            .displayOrder(item.getDisplayOrder())
                            .previousDisplayOrder(previousOrder)
                            .build();
                })
                .toList();
        log.info("Admin {} sắp xếp lại {} tiện ích", actorId, items.size());
        return ReorderResultResponse.builder()
                .updatedCount(items.size())
                .items(items)
                .cacheInvalidated(List.of(CacheName.AMENITIES))
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Nội bộ
    // ==================================================================

    private Amenity getEntity(Long id) {
        return amenityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.AMENITY_NOT_FOUND));
    }

    private int nextDisplayOrder() {
        return amenityRepository.findAll().stream()
                .map(Amenity::getDisplayOrder)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String cleanRequired(String input) {
        String cleaned = HtmlSanitizer.stripAllHtml(input);
        if (cleaned.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Tên tiện ích không hợp lệ");
        }
        return cleaned;
    }
}
