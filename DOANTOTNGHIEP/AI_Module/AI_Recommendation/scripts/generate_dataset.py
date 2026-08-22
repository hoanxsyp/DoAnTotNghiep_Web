"""
Script sinh dataset mock cho hệ thống gợi ý phòng trọ.
Output: data/rooms.json, data/users.json, data/view_history.json
"""

import json
import random
import uuid
from datetime import datetime, timedelta
from pathlib import Path

random.seed(42)

# ─── Constants ────────────────────────────────────────────────────────────────

DISTRICTS = [
    "Quận 1", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
    "Quận 7", "Quận 8", "Quận 10", "Quận 11", "Quận 12",
    "Bình Thạnh", "Gò Vấp", "Phú Nhuận", "Tân Bình", "Tân Phú",
    "Bình Tân", "Thủ Đức", "Hóc Môn", "Bình Chánh",
]

# Tọa độ gần đúng theo từng quận (lat, lng)
DISTRICT_COORDS = {
    "Quận 1":     (10.7769, 106.7009),
    "Quận 3":     (10.7800, 106.6890),
    "Quận 4":     (10.7580, 106.7040),
    "Quận 5":     (10.7545, 106.6620),
    "Quận 6":     (10.7480, 106.6340),
    "Quận 7":     (10.7300, 106.7200),
    "Quận 8":     (10.7230, 106.6280),
    "Quận 10":    (10.7740, 106.6680),
    "Quận 11":    (10.7630, 106.6480),
    "Quận 12":    (10.8680, 106.6560),
    "Bình Thạnh": (10.8120, 106.7140),
    "Gò Vấp":     (10.8380, 106.6650),
    "Phú Nhuận":  (10.7990, 106.6800),
    "Tân Bình":   (10.8020, 106.6520),
    "Tân Phú":    (10.7900, 106.6270),
    "Bình Tân":   (10.7530, 106.6010),
    "Thủ Đức":    (10.8700, 106.7650),
    "Hóc Môn":    (10.8930, 106.5960),
    "Bình Chánh": (10.6880, 106.5990),
}

ROOM_TYPES = ["phong_tro", "nha_tro", "chung_cu_mini", "can_ho_dich_vu"]

# Giá theo loại phòng (min, max) đơn vị: nghìn VNĐ
PRICE_RANGE = {
    "phong_tro":       (1_200, 2_800),
    "nha_tro":         (1_500, 3_500),
    "chung_cu_mini":   (3_000, 6_000),
    "can_ho_dich_vu":  (5_000, 12_000),
}

# Diện tích theo loại phòng (min, max) m²
AREA_RANGE = {
    "phong_tro":       (12, 25),
    "nha_tro":         (18, 35),
    "chung_cu_mini":   (25, 45),
    "can_ho_dich_vu":  (30, 60),
}

# Tiện ích và xác suất xuất hiện
AMENITIES_PROB = {
    "wifi":        0.90,
    "dieu_hoa":    0.70,
    "wc_rieng":    0.65,
    "may_giat":    0.50,
    "ban_cong":    0.40,
    "bep_rieng":   0.45,
    "tu_lanh":     0.35,
    "giu_xe":      0.75,
    "camera_an_ninh": 0.30,
    "thang_may":   0.25,
}

# Giá theo quận (hệ số nhân, quận trung tâm đắt hơn)
DISTRICT_PRICE_FACTOR = {
    "Quận 1": 1.5, "Quận 3": 1.3, "Quận 4": 1.1, "Quận 5": 1.2,
    "Quận 6": 1.0, "Quận 7": 1.3, "Quận 8": 0.9, "Quận 10": 1.1,
    "Quận 11": 1.0, "Quận 12": 0.85, "Bình Thạnh": 1.15, "Gò Vấp": 0.9,
    "Phú Nhuận": 1.2, "Tân Bình": 1.0, "Tân Phú": 0.9, "Bình Tân": 0.8,
    "Thủ Đức": 0.95, "Hóc Môn": 0.75, "Bình Chánh": 0.75,
}

TITLE_TEMPLATES = {
    "phong_tro":      ["Phòng trọ {area}m² {district}", "Phòng trọ giá rẻ {district}", "Phòng trọ sạch sẽ {district}"],
    "nha_tro":        ["Nhà trọ {area}m² {district}", "Nhà trọ an ninh {district}", "Nhà trọ thoáng mát {district}"],
    "chung_cu_mini":  ["Chung cư mini {area}m² {district}", "Căn hộ mini đầy đủ nội thất {district}", "Studio {area}m² {district}"],
    "can_ho_dich_vu": ["Căn hộ dịch vụ cao cấp {district}", "Serviced apartment {area}m² {district}", "Căn hộ dịch vụ full nội thất {district}"],
}

# ─── Generators ───────────────────────────────────────────────────────────────

def gen_amenities() -> list[str]:
    return [a for a, p in AMENITIES_PROB.items() if random.random() < p]


def gen_room(index: int) -> dict:
    room_type = random.choice(ROOM_TYPES)
    district = random.choice(DISTRICTS)

    price_min, price_max = PRICE_RANGE[room_type]
    factor = DISTRICT_PRICE_FACTOR[district]
    price = round(random.randint(price_min, price_max) * factor / 100) * 100  # làm tròn 100k

    area_min, area_max = AREA_RANGE[room_type]
    area = round(random.uniform(area_min, area_max), 1)

    base_lat, base_lng = DISTRICT_COORDS[district]
    lat = round(base_lat + random.uniform(-0.015, 0.015), 6)
    lng = round(base_lng + random.uniform(-0.015, 0.015), 6)

    template = random.choice(TITLE_TEMPLATES[room_type])
    title = template.format(area=area, district=district)

    amenities = gen_amenities()

    # Phòng cao cấp hơn thì có nhiều tiện ích hơn
    if room_type == "can_ho_dich_vu" and len(amenities) < 5:
        extras = [a for a in AMENITIES_PROB if a not in amenities]
        amenities += random.sample(extras, min(2, len(extras)))

    return {
        "id": str(uuid.uuid4()),
        "title": title,
        "price": int(price * 1000),   # lưu theo VNĐ thật
        "area": area,
        "district": district,
        "city": "Ho Chi Minh",
        "room_type": room_type,
        "amenities": amenities,
        "lat": lat,
        "lng": lng,
        "is_available": random.random() > 0.08,  # 92% còn trống
    }


HN_DISTRICTS = [
    "Ba Đình", "Bắc Từ Liêm", "Cầu Giấy", "Đống Đa", "Hai Bà Trưng",
    "Hà Đông", "Hoàn Kiếm", "Hoàng Mai", "Long Biên", "Nam Từ Liêm",
    "Tây Hồ", "Thanh Xuân",
]

USER_LOCATIONS = [
    ("Ho Chi Minh", d) for d in DISTRICTS
] + [
    ("Ha Noi", d) for d in HN_DISTRICTS
]


def gen_users(n: int) -> list[dict]:
    users = []
    for i in range(n):
        # 20% user chưa chọn city/district (chưa điền khi đăng ký)
        if random.random() < 0.2:
            city, district = None, None
        else:
            city, district = random.choice(USER_LOCATIONS)

        users.append({
            "id": str(uuid.uuid4()),
            "email": f"user{i+1:03d}@example.com",
            "city": city,
            "district": district,
        })
    return users


def gen_view_history(users: list[dict], rooms: list[dict], max_events: int = 3000) -> list[dict]:
    """
    Sinh lịch sử xem có tính thực tế:
    - Mỗi user có "preference" ngầm (quận, loại phòng, mức giá)
    - 70% sự kiện xem phù hợp preference → model học được pattern
    - 30% random → tránh overfitting
    """
    now = datetime.now()
    events = []

    for user in users:
        # Preference ngầm của user
        pref_district = random.choice(DISTRICTS)
        pref_type = random.choice(ROOM_TYPES)
        pref_price_max = random.randint(2_000_000, 8_000_000)

        # Số lần xem mỗi user: 5 ~ 40
        n_views = random.randint(5, 40)

        # Lọc phòng phù hợp preference
        preferred = [
            r for r in rooms
            if r["district"] == pref_district
            or r["room_type"] == pref_type
            or r["price"] <= pref_price_max
        ]
        if not preferred:
            preferred = rooms

        viewed_ids = set()
        for _ in range(n_views):
            # 70% chọn phòng phù hợp preference
            if random.random() < 0.7 and preferred:
                room = random.choice(preferred)
            else:
                room = random.choice(rooms)

            if room["id"] in viewed_ids:
                continue
            viewed_ids.add(room["id"])

            # Thời gian xem: trong 30 ngày gần nhất, phân phối lệch về gần đây
            days_ago = random.betavariate(1, 3) * 30   # beta: thiên về gần đây
            viewed_at = now - timedelta(days=days_ago, seconds=random.randint(0, 86400))

            events.append({
                "user_id": user["id"],
                "room_id": room["id"],
                "viewed_at": viewed_at.isoformat(),
            })

    # Shuffle để không lộ pattern theo thứ tự
    random.shuffle(events)
    return events[:max_events]


# ─── Main ─────────────────────────────────────────────────────────────────────

def main():
    out_dir = Path(__file__).parent.parent / "data"
    out_dir.mkdir(exist_ok=True)

    print("Generating rooms...")
    rooms = [gen_room(i) for i in range(800)]

    print("Generating users...")
    users = gen_users(100)

    print("Generating view history...")
    history = gen_view_history(users, rooms, max_events=3000)

    # Ghi ra file
    (out_dir / "rooms.json").write_text(
        json.dumps(rooms, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out_dir / "users.json").write_text(
        json.dumps(users, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    (out_dir / "view_history.json").write_text(
        json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8"
    )

    # Thống kê
    print("\n=== Dataset Summary ===")
    print(f"Rooms:        {len(rooms)}")
    print(f"Users:        {len(users)}")
    print(f"View events:  {len(history)}")
    print(f"Avg views/user: {len(history)/len(users):.1f}")

    type_counts = {}
    for r in rooms:
        type_counts[r["room_type"]] = type_counts.get(r["room_type"], 0) + 1
    print("\nRoom type distribution:")
    for t, c in sorted(type_counts.items()):
        print(f"  {t:20s}: {c}")

    prices = [r["price"] for r in rooms]
    print(f"\nPrice range:  {min(prices):,} ~ {max(prices):,} VND")
    print(f"Price avg:    {sum(prices)//len(prices):,} VND")

    available = sum(1 for r in rooms if r["is_available"])
    print(f"\nAvailable:    {available}/{len(rooms)} rooms")

    print(f"\nFiles saved to: {out_dir.resolve()}".encode("ascii", "replace").decode())


if __name__ == "__main__":
    main()
