package com.roomfinder.chat.normalizer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;

/** Kiểm thử nhận diện tiện ích — §3.3. */
class UtilityNormalizerTest {

    @Test
    void detectsSynonyms() {
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có điều hòa"));
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có máy lạnh"));
        assertTrue(UtilityNormalizer.detectAll("có máy giặt và chỗ để xe")
                .containsAll(List.of("washing_machine", "parking")));
        assertEquals("air_conditioner", UtilityNormalizer.normalizeOne("điều hoà"));
    }

    /**
     * Ca lỗi gốc: biến thể "ac" khớp substring trong "khác"/"các" khiến mọi câu
     * chứa các từ rất phổ biến này bị gán nhầm air_conditioner.
     */
    @Test
    void commonWordsContainingAcAreNotAirConditioner() {
        assertTrue(UtilityNormalizer.detectAll("tìm phòng khác ở Đống Đa").isEmpty());
        assertTrue(UtilityNormalizer.detectAll("cho mình xem các phòng ở Cầu Giấy").isEmpty());
        assertTrue(UtilityNormalizer.detectAll("phòng nào bác thấy ổn").isEmpty());
        assertFalse(UtilityNormalizer.detectAll("tìm phòng khác").contains("air_conditioner"));
    }

    /** "ac" đứng độc lập vẫn phải nhận ra. */
    @Test
    void standaloneAcIsStillRecognized() {
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectAll("phòng có ac không"));
    }

    @Test
    void returnsEmptyWhenNoUtility() {
        assertTrue(UtilityNormalizer.detectAll("tìm phòng dưới 3 triệu ở Thanh Xuân").isEmpty());
        assertEquals(null, UtilityNormalizer.normalizeOne("phòng đẹp"));
    }

    // --- 1.5: mở rộng theo danh mục tiện ích của web ---------------------

    @Test
    void recognizesExtendedUtilities() {
        assertEquals("water_heater", UtilityNormalizer.normalizeOne("bình nóng lạnh"));
        assertEquals("fridge", UtilityNormalizer.normalizeOne("tủ lạnh"));
        assertEquals("elevator", UtilityNormalizer.normalizeOne("thang máy"));
        assertEquals("balcony", UtilityNormalizer.normalizeOne("BAN CÔNG"));
        assertEquals("mezzanine", UtilityNormalizer.normalizeOne("gác lửng"));
        assertEquals("private_bathroom", UtilityNormalizer.normalizeOne("vệ sinh khép kín"));
        assertEquals("pet", UtilityNormalizer.normalizeOne("nuôi mèo"));
        assertEquals("full_furniture", UtilityNormalizer.normalizeOne("full đồ"));
        assertEquals("free_hours", UtilityNormalizer.normalizeOne("giờ giấc tự do"));
        assertEquals("car_parking", UtilityNormalizer.normalizeOne("chỗ để ô tô"));
        assertEquals("parking", UtilityNormalizer.normalizeOne("chỗ để xe điện"));
    }

    /** Viết tắt chỉ nhận khi cả span đúng bằng nó, không quét trong câu. */
    @Test
    void abbreviationsOnlyAsWholeSpan() {
        assertEquals("water_heater", UtilityNormalizer.normalizeOne("nl"));
        assertEquals("fridge", UtilityNormalizer.normalizeOne("tl"));
        assertEquals("private_bathroom", UtilityNormalizer.normalizeOne("VSKK"));
        assertEquals("wifi", UtilityNormalizer.normalizeOne("mạng"));
        assertTrue(UtilityNormalizer.detectAll("em tl sau nhé").isEmpty());
    }

    /** Trùng chữ sau khi bỏ dấu: "tự do" ≠ "tủ đồ", "mang xe" ≠ "mạng". */
    @Test
    void accentCollisionsDoNotMisfire() {
        assertEquals(List.of("free_hours"), UtilityNormalizer.detectAll("cần giờ giấc tự do"));
        assertFalse(UtilityNormalizer.detectAll("được mang xe điện vào").contains("wifi"));
        assertEquals("free_hours", UtilityNormalizer.normalizeOne("giờ giấc tự do"));
    }

    @Test
    void labelsAreVietnamese() {
        assertEquals("nóng lạnh", UtilityNormalizer.label("water_heater"));
        assertEquals("unknown_key", UtilityNormalizer.label("unknown_key"));
    }

    // --- Phủ định — câu chat THẬT trong ml/data2.md ---------------------------

    @Test
    void detectsNegatedUtilitiesInRealChat() {
        assertEquals(List.of("balcony"),
                UtilityNormalizer.detectNegated("không cần ban công đâu, có cửa sổ là được rồi"));
        assertEquals(List.of("air_conditioner"),
                UtilityNormalizer.detectNegated("có điều hoà thì tốt, không có cũng ko sao ạ"));
        assertEquals(List.of("washing_machine"),
                UtilityNormalizer.detectNegated("thôi bỏ tiêu chí máy giặt riêng ạ, dùng chung cũng oke"));
        assertEquals(List.of("full_furniture"),
                UtilityNormalizer.detectNegated("phòng trống hay full đồ đều được nhé, giá hợp lý là ok"));
        assertTrue(UtilityNormalizer.detectNegated("em chỉ cần nóng lạnh với điều hoà thôi, tủ giường tự sắm được")
                .contains("bed"));
        assertEquals(List.of("air_conditioner"), UtilityNormalizer.detectNegated("thôi khỏi cần điều hòa cũng được"));
        assertEquals(List.of("parking"), UtilityNormalizer.detectNegated("không có chỗ để xe cũng được"));
    }

    @Test
    void wantedUtilitiesAreNotNegated() {
        assertTrue(UtilityNormalizer.detectNegated("à em cần thêm chỗ để xe nữa nhé mn").isEmpty());
        assertTrue(UtilityNormalizer.detectNegated("tầng cao cũng ok miễn có thang máy nha").isEmpty());
        assertTrue(UtilityNormalizer.detectNegated("tìm phòng có điều hoà và máy giặt").isEmpty());
        // "chỉ cần" là MUỐN có, khác "không cần"
        List<String> neg = UtilityNormalizer.detectNegated("em chỉ cần nóng lạnh với điều hoà thôi, tủ giường tự sắm được");
        assertFalse(neg.contains("water_heater"));
        assertFalse(neg.contains("air_conditioner"));
        assertTrue(UtilityNormalizer.detectNegated("đi bộ ra đường lớn có xa lắm ko").isEmpty());
        // "bổ sung" bỏ dấu thành "bo sung" — nghĩa là THÊM, không phải "bỏ"
        assertTrue(UtilityNormalizer.detectNegated("em muốn bổ sung thêm chỗ để xe").isEmpty());
        assertEquals(List.of("parking"), UtilityNormalizer.detectNegated("thôi bỏ chỗ để xe đi"));
    }
}
