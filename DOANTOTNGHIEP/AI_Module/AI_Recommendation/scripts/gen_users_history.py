"""
Sinh users.json + view_history.json phù hợp với rooms.json thật (3 thành pho).
Chay sau preprocess_raw.py.
"""

import json
import random
import sys
import io
from datetime import datetime, timedelta
from pathlib import Path

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", line_buffering=True)

random.seed(42)

ROOT = Path(__file__).parent.parent
DATA = ROOT / "data"

# ─── Load rooms ───────────────────────────────────────────────────────────────

rooms = json.loads((DATA / "rooms.json").read_text(encoding="utf-8"))
print(f"[load] {len(rooms)} rooms")

# index theo city
rooms_by_city: dict[str, list] = {}
for r in rooms:
    rooms_by_city.setdefault(r["city"], []).append(r)

# index theo district
rooms_by_district: dict[str, list] = {}
for r in rooms:
    rooms_by_district.setdefault(r["district"], []).append(r)

# ─── User locations (city, district) theo ty le thuc te ──────────────────────

HCM_DISTRICTS = [
    "Quận 1", "Quận 2", "Quận 3", "Quận 4", "Quận 5", "Quận 6",
    "Quận 7", "Quận 8", "Quận 9", "Quận 10", "Quận 11", "Quận 12",
    "Quận Bình Thạnh", "Quận Gò Vấp", "Quận Phú Nhuận",
    "Quận Tân Bình", "Quận Tân Phú", "Quận Bình Tân", "Quận Thủ Đức",
    "Huyện Bình Chánh", "Huyện Hóc Môn", "Huyện Nhà Bè",
]

HN_DISTRICTS = [
    "Ba Đình", "Hoàn Kiếm", "Tây Hồ", "Long Biên", "Cầu Giấy",
    "Đống Đa", "Hai Bà Trưng", "Hoàng Mai", "Thanh Xuân",
    "Hà Đông", "Nam Từ Liêm", "Bắc Từ Liêm", "Gia Lâm",
]

DN_DISTRICTS = [
    "Quận Hải Châu", "Quận Thanh Khê", "Quận Sơn Trà",
    "Quận Ngũ Hành Sơn", "Quận Liên Chiểu", "Quận Cẩm Lệ",
    "Huyện Hoà Vang",
]

# filter chi lay district co phong that
HCM_DISTRICTS = [d for d in HCM_DISTRICTS if d in rooms_by_district]
HN_DISTRICTS  = [d for d in HN_DISTRICTS  if d in rooms_by_district]
DN_DISTRICTS  = [d for d in DN_DISTRICTS  if d in rooms_by_district]


def gen_users(n: int) -> list[dict]:
    users = []
    # phan bo: 60% HCM, 25% HN, 10% DN, 5% chua chon
    for i in range(n):
        roll = random.random()
        if roll < 0.05:
            city, district = None, None
        elif roll < 0.65:
            city = "Ho Chi Minh"
            district = random.choice(HCM_DISTRICTS)
        elif roll < 0.90:
            city = "Ha Noi"
            district = random.choice(HN_DISTRICTS)
        else:
            city = "Da Nang"
            district = random.choice(DN_DISTRICTS)

        users.append({
            "id":       __import__("uuid").uuid4().__str__(),
            "email":    f"user{i+1:03d}@example.com",
            "city":     city,
            "district": district,
        })
    return users


# ─── View history ─────────────────────────────────────────────────────────────

def gen_view_history(users: list[dict]) -> list[dict]:
    now = datetime.now()
    events = []

    for user in users:
        city     = user.get("city")
        district = user.get("district")

        # Pool uu tien: same city; fallback tat ca
        city_pool = rooms_by_city.get(city, rooms) if city else rooms
        dist_pool = rooms_by_district.get(district, []) if district else []

        # preference ngam: loai phong + muc gia
        pref_type      = random.choice(["phong_tro", "nha_tro", "chung_cu_mini", "can_ho_dich_vu"])
        pref_price_max = random.randint(2_000_000, 10_000_000)

        # preferred = same district + same type + in budget
        preferred = [
            r for r in city_pool
            if r["district"] == district
            or r["room_type"] == pref_type
            or r["price"] <= pref_price_max
        ] or city_pool

        n_views = random.randint(3, 45)
        viewed_ids: set[str] = set()

        for _ in range(n_views):
            roll = random.random()
            if roll < 0.45 and dist_pool:
                room = random.choice(dist_pool)
            elif roll < 0.80:
                room = random.choice(preferred)
            else:
                room = random.choice(rooms)

            if room["id"] in viewed_ids:
                continue
            viewed_ids.add(room["id"])

            days_ago   = random.betavariate(1, 3) * 60
            viewed_at  = now - timedelta(days=days_ago, seconds=random.randint(0, 86400))

            events.append({
                "user_id":  user["id"],
                "room_id":  room["id"],
                "viewed_at": viewed_at.isoformat(),
            })

    random.shuffle(events)
    return events


# ─── Main ────────────────────────────────────────────────────────────────────

N_USERS = 200

print(f"[gen] {N_USERS} users...")
users = gen_users(N_USERS)

print("[gen] view history...")
history = gen_view_history(users)

# Save
(DATA / "users.json").write_text(
    json.dumps(users, ensure_ascii=False, indent=2), encoding="utf-8"
)
(DATA / "view_history.json").write_text(
    json.dumps(history, ensure_ascii=False, indent=2), encoding="utf-8"
)

# Stats
from collections import Counter
city_counts = Counter(u["city"] for u in users)
print(f"\n[users] total={len(users)}")
for c, cnt in city_counts.most_common():
    print(f"  {c}: {cnt}")

views_per_user = Counter(e["user_id"] for e in history)
avg = sum(views_per_user.values()) / len(views_per_user)
print(f"\n[history] total={len(history)}  avg/user={avg:.1f}")
print(f"  min={min(views_per_user.values())}  max={max(views_per_user.values())}")

print(f"\n[saved] users.json + view_history.json -> {DATA}")
