package com.webtro.modules.user.service;

import com.webtro.modules.user.dto.response.LandlordDashboardResponse;

/**
 * Read-only landlord dashboard.
 */
public interface LandlordDashboardService {

    LandlordDashboardResponse getDashboard(Long userId, int days);
}
