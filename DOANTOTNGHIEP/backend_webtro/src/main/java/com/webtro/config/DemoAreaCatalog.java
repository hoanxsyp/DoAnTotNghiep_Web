package com.webtro.config;

import java.util.List;
import java.util.Map;

/**
 * Dữ liệu địa lý THẬT dùng để gieo tin mẫu theo khu vực ({@link DemoDataInitializer#topUpAreas()}):
 * tên đường/phố tiêu biểu của từng phường, địa điểm nổi bật của từng quận và mặt bằng giá phòng trọ.
 * Khóa = tên phường/quận đã bỏ tiền tố "Phường "/"Quận " (vd. "Cầu Giấy/Dịch Vọng") — không dựa
 * vào id vì id do migration cấp; tên trùng giữa hai quận ("Minh Khai") được phân biệt nhờ tên quận.
 */
final class DemoAreaCatalog {

    private DemoAreaCatalog() {
    }

    /** Đường/phố tiêu biểu theo "Quận/Phường" — phần tử đầu là đường chính. */
    static final Map<String, List<String>> STREETS = Map.ofEntries(
            // Ba Đình
            Map.entry("Ba Đình/Phúc Xá", List.of("Phúc Xá", "Nghĩa Dũng", "Tân Ấp")),
            Map.entry("Ba Đình/Trúc Bạch", List.of("Trúc Bạch", "Ngũ Xã", "Châu Long")),
            Map.entry("Ba Đình/Kim Mã", List.of("Kim Mã", "Vạn Phúc", "Nguyễn Thái Học")),
            Map.entry("Ba Đình/Ngọc Hà", List.of("Ngọc Hà", "Hoàng Hoa Thám", "Đội Nhân")),
            Map.entry("Ba Đình/Điện Biên", List.of("Điện Biên Phủ", "Trần Phú", "Nguyễn Tri Phương")),
            Map.entry("Ba Đình/Đội Cấn", List.of("Đội Cấn", "Liễu Giai", "Linh Lang")),
            // Hoàn Kiếm
            Map.entry("Hoàn Kiếm/Hàng Bạc", List.of("Hàng Bạc", "Mã Mây", "Hàng Buồm")),
            Map.entry("Hoàn Kiếm/Hàng Đào", List.of("Hàng Đào", "Hàng Ngang", "Hàng Đường")),
            Map.entry("Hoàn Kiếm/Cửa Đông", List.of("Hàng Điếu", "Đường Thành", "Phùng Hưng")),
            Map.entry("Hoàn Kiếm/Tràng Tiền", List.of("Tràng Tiền", "Ngô Quyền", "Lý Thường Kiệt")),
            Map.entry("Hoàn Kiếm/Chương Dương", List.of("Chương Dương Độ", "Vạn Kiếp", "Bạch Đằng")),
            // Tây Hồ
            Map.entry("Tây Hồ/Bưởi", List.of("Lạc Long Quân", "Hoàng Hoa Thám", "Võng Thị")),
            Map.entry("Tây Hồ/Thụy Khuê", List.of("Thụy Khuê", "Văn Cao", "Dốc Tam Đa")),
            Map.entry("Tây Hồ/Yên Phụ", List.of("Yên Phụ", "Nghi Tàm", "Thanh Niên")),
            Map.entry("Tây Hồ/Quảng An", List.of("Xuân Diệu", "Quảng An", "Tô Ngọc Vân")),
            Map.entry("Tây Hồ/Nhật Tân", List.of("Âu Cơ", "Nhật Tân", "Đặng Thai Mai")),
            // Long Biên
            Map.entry("Long Biên/Ngọc Lâm", List.of("Ngọc Lâm", "Nguyễn Văn Cừ", "Long Biên 2")),
            Map.entry("Long Biên/Bồ Đề", List.of("Bồ Đề", "Nguyễn Sơn", "Hồng Tiến")),
            Map.entry("Long Biên/Gia Thụy", List.of("Gia Thụy", "Nguyễn Văn Cừ", "Ngọc Thụy")),
            Map.entry("Long Biên/Việt Hưng", List.of("Việt Hưng", "Ngô Gia Tự", "Đoàn Khuê")),
            Map.entry("Long Biên/Sài Đồng", List.of("Sài Đồng", "Nguyễn Văn Linh", "Vũ Xuân Thiều")),
            // Cầu Giấy
            Map.entry("Cầu Giấy/Nghĩa Đô", List.of("Hoàng Quốc Việt", "Nghĩa Đô", "Hoàng Sâm")),
            Map.entry("Cầu Giấy/Dịch Vọng", List.of("Xuân Thủy", "Dịch Vọng", "Trần Đăng Ninh")),
            Map.entry("Cầu Giấy/Quan Hoa", List.of("Quan Hoa", "Nguyễn Khánh Toàn", "Nguyễn Văn Huyên")),
            Map.entry("Cầu Giấy/Yên Hòa", List.of("Trung Kính", "Yên Hòa", "Nguyễn Chánh")),
            Map.entry("Cầu Giấy/Mai Dịch", List.of("Hồ Tùng Mậu", "Phạm Hùng", "Trần Bình")),
            // Đống Đa
            Map.entry("Đống Đa/Cát Linh", List.of("Cát Linh", "Giảng Võ", "Hào Nam")),
            Map.entry("Đống Đa/Văn Miếu", List.of("Văn Miếu", "Quốc Tử Giám", "Ngô Sĩ Liên")),
            Map.entry("Đống Đa/Khâm Thiên", List.of("Khâm Thiên", "Lê Duẩn", "Nguyễn Lương Bằng")),
            Map.entry("Đống Đa/Láng Hạ", List.of("Láng Hạ", "Thái Hà", "Huỳnh Thúc Kháng")),
            Map.entry("Đống Đa/Ô Chợ Dừa", List.of("Xã Đàn", "Hoàng Cầu", "Đê La Thành")),
            Map.entry("Đống Đa/Trung Liệt", List.of("Thái Hà", "Đặng Tiến Đông", "Tây Sơn")),
            // Hai Bà Trưng
            Map.entry("Hai Bà Trưng/Bách Khoa", List.of("Tạ Quang Bửu", "Lê Thanh Nghị", "Trần Đại Nghĩa")),
            Map.entry("Hai Bà Trưng/Bạch Mai", List.of("Bạch Mai", "Phố Huế", "Lê Thanh Nghị")),
            Map.entry("Hai Bà Trưng/Đồng Tâm", List.of("Đồng Tâm", "Giải Phóng", "Trần Đại Nghĩa")),
            Map.entry("Hai Bà Trưng/Minh Khai", List.of("Minh Khai", "Kim Ngưu", "Lạc Trung")),
            Map.entry("Hai Bà Trưng/Vĩnh Tuy", List.of("Vĩnh Tuy", "Minh Khai", "Lĩnh Nam")),
            // Hoàng Mai
            Map.entry("Hoàng Mai/Định Công", List.of("Định Công", "Kim Giang", "Định Công Thượng")),
            Map.entry("Hoàng Mai/Đại Kim", List.of("Kim Giang", "Nguyễn Xiển", "Đại Kim")),
            Map.entry("Hoàng Mai/Hoàng Liệt", List.of("Linh Đàm", "Nguyễn Hữu Thọ", "Bằng Liệt")),
            Map.entry("Hoàng Mai/Tân Mai", List.of("Tân Mai", "Trương Định", "Nguyễn Chính")),
            Map.entry("Hoàng Mai/Giáp Bát", List.of("Giải Phóng", "Kim Đồng", "Giáp Bát")),
            // Thanh Xuân
            Map.entry("Thanh Xuân/Thượng Đình", List.of("Nguyễn Trãi", "Vũ Trọng Phụng", "Thượng Đình")),
            Map.entry("Thanh Xuân/Khương Trung", List.of("Khương Trung", "Vương Thừa Vũ", "Khương Hạ")),
            Map.entry("Thanh Xuân/Nhân Chính", List.of("Hoàng Đạo Thúy", "Lê Văn Lương", "Quan Nhân")),
            Map.entry("Thanh Xuân/Thanh Xuân Bắc", List.of("Nguyễn Quý Đức", "Nguyễn Tuân", "Vũ Hữu")),
            Map.entry("Thanh Xuân/Hạ Đình", List.of("Hạ Đình", "Nguyễn Xiển", "Kim Giang")),
            // Nam Từ Liêm
            Map.entry("Nam Từ Liêm/Mỹ Đình 1", List.of("Lê Đức Thọ", "Nguyễn Hoàng", "Đình Thôn")),
            Map.entry("Nam Từ Liêm/Mỹ Đình 2", List.of("Hàm Nghi", "Nguyễn Cơ Thạch", "Trần Văn Lai")),
            Map.entry("Nam Từ Liêm/Mễ Trì", List.of("Mễ Trì", "Lương Thế Vinh", "Đỗ Đức Dục")),
            Map.entry("Nam Từ Liêm/Cầu Diễn", List.of("Hồ Tùng Mậu", "Cầu Diễn", "Phú Diễn")),
            Map.entry("Nam Từ Liêm/Phú Đô", List.of("Phú Đô", "Lê Quang Đạo", "Mễ Trì Hạ")),
            // Bắc Từ Liêm
            Map.entry("Bắc Từ Liêm/Cổ Nhuế 1", List.of("Cổ Nhuế", "Phạm Văn Đồng", "Hoàng Công Chất")),
            Map.entry("Bắc Từ Liêm/Cổ Nhuế 2", List.of("Cổ Nhuế", "Trần Cung", "Đức Thắng")),
            Map.entry("Bắc Từ Liêm/Xuân Đỉnh", List.of("Xuân Đỉnh", "Phạm Văn Đồng", "Tân Xuân")),
            Map.entry("Bắc Từ Liêm/Đông Ngạc", List.of("Đông Ngạc", "Phạm Văn Đồng", "Đức Diễn")),
            Map.entry("Bắc Từ Liêm/Minh Khai", List.of("Văn Tiến Dũng", "Phú Diễn", "Nguyên Xá")),
            // Hà Đông
            Map.entry("Hà Đông/Nguyễn Trãi", List.of("Nguyễn Trãi", "Quang Trung", "Bà Triệu")),
            Map.entry("Hà Đông/Mộ Lao", List.of("Tố Hữu", "Nguyễn Văn Lộc", "Làng Việt Kiều Châu Âu")),
            Map.entry("Hà Đông/Văn Quán", List.of("Văn Quán", "Phùng Hưng", "Chiến Thắng")),
            Map.entry("Hà Đông/Hà Cầu", List.of("Hà Cầu", "Quang Trung", "Ngô Thì Nhậm")),
            Map.entry("Hà Đông/Quang Trung", List.of("Quang Trung", "Lê Trọng Tấn", "Ngô Quyền")));

    /** Địa điểm nổi bật quanh từng quận — đưa vào mô tả để tìm kiếm/chatbot khớp được. */
    static final Map<String, List<String>> LANDMARKS = Map.ofEntries(
            Map.entry("Ba Đình", List.of("Lăng Bác", "Học viện Ngoại giao", "Lotte Center Liễu Giai")),
            Map.entry("Hoàn Kiếm", List.of("Hồ Gươm", "phố cổ Hà Nội", "chợ Đồng Xuân")),
            Map.entry("Tây Hồ", List.of("Hồ Tây", "Lotte Mall Tây Hồ", "chợ Bưởi")),
            Map.entry("Long Biên", List.of("Aeon Mall Long Biên", "Vincom Long Biên", "cầu Chương Dương")),
            Map.entry("Cầu Giấy", List.of("ĐH Quốc gia Hà Nội", "ĐH Sư phạm Hà Nội", "công viên Cầu Giấy")),
            Map.entry("Đống Đa", List.of("ĐH Y Hà Nội", "ĐH Thủy lợi", "ga Cát Linh")),
            Map.entry("Hai Bà Trưng", List.of("ĐH Bách Khoa Hà Nội", "ĐH Kinh tế Quốc dân", "Bệnh viện Bạch Mai")),
            Map.entry("Hoàng Mai", List.of("bán đảo Linh Đàm", "bến xe Giáp Bát", "công viên Yên Sở")),
            Map.entry("Thanh Xuân", List.of("ĐH Khoa học Tự nhiên", "Royal City", "ngã tư Sở")),
            Map.entry("Nam Từ Liêm", List.of("sân vận động Mỹ Đình", "Keangnam Landmark", "ĐH Thương mại")),
            Map.entry("Bắc Từ Liêm", List.of("ĐH Mỏ - Địa chất", "ĐH Tài nguyên và Môi trường", "Công viên Hòa Bình")),
            Map.entry("Hà Đông", List.of("Học viện Công nghệ Bưu chính Viễn thông", "Aeon Mall Hà Đông", "ĐH Kiến trúc")));

    /** Quận có nhiều trường đại học — tin ở đây hay gắn tiện ích "Gần trường đại học". */
    static final List<String> UNIVERSITY_DISTRICTS =
            List.of("Cầu Giấy", "Đống Đa", "Hai Bà Trưng", "Thanh Xuân", "Bắc Từ Liêm", "Hà Đông", "Nam Từ Liêm");

    /** Giá thuê PHÒNG TRỌ ~20m2 phổ biến theo quận (VND/tháng) — mốc để suy giá các loại hình khác. */
    static final Map<String, Long> BASE_PRICE = Map.ofEntries(
            Map.entry("Hoàn Kiếm", 4_200_000L), Map.entry("Ba Đình", 3_800_000L),
            Map.entry("Tây Hồ", 3_800_000L), Map.entry("Đống Đa", 3_400_000L),
            Map.entry("Cầu Giấy", 3_400_000L), Map.entry("Hai Bà Trưng", 3_200_000L),
            Map.entry("Thanh Xuân", 3_200_000L), Map.entry("Nam Từ Liêm", 3_100_000L),
            Map.entry("Long Biên", 2_800_000L), Map.entry("Hoàng Mai", 2_600_000L),
            Map.entry("Bắc Từ Liêm", 2_600_000L), Map.entry("Hà Đông", 2_500_000L));

    static String key(String districtName, String wardName) {
        return shortName(districtName) + "/" + shortName(wardName);
    }

    /** "Quận Cầu Giấy" → "Cầu Giấy", "Phường Mỹ Đình 1" → "Mỹ Đình 1". */
    static String shortName(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceFirst("^(Quận|Huyện|Thị xã|Phường|Xã|Thị trấn)\\s+", "").trim();
    }

    /** Đường của phường; phường chưa có trong bảng thì dùng chính tên phường (vẫn đúng địa giới). */
    static List<String> streets(String districtName, String wardName) {
        return STREETS.getOrDefault(key(districtName, wardName), List.of(shortName(wardName)));
    }

    static List<String> landmarks(String districtName) {
        return LANDMARKS.getOrDefault(shortName(districtName), List.of());
    }

    static long basePrice(String districtName) {
        return BASE_PRICE.getOrDefault(shortName(districtName), 3_000_000L);
    }
}
