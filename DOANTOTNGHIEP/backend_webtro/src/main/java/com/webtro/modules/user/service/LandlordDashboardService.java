package com.webtro.modules.user.service;

import com.webtro.modules.user.dto.response.LandlordDashboardResponse;

/**
 * Tổng quan chủ trọ (canonical 4.4): số tin theo trạng thái, tổng lượt xem/lưu/liên hệ, điểm uy tín,
 * tỷ lệ phản hồi. Chỉ đọc; tổng hợp từ {@code ListingRepository} + hồ sơ chủ trọ.
 */
public interface LandlordDashboardService {

    /** Dashboard của chủ trọ đang đăng nhập. */
    LandlordDashboardResponse getDashboard(Long userId);
}
