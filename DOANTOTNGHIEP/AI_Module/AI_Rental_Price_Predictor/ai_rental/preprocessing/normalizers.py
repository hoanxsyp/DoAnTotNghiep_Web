# -*- coding: utf-8 -*-
"""Hàm chuẩn hoá dùng chung cho crawler / preprocess / API."""
import re
import math
import unicodedata
import hashlib
import numpy as np

# ---- Quận Hà Nội: slug <-> tên, toạ độ tâm quận (xấp xỉ) ----
SLUG2NAME = {
    "quan-ba-dinh": "Ba Đình", "quan-bac-tu-liem": "Bắc Từ Liêm",
    "quan-cau-giay": "Cầu Giấy", "quan-dong-da": "Đống Đa",
    "quan-ha-dong": "Hà Đông", "quan-hai-ba-trung": "Hai Bà Trưng",
    "quan-hoan-kiem": "Hoàn Kiếm", "quan-hoang-mai": "Hoàng Mai",
    "quan-long-bien": "Long Biên", "quan-nam-tu-liem": "Nam Từ Liêm",
    "quan-tay-ho": "Tây Hồ", "quan-thanh-xuan": "Thanh Xuân",
}
HK_CENTER = (21.0287, 105.8524)  # Hồ Gươm
DISTRICT_CENTER = {
    "Ba Đình": (21.0350, 105.8140), "Bắc Từ Liêm": (21.0700, 105.7550),
    "Cầu Giấy": (21.0360, 105.7900), "Đống Đa": (21.0170, 105.8290),
    "Hà Đông": (20.9710, 105.7788), "Hai Bà Trưng": (21.0075, 105.8500),
    "Hoàn Kiếm": (21.0287, 105.8524), "Hoàng Mai": (20.9720, 105.8560),
    "Long Biên": (21.0450, 105.8900), "Nam Từ Liêm": (21.0170, 105.7640),
    "Tây Hồ": (21.0680, 105.8200), "Thanh Xuân": (20.9955, 105.8050),
}

# Only reject a listing when its address explicitly names a location outside
# Ha Noi.  Listings with a short address are kept because the crawler itself
# is scoped to Ha Noi and many valid cards omit the city name.
OUTSIDE_HANOI_MARKERS = (
    "ho chi minh", "tp hcm", "thanh pho ho chi minh", "binh duong",
    "da nang", "can tho", "hai phong",
)
HANOI_MARKERS = ("ha noi", "thu do ha noi")
HANOI_LAT_RANGE = (20.45, 21.55)
HANOI_LON_RANGE = (105.25, 106.10)


def normalize_location_text(value) -> str:
    """Lowercase text without Vietnamese accents, for location comparisons."""
    text = unicodedata.normalize("NFD", str(value or "").casefold())
    text = "".join(ch for ch in text if unicodedata.category(ch) != "Mn")
    return text.replace("đ", "d")


def is_location_outside_hanoi(address, latitude=None, longitude=None) -> bool:
    """Return True for an address/GPS point explicitly outside Ha Noi.

    A district hint from the crawl URL is intentionally not considered here:
    the address and coordinates are the authoritative location fields.
    """
    address_text = normalize_location_text(address)
    if any(marker in address_text for marker in OUTSIDE_HANOI_MARKERS):
        return True

    try:
        lat, lon = float(latitude), float(longitude)
    except (TypeError, ValueError):
        return False
    if not (math.isfinite(lat) and math.isfinite(lon)):
        return False
    return not (HANOI_LAT_RANGE[0] <= lat <= HANOI_LAT_RANGE[1]
                and HANOI_LON_RANGE[0] <= lon <= HANOI_LON_RANGE[1])


def location_confidence(address, latitude=None, longitude=None) -> str:
    """Classify how strongly a listing is tied to Ha Noi without using its URL."""
    if is_location_outside_hanoi(address, latitude, longitude):
        return "outside"
    address_text = normalize_location_text(address)
    if any(marker in address_text for marker in HANOI_MARKERS):
        return "high"
    try:
        lat, lon = float(latitude), float(longitude)
        if (math.isfinite(lat) and math.isfinite(lon)
                and HANOI_LAT_RANGE[0] <= lat <= HANOI_LAT_RANGE[1]
                and HANOI_LON_RANGE[0] <= lon <= HANOI_LON_RANGE[1]):
            return "high"
    except (TypeError, ValueError):
        pass
    return "medium" if address_text else "low"


def listing_group_key(address, latitude=None, longitude=None, phone=None, source_url=None) -> str:
    """Stable, non-PII grouping key for deduplication and validation splits."""
    try:
        lat, lon = float(latitude), float(longitude)
        if math.isfinite(lat) and math.isfinite(lon):
            return f"geo:{lat:.4f}:{lon:.4f}"
    except (TypeError, ValueError):
        pass

    address_text = normalize_location_text(address)
    # A city-only address is too broad to represent one building.
    if len(address_text) >= 16 and address_text not in {"ha noi", "hanoi"}:
        value = f"address:{address_text}"
    elif phone:
        value = f"phone:{re.sub(r'\\D', '', str(phone))}"
    else:
        value = f"url:{source_url or ''}"
    return hashlib.sha1(value.encode("utf-8")).hexdigest()[:20]

AMENITY_PATTERNS = {
    "has_dieu_hoa":  r"điều\s*hòa|điều\s*hoà|máy\s*lạnh|\bđh\b",
    "has_khep_kin":  r"khép\s*kín|khep\s*kin|vệ\s*sinh\s*riêng|wc\s*riêng|vs\s*riêng",
    "has_ban_cong":  r"ban\s*công|ban\s*cong|logia|lô\s*gia",
    "has_thang_may": r"thang\s*máy|thang\s*may",
    "has_full_do":   r"full\s*đồ|full\s*nội\s*thất|đầy\s*đủ\s*nội\s*thất|nội\s*thất\s*đầy\s*đủ|full\s*nt",
    "has_gac":       r"\bgác\b|gác\s*xép|gác\s*lửng",
    "has_may_giat":  r"máy\s*giặt|may\s*giat",
    "has_nong_lanh": r"nóng\s*lạnh|bình\s*nóng|nong\s*lanh",
    "has_wifi":      r"wifi|internet|mạng",
    "has_de_xe":     r"để\s*xe|gửi\s*xe|hầm\s*xe|chỗ\s*xe",
}


def strip_html(t) -> str:
    if not t:
        return ""
    return re.sub(r"<[^>]+>", " ", str(t))


def parse_price_million(raw):
    """priceRange/price VND hoặc text -> triệu VND/tháng (NaN nếu không rõ)."""
    if raw is None:
        return np.nan
    if isinstance(raw, (int, float)):
        return round(float(raw) / 1_000_000, 3) if float(raw) > 0 else np.nan
    t = str(raw).lower().strip()
    if re.search(r"thỏa\s*thuận|liên\s*hệ", t):
        return np.nan
    digits = re.sub(r"\D", "", t)
    if re.fullmatch(r"\d{6,10}", digits) and "triệu" not in t and "nghìn" not in t:
        v = int(digits)
        return round(v / 1_000_000, 3) if v > 0 else np.nan
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*(triệu|tr)", t)
    if m:
        return round(float(m.group(1).replace(",", ".")), 3)
    m = re.search(r"(\d+(?:[.,]\d+)?)\s*(nghìn|ngàn|k)", t)
    if m:
        return round(float(m.group(1).replace(",", ".")) / 1000, 3)
    return np.nan


def parse_area(raw):
    if raw is None:
        return np.nan
    m = re.search(r"(\d+(?:[.,]\d+)?)", str(raw).replace(",", "."))
    if not m:
        return np.nan
    v = float(m.group(1))
    return v if 5 <= v <= 300 else np.nan


def extract_ward(address) -> str:
    if not address:
        return "unknown"
    m = re.search(r"(?:phường|xã|p\.)\s*([^,]+)", str(address), re.I)
    return m.group(1).strip() if m else "unknown"


def extract_district(address, hint=None) -> str:
    """Ưu tiên district đã stamp (hint); nếu không có thì lấy từ 'Quận ...' trong address."""
    if hint:
        return SLUG2NAME.get(hint, hint)
    if address:
        m = re.search(r"(?:quận|huyện|q\.)\s*([^,]+)", str(address), re.I)
        if m:
            return m.group(1).strip()
    return "unknown"


def detect_room_type(text) -> str:
    t = str(text).lower()
    if re.search(r"ở\s*ghép|o\s*ghep", t):                              return "o_ghep"
    if re.search(r"ccmn|căn\s*hộ\s*mini|chung\s*cư\s*mini|studio", t):  return "can_ho_mini"
    if re.search(r"nguyên\s*căn", t):                                   return "nha_nguyen_can"
    if re.search(r"căn\s*hộ|chung\s*cư", t):                            return "can_ho"
    return "phong_tro"


def haversine_km(lat1, lon1, lat2=HK_CENTER[0], lon2=HK_CENTER[1]):
    if lat1 is None or lon1 is None or (isinstance(lat1, float) and math.isnan(lat1)):
        return np.nan
    R = 6371.0
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return round(R * 2 * math.asin(math.sqrt(a)), 3)
