package com.webtro.modules.user.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tổng quan chủ trọ ({@code GET /api/landlord/dashboard}): số tin theo trạng thái, tổng lượt
 * xem/lưu/liên hệ, điểm uy tín và tỷ lệ phản hồi.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "LandlordDashboardResponse", description = "Tổng quan chủ trọ")
public class LandlordDashboardResponse {

    /** Tổng số tin (mọi trạng thái, chưa xóa mềm). */
    private long totalListings;

    /** Số tin theo từng trạng thái (khóa = tên trạng thái). */
    private Map<String, Long> listingsByStatus;

    /** Tổng lượt xem tất cả tin. */
    private long totalViews;

    /** Tổng lượt lưu tất cả tin. */
    private long totalFavorites;

    /** Tổng lượt liên hệ tất cả tin. */
    private long totalContacts;

    /** Điểm uy tín (0-100). */
    private Integer trustScore;

    /** Nhãn uy tín (Tốt/Bình thường/Rủi ro/Cần kiểm duyệt). */
    private String trustLabel;

    /** Tỷ lệ phản hồi (%) — null nếu chưa có dữ liệu. */
    private Integer responseRatePercent;

    /** Điểm đánh giá trung bình (0-5). */
    private BigDecimal averageRating;

    /** Số lượt đánh giá. */
    private Integer reviewCount;

    /** Số báo cáo hợp lệ đã nhận. */
    private Integer validReportCount;

    /** Số lần bị cảnh báo vi phạm. */
    private Integer warningCount;
}
