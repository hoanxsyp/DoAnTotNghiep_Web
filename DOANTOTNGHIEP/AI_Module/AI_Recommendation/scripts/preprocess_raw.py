"""
Chuyển đổi raw Kaggle CSV → data/rooms.json
Input:  data/raw/hcm.csv, hn.csv, dn.csv
Output: data/rooms.json  (thay thế mock data)
"""

import json
import re
import sys
import uuid
from pathlib import Path

import pandas as pd

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

# ─── Canonical district definitions ──────────────────────────────────────────

HCM_DISTRICTS = {
    "Quận 1":        (10.7769, 106.7009),
    "Quận 2":        (10.7872, 106.7518),
    "Quận 3":        (10.7780, 106.6920),
    "Quận 4":        (10.7580, 106.7040),
    "Quận 5":        (10.7553, 106.6680),
    "Quận 6":        (10.7487, 106.6349),
    "Quận 7":        (10.7362, 106.7219),
    "Quận 8":        (10.7232, 106.6283),
    "Quận 9":        (10.8412, 106.7856),
    "Quận 10":       (10.7726, 106.6680),
    "Quận 11":       (10.7627, 106.6495),
    "Quận 12":       (10.8680, 106.6585),
    "Quận Bình Thạnh": (10.8122, 106.7139),
    "Quận Gò Vấp":   (10.8384, 106.6655),
    "Quận Phú Nhuận":(10.7994, 106.6801),
    "Quận Tân Bình": (10.8012, 106.6524),
    "Quận Tân Phú":  (10.7906, 106.6282),
    "Quận Bình Tân": (10.7631, 106.6048),
    "Quận Thủ Đức":  (10.8534, 106.7545),
    "Huyện Bình Chánh": (10.6736, 106.6016),
    "Huyện Hóc Môn": (10.8912, 106.5945),
    "Huyện Nhà Bè":  (10.6824, 106.7334),
    "Huyện Củ Chi":  (11.0014, 106.4828),
}

HN_DISTRICTS = {
    "Ba Đình":       (21.0358, 105.8390),
    "Hoàn Kiếm":     (21.0285, 105.8542),
    "Tây Hồ":        (21.0714, 105.8178),
    "Long Biên":     (21.0603, 105.8989),
    "Cầu Giấy":      (21.0326, 105.7942),
    "Đống Đa":       (21.0245, 105.8412),
    "Hai Bà Trưng":  (21.0125, 105.8610),
    "Hoàng Mai":     (20.9813, 105.8538),
    "Thanh Xuân":    (20.9978, 105.8086),
    "Hà Đông":       (20.9696, 105.7768),
    "Nam Từ Liêm":   (21.0092, 105.7634),
    "Bắc Từ Liêm":   (21.0614, 105.7607),
    "Gia Lâm":       (21.0058, 105.9312),
    "Đông Anh":      (21.1473, 105.8453),
    "Sóc Sơn":       (21.2432, 105.8543),
    "Thường Tín":    (20.8662, 105.8640),
    "Hoài Đức":      (21.0506, 105.7256),
}

DN_DISTRICTS = {
    "Quận Hải Châu":     (16.0544, 108.2022),
    "Quận Thanh Khê":    (16.0707, 108.1787),
    "Quận Sơn Trà":      (16.0748, 108.2333),
    "Quận Ngũ Hành Sơn": (15.9996, 108.2672),
    "Quận Liên Chiểu":   (16.1022, 108.1490),
    "Quận Cẩm Lệ":       (16.0155, 108.2115),
    "Huyện Hoà Vang":    (15.9826, 108.1426),
}

CITY_DISTRICTS = {
    "Ho Chi Minh": HCM_DISTRICTS,
    "Ha Noi":      HN_DISTRICTS,
    "Da Nang":     DN_DISTRICTS,
}

# ─── City normalizer ──────────────────────────────────────────────────────────

CITY_PATTERNS = [
    (re.compile(r"hồ chí minh|ho chi minh|tp\.?hcm|tphcm|hcm|sài gòn", re.I), "Ho Chi Minh"),
    (re.compile(r"hà nội|ha noi|hn\b",                                   re.I), "Ha Noi"),
    (re.compile(r"đà nẵng|da nang|dn\b",                                 re.I), "Da Nang"),
]

def normalize_city(raw: str) -> str | None:
    for pat, name in CITY_PATTERNS:
        if pat.search(raw):
            return name
    return None


# ─── District normalizer ──────────────────────────────────────────────────────

def _build_district_lookup(districts: dict[str, tuple]) -> list[tuple[re.Pattern, str]]:
    """Build (pattern, canonical) pairs sorted longest-first to avoid partial matches."""
    rows = []
    for canon in districts:
        # strip prefix Quận/Huyện for alias matching
        stripped = re.sub(r"^(quận|huyện)\s+", "", canon, flags=re.I)
        aliases = {canon, stripped}

        # number-only districts: "Quận 1" → also match "Q1", "Q.1"
        m = re.match(r"(\d+)$", stripped)
        if m:
            n = m.group(1)
            aliases |= {f"Q{n}", f"Q.{n}", f"Quận {n}"}

        # build alternation pattern
        pats = sorted(aliases, key=len, reverse=True)
        pat_str = "|".join(re.escape(p) for p in pats)
        rows.append((re.compile(r"\b(?:" + pat_str + r")\b", re.I | re.U), canon))

    # longest canonical name first → greedy match
    rows.sort(key=lambda x: len(x[1]), reverse=True)
    return rows


_DISTRICT_LOOKUP: dict[str, list] = {
    city: _build_district_lookup(dmap)
    for city, dmap in CITY_DISTRICTS.items()
}


def normalize_district(raw: str, city: str) -> str | None:
    for pat, canon in _DISTRICT_LOOKUP.get(city, []):
        if pat.search(raw):
            return canon
    return None


# ─── Room-type extractor ──────────────────────────────────────────────────────

ROOM_TYPE_RULES = [
    (re.compile(r"căn hộ dịch vụ|can ho dich vu|dịch vụ",          re.I), "can_ho_dich_vu"),
    (re.compile(r"chung cư mini|chung cu mini|cc mini|dạng chung cư", re.I), "chung_cu_mini"),
    (re.compile(r"nhà trọ|nha tro|nhà nguyên căn|nguyên căn",        re.I), "nha_tro"),
    (re.compile(r"phòng trọ|phong tro|phòng cho thuê",               re.I), "phong_tro"),
]

def extract_room_type(title: str) -> str:
    for pat, rtype in ROOM_TYPE_RULES:
        if pat.search(title):
            return rtype
    return "phong_tro"


# ─── Amenity extractor ───────────────────────────────────────────────────────

AMENITY_RULES = [
    (re.compile(r"wifi|wi-fi|internet",                              re.I), "wifi"),
    (re.compile(r"điều hòa|dieu hoa|máy lạnh|may lanh|air.?con",   re.I), "dieu_hoa"),
    (re.compile(r"wc riêng|toilet riêng|vệ sinh riêng|khép kín|wc khép|nhà vệ sinh riêng", re.I), "wc_rieng"),
    (re.compile(r"nóng lạnh|nong lanh|máy nước nóng|water heater",  re.I), "may_nuoc_nong"),
    (re.compile(r"\bbếp\b|nấu ăn|bếp nấu|nhà bếp",                 re.I), "bep"),
    (re.compile(r"tủ lạnh|tu lanh|refrigerator",                    re.I), "tu_lanh"),
    (re.compile(r"máy giặt|may giat|washing",                       re.I), "may_giat"),
    (re.compile(r"ban công|balcony|bancong",                        re.I), "ban_cong"),
    (re.compile(r"chỗ để xe|gửi xe|bãi xe|parking|để xe",          re.I), "cho_de_xe"),
    (re.compile(r"full nội thất|full tiện nghi|đầy đủ tiện nghi|nội thất đầy đủ", re.I), "_full"),
]

FULL_AMENITY_SET = ["wifi", "dieu_hoa", "wc_rieng", "may_nuoc_nong", "bep"]

def extract_amenities(title: str) -> list[str]:
    amenities: set[str] = set()
    for pat, key in AMENITY_RULES:
        if pat.search(title):
            if key == "_full":
                amenities.update(FULL_AMENITY_SET)
            else:
                amenities.add(key)
    return sorted(amenities)


# ─── Main preprocessing ───────────────────────────────────────────────────────

def parse_row(row: pd.Series) -> dict | None:
    addr  = str(row.get("address", "") or "")
    title = str(row.get("title",   "") or "")
    price_raw   = row.get("price",   None)
    acreage_raw = row.get("acreage", None)

    # ── City ──────────────────────────────────────────────────────────────────
    city = normalize_city(addr)
    if city is None:
        return None

    # ── District ──────────────────────────────────────────────────────────────
    district = normalize_district(addr, city)
    if district is None:
        return None

    # ── Price (triệu → VND, filter 0.5M–20M) ─────────────────────────────────
    try:
        price_mil = float(price_raw)
    except (TypeError, ValueError):
        return None
    price = round(price_mil * 1_000_000)
    if not (500_000 <= price <= 20_000_000):
        return None

    # ── Area (filter 10–100 m²) ───────────────────────────────────────────────
    try:
        area = float(acreage_raw)
    except (TypeError, ValueError):
        return None
    if not (10 <= area <= 100):
        return None

    # ── Room type & amenities ─────────────────────────────────────────────────
    room_type = extract_room_type(title)
    amenities = extract_amenities(title)

    # ── Coordinates ───────────────────────────────────────────────────────────
    lat, lng = CITY_DISTRICTS[city][district]
    # small random jitter so rooms in same district differ slightly
    import random
    lat += random.uniform(-0.008, 0.008)
    lng += random.uniform(-0.008, 0.008)

    return {
        "id":           str(uuid.uuid4()),
        "title":        title.strip(),
        "price":        price,
        "area":         round(area, 1),
        "city":         city,
        "district":     district,
        "room_type":    room_type,
        "amenities":    amenities,
        "lat":          round(lat, 6),
        "lng":          round(lng, 6),
        "is_available": True,
    }


def main():
    import sys, io, random
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", line_buffering=True)
    random.seed(42)

    raw_dir = ROOT / "data" / "raw"

    dfs = []
    for fname in ["hcm.csv", "hn.csv", "dn.csv"]:
        path = raw_dir / fname
        if path.exists():
            dfs.append(pd.read_csv(path, encoding="utf-8"))
            print(f"[load] {fname}: {len(dfs[-1])} rows")
        else:
            print(f"[skip] {fname} not found")

    if not dfs:
        print("ERROR: Khong tim thay file CSV nao trong data/raw/")
        return

    all_df = pd.concat(dfs, ignore_index=True)
    print(f"\n[total] {len(all_df)} raw rows")

    rooms = []
    skipped = 0
    for _, row in all_df.iterrows():
        result = parse_row(row)
        if result:
            rooms.append(result)
        else:
            skipped += 1

    print(f"[parse] {len(rooms)} valid  |  {skipped} skipped")

    # ── Save trước khi print stats (tránh crash mất data) ────────────────────
    out = ROOT / "data" / "rooms.json"
    out.write_text(json.dumps(rooms, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[saved] {len(rooms)} phong tro -> {out}")

    # ── Stats ─────────────────────────────────────────────────────────────────
    from collections import Counter
    cities = Counter(r["city"] for r in rooms)
    print(f"\n[cities] {dict(cities)}")

    for city in cities:
        dists = Counter(r["district"] for r in rooms if r["city"] == city)
        print(f"  {city}: {len(dists)} quan")
        for d, cnt in dists.most_common(5):
            print(f"    {d}: {cnt}")

    prices = [r["price"] for r in rooms]
    areas  = [r["area"]  for r in rooms]
    print(f"\n[price] min={min(prices):,.0f}  max={max(prices):,.0f}  avg={sum(prices)/len(prices):,.0f}")
    print(f"[area]  min={min(areas):.1f}  max={max(areas):.1f}  avg={sum(areas)/len(areas):.1f}")

    rtypes = Counter(r["room_type"] for r in rooms)
    print(f"\n[room_type] {dict(rtypes)}")

    amenity_counts = Counter(a for r in rooms for a in r["amenities"])
    print(f"\n[amenities] {dict(amenity_counts.most_common(10))}")


if __name__ == "__main__":
    main()
