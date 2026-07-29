package com.webtro.modules.catalog.service.impl;

import com.webtro.common.enums.CategoryCode;
import com.webtro.constant.CacheName;
import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessException;
import com.webtro.exception.BusinessRuleException;
import com.webtro.exception.ConflictException;
import com.webtro.exception.ResourceNotFoundException;
import com.webtro.modules.catalog.dto.request.CreateCategoryRequest;
import com.webtro.modules.catalog.dto.request.ReorderRequest;
import com.webtro.modules.catalog.dto.request.ToggleRequest;
import com.webtro.modules.catalog.dto.request.UpdateCategoryRequest;
import com.webtro.modules.catalog.dto.response.CategoryResponse;
import com.webtro.modules.catalog.dto.response.ReorderResultResponse;
import com.webtro.modules.catalog.dto.response.ToggleResultResponse;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.mapper.CategoryMapper;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.catalog.service.CategoryService;
import com.webtro.util.HtmlSanitizer;
import com.webtro.util.SlugUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Cài đặt {@link CategoryService}. Đọc công khai được cache trong {@link CacheName#CATEGORIES};
 * mọi thao tác ghi xóa toàn bộ cache đó ({@code allEntries}) — số danh mục nhỏ nên chi phí không
 * đáng kể và tránh phải suy luận từng key.
 *
 * <p>Kiểm tra "còn tin đăng" dùng cột đếm sẵn {@code listing_count} trên entity, tránh chạm
 * repository của module listing (canonical mục 3 luật 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    // ==================================================================
    //  Đọc
    // ==================================================================

    @Override
    @Cacheable(cacheNames = CacheName.CATEGORIES, key = "'list:' + #activeOnly")
    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategories(boolean activeOnly) {
        List<Category> categories = activeOnly
                ? categoryRepository.findByIsActiveTrueOrderByDisplayOrderAsc()
                : categoryRepository.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
        return categories.stream().map(categoryMapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryMapper.toResponse(getEntity(id));
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findByCode(CategoryCode code) {
        Category category = categoryRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsActiveCategory(Long id) {
        return categoryRepository.findById(id)
                .map(Category::getIsActive)
                .orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getRequiredFields(Long id) {
        return categoryMapper.fromJson(getEntity(id).getRequiredFields());
    }

    // ==================================================================
    //  Ghi (Admin)
    // ==================================================================

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CATEGORIES, allEntries = true)
    public CategoryResponse create(CreateCategoryRequest request, Long actorId) {
        if (categoryRepository.existsByCode(request.getCode())) {
            throw new ConflictException(ErrorCode.CATEGORY_CODE_DUPLICATE);
        }
        String name = cleanRequired(request.getName(), "Tên danh mục");
        Category category = Category.builder()
                .code(request.getCode())
                .name(name)
                .slug(uniqueSlug(name))
                .description(clean(request.getDescription()))
                .icon(request.getIconUrl())
                .requiredFields(categoryMapper.toJson(request.getRequiredFields()))
                .optionalFields(categoryMapper.toJson(request.getOptionalFields()))
                .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : nextDisplayOrder())
                .isActive(request.getActive() == null || request.getActive())
                .listingCount(0)
                .build();
        category = categoryRepository.save(category);
        log.info("Admin {} tạo danh mục {} (id={})", actorId, category.getCode(), category.getId());
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CATEGORIES, allEntries = true)
    public CategoryResponse update(Long id, UpdateCategoryRequest request, Long actorId) {
        Category category = getEntity(id);
        String name = cleanRequired(request.getName(), "Tên danh mục");
        category.setName(name);
        category.setDescription(clean(request.getDescription()));
        if (request.getIconUrl() != null) {
            category.setIcon(request.getIconUrl());
        }
        if (request.getDisplayOrder() != null) {
            category.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getRequiredFields() != null) {
            category.setRequiredFields(categoryMapper.toJson(request.getRequiredFields()));
        }
        if (request.getOptionalFields() != null) {
            category.setOptionalFields(categoryMapper.toJson(request.getOptionalFields()));
        }
        if (request.getActive() != null) {
            category.setIsActive(request.getActive());
        }
        category = categoryRepository.save(category);
        log.info("Admin {} sửa danh mục id={}", actorId, id);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CATEGORIES, allEntries = true)
    public void hide(Long id, Long actorId) {
        Category category = getEntity(id);
        if (category.getListingCount() != null && category.getListingCount() > 0) {
            throw new BusinessRuleException(ErrorCode.CATEGORY_IN_USE);
        }
        category.setIsActive(false);
        categoryRepository.save(category);
        log.info("Admin {} ẩn danh mục id={}", actorId, id);
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CATEGORIES, allEntries = true)
    public ToggleResultResponse toggle(Long id, ToggleRequest request, Long actorId) {
        Category category = getEntity(id);
        boolean previous = Boolean.TRUE.equals(category.getIsActive());
        boolean target = Boolean.TRUE.equals(request.getActive());
        if (previous != target) {
            category.setIsActive(target);
            categoryRepository.save(category);
            log.info("Admin {} {} danh mục id={} (lý do: {})",
                    actorId, target ? "bật" : "tắt", id, request.getReason());
        }
        int affected = category.getListingCount() != null ? category.getListingCount() : 0;
        return ToggleResultResponse.builder()
                .id(category.getId())
                .code(category.getCode().name())
                .name(category.getName())
                .active(target)
                .previousActive(previous)
                .affectedListingCount(affected)
                .note(affected + " tin hiện có giữ nguyên hiển thị; danh mục chỉ ảnh hưởng form đăng tin và bộ lọc.")
                .cacheInvalidated(List.of(CacheName.CATEGORIES))
                .updatedAt(Instant.now())
                .build();
    }

    @Override
    @Transactional
    @CacheEvict(cacheNames = CacheName.CATEGORIES, allEntries = true)
    public ReorderResultResponse reorder(ReorderRequest request, Long actorId) {
        CatalogReorderSupport.validateNoDuplicates(request);
        List<ReorderResultResponse.ReorderItemResponse> items = request.getItems().stream()
                .map(item -> {
                    Category category = getEntity(item.getId());
                    int previousOrder = category.getDisplayOrder() != null ? category.getDisplayOrder() : 0;
                    category.setDisplayOrder(item.getDisplayOrder());
                    categoryRepository.save(category);
                    return ReorderResultResponse.ReorderItemResponse.builder()
                            .id(category.getId())
                            .code(category.getCode().name())
                            .name(category.getName())
                            .displayOrder(item.getDisplayOrder())
                            .previousDisplayOrder(previousOrder)
                            .build();
                })
                .toList();
        log.info("Admin {} sắp xếp lại {} danh mục", actorId, items.size());
        return ReorderResultResponse.builder()
                .updatedCount(items.size())
                .items(items)
                .cacheInvalidated(List.of(CacheName.CATEGORIES))
                .updatedAt(Instant.now())
                .build();
    }

    // ==================================================================
    //  Nội bộ
    // ==================================================================

    private Category getEntity(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private int nextDisplayOrder() {
        return categoryRepository.findAll().stream()
                .map(Category::getDisplayOrder)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private String uniqueSlug(String name) {
        String base = SlugUtil.toSlug(name);
        String candidate = base;
        int suffix = 2;
        while (categoryRepository.existsBySlug(candidate)) {
            candidate = base + "-" + suffix++;
        }
        return candidate;
    }

    private String clean(String input) {
        return input == null ? null : HtmlSanitizer.stripAllHtml(input);
    }

    private String cleanRequired(String input, String fieldLabel) {
        String cleaned = HtmlSanitizer.stripAllHtml(input);
        if (cleaned.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, fieldLabel + " không hợp lệ");
        }
        return cleaned;
    }
}
