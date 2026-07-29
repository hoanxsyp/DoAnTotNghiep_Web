package com.webtro.modules.listing.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Tác động kiểm duyệt khi sửa tin: có phải duyệt lại không, các trường nhạy cảm đã đổi
 * (docs/03 mục 4.4.8, 4.4.17).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModerationImpactResponse {

    private Boolean requiresReapproval;
    private String previousStatus;
    private String newStatus;
    private List<String> sensitiveFieldsChanged;
    private String reason;
}
