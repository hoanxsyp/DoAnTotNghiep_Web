"""
Regenerate view_history.json dựa trên users.json và rooms.json hiện tại.
Giữ nguyên rooms.json và users.json, chỉ tạo lại view_history.
"""

import json
import random
from datetime import datetime, timedelta
from pathlib import Path

random.seed(42)

DATA_DIR = Path(__file__).parent.parent / "data"
users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))

now = datetime.now()
events = []

for user in users:
    pref_district  = user.get("district") or random.choice([r["district"] for r in rooms])
    pref_city      = user.get("city") or random.choice(["Ho Chi Minh", "Ha Noi"])
    pref_price_max = random.randint(2_000_000, 8_000_000)

    # Phòng phù hợp preference
    preferred = [
        r for r in rooms
        if r["city"] == pref_city
        or r["district"] == pref_district
        or r["price"] <= pref_price_max
    ] or rooms

    n_views  = random.randint(5, 40)
    seen_ids = set()

    for _ in range(n_views):
        room = random.choice(preferred) if random.random() < 0.7 else random.choice(rooms)
        if room["id"] in seen_ids:
            continue
        seen_ids.add(room["id"])

        days_ago  = random.betavariate(1, 3) * 30
        viewed_at = now - timedelta(days=days_ago, seconds=random.randint(0, 86400))

        events.append({
            "user_id":   user["id"],
            "room_id":   room["id"],
            "viewed_at": viewed_at.isoformat(),
        })

random.shuffle(events)

(DATA_DIR / "view_history.json").write_text(
    json.dumps(events, ensure_ascii=False, indent=2),
    encoding="utf-8"
)

print(f"Users        : {len(users)}")
print(f"Rooms        : {len(rooms)}")
print(f"View events  : {len(events)}")
print(f"Avg per user : {len(events)/len(users):.1f}")
