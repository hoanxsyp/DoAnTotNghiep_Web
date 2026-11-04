package com.webtro.modules.admin.service.impl;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.CategoryCode;
import com.webtro.common.enums.ListingStatus;
import com.webtro.exception.BusinessException;
import com.webtro.modules.admin.dto.response.AdminListingResponse;
import com.webtro.modules.admin.mapper.AdminListingMapper;
import com.webtro.modules.admin.service.AuditLogService;
import com.webtro.modules.admin.service.SystemConfigService;
import com.webtro.modules.catalog.entity.Category;
import com.webtro.modules.catalog.repository.CategoryRepository;
import com.webtro.modules.listing.entity.Listing;
import com.webtro.modules.listing.repository.ListingRepository;
import com.webtro.modules.listing.service.ListingCategoryCountPublisher;
import com.webtro.modules.listing.service.ListingOwnerStatsPublisher;
import com.webtro.modules.listing.statemachine.ListingStateMachine;
import com.webtro.modules.moderation.repository.ModerationActionRepository;
import com.webtro.modules.moderation.repository.ReportRepository;
import com.webtro.modules.moderation.spi.ListingModerationGateway;
import com.webtro.modules.notification.service.NotificationService;
import com.webtro.modules.user.entity.User;
import com.webtro.modules.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminListingServiceImplFilterTest {

    @Mock private ListingRepository listingRepository;
    @Mock private ListingStateMachine listingStateMachine;
    @Mock private ListingModerationGateway listingModerationGateway;
    @Mock private ModerationActionRepository moderationActionRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private SystemConfigService systemConfigService;
    @Mock private AuditLogService auditLogService;
    @Mock private NotificationService notificationService;
    @Mock private AdminListingMapper adminListingMapper;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private ListingCategoryCountPublisher categoryCountPublisher;
    @Mock private ListingOwnerStatsPublisher ownerStatsPublisher;
    @Mock private CategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private AdminListingServiceImpl service;

    @Test
    void loadsOnlyRequestedModerationStatusAndEnrichesMetadata() {
        Pageable pageable = PageRequest.of(0, 20);
        Listing listing = Listing.builder().ownerId(8L).categoryId(3L).rentalUnitId(2L)
                .wardId(1L).status(ListingStatus.PENDING).build();
        listing.setId(9L);
        AdminListingResponse mapped = AdminListingResponse.builder()
                .id(9L).ownerId(8L).categoryId(3L).build();
        Category category = Category.builder().code(CategoryCode.BOARDING_HOUSE).name("Phòng trọ").build();
        category.setId(3L);
        User owner = User.builder().fullName("Chủ trọ A").email("owner@example.test").passwordHash("hash").build();
        owner.setId(8L);

        when(listingRepository.findByStatusAndDeletedAtIsNull(ListingStatus.PENDING, pageable))
                .thenReturn(new PageImpl<>(List.of(listing), pageable, 1));
        when(adminListingMapper.toResponse(listing, 0L)).thenReturn(mapped);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        PageResponse<AdminListingResponse> result = service.moderationQueue(ListingStatus.PENDING, pageable);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getCategoryCode()).isEqualTo("BOARDING_HOUSE");
            assertThat(item.getCategoryName()).isEqualTo("Phòng trọ");
            assertThat(item.getOwnerName()).isEqualTo("Chủ trọ A");
        });
        verify(listingRepository).findByStatusAndDeletedAtIsNull(ListingStatus.PENDING, pageable);
    }

    @Test
    void rejectsStatusOutsideModerationQueues() {
        Pageable pageable = PageRequest.of(0, 20);
        assertThatThrownBy(() -> service.moderationQueue(ListingStatus.ACTIVE, pageable))
                .isInstanceOf(BusinessException.class);
        verify(listingRepository, never()).findByStatusAndDeletedAtIsNull(any(), any());
    }

    @Test
    void resolvesCategoryCodeBeforeSearching() {
        Pageable pageable = PageRequest.of(0, 20);
        Category category = Category.builder().code(CategoryCode.BOARDING_HOUSE).name("Phòng trọ").build();
        category.setId(3L);
        when(categoryRepository.findByCode(CategoryCode.BOARDING_HOUSE)).thenReturn(Optional.of(category));
        when(listingRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Listing>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.searchListings(null, null, null, null, CategoryCode.BOARDING_HOUSE,
                null, null, null, null, null, null, pageable);

        verify(categoryRepository).findByCode(CategoryCode.BOARDING_HOUSE);
    }

    @Test
    void keepsExplicitCategoryIdWithoutResolvingCode() {
        Pageable pageable = PageRequest.of(0, 20);
        when(listingRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Listing>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.searchListings(null, null, null, 7L, CategoryCode.BOARDING_HOUSE,
                null, null, null, null, null, null, pageable);

        verify(categoryRepository, never()).findByCode(any());
    }

    @Test
    void handlesMissingCategoryAndOwnerMetadata() {
        Pageable pageable = PageRequest.of(0, 20);
        Listing listing = Listing.builder().ownerId(8L).categoryId(3L).rentalUnitId(2L)
                .wardId(1L).status(ListingStatus.NEED_REVIEW).build();
        AdminListingResponse mapped = AdminListingResponse.builder().ownerId(8L).categoryId(3L).build();
        when(listingRepository.findByStatusAndDeletedAtIsNull(ListingStatus.NEED_REVIEW, pageable))
                .thenReturn(new PageImpl<>(List.of(listing), pageable, 1));
        when(adminListingMapper.toResponse(listing, 0L)).thenReturn(mapped);
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<AdminListingResponse> result = service.moderationQueue(ListingStatus.NEED_REVIEW, pageable);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getCategoryCode()).isNull();
            assertThat(item.getCategoryName()).isNull();
            assertThat(item.getOwnerName()).isNull();
        });
    }

    @Test
    void handlesCategoryWithoutCodeAndSearchWithoutCategoryFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        Listing listing = Listing.builder().categoryId(3L).status(ListingStatus.PENDING).build();
        AdminListingResponse mapped = AdminListingResponse.builder().categoryId(3L).build();
        Category category = Category.builder().name("Danh mục cũ").build();
        category.setId(3L);
        when(listingRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Listing>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(listing), pageable, 1));
        when(adminListingMapper.toResponse(listing, 0L)).thenReturn(mapped);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(userRepository.findAllById(any())).thenReturn(List.of());

        PageResponse<AdminListingResponse> result = service.searchListings(
                null, null, null, null, null, null, null, null,
                null, null, null, pageable);

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getCategoryCode()).isNull();
            assertThat(item.getCategoryName()).isEqualTo("Danh mục cũ");
        });
        verify(categoryRepository, never()).findByCode(any());
    }

    @Test
    void usesNonMatchingIdWhenCategoryCodeIsAbsentFromCatalog() {
        Pageable pageable = PageRequest.of(0, 20);
        when(categoryRepository.findByCode(CategoryCode.BOARDING_HOUSE)).thenReturn(Optional.empty());
        when(listingRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Listing>>any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));
        when(categoryRepository.findAllById(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of());

        service.searchListings(null, null, null, null, CategoryCode.BOARDING_HOUSE,
                null, null, null, null, null, null, pageable);

        verify(categoryRepository).findByCode(CategoryCode.BOARDING_HOUSE);
    }

    @Test
    void enrichesListingDetailMetadata() {
        Listing listing = Listing.builder().ownerId(8L).categoryId(3L).status(ListingStatus.PENDING).build();
        listing.setId(9L);
        AdminListingResponse mapped = AdminListingResponse.builder()
                .id(9L).ownerId(8L).categoryId(3L).build();
        Category category = Category.builder().code(CategoryCode.BOARDING_HOUSE).name("Phòng trọ").build();
        category.setId(3L);
        User owner = User.builder().fullName("Chủ trọ A").email("owner@example.test").passwordHash("hash").build();
        owner.setId(8L);
        when(listingRepository.findAnyById(9L)).thenReturn(Optional.of(listing));
        when(reportRepository.countByTargetTypeAndTargetIdAndDeletedAtIsNull(any(), eq(9L))).thenReturn(2L);
        when(adminListingMapper.toResponse(listing, 2L)).thenReturn(mapped);
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(userRepository.findAllById(any())).thenReturn(List.of(owner));

        AdminListingResponse result = service.getListingDetail(9L);

        assertThat(result.getCategoryCode()).isEqualTo("BOARDING_HOUSE");
        assertThat(result.getCategoryName()).isEqualTo("Phòng trọ");
        assertThat(result.getOwnerName()).isEqualTo("Chủ trọ A");
    }
}
