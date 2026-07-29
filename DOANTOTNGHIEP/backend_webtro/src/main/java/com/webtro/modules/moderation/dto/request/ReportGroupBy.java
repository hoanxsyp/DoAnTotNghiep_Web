package com.webtro.modules.moderation.dto.request;

/**
 * Cách gom nhóm khi liệt kê báo cáo cho quản trị (canonical 4.16.1). Là tham số truy vấn, KHÔNG
 * lưu DB.
 *
 * <p>Canonical §5 nêu {@code ReportGroupBy} gồm LISTING/USER/REASON ở mức ý tưởng; đặc tả endpoint
 * 4.16.1 chốt lại hai giá trị thực thi: {@code NONE} (danh sách phẳng) và {@code TARGET} (gom theo
 * đối tượng bị báo cáo). File này theo đặc tả endpoint.
 */
public enum ReportGroupBy {

    /** Không gom nhóm — trả danh sách phẳng từng báo cáo. */
    NONE,

    /** Gom nhóm theo (targetType, targetId). */
    TARGET
}
