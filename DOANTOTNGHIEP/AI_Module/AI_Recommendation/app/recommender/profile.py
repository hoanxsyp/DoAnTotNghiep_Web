"""
User Profile Builder — Bước 3 của pipeline.

Tính vector đại diện cho user dựa trên:
  - Lịch sử xem (weighted average + time decay)
  - Địa chỉ đăng ký (cold start khi chưa có lịch sử)
  - Blend cả hai khi user mới bắt đầu xem (< MIN_VIEWS lượt)

Output: vector float32 cùng chiều với room vector (49 dims),
        đã được L2-normalize → có thể đưa thẳng vào FAISS.search().
"""

import json
import math
from datetime import datetime, timezone
from pathlib import Path

import faiss
import numpy as np

from app.features.extractor import get_extractor

# ─── Hyperparameters ──────────────────────────────────────────────────────────

DECAY_LAMBDA   = 0.005   # tốc độ decay theo giờ; xem 1 tuần trước ~ weight 0.43
MIN_VIEWS      = 10      # số lượt xem tối thiểu để history hoàn toàn thay thế address
MAX_HISTORY    = 50      # chỉ lấy MAX_HISTORY lượt xem gần nhất để tính profile


# ─── Time decay ───────────────────────────────────────────────────────────────

def _parse_dt(viewed_at: str | datetime) -> datetime:
    if isinstance(viewed_at, datetime):
        return viewed_at
    # ISO format có thể có hoặc không có timezone
    dt = datetime.fromisoformat(viewed_at)
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=timezone.utc)
    return dt


def _decay_weight(viewed_at: str | datetime) -> float:
    """
    Exponential decay theo số giờ kể từ lúc xem.
    weight = e^(-lambda * age_hours)
      age=0h   → 1.000  (vừa xem)
      age=24h  → 0.887  (hôm qua)
      age=168h → 0.432  (1 tuần)
      age=720h → 0.028  (1 tháng)
    """
    now = datetime.now(timezone.utc)
    viewed_dt = _parse_dt(viewed_at)
    age_hours = (now - viewed_dt).total_seconds() / 3600
    return math.exp(-DECAY_LAMBDA * age_hours)


# ─── Cold start profile từ địa chỉ ───────────────────────────────────────────

def build_address_profile(city: str | None, district: str | None) -> np.ndarray | None:
    """
    Tạo profile vector từ city + district của user.
    Các chiều không liên quan (price, area, amenities) để ở 0
    → FAISS sẽ ưu tiên phòng cùng khu vực, không bias về giá/tiện ích.

    Trả về None nếu city và district đều trống.
    """
    if not city and not district:
        return None

    extractor = get_extractor()

    # Tạo "synthetic room" đại diện cho khu vực của user
    synthetic_room = {
        "id":        "__address_profile__",
        "price":     0,       # để 0: không bias về giá
        "area":      0,       # để 0: không bias về diện tích
        "city":      city or "",
        "district":  district or "",
        "room_type": "",      # không bias về loại phòng
        "amenities": [],      # không bias về tiện ích
        "is_available": True,
    }

    vec = extractor.extract(synthetic_room)

    # Normalize → cùng không gian với room vectors
    vec = vec.reshape(1, -1)
    faiss.normalize_L2(vec)
    return vec.reshape(-1)


# ─── History profile từ lịch sử xem ─────────────────────────────────────────

def build_history_profile(
    view_history: list[dict],
    room_cache: dict,
) -> tuple[np.ndarray | None, int]:
    """
    Tính user profile từ lịch sử xem bằng weighted average + time decay.

    Args:
        view_history: list of {"room_id": str, "viewed_at": str/datetime}
                      Không cần sắp xếp theo thời gian.
        room_cache:   dict room_id → room dict (từ IndexBundle)

    Returns:
        (profile_vector, n_valid_views)
        profile_vector là None nếu không có view nào hợp lệ.
    """
    extractor = get_extractor()

    # Sắp xếp mới → cũ, chỉ lấy MAX_HISTORY lượt gần nhất
    sorted_history = sorted(
        view_history,
        key=lambda e: e.get("viewed_at", ""),
        reverse=True,
    )[:MAX_HISTORY]

    vectors = []
    weights = []

    for event in sorted_history:
        room_id = event.get("room_id")
        if room_id not in room_cache:
            continue  # phòng đã bị xóa khỏi index

        room = room_cache[room_id]
        weight = _decay_weight(event.get("viewed_at", datetime.now(timezone.utc)))

        vec = extractor.extract(room)
        vectors.append(vec)
        weights.append(weight)

    if not vectors:
        return None, 0

    # Weighted average
    weights_arr = np.array(weights, dtype=np.float32)
    matrix = np.stack(vectors).astype(np.float32)
    profile = np.average(matrix, axis=0, weights=weights_arr).astype(np.float32)

    # Normalize
    profile = profile.reshape(1, -1)
    faiss.normalize_L2(profile)
    return profile.reshape(-1), len(vectors)


# ─── Blend: kết hợp history + address ────────────────────────────────────────

def blend_profiles(
    history_profile: np.ndarray | None,
    address_profile: np.ndarray | None,
    n_views: int,
) -> np.ndarray | None:
    """
    Blend hai profile theo số lượt xem:
      n_views = 0          → address_profile 100%
      n_views = MIN_VIEWS  → history_profile 100%
      n_views ở giữa       → interpolate tuyến tính

    Nếu cả hai đều None → trả về None (fallback về popular rooms).
    """
    if history_profile is None and address_profile is None:
        return None

    if history_profile is None:
        return address_profile

    if address_profile is None:
        return history_profile

    # alpha tăng từ 0 → 1 khi n_views tăng từ 0 → MIN_VIEWS
    alpha = min(n_views / MIN_VIEWS, 1.0)
    blended = alpha * history_profile + (1.0 - alpha) * address_profile

    # Re-normalize sau khi blend
    blended = blended.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(blended)
    return blended.reshape(-1)


# ─── Public API ───────────────────────────────────────────────────────────────

def build_user_profile(
    user: dict,
    view_history: list[dict],
    room_cache: dict,
) -> np.ndarray | None:
    """
    Hàm duy nhất cần gọi từ bên ngoài.

    Args:
        user:         {"id", "email", "city"?, "district"?}
        view_history: [{"room_id", "viewed_at"}, ...]
        room_cache:   IndexBundle.room_cache

    Returns:
        profile vector (49 dims, L2-normalized) hoặc None nếu không có gì.

    Flow:
        history_profile + address_profile → blend → final profile
    """
    history_profile, n_views = build_history_profile(view_history, room_cache)
    address_profile = build_address_profile(
        user.get("city"),
        user.get("district"),
    )
    return blend_profiles(history_profile, address_profile, n_views)


# ─── Quick test ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import sys
    sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

    from app.recommender.indexer import load_index

    DATA_DIR = Path(__file__).parent.parent.parent / "data"
    users   = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    history = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    bundle  = load_index()

    def get_history(user_id: str) -> list[dict]:
        return [e for e in history if e["user_id"] == user_id]

    print("=" * 60)
    print("CASE 1 — User có nhiều lịch sử xem (history dominates)")
    print("=" * 60)
    heavy_user = max(users, key=lambda u: len(get_history(u["id"])))
    h = get_history(heavy_user["id"])
    profile = build_user_profile(heavy_user, h, bundle.room_cache)
    print(f"User    : {heavy_user['email']}")
    print(f"City    : {heavy_user['city']}  |  District: {heavy_user['district']}")
    print(f"Views   : {len(h)}")
    print(f"Profile : shape={profile.shape}  norm={np.linalg.norm(profile):.4f}")
    print(f"  vector[:6] = {profile[:6]}")

    print()
    print("=" * 60)
    print("CASE 2 — User mới chưa xem gì (cold start từ address)")
    print("=" * 60)
    new_user = {"id": "new-001", "email": "new@ex.com", "city": "Ha Noi", "district": "Hoàn Kiếm"}
    profile_cold = build_user_profile(new_user, [], bundle.room_cache)
    print(f"User    : {new_user['email']}")
    print(f"City    : {new_user['city']}  |  District: {new_user['district']}")
    print(f"Views   : 0 (cold start)")
    print(f"Profile : shape={profile_cold.shape}  norm={np.linalg.norm(profile_cold):.4f}")

    print()
    print("=" * 60)
    print("CASE 3 — User không có địa chỉ, không có lịch sử")
    print("=" * 60)
    ghost_user = {"id": "y", "email": "ghost@ex.com", "city": None, "district": None}
    profile_ghost = build_user_profile(ghost_user, [], bundle.room_cache)
    print(f"User    : {ghost_user['email']}")
    print(f"Profile : {profile_ghost}  → fallback về popular rooms")

    print()
    print("=" * 60)
    print("CASE 4 — Time decay weights")
    print("=" * 60)
    test_cases = [0, 1, 24, 168, 720]
    print(f"{'Age':>8}  {'Weight':>8}")
    for hours in test_cases:
        from datetime import timedelta
        fake_dt = datetime.now(timezone.utc) - timedelta(hours=hours)
        w = _decay_weight(fake_dt)
        bar = "█" * int(w * 20)
        print(f"{str(hours)+'h':>8}  {w:.4f}  {bar}")

    print()
    print("=" * 60)
    print("CASE 5 — Blend interpolation")
    print("=" * 60)
    sample_user = next(u for u in users if u["city"])
    h_sample = get_history(sample_user["id"])[:5]
    addr_p = build_address_profile(sample_user["city"], sample_user["district"])
    hist_p, nv = build_history_profile(h_sample, bundle.room_cache)
    print(f"User: {sample_user['email']} | {sample_user['district']}, {sample_user['city']}")
    print(f"{'n_views':>8}  {'alpha':>6}  {'description'}")
    for n in [0, 2, 5, 10, 20]:
        alpha = min(n / MIN_VIEWS, 1.0)
        desc = "address 100%" if alpha == 0 else ("history 100%" if alpha == 1.0 else f"blend {int(alpha*100)}% hist")
        print(f"{n:>8}  {alpha:>6.2f}  {desc}")
