"""Training data shared by the room recommendation rankers."""

from numbers import Integral

import faiss
import numpy as np

from app.recommender.faiss.indexer import IndexBundle
from app.recommender.interactions import aggregate_interactions, relevance_label
from app.recommender.profile import build_history_profile
from app.reranker.features import FEATURE_DIM, compute_features, compute_user_stats

RankingData = tuple[np.ndarray, np.ndarray, list[int]]

TRAIN_RATIO       = 0.8    # 80% history cũ dùng để train
NEG_POS_RATIO     = 4      # tối đa 4 hard negatives mỗi positive
CANDIDATE_FETCH   = 80     # lấy bao nhiêu KNN candidates để tạo negatives
MIN_VIEWS         = 5      # bỏ qua user có < MIN_VIEWS phòng đã tương tác


def validate_ranking_data(data: RankingData, name: str, *, require_label_variation: bool = False) -> None:
    X, y, groups = data
    if X.ndim != 2 or X.shape[1] != FEATURE_DIM or len(X) == 0:
        raise ValueError(f"{name}: expected a non-empty matrix with {FEATURE_DIM} features.")
    if y.shape != (len(X),) or not np.isin(y, [0, 1, 2, 3]).all():
        raise ValueError(f"{name}: expected one relevance label from 0 to 3 per row.")
    if not groups or any(not isinstance(size, Integral) or size <= 0 for size in groups):
        raise ValueError(f"{name}: expected positive user-group sizes.")
    if sum(groups) != len(X):
        raise ValueError(f"{name}: user-group sizes must sum to the number of rows.")
    if require_label_variation:
        boundaries = np.cumsum([0, *groups])
        if not any(np.ptp(y[start:end]) > 0 for start, end in zip(boundaries, boundaries[1:])):
            raise ValueError(f"{name}: expected at least one user group with different relevance labels.")


def temporal_split(history: list[dict]) -> tuple[list[dict], list[dict]]:
    """Split chronologically while keeping all events of a room together.

    Keeping a user-room interaction in one side preserves its complete view
    count/favorite label and prevents the same room from leaking into both the
    profile history and the ranking target.
    """
    def event_time(event: dict) -> str:
        return event.get("occurred_at") or event.get("viewed_at", "")

    events_by_room: dict[str, list[dict]] = {}
    for event in history:
        room_id = event.get("room_id")
        if room_id:
            events_by_room.setdefault(room_id, []).append(event)

    room_timelines = sorted(
        events_by_room.values(),
        key=lambda events: max(event_time(event) for event in events),
    )
    split_idx = max(1, int(len(room_timelines) * TRAIN_RATIO))
    train_rooms = room_timelines[:split_idx]
    test_rooms = room_timelines[split_idx:]
    train_events = sorted(
        [event for events in train_rooms for event in events], key=event_time
    )
    test_events = sorted(
        [event for events in test_rooms for event in events], key=event_time
    )
    return train_events, test_events


def _get_faiss_candidates(
    train_history: list[dict],
    bundle: IndexBundle,
    user: dict,
    k: int = CANDIDATE_FETCH,
) -> list[tuple[str, float]]:
    """Trả về list (room_id, faiss_score) từ FAISS search."""
    profile, _ = build_history_profile(train_history, bundle.room_cache)

    if profile is None:
        # cold start: dùng district của user
        from app.recommender.profile import build_address_profile
        profile = build_address_profile(user.get("city"), user.get("district"))

    if profile is None:
        return []

    query = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(query)

    scores, positions = bundle.index.search(query, k)
    results = []
    for pos, score in zip(positions[0], scores[0]):
        if pos == -1:
            continue
        room_id = bundle.id_map[pos]
        results.append((room_id, float(score)))
    return results


def _compute_faiss_score(room: dict, profile: np.ndarray | None, bundle: IndexBundle) -> float:
    """Tính cosine similarity giữa user profile và một room bất kỳ."""
    if profile is None:
        return 0.0
    from app.features.extractor import get_extractor
    extractor = get_extractor()
    room_vec = extractor.extract(room).reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(room_vec)
    q = profile.reshape(1, -1).astype(np.float32)
    faiss.normalize_L2(q)
    score = float(np.dot(q[0], room_vec[0]))
    return max(0.0, score)


def generate_training_data(
    users: list[dict],
    history_by_user: dict[str, list[dict]],
    bundle: IndexBundle,
) -> tuple[np.ndarray, np.ndarray, list[int]]:
    """
    Sinh (X, y, groups) cho mô hình xếp hạng.

    Mỗi user là một query group. Tương tác trong phần holdout được gán mức
    relevance: favorite=3, xem nhiều=2, xem ít=1. Negative khó được lấy từ
    top candidates của FAISS để khớp với phân phối lúc inference.
    """
    X_rows, y_rows, groups = [], [], []
    n_pos = n_neg = n_skipped = 0
    label_counts = {0: 0, 1: 0, 2: 0, 3: 0}

    eligible = [
        user for user in users
        if len(aggregate_interactions(history_by_user.get(user["id"], []))) >= MIN_VIEWS
    ]
    print(f"  Eligible users: {len(eligible)}")

    for user in eligible:
        uid = user["id"]
        user_history = history_by_user[uid]

        train_history, test_history = temporal_split(user_history)
        if not test_history:
            n_skipped += 1
            continue

        user_stats = compute_user_stats(train_history, bundle.room_cache)
        user_district = user.get("district")
        train_room_ids = set(aggregate_interactions(train_history))
        test_interactions = aggregate_interactions(test_history)
        relevance_by_room = {
            room_id: relevance_label(interaction)
            for room_id, interaction in test_interactions.items()
            if room_id in bundle.room_cache
        }

        if not relevance_by_room:
            n_skipped += 1
            continue

        # Tính profile và lấy hard negatives đúng từ candidate pool của FAISS.
        profile, _ = build_history_profile(train_history, bundle.room_cache)
        faiss_candidates = _get_faiss_candidates(train_history, bundle, user)
        faiss_scores = dict(faiss_candidates)

        positive_ids = list(relevance_by_room)
        negative_ids = [
            room_id
            for room_id, _ in faiss_candidates
            if room_id not in train_room_ids and room_id not in relevance_by_room
        ]
        negative_ids = negative_ids[: len(positive_ids) * NEG_POS_RATIO]

        # Positives được giữ lại dù nằm ngoài top-80 để model vẫn học được tín
        # hiệu relevance; negatives là các phòng gần profile nhưng không chọn.
        candidate_ids = positive_ids + negative_ids
        if not candidate_ids:
            n_skipped += 1
            continue

        group_size = 0
        for room_id in candidate_ids:
            room = bundle.room_cache[room_id]
            score = faiss_scores.get(room_id)
            if score is None:
                score = _compute_faiss_score(room, profile, bundle)
            feat  = compute_features(user_stats, room, score, user_district)
            label = relevance_by_room.get(room_id, 0)
            X_rows.append(feat)
            y_rows.append(label)
            label_counts[label] += 1
            n_pos += int(label > 0)
            n_neg += int(label == 0)
            group_size += 1
        groups.append(group_size)

    print(f"  Positives : {n_pos}")
    print(f"  Negatives : {n_neg}")
    print(f"  Skipped   : {n_skipped}")
    print(f"  Ratio     : 1:{n_neg//n_pos if n_pos else 0}")
    print(f"  Labels    : {label_counts}")
    print(f"  Groups    : {len(groups)}")

    if not X_rows:
        raise ValueError("No training samples generated from the interaction data.")
    X = np.stack(X_rows).astype(np.float32)
    y = np.array(y_rows, dtype=np.int32)
    return X, y, groups
