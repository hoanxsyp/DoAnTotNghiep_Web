package com.webtro.modules.listing.dto.response;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.webtro.common.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Paginated owner listings with counts used by the status filter tabs. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MyListingsResponse", description = "Owner listing page and status counts")
public class MyListingsResponse {

    @JsonUnwrapped
    private PageResponse<ListingSummaryResponse> page;

    @Schema(description = "Non-deleted listing count by status")
    private Map<String, Long> statusCounts;
}
