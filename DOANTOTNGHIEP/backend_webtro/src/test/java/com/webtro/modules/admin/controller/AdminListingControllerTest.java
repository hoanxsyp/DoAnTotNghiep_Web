package com.webtro.modules.admin.controller;

import com.webtro.common.PageResponse;
import com.webtro.common.enums.ListingStatus;
import com.webtro.modules.admin.service.AdminListingService;
import com.webtro.modules.moderation.service.ModerationService;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminListingControllerTest {

    @Test
    void passesRequestedModerationStatusToService() {
        AdminListingService listingService = mock(AdminListingService.class);
        ModerationService moderationService = mock(ModerationService.class);
        AdminListingController controller = new AdminListingController(listingService, moderationService);
        PageRequest pageable = PageRequest.of(0, 20);
        PageResponse<com.webtro.modules.admin.dto.response.AdminListingResponse> page =
                PageResponse.<com.webtro.modules.admin.dto.response.AdminListingResponse>builder()
                        .items(List.of()).page(0).size(20).totalElements(0).totalPages(0)
                        .first(true).last(true).build();
        when(listingService.moderationQueue(ListingStatus.NEED_REVIEW, pageable)).thenReturn(page);

        var response = controller.moderationQueue(ListingStatus.NEED_REVIEW, pageable);

        assertThat(response.getBody()).isNotNull();
        verify(listingService).moderationQueue(ListingStatus.NEED_REVIEW, pageable);
    }
}
