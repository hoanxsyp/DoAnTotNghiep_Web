package com.webtro.modules.catalog.service.impl;

import com.webtro.constant.ErrorCode;
import com.webtro.exception.BusinessRuleException;
import com.webtro.modules.catalog.dto.request.ReorderRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Tiện ích dùng chung cho thao tác sắp xếp thứ tự hiển thị (danh mục, tiện ích) — mục
 * 4.17.21–4.17.22: {@code id} và {@code displayOrder} gửi lên đều không được trùng nhau trong
 * cùng một mảng, ngược lại → {@code DISPLAY_ORDER_DUPLICATE} (422).
 */
final class CatalogReorderSupport {

    private CatalogReorderSupport() {
    }

    static void validateNoDuplicates(ReorderRequest request) {
        Set<Long> seenIds = new HashSet<>();
        Set<Integer> seenOrders = new HashSet<>();
        for (ReorderRequest.ReorderItemRequest item : request.getItems()) {
            if (!seenIds.add(item.getId())) {
                throw new BusinessRuleException(ErrorCode.DISPLAY_ORDER_DUPLICATE,
                        "Id " + item.getId() + " xuất hiện nhiều lần trong danh sách");
            }
            if (!seenOrders.add(item.getDisplayOrder())) {
                throw new BusinessRuleException(ErrorCode.DISPLAY_ORDER_DUPLICATE,
                        "Thứ tự hiển thị " + item.getDisplayOrder() + " bị trùng trong danh sách");
            }
        }
    }
}
