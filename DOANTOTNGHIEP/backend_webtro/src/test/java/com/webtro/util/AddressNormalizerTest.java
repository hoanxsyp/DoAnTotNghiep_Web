package com.webtro.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AddressNormalizerTest {

    private static final String BASE = AddressNormalizer.unitKey("Số 12, ngõ 62 đường Quang Trung", null);

    @ParameterizedTest
    @ValueSource(strings = {
            "Số 12, ngõ 62 đường Quang Trung",
            "12 ng 62 đường Quang Trung",
            "12 ngõ62 đ. Quang Trung",
            "số12, ngõ 62, đg Quang Trung",
            "SỐ 12 NGÕ 62 ĐƯỜNG QUANG TRUNG",
    })
    void abbreviationsAndGluedNumbersGiveTheSameKey(String variant) {
        assertEquals(BASE, AddressNormalizer.unitKey(variant, null));
    }

    @Test
    void nghMeansNgach() {
        assertEquals(
                AddressNormalizer.unitKey("Ngõ 62 ngách 3 số 5", null),
                AddressNormalizer.unitKey("ng 62 ngh 3 so 5", null));
    }

    @Test
    void orderAndTypeWordsStillMatter() {
        // Hai địa chỉ khác nhau, không được coi là trùng.
        assertNotEquals(
                AddressNormalizer.unitKey("ngõ 12 số 62", null),
                AddressNormalizer.unitKey("số 12 ngõ 62", null));
        assertNotEquals(
                AddressNormalizer.unitKey("ngõ 62 ngách 12", null),
                AddressNormalizer.unitKey("ngõ 12 ngách 62", null));
    }

    @Test
    void streetNamesWithLettersAndDigitsAreNotSplit() {
        assertNotEquals(
                AddressNormalizer.unitKey("45/12 Đường D2", null),
                AddressNormalizer.unitKey("45/12 Đường 2", null));
    }

    @Test
    void ignoresAccentsCasePunctuationAndFillerWordsInRoomNumber() {
        assertEquals(
                AddressNormalizer.unitKey("Số 12, Ngõ 5 Cầu Giấy", "Phòng 101"),
                AddressNormalizer.unitKey("12 ngo 5  cau giay", "P.101"));
    }

    @Test
    void mapsDStrokeToD() {
        assertEquals(
                AddressNormalizer.unitKey("Số 3 đường Đê La Thành", null),
                AddressNormalizer.unitKey("so 3 duong de la thanh", ""));
    }

    @Test
    void differentRoomNumbersAtSameAddressAreDifferentUnits() {
        assertNotEquals(
                AddressNormalizer.unitKey("Số 12 ngõ 5", "101"),
                AddressNormalizer.unitKey("Số 12 ngõ 5", "102"));
    }
}
