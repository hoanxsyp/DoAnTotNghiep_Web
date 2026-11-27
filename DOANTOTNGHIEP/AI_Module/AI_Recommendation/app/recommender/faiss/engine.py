"""
Recommendation Engine — Bước 4 của pipeline.

Nhận user profile vector → FAISS search → filter → re-rank → top-K kết quả.

Filter pipeline:
  1. Loại phòng user đã xem (không gợi ý lại)
  2. Loại phòng không còn trống (is_available=False)
  3. Loại phòng cùng city nếu user có city preference

Re-rank:
  - FAISS score (cosine similarity) là điểm chính
  - Bonus nhẹ nếu phòng cùng city với user (tránh gợi ý Hà Nội cho user HCM)
"""

import json
import time
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path

import faiss
import numpy as np

from app.recommender.faiss.indexer import IndexBundle, get_index
from app.recommender.profile import build_user_profile

# ─── Config ───────────────────────────────────────────────────────────────────

FETCH_MULTIPLIER = 4     # lấy K * FETCH_MULTIPLIER kết quả từ FAISS trước khi filter
SAME_CITY_BONUS  = 0.05  # bonus score nếu phòng cùng city với user
DEFAULT_K        = 10


# ─── Result dataclass ─────────────────────────────────────────────────────────

@dataclass
class RecommendResult:
    room: dict
    score: float      # cosine similarity sau re-rank (0 → 1)
    is_cold_start: bool = False  # True nếu profile từ address (không có lịch sử)


# ─── Core engine ──────────────────────────────────────────────────────────────

def retrieve_candidates(
    profile: np.ndarray | None,
    bundle: IndexBundle,
    excluded_room_ids: set[str],
    fetch_k: int,
) -> Iterator[tuple[dict, float]]:
    """Yield available, unseen rooms in FAISS retrieval order."""
    if profile is None:
        return

    query = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(query)
    scores, positions = bundle.index.search(query, fetch_k)
    for position, score in zip(positions[0], scores[0]):
        if position == -1:
            continue
        room_id = bundle.id_map[position]
        room = bundle.room_cache.get(room_id)
        if room is not None and room_id not in excluded_room_ids and room.get("is_available", True):
            yield room, float(score)


def _popular_rooms(bundle: IndexBundle, k: int, city: str | None = None) -> list[RecommendResult]:
    """
    Fallback khi không có profile.
    Trả về k phòng còn trống, ưu tiên theo city nếu có.
    """
    available = [
        r for r in bundle.room_cache.values()
        if r.get("is_available", True)
    ]
    if city:
        same_city = [r for r in available if r.get("city") == city]
        pool = same_city if same_city else available
    else:
        pool = available

    # Shuffle để không luôn trả cùng k phòng đầu tiên
    rng = np.random.default_rng(seed=int(time.time()) % 1000)
    rng.shuffle(pool)

    return [
        RecommendResult(room=r, score=0.0, is_cold_start=True)
        for r in pool[:k]
    ]


def recommend(
    user: dict,
    view_history: list[dict],
    bundle: IndexBundle | None = None,
    k: int = DEFAULT_K,
) -> list[RecommendResult]:
    """
    Pipeline đầy đủ: user + history → top-K recommendations.

    Args:
        user:         {"id", "email", "city"?, "district"?}
        view_history: [{"room_id", "viewed_at"}, ...]
        bundle:       IndexBundle (dùng singleton nếu None)
        k:            số phòng muốn gợi ý

    Returns:
        list[RecommendResult] đã sort theo score giảm dần
    """
    if bundle is None:
        bundle = get_index()

    # ── Bước 1: Tính user profile ─────────────────────────────────────────────
    profile = build_user_profile(user, view_history, bundle.room_cache)

    if profile is None:
        # Không có history lẫn address → fallback popular
        return _popular_rooms(bundle, k, city=user.get("city"))

    is_cold = len(view_history) == 0

    fetch_k = min(k * FETCH_MULTIPLIER, len(bundle))
    viewed_ids  = {e["room_id"] for e in view_history}
    user_city   = user.get("city")

    results: list[RecommendResult] = []

    for room, score in retrieve_candidates(profile, bundle, viewed_ids, fetch_k):
        # ── Bước 4: Re-rank ───────────────────────────────────────────────────
        final_score = score

        # Bonus nhẹ nếu cùng city với user (tránh cross-city recommendation)
        if user_city and room.get("city") == user_city:
            final_score += SAME_CITY_BONUS

        results.append(RecommendResult(
            room=room,
            score=round(final_score, 4),
            is_cold_start=is_cold,
        ))

        if len(results) >= k:
            break

    # Nếu sau filter không đủ k → bổ sung từ popular
    if len(results) < k:
        exclude = viewed_ids | {r.room["id"] for r in results}
        extras  = [
            RecommendResult(room=r, score=0.0, is_cold_start=is_cold)
            for r in bundle.room_cache.values()
            if r["id"] not in exclude and r.get("is_available", True)
        ]
        results += extras[: k - len(results)]

    # Sort theo score giảm dần
    results.sort(key=lambda r: r.score, reverse=True)
    return results[:k]


# ─── Quick test ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import sys
    sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

    DATA_DIR = Path(__file__).resolve().parents[3] / "data"
    users    = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    history  = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    bundle   = get_index()

    def get_history(uid: str) -> list[dict]:
        return [e for e in history if e["user_id"] == uid]

    def print_results(title: str, results: list[RecommendResult], user: dict):
        print(f"\n{'=' * 65}")
        print(f" {title}")
        print(f"{'=' * 65}")
        print(f" User    : {user['email']}")
        print(f" City    : {user.get('city')}  |  District: {user.get('district')}")
        cold = results[0].is_cold_start if results else False
        print(f" Mode    : {'COLD START (address)' if cold else 'HISTORY-BASED'}")
        print(f"{'-' * 65}")
        print(f" {'#':>2}  {'Score':>6}  {'Loại phòng':<16}  {'Quận':<16}  {'Giá':>13}  City")
        print(f"{'-' * 65}")
        for i, res in enumerate(results, 1):
            r = res.room
            print(
                f" {i:>2}  {res.score:>6.4f}  {r['room_type']:<16}  "
                f"{r['district']:<16}  {r['price']:>12,}  {r['city']}"
            )

    # ── Case 1: User có nhiều lịch sử xem ────────────────────────────────────
    heavy = max(users, key=lambda u: len(get_history(u["id"])))
    h = get_history(heavy["id"])
    results1 = recommend(heavy, h, bundle, k=10)
    print_results("CASE 1 — User có nhiều lịch sử xem", results1, heavy)
    print(f"\n Đã xem: {len(h)} phòng — hiển thị 5 phòng gần nhất")
    for e in sorted(
        h,
        key=lambda x: x.get("occurred_at") or x.get("viewed_at", ""),
        reverse=True,
    )[:5]:
        r = bundle.room_cache.get(e["room_id"], {})
        print(f"   · {r.get('title','?'):<40} {r.get('district','?')}")

    # ── Case 2: Cold start từ địa chỉ ────────────────────────────────────────
    new_user = {"id": "new-001", "email": "new@ex.com",
                "city": "Ho Chi Minh", "district": "Bình Thạnh"}
    results2 = recommend(new_user, [], bundle, k=10)
    print_results("CASE 2 — Cold start (chưa có lịch sử)", results2, new_user)

    # ── Case 3: Không có gì cả ───────────────────────────────────────────────
    ghost = {"id": "ghost", "email": "ghost@ex.com", "city": None, "district": None}
    results3 = recommend(ghost, [], bundle, k=5)
    print_results("CASE 3 — Không có địa chỉ lẫn lịch sử (popular fallback)", results3, ghost)

    # ── Benchmark ─────────────────────────────────────────────────────────────
    print(f"\n{'=' * 65}")
    print(" BENCHMARK — 100 requests liên tiếp")
    print(f"{'=' * 65}")
    t0 = time.perf_counter()
    for user in users:
        recommend(user, get_history(user["id"]), bundle, k=10)
    elapsed = time.perf_counter() - t0
    print(f" Total   : {elapsed*1000:.1f} ms")
    print(f" Per req : {elapsed/len(users)*1000:.2f} ms/request")
    print(f" RPS     : {len(users)/elapsed:.0f} requests/second")
