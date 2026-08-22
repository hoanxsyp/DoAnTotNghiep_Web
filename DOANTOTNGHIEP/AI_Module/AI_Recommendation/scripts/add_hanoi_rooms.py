"""
Thêm 200 phòng Hà Nội vào data/rooms.json hiện có.
"""

import json
import random
import uuid
from pathlib import Path

random.seed(99)

HN_DISTRICTS = [
    "Hoàn Kiếm", "Ba Đình", "Đống Đa", "Hai Bà Trưng",
    "Hoàng Mai", "Thanh Xuân", "Cầu Giấy", "Long Biên",
    "Nam Từ Liêm", "Bắc Từ Liêm", "Tây Hồ", "Hà Đông",
]

HN_DISTRICT_COORDS = {
    "Hoàn Kiếm":    (21.0285, 105.8542),
    "Ba Đình":      (21.0358, 105.8342),
    "Đống Đa":      (21.0245, 105.8412),
    "Hai Bà Trưng": (21.0138, 105.8612),
    "Hoàng Mai":    (20.9820, 105.8640),
    "Thanh Xuân":   (20.9950, 105.8120),
    "Cầu Giấy":     (21.0350, 105.7900),
    "Long Biên":    (21.0430, 105.8870),
    "Nam Từ Liêm":  (21.0130, 105.7650),
    "Bắc Từ Liêm":  (21.0680, 105.7590),
    "Tây Hồ":       (21.0680, 105.8240),
    "Hà Đông":      (20.9610, 105.7760),
}

HN_DISTRICT_PRICE_FACTOR = {
    "Hoàn Kiếm": 1.6, "Ba Đình": 1.4, "Đống Đa": 1.2, "Hai Bà Trưng": 1.2,
    "Hoàng Mai": 0.9, "Thanh Xuân": 1.1, "Cầu Giấy": 1.2, "Long Biên": 0.9,
    "Nam Từ Liêm": 0.95, "Bắc Từ Liêm": 0.85, "Tây Hồ": 1.3, "Hà Đông": 0.85,
}

ROOM_TYPES = ["phong_tro", "nha_tro", "chung_cu_mini", "can_ho_dich_vu"]

PRICE_RANGE = {
    "phong_tro":      (1_200, 2_800),
    "nha_tro":        (1_500, 3_500),
    "chung_cu_mini":  (3_000, 6_000),
    "can_ho_dich_vu": (5_000, 12_000),
}

AREA_RANGE = {
    "phong_tro":      (12, 25),
    "nha_tro":        (18, 35),
    "chung_cu_mini":  (25, 45),
    "can_ho_dich_vu": (30, 60),
}

AMENITIES_PROB = {
    "wifi": 0.90, "dieu_hoa": 0.70, "wc_rieng": 0.65,
    "may_giat": 0.50, "ban_cong": 0.40, "bep_rieng": 0.45,
    "tu_lanh": 0.35, "giu_xe": 0.75, "camera_an_ninh": 0.30, "thang_may": 0.25,
}

TITLE_TEMPLATES = {
    "phong_tro":      ["Phòng trọ {area}m² {district}", "Phòng trọ giá rẻ {district}", "Phòng trọ sạch sẽ {district}"],
    "nha_tro":        ["Nhà trọ {area}m² {district}", "Nhà trọ an ninh {district}", "Nhà trọ thoáng mát {district}"],
    "chung_cu_mini":  ["Chung cư mini {area}m² {district}", "Studio {area}m² {district}", "Căn hộ mini đủ nội thất {district}"],
    "can_ho_dich_vu": ["Căn hộ dịch vụ cao cấp {district}", "Serviced apartment {area}m² {district}", "Căn hộ dịch vụ full nội thất {district}"],
}


def gen_hanoi_room() -> dict:
    room_type = random.choice(ROOM_TYPES)
    district = random.choice(HN_DISTRICTS)

    price_min, price_max = PRICE_RANGE[room_type]
    factor = HN_DISTRICT_PRICE_FACTOR[district]
    price = round(random.randint(price_min, price_max) * factor / 100) * 100

    area_min, area_max = AREA_RANGE[room_type]
    area = round(random.uniform(area_min, area_max), 1)

    base_lat, base_lng = HN_DISTRICT_COORDS[district]
    lat = round(base_lat + random.uniform(-0.015, 0.015), 6)
    lng = round(base_lng + random.uniform(-0.015, 0.015), 6)

    amenities = [a for a, p in AMENITIES_PROB.items() if random.random() < p]

    template = random.choice(TITLE_TEMPLATES[room_type])
    title = template.format(area=area, district=district)

    return {
        "id": str(uuid.uuid4()),
        "title": title,
        "price": int(price * 1000),
        "area": area,
        "district": district,
        "city": "Ha Noi",
        "room_type": room_type,
        "amenities": amenities,
        "lat": lat,
        "lng": lng,
        "is_available": random.random() > 0.08,
    }


def main():
    data_path = Path(__file__).parent.parent / "data" / "rooms.json"

    existing = json.loads(data_path.read_text(encoding="utf-8"))
    before = len(existing)

    hanoi_rooms = [gen_hanoi_room() for _ in range(200)]
    existing.extend(hanoi_rooms)

    data_path.write_text(json.dumps(existing, ensure_ascii=False, indent=2), encoding="utf-8")

    print(f"Before : {before} rooms")
    print(f"Added  : {len(hanoi_rooms)} Hanoi rooms")
    print(f"Total  : {len(existing)} rooms")

    city_counts = {}
    for r in existing:
        city_counts[r["city"]] = city_counts.get(r["city"], 0) + 1
    print("\nBy city:")
    for city, count in sorted(city_counts.items()):
        print(f"  {city}: {count}")

    district_counts = {}
    for r in hanoi_rooms:
        district_counts[r["district"]] = district_counts.get(r["district"], 0) + 1
    print("\nHanoi districts:")
    for d, c in sorted(district_counts.items(), key=lambda x: -x[1]):
        print(f"  {c:3d}  {d}")


if __name__ == "__main__":
    main()
