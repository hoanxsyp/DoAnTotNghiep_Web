"""
LightGBM Re-ranker — Tầng 2 của pipeline.

Nhận candidates từ FAISS (tầng 1) → re-rank bằng LightGBM → top-K cuối.
"""

import time
import warnings
from dataclasses import dataclass

import faiss
import numpy as np
import pandas as pd

from app.reranker.features import compute_user_stats, compute_features, get_feature_names
from app.reranker.trainer import get_model
from app.recommender.indexer import IndexBundle, get_index
from app.recommender.profile import build_user_profile

CANDIDATE_FETCH  = 80   # lấy bao nhiêu candidates từ FAISS trước khi re-rank
DEFAULT_K        = 10
MIN_VIEWS_FOR_LGBM = 5  # dưới ngưỡng này → FAISS đáng tin hơn LightGBM


@dataclass
class RankedResult:
    room:         dict
    lgbm_score:   float   # xác suất user thích phòng (LightGBM)
    faiss_score:  float   # cosine similarity từ tầng 1
    is_cold_start: bool = False


def rerank(
    user: dict,
    view_history: list[dict],
    bundle: IndexBundle | None = None,
    k: int = DEFAULT_K,
) -> list[RankedResult]:
    """
    Pipeline 2 tầng đầy đủ:
      Tầng 1: FAISS lấy CANDIDATE_FETCH candidates
      Tầng 2: LightGBM re-rank → top-k

    Args:
        user:         {"id", "email", "city"?, "district"?}
        view_history: [{"room_id", "viewed_at"}, ...]
        bundle:       IndexBundle
        k:            số kết quả trả về
    """
    if bundle is None:
        bundle = get_index()

    model   = get_model()
    n_views = len(view_history)
    is_cold = n_views == 0

    # ── Tầng 1: FAISS retrieval ───────────────────────────────────────────────
    profile = build_user_profile(user, view_history, bundle.room_cache)

    if profile is None:
        return _popular_fallback(bundle, k, user.get("city"))

    query = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(query)

    scores, positions = bundle.index.search(query, CANDIDATE_FETCH)
    scores    = scores[0]
    positions = positions[0]

    # ── Filter cơ bản ─────────────────────────────────────────────────────────
    viewed_ids  = {e["room_id"] for e in view_history}
    user_stats  = compute_user_stats(view_history, bundle.room_cache)
    user_district = user.get("district")

    candidates = []
    for pos, faiss_score in zip(positions, scores):
        if pos == -1:
            continue
        room_id = bundle.id_map[pos]
        room    = bundle.room_cache.get(room_id)
        if room is None or room_id in viewed_ids:
            continue
        if not room.get("is_available", True):
            continue
        candidates.append((room, float(faiss_score)))

    if not candidates:
        return _popular_fallback(bundle, k, user.get("city"))

    # ── Bypass LightGBM khi không đủ data ────────────────────────────────────
    # Dưới MIN_VIEWS_FOR_LGBM: user stats quá thưa → LightGBM không đáng tin
    # → Giữ nguyên thứ tự FAISS (đã dùng address profile → đủ tốt cho cold start)
    if n_views < MIN_VIEWS_FOR_LGBM:
        return [
            RankedResult(
                room=room,
                lgbm_score=round(faiss_score, 4),  # dùng faiss_score thay thế
                faiss_score=round(faiss_score, 4),
                is_cold_start=is_cold,
            )
            for room, faiss_score in candidates[:k]
        ]

    # ── Tầng 2: LightGBM scoring ──────────────────────────────────────────────
    raw_matrix = np.stack([
        compute_features(user_stats, room, faiss_score, user_district)
        for room, faiss_score in candidates
    ]).astype(np.float32)

    feature_matrix = pd.DataFrame(raw_matrix, columns=get_feature_names())

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        lgbm_scores = model.predict_proba(feature_matrix)[:, 1]

    # ── Assemble results ──────────────────────────────────────────────────────
    results = [
        RankedResult(
            room=room,
            lgbm_score=round(float(lgbm_scores[i]), 4),
            faiss_score=round(faiss_score, 4),
            is_cold_start=is_cold,
        )
        for i, (room, faiss_score) in enumerate(candidates)
    ]

    # Sort theo LightGBM score
    results.sort(key=lambda r: r.lgbm_score, reverse=True)
    return results[:k]


def _popular_fallback(bundle: IndexBundle, k: int, city: str | None) -> list[RankedResult]:
    available = [
        r for r in bundle.room_cache.values()
        if r.get("is_available", True)
        and (city is None or r.get("city") == city)
    ] or list(bundle.room_cache.values())

    import random
    random.shuffle(available)
    return [
        RankedResult(room=r, lgbm_score=0.0, faiss_score=0.0, is_cold_start=True)
        for r in available[:k]
    ]
