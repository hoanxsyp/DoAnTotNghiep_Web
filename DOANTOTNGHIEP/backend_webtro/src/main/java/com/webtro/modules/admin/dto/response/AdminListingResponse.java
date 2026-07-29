package com.webtro.modules.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một tin đăng trong màn kiểm duyệt (canonical 4.14.1, dạng rút gọn cho danh sách).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminListingResponse", description = "Tin đăng (góc nhìn kiểm duyệt)")
public class AdminListingResponse {

    private Long id;
    private String title;
    private String slug;
    private Long categoryId;
    private BigDecimal price;
    private BigDecimal area;
    private Long ownerId;
    private String status;
    private String statusLabel;
    private Integer trustScore;
    private Long reportCount;
    private Boolean priceDeviationFlagged;
    private Long provinceId;
    private Long districtId;
    private Long wardId;
    private Instant publishedAt;
    private Instant expiredAt;
    private Instant createdAt;
    private Instant updatedAt;
}
