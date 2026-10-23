package com.roomfinder.chat.dto;

import com.roomfinder.chat.domain.Room;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

/** Thẻ phòng gọn để frontend render RoomCard — §7.1. */
@Getter
@Builder
@AllArgsConstructor
public class RoomCardDto {
    private Long id;
    private String title;
    private Long price;
    private BigDecimal area;
    private String district;
    private String address;
    private Double distanceM;      // null nếu không tìm theo POI
    private String thumbnail;
    /** Slug tin trên web; null với phòng demo (nguồn db). */
    private String slug;
    /** Link trang tin: web dùng {@code /tin/{slug}-{id}}, phòng demo dùng {@code /rooms/{id}}. */
    private String url;

    public static RoomCardDto from(Room r) {
        return RoomCardDto.builder()
                .id(r.getId())
                .title(r.getTitle())
                .price(r.getPrice())
                .area(r.getArea())
                .district(r.getDistrict())
                .address(r.getAddressText())
                .distanceM(r.getDistanceM() == null ? null
                        : Math.round(r.getDistanceM() * 10.0) / 10.0)
                .thumbnail(r.getThumbnail())
                .slug(r.getSlug())
                .url(r.getSlug() != null ? "/tin/" + r.getSlug() + "-" + r.getId() : "/rooms/" + r.getId())
                .build();
    }
}
