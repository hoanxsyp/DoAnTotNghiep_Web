package com.roomfinder.chat.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** NER gán mọi mức giá là PRICE_MAX — xét từ đứng quanh để nhận ra giá TỐI THIỂU. */
class LocalNluPriceCueTest {

    private static boolean min(String msg, String span) {
        int start = msg.indexOf(span);
        return LocalNluServiceImpl.isMinPrice(msg, start, start + span.length(), span);
    }

    @Test
    void cueBeforeMeansMinimum() {
        assertTrue(min("tìm phòng khác ở Hoàn Kiếm từ 4 triệu", "4 triệu"));
        assertTrue(min("phòng trên 3tr ở Cầu Giấy", "3tr"));
        assertTrue(min("tối thiểu 2tr5 nhé", "2tr5"));
    }

    @Test
    void cueAfterMeansMinimum() {
        assertTrue(min("ngân sách 4tr trở lên", "4tr"));
    }

    @Test
    void ordinaryBudgetStaysMaximum() {
        assertFalse(min("tìm phòng dưới 3 triệu ở Cầu Giấy", "3 triệu"));
        assertFalse(min("tc 3tr", "3tr"));
        // "từ ... đổ lại" = tối đa: "đổ lại" thắng
        assertFalse(min("tài chính từ 3tr đổ lại", "3tr"));
        // "rẻ hơn X" = tối đa, dù có chữ "hơn"
        assertFalse(min("có phòng nào rẻ hơn 3 củ nữa k", "3 củ"));
        assertFalse(min("phòng thấp hơn 2tr5", "2tr5"));
        assertTrue(min("phòng đắt hơn 3tr cũng được", "3tr"));
        // khoảng giá giữ PRICE_MAX (normalizer xử lý riêng)
        assertFalse(min("tầm từ 2tr5-3tr", "2tr5-3tr"));
    }
}
