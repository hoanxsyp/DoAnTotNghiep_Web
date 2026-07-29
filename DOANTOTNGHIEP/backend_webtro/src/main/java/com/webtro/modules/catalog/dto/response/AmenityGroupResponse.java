package com.webtro.modules.catalog.dto.response;

import com.webtro.common.enums.AmenityGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Gom các tiện ích theo {@link AmenityGroup} — phục vụ panel bộ lọc và form đăng tin gom nhóm
 * (nội thất, an ninh, sinh hoạt, giao thông) {@code [§10.5]}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AmenityGroupResponse", description = "Nhóm tiện ích kèm danh sách tiện ích")
public class AmenityGroupResponse {

    @Schema(description = "Mã nhóm", example = "FURNITURE")
    private AmenityGroup group;

    @Schema(description = "Nhãn nhóm tiếng Việt", example = "Nội thất")
    private String groupLabel;

    @Schema(description = "Danh sách tiện ích thuộc nhóm")
    private List<AmenityResponse> items;
}
