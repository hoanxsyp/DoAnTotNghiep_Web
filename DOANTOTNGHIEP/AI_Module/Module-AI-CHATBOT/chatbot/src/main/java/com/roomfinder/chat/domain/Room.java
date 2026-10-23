package com.roomfinder.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phòng mà chatbot làm việc. Nguồn {@code db}: ánh xạ bảng `room` (đã bổ sung cột theo §8).
 * Nguồn {@code backend}: dựng từ API tin đăng của web, KHÔNG bao giờ lưu xuống DB.
 * distance_m KHÔNG lưu trong DB — tính runtime khi tìm theo POI. Vector mô tả cho semantic
 * rerank cache ở bảng riêng {@code listing_embedding} (xem {@link ListingEmbedding}).
 */
@Entity
@Table(name = "room")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Long price;                 // VND
    private BigDecimal area;            // m2
    private String district;
    private String addressText;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;

    private Boolean hasAirConditioner;
    private Boolean hasParking;
    private Boolean hasWifi;
    private Boolean hasWashingMachine;
    private Boolean isPrivateBathroom;

    private String roomType;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String thumbnail;

    /** Khoảng cách (m) tới POI — chỉ set khi tìm theo "gần X". Không map cột. */
    @Transient
    private Double distanceM;

    // --- Chỉ có khi nguồn là tin đăng của web (BackendRoomSource), không map cột ---

    /** Slug tin đăng — link {@code /tin/{slug}-{id}} trên web. */
    @Transient
    private String slug;

    /** Phường của tin (wards.id phía web). */
    @Transient
    private Long wardId;

    /** Tên các tiện ích của tin (vd "Máy lạnh", "Thang máy") — để NLG mô tả đúng. */
    @Transient
    private List<String> amenityNames;

    /**
     * Key tiện ích (theo {@code UtilityNormalizer}, vd "private_bathroom", "balcony") mà tin này CÓ —
     * để trả lời "các phòng đó có vskk không" cho mọi tiện ích. {@code null} = nguồn không đủ dữ liệu.
     */
    @Transient
    private java.util.Set<String> utilityKeys;
}
