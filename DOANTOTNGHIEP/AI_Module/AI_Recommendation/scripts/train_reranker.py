"""
Script train LightGBM Re-ranker.
Chạy: py scripts/train_reranker.py
"""

import json
import sys
import time
from pathlib import Path

sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from app.recommender.indexer import get_index
from app.reranker.trainer import generate_training_data, train_model, save_model

DATA_DIR = ROOT / "data"


def main():
    print("=" * 60)
    print(" STEP 1 — Load data")
    print("=" * 60)
    users   = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    history = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    bundle  = get_index()

    history_by_user: dict[str, list] = {}
    for e in history:
        history_by_user.setdefault(e["user_id"], []).append(e)

    print(f"  Users        : {len(users)}")
    print(f"  View events  : {len(history)}")
    print(f"  Rooms indexed: {len(bundle)}")

    print("\n" + "=" * 60)
    print(" STEP 2 — Generate training data")
    print("=" * 60)
    t0 = time.perf_counter()
    X, y = generate_training_data(users, history_by_user, bundle)
    print(f"  Done in {time.perf_counter()-t0:.2f}s")
    print(f"  X shape: {X.shape}  |  y shape: {y.shape}")

    print("\n" + "=" * 60)
    print(" STEP 3 — Train LightGBM")
    print("=" * 60)
    t0 = time.perf_counter()
    model, meta = train_model(X, y)
    meta["train_time_sec"] = round(time.perf_counter() - t0, 2)
    print(f"\n  Train time: {meta['train_time_sec']}s")

    print("\n" + "=" * 60)
    print(" STEP 4 — Save model")
    print("=" * 60)
    save_model(model, meta)

    print("\n" + "=" * 60)
    print(" STEP 5 — Quick inference test")
    print("=" * 60)
    from app.reranker.ranker import rerank

    heavy = max(users, key=lambda u: len(history_by_user.get(u["id"], [])))
    h     = history_by_user.get(heavy["id"], [])
    t0    = time.perf_counter()
    results = rerank(heavy, h, bundle, k=10)
    elapsed = (time.perf_counter() - t0) * 1000

    print(f"  User  : {heavy['email']}  ({len(h)} views)")
    print(f"  Time  : {elapsed:.1f} ms")
    print(f"\n  {'#':>2}  {'LGBM':>6}  {'FAISS':>6}  {'Loại':<16}  {'Quận':<16}  {'Giá':>13}")
    print(f"  {'-'*65}")
    for i, r in enumerate(results, 1):
        rm = r.room
        print(
            f"  {i:>2}  {r.lgbm_score:>6.4f}  {r.faiss_score:>6.4f}"
            f"  {rm['room_type']:<16}  {rm['district']:<16}  {rm['price']:>12,}"
        )

    print("\n" + "=" * 60)
    print(f" DONE — Model summary")
    print("=" * 60)
    for k, v in meta.items():
        print(f"  {k:<20}: {v}")


if __name__ == "__main__":
    main()
