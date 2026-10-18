package com.roomfinder.chat.listing;

import com.roomfinder.chat.domain.Room;
import com.roomfinder.chat.model.Filters;
import com.roomfinder.chat.repository.RoomRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Bảng {@code room} trong DB riêng của chatbot (59 phòng demo) — hành vi gốc của module.
 * Bảng demo chỉ có 4 cột tiện ích (điều hòa, chỗ để xe, wifi, máy giặt); tiện ích khác không
 * lọc được ở nguồn này (bỏ qua, không lọc oan). Đủ ~28 tiện ích: nguồn {@code backend}.
 */
@Component
@ConditionalOnProperty(prefix = "roomfinder.listing", name = "source", havingValue = "db", matchIfMissing = true)
public class DbRoomSource implements RoomSource {

    private final RoomRepository roomRepo;

    public DbRoomSource(RoomRepository roomRepo) {
        this.roomRepo = roomRepo;
    }

    @Override
    public List<Room> search(Filters f, Double centerLat, Double centerLng, double radiusM, int limit) {
        Boolean hasAc = f.hasUtility("air_conditioner") ? Boolean.TRUE : null;
        Boolean hasParking = f.hasUtility("parking") ? Boolean.TRUE : null;
        Boolean hasWifi = f.hasUtility("wifi") ? Boolean.TRUE : null;
        Boolean hasWashing = f.hasUtility("washing_machine") ? Boolean.TRUE : null;

        if (centerLat != null && centerLng != null) {
            return roomRepo.searchByGeo(f.getPriceMin(), f.getPriceMax(), f.getAreaMin(),
                    hasAc, hasParking, hasWifi, hasWashing, f.getRoomType(),
                    centerLat, centerLng, radiusM, limit);
        }
        return roomRepo.searchByFilters(f.getPriceMin(), f.getPriceMax(), f.getLocation(),
                f.getAreaMin(), hasAc, hasParking, hasWifi, hasWashing, f.getRoomType(), limit);
    }

    @Override
    public List<Room> findByIds(List<Long> ids) {
        return roomRepo.findAllById(ids);
    }
}
