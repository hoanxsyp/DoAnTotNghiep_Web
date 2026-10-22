package com.webtro.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HtmlSanitizerTest {

    @Test
    void keepLinesPreservesParagraphsForDescriptions() {
        String input = "Căn hộ 2 phòng ngủ.\n\nĐịa chỉ:   Số 120\r\n- Diện tích 61m2\n\n\n\nLiên hệ chủ nhà.";
        assertEquals("Căn hộ 2 phòng ngủ.\n\nĐịa chỉ: Số 120\n- Diện tích 61m2\n\nLiên hệ chủ nhà.",
                HtmlSanitizer.stripAllHtmlKeepLines(input));
    }

    @Test
    void keepLinesStillRemovesHtmlAndTrimsBlankEdges() {
        assertEquals("Phòng đẹp\nalert(1)",
                HtmlSanitizer.stripAllHtmlKeepLines("\n\n<b>Phòng</b> đẹp\n<script>x</script>alert(1)\n\n"));
    }

    @Test
    void stripAllHtmlStillCollapsesToOneLineForTitles() {
        assertEquals("Phòng đẹp gần trường", HtmlSanitizer.stripAllHtml("Phòng đẹp\ngần   trường"));
    }
}
