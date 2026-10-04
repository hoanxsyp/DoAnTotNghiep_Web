"""In-memory data provider used by the standalone recommendation demo.

The JSON files are treated as immutable baseline data. View events created from
the demo UI only live in memory and can be reset without touching the files.
"""

import json
from copy import deepcopy
from datetime import datetime, timedelta, timezone
from pathlib import Path
from threading import RLock

from app.data.base import DataProvider

DATA_DIR = Path(__file__).parent.parent.parent / "data"


class MockDataProvider(DataProvider):

    def __init__(self):
        self._rooms:   dict[str, dict] = {}
        self._users:   dict[str, dict] = {}
        self._history: list[dict]      = []
        self._baseline_users: dict[str, dict] = {}
        self._baseline_history: list[dict] = []
        self._demo_user_ids: list[str] = []
        self._lock = RLock()
        self._load()

    def _load(self):
        rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))
        self._rooms = {r["id"]: r for r in rooms}

        users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
        self._users = {u["id"]: u for u in users}

        history = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
        self._add_demo_profiles(users, history)

        self._baseline_users = deepcopy(self._users)
        self._baseline_history = deepcopy(history)
        self.reset_demo()

    def _add_demo_profiles(self, users: list[dict], history: list[dict]) -> None:
        """Create three predictable users without modifying users.json."""
        now = datetime.now(timezone.utc)
        scenarios = [
            ("demo-cold-start", "Người dùng mới", "Ha Noi", 0),
            ("demo-light-user", "Người dùng ít lịch sử", "Ho Chi Minh", 3),
            ("demo-heavy-user", "Người dùng đủ lịch sử", "Da Nang", 12),
        ]

        for user_id, label, city, view_count in scenarios:
            candidates = [
                room for room in self._rooms.values()
                if room.get("city") == city and room.get("is_available", True)
            ]
            if not candidates:
                candidates = list(self._rooms.values())

            preferred_type = candidates[0].get("room_type") if candidates else None
            similar_rooms = [
                room for room in candidates
                if room.get("room_type") == preferred_type
            ] or candidates
            selected_rooms = similar_rooms[:view_count]
            district = (selected_rooms or candidates)[0].get("district") if candidates else None

            user = {
                "id": user_id,
                "email": f"{user_id}@demo.local",
                "city": city,
                "district": district,
                "display_name": label,
                "is_demo_profile": True,
            }
            users.append(user)
            self._users[user_id] = user
            self._demo_user_ids.append(user_id)

            for index, room in enumerate(selected_rooms):
                history.append({
                    "user_id": user_id,
                    "room_id": room["id"],
                    "viewed_at": (now - timedelta(hours=view_count - index)).isoformat(),
                })

    def get_all_rooms(self) -> list[dict]:
        return list(self._rooms.values())

    def get_all_users(self, demo_only: bool = False) -> list[dict]:
        users = list(self._users.values())
        if demo_only:
            return [self._users[user_id] for user_id in self._demo_user_ids]
        return users

    def get_room_by_id(self, room_id: str) -> dict | None:
        return self._rooms.get(room_id)

    def get_user(self, user_id: str) -> dict | None:
        return self._users.get(user_id)

    def get_view_history(self, user_id: str, limit: int = 250) -> list[dict]:
        """Backward-compatible alias for callers that still request views."""
        return self.get_interactions(user_id, limit)

    def get_interactions(self, user_id: str, limit: int = 250) -> list[dict]:
        with self._lock:
            events = [dict(e) for e in self._history if e["user_id"] == user_id]
        events.sort(
            key=lambda e: e.get("occurred_at") or e.get("viewed_at", ""),
            reverse=True,
        )
        return events[:limit]

    def save_view_event(self, event: dict) -> None:
        self.save_interaction(event)

    def save_interaction(self, event: dict) -> None:
        with self._lock:
            self._history.append(dict(event))

    def reset_demo(self) -> None:
        with self._lock:
            self._users = deepcopy(self._baseline_users)
            self._history = deepcopy(self._baseline_history)


# Singleton
_provider: MockDataProvider | None = None

def get_provider() -> MockDataProvider:
    global _provider
    if _provider is None:
        _provider = MockDataProvider()
    return _provider
