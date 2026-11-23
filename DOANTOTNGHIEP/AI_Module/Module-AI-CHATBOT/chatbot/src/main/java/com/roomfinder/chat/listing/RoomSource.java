package com.roomfinder.chat.listing;

import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;

import java.util.List;

/**
 * Nguồn dữ liệu phòng cho tầng Retrieval — tách để đổi giữa DB demo của chatbot
 * ({@link DbRoomSource}) và tin đăng thật của web ({@link BackendRoomSource}) bằng cấu hình
 * {@code roomfinder.listing.source}, không đụng logic nới lỏng / rerank / NLG.
 */
public interface RoomSource {

    /**
     * Lọc cứng theo {@code f}. Có {@code centerLat/centerLng} thì chỉ lấy phòng trong
     * {@code radiusM} và sắp theo khoảng cách tăng dần; không có thì sắp theo giá tăng dần.
     */
    List<Room> search(Filters f, Double centerLat, Double centerLng, double radiusM, int limit);

    /** Phòng theo id (bỏ qua id không còn tồn tại / không còn công khai); thứ tự không đảm bảo. */
    List<Room> findByIds(List<Long> ids);
}
