"""
Feature Engineering cho LightGBM Re-ranker.

Mỗi (user, room) pair → vector features ~40 chiều gồm:
  - User stats    : thống kê từ lịch sử xem (avg price, preferred type...)
  - Room features : đặc trưng của phòng
  - Interaction   : độ khớp giữa user và phòng (price diff, amenity jaccard...)
  - FAISS score   : signal từ tầng 1
"""

import math
from collections import Counter

import numpy as np

# ─── Vocabulary (giữ nhất quán với extractor.py) ──────────────────────────────

CITIES      = ["Ha Noi", "Ho Chi Minh"]
ROOM_TYPES  = ["can_ho_dich_vu", "chung_cu_mini", "nha_tro", "phong_tro"]
AMENITIES   = [
    "ban_cong", "bep_rieng", "camera_an_ninh", "dieu_hoa",
    "giu_xe", "may_giat", "thang_may", "tu_lanh", "wc_rieng", "wifi",
]

# Tọa độ trung tâm mỗi quận — dùng để tính khoảng cách địa lý
DISTRICT_COORDS = {
    # HCM
    "Quận 1": (10.7769, 106.7009), "Quận 3": (10.7800, 106.6890),
    "Quận 4": (10.7580, 106.7040), "Quận 5": (10.7545, 106.6620),
    "Quận 6": (10.7480, 106.6340), "Quận 7": (10.7300, 106.7200),
    "Quận 8": (10.7230, 106.6280), "Quận 10": (10.7740, 106.6680),
    "Quận 11": (10.7630, 106.6480), "Quận 12": (10.8680, 106.6560),
    "Bình Thạnh": (10.8120, 106.7140), "Gò Vấp": (10.8380, 106.6650),
    "Phú Nhuận": (10.7990, 106.6800), "Tân Bình": (10.8020, 106.6520),
    "Tân Phú": (10.7900, 106.6270), "Bình Tân": (10.7530, 106.6010),
    "Thủ Đức": (10.8700, 106.7650), "Hóc Môn": (10.8930, 106.5960),
    "Bình Chánh": (10.6880, 106.5990),
    # Hà Nội
    "Hoàn Kiếm": (21.0285, 105.8542), "Ba Đình": (21.0358, 105.8342),
    "Đống Đa": (21.0245, 105.8412), "Hai Bà Trưng": (21.0138, 105.8612),
    "Hoàng Mai": (20.9820, 105.8640), "Thanh Xuân": (20.9950, 105.8120),
    "Cầu Giấy": (21.0350, 105.7900), "Long Biên": (21.0430, 105.8870),
    "Nam Từ Liêm": (21.0130, 105.7650), "Bắc Từ Liêm": (21.0680, 105.7590),
    "Tây Hồ": (21.0680, 105.8240), "Hà Đông": (20.9610, 105.7760),
}

PRICE_MAX = 18_000_000
AREA_MAX  = 60.0


# ─── Geo utils ────────────────────────────────────────────────────────────────

def _haversine_km(lat1: float, lng1: float, lat2: float, lng2: float) -> float:
    R = 6371.0
    dlat = math.radians(lat2 - lat1)
    dlng = math.radians(lng2 - lng1)
    a = math.sin(dlat/2)**2 + math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) * math.sin(dlng/2)**2
    return R * 2 * math.asin(math.sqrt(a))


def _district_coords(district: str) -> tuple[float, float] | None:
    return DISTRICT_COORDS.get(district)


# ─── User stats ───────────────────────────────────────────────────────────────

def compute_user_stats(view_history: list[dict], room_cache: dict) -> dict:
    """
    Tính thống kê tổng hợp từ lịch sử xem của user.
    Trả về dict dùng để tính interaction features với từng candidate room.
    """
    viewed_rooms = [
        room_cache[e["room_id"]]
        for e in view_history
        if e["room_id"] in room_cache
    ]

    if not viewed_rooms:
        return _empty_user_stats()

    prices = [r["price"] for r in viewed_rooms]
    areas  = [r["area"]  for r in viewed_rooms]

    avg_price = sum(prices) / len(prices)
    std_price = (sum((p - avg_price)**2 for p in prices) / len(prices)) ** 0.5

    avg_area  = sum(areas) / len(areas)
    std_area  = (sum((a - avg_area)**2 for a in areas) / len(areas)) ** 0.5

    type_counter     = Counter(r["room_type"] for r in viewed_rooms)
    district_counter = Counter(r["district"]  for r in viewed_rooms)
    city_counter     = Counter(r["city"]      for r in viewed_rooms)

    preferred_type     = type_counter.most_common(1)[0][0]
    preferred_district = district_counter.most_common(1)[0][0]
    preferred_city     = city_counter.most_common(1)[0][0]

    amenity_freq = {a: 0.0 for a in AMENITIES}
    for r in viewed_rooms:
        for a in r.get("amenities", []):
            if a in amenity_freq:
                amenity_freq[a] += 1
    for a in amenity_freq:
        amenity_freq[a] /= len(viewed_rooms)  # normalize → frequency [0,1]

    return {
        "avg_price":        avg_price,
        "std_price":        std_price,
        "avg_area":         avg_area,
        "std_area":         std_area,
        "n_views":          len(viewed_rooms),
        "preferred_type":   preferred_type,
        "preferred_district": preferred_district,
        "preferred_city":   preferred_city,
        "amenity_freq":     amenity_freq,
    }


def _empty_user_stats() -> dict:
    return {
        "avg_price":        0.0,
        "std_price":        0.0,
        "avg_area":         0.0,
        "std_area":         0.0,
        "n_views":          0,
        "preferred_type":   "",
        "preferred_district": "",
        "preferred_city":   "",
        "amenity_freq":     {a: 0.0 for a in AMENITIES},
    }


# ─── Feature vector ───────────────────────────────────────────────────────────

def compute_features(
    user_stats: dict,
    room: dict,
    faiss_score: float,
    user_district: str | None = None,
) -> np.ndarray:
    """
    Tính feature vector cho cặp (user, room).

    Args:
        user_stats:    kết quả từ compute_user_stats()
        room:          room dict
        faiss_score:   cosine similarity từ FAISS (tầng 1)
        user_district: district đăng ký của user (cho geo distance)
    """
    feats = []

    # ── 1. FAISS signal (1 feat) ──────────────────────────────────────────────
    feats.append(float(faiss_score))

    # ── 2. User stats (5 feats) ───────────────────────────────────────────────
    feats.append(user_stats["avg_price"] / PRICE_MAX)
    feats.append(user_stats["std_price"] / PRICE_MAX)
    feats.append(user_stats["avg_area"]  / AREA_MAX)
    feats.append(user_stats["std_area"]  / AREA_MAX)
    feats.append(min(user_stats["n_views"] / 50.0, 1.0))  # normalize

    # ── 3. Room features (4 feats) ────────────────────────────────────────────
    feats.append(room["price"] / PRICE_MAX)
    feats.append(room["area"]  / AREA_MAX)
    feats.append(float(CITIES.index(room["city"]) if room["city"] in CITIES else -1))
    feats.append(float(ROOM_TYPES.index(room["room_type"]) if room["room_type"] in ROOM_TYPES else -1))

    # ── 4. Interaction: price (3 feats) ───────────────────────────────────────
    avg_p = user_stats["avg_price"]
    std_p = user_stats["std_price"]
    price_diff = (room["price"] - avg_p) / PRICE_MAX if avg_p > 0 else 0.0
    feats.append(price_diff)
    feats.append(abs(price_diff))
    # trong 1 std của user không?
    in_range = 1.0 if std_p > 0 and abs(room["price"] - avg_p) <= std_p else 0.0
    feats.append(in_range)

    # ── 5. Interaction: area (2 feats) ────────────────────────────────────────
    avg_a = user_stats["avg_area"]
    area_diff = (room["area"] - avg_a) / AREA_MAX if avg_a > 0 else 0.0
    feats.append(area_diff)
    feats.append(abs(area_diff))

    # ── 6. Interaction: categorical match (3 feats) ───────────────────────────
    feats.append(1.0 if room["room_type"] == user_stats["preferred_type"] else 0.0)
    feats.append(1.0 if room["district"]  == user_stats["preferred_district"] else 0.0)
    feats.append(1.0 if room["city"]      == user_stats["preferred_city"] else 0.0)

    # ── 7. Interaction: amenities (3 feats) ───────────────────────────────────
    room_amenities  = set(room.get("amenities", []))
    user_freq       = user_stats["amenity_freq"]
    user_amenities  = {a for a, f in user_freq.items() if f >= 0.5}  # hay xem (>=50%)

    overlap  = len(room_amenities & user_amenities)
    union    = len(room_amenities | user_amenities)
    jaccard  = overlap / union if union > 0 else 0.0
    weighted_match = sum(user_freq.get(a, 0) for a in room_amenities) / len(AMENITIES)

    feats.append(float(overlap))
    feats.append(jaccard)
    feats.append(weighted_match)

    # ── 8. Geo distance (1 feat) ──────────────────────────────────────────────
    dist_km = 0.0
    ref_district = user_district or user_stats["preferred_district"]
    if ref_district and "lat" in room and "lng" in room:
        ref_coords = _district_coords(ref_district)
        if ref_coords:
            dist_km = _haversine_km(ref_coords[0], ref_coords[1], room["lat"], room["lng"])
    feats.append(min(dist_km / 20.0, 1.0))  # normalize: 20km = max

    # ── 9. Amenity frequency profile (10 feats) ───────────────────────────────
    for amenity in AMENITIES:
        has_amenity  = 1.0 if amenity in room_amenities else 0.0
        user_pref    = user_freq.get(amenity, 0.0)
        feats.append(has_amenity * user_pref)  # match strength per amenity

    return np.array(feats, dtype=np.float32)


def get_feature_names() -> list[str]:
    names = [
        # FAISS
        "faiss_score",
        # User stats
        "user_avg_price", "user_std_price", "user_avg_area", "user_std_area", "user_n_views",
        # Room
        "room_price", "room_area", "room_city_enc", "room_type_enc",
        # Price interaction
        "price_diff", "price_abs_diff", "price_in_1std",
        # Area interaction
        "area_diff", "area_abs_diff",
        # Categorical match
        "type_match", "district_match", "city_match",
        # Amenity interaction
        "amenity_overlap_count", "amenity_jaccard", "amenity_weighted_match",
        # Geo
        "geo_distance_norm",
    ]
    # Per-amenity match
    for a in AMENITIES:
        names.append(f"amenity_match_{a}")
    return names


FEATURE_DIM = len(get_feature_names())
