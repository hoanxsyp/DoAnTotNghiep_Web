package com.webtro.util;

import com.webtro.constant.AppConstant;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Tiện ích tọa độ địa lý.
 *
 * <p>{@link #roundForPublic} làm tròn tọa độ cho khách chưa đăng nhập (canonical mục 8): giữ 3
 * chữ số thập phân (~110m) để hiển thị vị trí tương đối trên bản đồ mà không lộ tọa độ chính xác
 * của nhà riêng — cùng tinh thần với che số điện thoại {@code [§3.8]}.
 */
public final class GeoUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoUtil() {
    }

    public static BigDecimal roundForPublic(BigDecimal coordinate) {
        if (coordinate == null) {
            return null;
        }
        return coordinate.setScale(AppConstant.PUBLIC_GEO_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Khoảng cách Haversine giữa hai tọa độ, đơn vị km. Dùng cho AI dự đoán giá
     * (khoảng cách đến trung tâm) và gợi ý theo khu vực.
     */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
