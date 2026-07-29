package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Kết quả chung cho các hành động đổi trạng thái tin: submit, hide, unhide, close
 * (docs/03 mục 4.4.10–4.4.13). Các trường không liên quan để {@code null} và bị loại khỏi JSON.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingActionResponse {

    private Long id;
    private String status;
    private String previousStatus;
    private Instant at;
    private Instant expiredAt;
    private Long daysRemaining;

    // ----- submit -----
    private Boolean autoApproved;

    // ----- hide / unhide -----
    private Boolean canUnhide;

    // ----- close -----
    private String closeReason;
}
