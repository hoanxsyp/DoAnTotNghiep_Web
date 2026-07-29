package com.webtro.modules.admin.service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Đọc/ghi cấu hình hệ thống (canonical mục 9). Mọi ngưỡng nghiệp vụ đọc qua đây thay vì hardcode
 * {@code [§13.4]}. Có cache Redis, invalidate khi Admin cập nhật.
 *
 * <p>Các hàm {@code getX} luôn trả về giá trị (đọc từ DB/cache); nếu key không tồn tại thì ném lỗi
 * cấu hình — vì thiếu key là lỗi seed, không nên âm thầm dùng giá trị đoán.
 */
public interface SystemConfigService {

    String getString(String key);

    int getInt(String key);

    long getLong(String key);

    BigDecimal getDecimal(String key);

    double getDouble(String key);

    boolean getBoolean(String key);

    /** Danh sách các số nguyên từ chuỗi phân tách bằng dấu phẩy (ví dụ "3,1" cho nhắc hết hạn). */
    List<Integer> getIntList(String key);

    /** Cập nhật giá trị (Admin), có kiểm tra min/max và ghi audit; trả về giá trị mới. */
    String updateValue(String key, String newValue, Long actorId);

    /** Xóa cache một key (hoặc toàn bộ nếu key null). */
    void evictCache(String key);
}
