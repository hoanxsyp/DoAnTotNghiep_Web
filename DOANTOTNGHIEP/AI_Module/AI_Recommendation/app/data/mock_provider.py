"""
Mock DataProvider đọc từ JSON files.
Swap sang ApiDataProvider khi BE thật sẵn sàng.
"""

import json
import os
from datetime import datetime, timezone
from pathlib import Path

from app.data.base import DataProvider

DATA_DIR = Path(__file__).parent.parent.parent / "data"


class MockDataProvider(DataProvider):

    def __init__(self):
        self._rooms:   dict[str, dict] = {}
        self._users:   dict[str, dict] = {}
        self._history: list[dict]      = []
        self._load()

    def _load(self):
        rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))
        self._rooms = {r["id"]: r for r in rooms}

        users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
        self._users = {u["id"]: u for u in users}

        self._history = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))

    def get_all_rooms(self) -> list[dict]:
        return list(self._rooms.values())

    def get_room_by_id(self, room_id: str) -> dict | None:
        return self._rooms.get(room_id)

    def get_user(self, user_id: str) -> dict | None:
        return self._users.get(user_id)

    def get_view_history(self, user_id: str, limit: int = 50) -> list[dict]:
        events = [e for e in self._history if e["user_id"] == user_id]
        events.sort(key=lambda e: e["viewed_at"], reverse=True)
        return events[:limit]

    def save_view_event(self, event: dict) -> None:
        self._history.append(event)
        history_path = DATA_DIR / "view_history.json"
        history_path.write_text(
            json.dumps(self._history, ensure_ascii=False, indent=2),
            encoding="utf-8"
        )


# Singleton
_provider: MockDataProvider | None = None

def get_provider() -> MockDataProvider:
    global _provider
    if _provider is None:
        _provider = MockDataProvider()
    return _provider
