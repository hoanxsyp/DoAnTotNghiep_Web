import time
from datetime import datetime, timezone
from typing import Optional

from fastapi import APIRouter, HTTPException, Query, BackgroundTasks

from app.data.mock_provider import get_provider
from app.models.room import Room, RoomRecommendation
from app.models.user import InteractionEvent, User, ViewEvent
from app.recommender.indexer import get_index, build_and_save, reload_index
from app.recommender.interactions import aggregate_interactions, interaction_count
from app.reranker.ranker import rerank
from app.reranker.features import compute_user_stats
from app.reranker.trainer import load_model

router = APIRouter()


def _effective_signal_count(events: list[dict]) -> int:
    return interaction_count(aggregate_interactions(events))


def _scenario(signal_count: int) -> str:
    if signal_count == 0:
        return "COLD_START"
    if signal_count < 5:
        return "LIGHT"
    return "HEAVY"


def _method(signal_count: int) -> str:
    if signal_count == 0:
        return "FAISS_COLD_START"
    if signal_count < 5:
        return "FAISS"
    return "FAISS_LIGHTGBM"


def _recommendation_reasons(
    user: dict, stats: dict, room: dict, is_cold: bool
) -> list[str]:
    reasons: list[str] = []
    if room.get("city") == user.get("city"):
        reasons.append("Cùng thành phố với hồ sơ người dùng")
    if room.get("district") in {user.get("district"), stats.get("preferred_district")}:
        reasons.append("Đúng khu vực thường quan tâm")

    average_price = stats.get("avg_price", 0)
    if average_price and abs(room.get("price", 0) - average_price) / average_price <= 0.2:
        reasons.append("Giá gần mức thường xem")
    if stats.get("preferred_type") and room.get("room_type") == stats["preferred_type"]:
        reasons.append("Đúng loại phòng thường xem")

    preferred_amenities = {
        name for name, frequency in stats.get("amenity_freq", {}).items()
        if frequency >= 0.5
    }
    overlap = preferred_amenities.intersection(room.get("amenities", []))
    if overlap:
        reasons.append(f"Khớp {len(overlap)} tiện ích quan tâm")
    if is_cold and not reasons:
        reasons.append("Gợi ý khởi tạo từ vị trí đăng ký")
    return reasons[:3] or ["Phù hợp với hồ sơ nội dung tổng hợp"]


# ─── Health ───────────────────────────────────────────────────────────────────

@router.get("/health")
def health():
    bundle = get_index()
    return {
        "status":   "ok",
        "rooms_indexed": len(bundle),
        "index_type": type(bundle.index).__name__,
        "built_at": bundle.meta.get("built_at"),
    }


# ─── Rooms ────────────────────────────────────────────────────────────────────

@router.get("/rooms", response_model=list[Room])
def list_rooms(
    city:      Optional[str]   = None,
    district:  Optional[str]   = None,
    room_type: Optional[str]   = None,
    min_price: Optional[float] = None,
    max_price: Optional[float] = None,
    min_area:  Optional[float] = None,
    available: Optional[bool]  = True,
    page:      int = Query(1, ge=1),
    limit:     int = Query(20, ge=1, le=100),
):
    rooms = get_provider().get_all_rooms()

    if city:      rooms = [r for r in rooms if r["city"] == city]
    if district:  rooms = [r for r in rooms if r["district"] == district]
    if room_type: rooms = [r for r in rooms if r["room_type"] == room_type]
    if min_price: rooms = [r for r in rooms if r["price"] >= min_price]
    if max_price: rooms = [r for r in rooms if r["price"] <= max_price]
    if min_area:  rooms = [r for r in rooms if r["area"] >= min_area]
    if available is not None:
        rooms = [r for r in rooms if r["is_available"] == available]

    start = (page - 1) * limit
    return rooms[start: start + limit]


@router.get("/rooms/{room_id}", response_model=Room)
def get_room(room_id: str):
    room = get_provider().get_room_by_id(room_id)
    if not room:
        raise HTTPException(status_code=404, detail="Room not found")
    return room


# ─── Users ────────────────────────────────────────────────────────────────────

@router.get("/users/{user_id}", response_model=User)
def get_user(user_id: str):
    user = get_provider().get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    return user


# ─── View Events ──────────────────────────────────────────────────────────────

@router.post("/view-events", status_code=201)
def record_view_event(event: ViewEvent):
    provider = get_provider()

    if not provider.get_user(event.user_id):
        raise HTTPException(status_code=404, detail="User not found")
    if not provider.get_room_by_id(event.room_id):
        raise HTTPException(status_code=404, detail="Room not found")

    record = {
        "user_id":   event.user_id,
        "room_id":   event.room_id,
        "event_type": "view",
        "occurred_at": (event.viewed_at or datetime.now(timezone.utc)).isoformat(),
        "session_id": event.session_id,
        "duration_seconds": event.duration_seconds,
    }
    provider.save_interaction(record)
    return {"message": "View event recorded"}


@router.post("/interactions", status_code=201)
def record_interaction(event: InteractionEvent):
    provider = get_provider()
    if not provider.get_user(event.user_id):
        raise HTTPException(status_code=404, detail="User not found")
    if not provider.get_room_by_id(event.room_id):
        raise HTTPException(status_code=404, detail="Room not found")

    record = {
        "user_id": event.user_id,
        "room_id": event.room_id,
        "event_type": event.event_type,
        "occurred_at": (event.occurred_at or datetime.now(timezone.utc)).isoformat(),
        "session_id": event.session_id,
    }
    if event.event_type == "view":
        record["duration_seconds"] = event.duration_seconds
    provider.save_interaction(record)
    return {"message": f"{event.event_type} interaction recorded"}


@router.get("/users/{user_id}/favorites", response_model=list[Room])
def get_user_favorites(user_id: str):
    provider = get_provider()
    if not provider.get_user(user_id):
        raise HTTPException(status_code=404, detail="User not found")

    interactions = aggregate_interactions(
        provider.get_interactions(user_id, limit=10_000)
    )
    rooms = []
    for room_id, interaction in interactions.items():
        if interaction["is_favorite"]:
            room = provider.get_room_by_id(room_id)
            if room:
                rooms.append(room)
    return rooms


# ─── Recommendations ──────────────────────────────────────────────────────────

@router.get("/recommendations/{user_id}", response_model=list[RoomRecommendation])
def get_recommendations(
    user_id: str,
    k: int = Query(10, ge=1, le=50),
):
    provider = get_provider()
    user = provider.get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    # Fetch raw events; profile.py will aggregate them and keep the latest
    # MAX_HISTORY user-room interactions.
    history = provider.get_interactions(user_id, limit=250)
    bundle  = get_index()
    results = rerank(user, history, bundle, k=k)

    return [
        RoomRecommendation(
            room=Room(**r.room),
            score=r.lgbm_score,
            is_cold_start=r.is_cold_start,
        )
        for r in results
    ]


# ─── Admin ────────────────────────────────────────────────────────────────────

# --- Standalone demo -------------------------------------------------------

@router.get("/demo/users")
def list_demo_users():
    provider = get_provider()
    result = []
    for user in provider.get_all_users(demo_only=True):
        events = provider.get_interactions(user["id"], limit=10_000)
        signal_count = _effective_signal_count(events)
        result.append({
            **user,
            "view_count": signal_count,
            "scenario": _scenario(signal_count),
        })
    return result


@router.get("/demo/users/{user_id}/history")
def get_demo_history(user_id: str, limit: int = Query(8, ge=1, le=50)):
    provider = get_provider()
    if not provider.get_user(user_id):
        raise HTTPException(status_code=404, detail="User not found")

    events = provider.get_interactions(user_id, limit=limit)
    return [
        {
            **event,
            # Backward-compatible field for the existing demo UI.
            "viewed_at": event.get("occurred_at") or event.get("viewed_at"),
            "room": provider.get_room_by_id(event["room_id"]),
        }
        for event in events
        if provider.get_room_by_id(event["room_id"])
    ]


@router.get("/demo/recommendations/{user_id}")
def get_demo_recommendations(user_id: str, k: int = Query(8, ge=1, le=20)):
    provider = get_provider()
    user = provider.get_user(user_id)
    if not user:
        raise HTTPException(status_code=404, detail="User not found")

    history = provider.get_interactions(user_id, limit=250)
    signal_count = _effective_signal_count(history)
    bundle = get_index()
    started_at = time.perf_counter()
    results = rerank(user, history, bundle, k=k)
    elapsed_ms = round((time.perf_counter() - started_at) * 1000, 2)
    stats = compute_user_stats(history, bundle.room_cache)

    preferred_amenities = [
        name for name, frequency in stats.get("amenity_freq", {}).items()
        if frequency >= 0.5
    ]
    profile = {
        "average_price": round(stats.get("avg_price", 0)),
        "average_area": round(stats.get("avg_area", 0), 1),
        "preferred_city": stats.get("preferred_city") or user.get("city"),
        "preferred_district": stats.get("preferred_district") or user.get("district"),
        "preferred_room_type": stats.get("preferred_type") or None,
        "preferred_amenities": preferred_amenities,
    }

    return {
        "user": user,
        "view_count": signal_count,
        "scenario": _scenario(signal_count),
        "ranking_method": _method(signal_count),
        "processing_time_ms": elapsed_ms,
        "profile": profile,
        "items": [
            {
                "rank": index,
                "room": result.room,
                "final_score": result.lgbm_score,
                "faiss_score": result.faiss_score,
                "is_cold_start": result.is_cold_start,
                "reasons": _recommendation_reasons(
                    user, stats, result.room, result.is_cold_start
                ),
            }
            for index, result in enumerate(results, start=1)
        ],
    }


@router.post("/demo/view-events", status_code=201)
def record_demo_view_event(event: ViewEvent):
    return record_view_event(event)


@router.post("/demo/reset")
def reset_demo_data():
    provider = get_provider()
    provider.reset_demo()
    return {
        "message": "Demo data reset",
        "users": len(provider.get_all_users(demo_only=True)),
    }


def _rebuild_task():
    build_and_save()
    reload_index()

@router.post("/admin/rebuild-index")
def rebuild_index(background_tasks: BackgroundTasks):
    background_tasks.add_task(_rebuild_task)
    return {"message": "Index rebuild started in background"}


def _retrain_task():
    import json, sys
    from pathlib import Path
    ROOT = Path(__file__).parent.parent.parent
    sys.path.insert(0, str(ROOT))
    from app.reranker.trainer import (
        generate_training_data,
        reload_model,
        save_model,
        train_model,
    )

    provider = get_provider()
    users    = provider._users.values()
    history_by_user = {}
    for e in provider._history:
        history_by_user.setdefault(e["user_id"], []).append(e)

    bundle = get_index()
    X, y, groups = generate_training_data(list(users), history_by_user, bundle)
    model, meta = train_model(X, y, groups)
    save_model(model, meta)
    reload_model()

@router.post("/admin/retrain")
def retrain_model(background_tasks: BackgroundTasks):
    background_tasks.add_task(_retrain_task)
    return {"message": "LightGBM retrain started in background"}
