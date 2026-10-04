"""Generate synthetic view/favorite events from users and rooms.

The generator intentionally creates behavior correlated with a hidden user
preference so the ranking model has a meaningful signal to learn.
"""

from __future__ import annotations

import random
import uuid
from collections import Counter
from datetime import datetime, timedelta, timezone


ROOM_TYPES = ["phong_tro", "nha_tro", "chung_cu_mini", "can_ho_dich_vu"]
AMENITIES = [
    "ban_cong", "bep", "cho_de_xe", "dieu_hoa", "may_giat",
    "may_nuoc_nong", "tu_lanh", "wc_rieng", "wifi",
]


def _preference_score(room: dict, preference: dict) -> float:
    score = 0.0
    score += 3.0 if room.get("city") == preference["city"] else 0.0
    score += 3.0 if room.get("district") == preference["district"] else 0.0
    score += 2.0 if room.get("room_type") == preference["room_type"] else 0.0

    price = float(room.get("price", 0))
    budget = preference["budget"]
    price_closeness = max(0.0, 1.0 - abs(price - budget) / max(budget, 1.0))
    score += 2.0 * price_closeness

    room_amenities = set(room.get("amenities", []))
    preferred_amenities = preference["amenities"]
    score += len(room_amenities & preferred_amenities) / max(len(preferred_amenities), 1)
    return score


def _make_view_event(
    user_id: str,
    room_id: str,
    occurred_at: datetime,
    session_number: int,
    duration_seconds: int,
) -> dict:
    session_key = f"{user_id}:{room_id}:{session_number}:{occurred_at.isoformat()}"
    session_id = str(uuid.uuid5(uuid.NAMESPACE_URL, session_key))
    return {
        "user_id": user_id,
        "room_id": room_id,
        "event_type": "view",
        "occurred_at": occurred_at.isoformat(),
        "session_id": session_id,
        "duration_seconds": duration_seconds,
    }


def generate_interactions(
    users: list[dict],
    rooms: list[dict],
    seed: int = 42,
    now: datetime | None = None,
) -> list[dict]:
    rng = random.Random(seed)
    now = now or datetime.now(timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)

    rooms_by_city: dict[str, list[dict]] = {}
    for room in rooms:
        if room.get("is_available", True):
            rooms_by_city.setdefault(room.get("city", ""), []).append(room)

    events: list[dict] = []
    for user in users:
        city_pool = rooms_by_city.get(user.get("city"), rooms)
        preference = {
            "city": user.get("city"),
            "district": user.get("district"),
            "room_type": rng.choice(ROOM_TYPES),
            "budget": rng.randint(2_000_000, 10_000_000),
            "amenities": set(rng.sample(AMENITIES, k=rng.randint(2, 5))),
        }

        # Score a bounded sample for speed while retaining some exploration.
        sample_size = min(len(city_pool), 500)
        sampled_rooms = rng.sample(city_pool, sample_size)
        scored = sorted(
            ((_preference_score(room, preference), rng.random(), room) for room in sampled_rooms),
            key=lambda item: (item[0], item[1]),
            reverse=True,
        )

        n_rooms = min(rng.randint(15, 30), len(scored))
        n_favorite = max(2, round(n_rooms * 0.12))
        n_many = max(3, round(n_rooms * 0.25))

        high_interest = [room for _, _, room in scored[: max(n_rooms * 3, n_rooms)]]
        selected = rng.sample(high_interest, n_rooms)
        selected.sort(key=lambda room: _preference_score(room, preference), reverse=True)

        favorite_ids = {room["id"] for room in selected[:n_favorite]}
        many_ids = {
            room["id"]
            for room in selected[n_favorite:n_favorite + n_many]
        }

        for room in selected:
            if room["id"] in favorite_ids:
                category = "favorite"
                session_count = rng.randint(2, 5)
                duration_range = (60, 300)
            elif room["id"] in many_ids:
                category = "many"
                session_count = rng.randint(3, 6)
                duration_range = (30, 180)
            else:
                category = "few"
                session_count = rng.randint(1, 2)
                duration_range = (5, 90)

            latest_at = now - timedelta(
                days=rng.betavariate(1.2, 3.0) * 60,
                seconds=rng.randint(0, 86_400),
            )
            session_times = [latest_at]
            for _ in range(1, session_count):
                session_times.append(
                    session_times[-1] - timedelta(days=rng.randint(1, 5), hours=rng.randint(0, 12))
                )
            session_times.sort()

            for session_number, occurred_at in enumerate(session_times, 1):
                events.append(_make_view_event(
                    user["id"],
                    room["id"],
                    occurred_at,
                    session_number,
                    rng.randint(*duration_range),
                ))

            if category == "favorite":
                events.append({
                    "user_id": user["id"],
                    "room_id": room["id"],
                    "event_type": "favorite",
                    "occurred_at": (latest_at + timedelta(minutes=rng.randint(1, 20))).isoformat(),
                    "session_id": events[-1]["session_id"],
                })

    rng.shuffle(events)
    return events


def summarize_interactions(events: list[dict]) -> dict:
    event_types = Counter(event["event_type"] for event in events)
    pair_views: Counter = Counter(
        (event["user_id"], event["room_id"])
        for event in events
        if event["event_type"] == "view"
    )
    favorite_pairs = {
        (event["user_id"], event["room_id"])
        for event in events
        if event["event_type"] == "favorite"
    }
    many_pairs = {
        pair for pair, count in pair_views.items()
        if count >= 3 and pair not in favorite_pairs
    }
    few_pairs = set(pair_views) - favorite_pairs - many_pairs
    return {
        "events": len(events),
        "views": event_types["view"],
        "favorites": event_types["favorite"],
        "unique_pairs": len(pair_views),
        "few_pairs": len(few_pairs),
        "many_pairs": len(many_pairs),
        "favorite_pairs": len(favorite_pairs),
    }
