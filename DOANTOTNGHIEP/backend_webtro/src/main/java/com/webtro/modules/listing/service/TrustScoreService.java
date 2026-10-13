package com.webtro.modules.listing.service;

import com.webtro.common.enums.TrustLabel;

import java.math.BigDecimal;

/**
 * Tính điểm uy tín tin đăng và chủ trọ (canonical mục 10.5, §5.7, §5.8).
 *
 * <p>Giữ ĐÚNG công thức tài liệu §5.8, mọi hệ số đọc từ {@code system_configs} (không hardcode).
 */
public interface TrustScoreService {

    /**
     * Tính điểm uy tín tin: base + AvgRating·wRating − ValidReport·wReport − Warning·wWarn, kẹp vào
     * [min, max]. Công thức §5.8 gốc còn hai số hạng theo cảm xúc bình luận (Pos·wPos − Neg·wNeg);
     * module phân tích cảm xúc đã bị loại khỏi hệ thống nên hai số hạng này không còn.
     */
    int calculateListingTrustScore(BigDecimal averageRating, int validReports, int violationWarnings);

    /** Tính lại và LƯU điểm uy tín cho một tin (đọc số liệu từ listing). */
    void recalculateAndSaveListing(Long listingId);

    /** Tính lại điểm uy tín chủ trọ (canonical mục 10.5): AVG(tin) − warning·w + ResponseTerm. */
    void recalculateLandlord(Long landlordId);

    /** Nhãn uy tín suy ra từ điểm (dùng hiển thị cảnh báo nhẹ [§3.8]). */
    TrustLabel labelOf(int trustScore);
}
