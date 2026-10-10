"""Utilities for aggregating user-room interaction events.

Legacy records without ``event_type`` are treated as ``view`` events so the
new algorithm remains compatible with the current dataset during migration.
"""

from __future__ import annotations

import math
from datetime import datetime, timezone


VIEW_FEW_WEIGHT = 1.0
VIEW_MANY_WEIGHT = 2.0
FAVORITE_WEIGHT = 4.0
MANY_VIEW_THRESHOLD = 3
SESSION_GAP_MINUTES = 30


def _parse_event_time(value: str | datetime | None) -> datetime:
    if value is None:
        return datetime.now(timezone.utc)
    if isinstance(value, datetime):
        dt = value
    else:
        dt = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def _event_time(event: dict) -> datetime:
    return _parse_event_time(event.get("occurred_at") or event.get("viewed_at"))


def aggregate_interactions(events: list[dict]) -> dict[str, dict]:
    """Aggregate raw events into one record per room.

    Views in the same explicit session, or consecutive views less than 30
    minutes apart without a session id, count as one view session. Favorite
    and unfavorite events are applied chronologically.
    """
    by_room: dict[str, list[dict]] = {}
    for event in events:
        room_id = event.get("room_id")
        if room_id:
            by_room.setdefault(room_id, []).append(event)

    aggregated: dict[str, dict] = {}
    for room_id, room_events in by_room.items():
        ordered = sorted(room_events, key=_event_time)
        view_count = 0
        is_favorite = False
        favorite_at: datetime | None = None
        last_view_at: datetime | None = None
        last_interacted_at: datetime | None = None
        seen_sessions: set[str] = set()

        for event in ordered:
            event_type = event.get("event_type", "view")
            occurred_at = _event_time(event)
            last_interacted_at = occurred_at

            if event_type == "favorite":
                is_favorite = True
                favorite_at = occurred_at
                continue
            if event_type == "unfavorite":
                is_favorite = False
                favorite_at = None
                continue
            if event_type != "view":
                continue

            session_id = event.get("session_id")
            if session_id:
                if session_id in seen_sessions:
                    continue
                seen_sessions.add(session_id)
                view_count += 1
            else:
                gap_minutes = (
                    (occurred_at - last_view_at).total_seconds() / 60
                    if last_view_at is not None
                    else math.inf
                )
                if gap_minutes >= SESSION_GAP_MINUTES:
                    view_count += 1
            last_view_at = occurred_at

        if view_count == 0 and not is_favorite:
            continue

        aggregated[room_id] = {
            "room_id": room_id,
            "view_count": view_count,
            "is_favorite": is_favorite,
            "favorite_at": favorite_at,
            "last_viewed_at": last_view_at,
            "last_interacted_at": last_interacted_at,
        }
    return aggregated


def relevance_label(interaction: dict) -> int:
    """Return graded relevance: none=0, few views=1, many views=2, favorite=3."""
    if interaction.get("is_favorite"):
        return 3
    view_count = int(interaction.get("view_count", 0))
    if view_count >= MANY_VIEW_THRESHOLD:
        return 2
    if view_count > 0:
        return 1
    return 0


def profile_weight(interaction: dict) -> float:
    """Return the behavior weight used when building a FAISS user profile."""
    if interaction.get("is_favorite"):
        return FAVORITE_WEIGHT
    if int(interaction.get("view_count", 0)) >= MANY_VIEW_THRESHOLD:
        return VIEW_MANY_WEIGHT
    return VIEW_FEW_WEIGHT


def interaction_count(interactions: dict[str, dict]) -> int:
    """Count effective signals used to blend history and address profiles."""
    return sum(
        int(item.get("view_count", 0)) + int(bool(item.get("is_favorite")))
        for item in interactions.values()
    )
