"""
So sánh kết quả 2 tầng: FAISS only vs FAISS + LightGBM
"""

import json
import sys
import time
from pathlib import Path

sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from app.recommender.engine import recommend as faiss_recommend
from app.recommender.indexer import get_index
from app.reranker.ranker import rerank as lgbm_rerank

DATA_DIR = ROOT / "data"
users    = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
history  = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
bundle   = get_index()

history_by_user = {}
for e in history:
    history_by_user.setdefault(e["user_id"], []).append(e)

def get_history(uid): return history_by_user.get(uid, [])

SEP = "=" * 70

def print_comparison(user, h):
    print(f"\n{SEP}")
    print(f" USER: {user['email']}")
    print(f" City: {user.get('city')}  |  District: {user.get('district')}")
    print(f" Views: {len(h)}")
    if h:
        recent = sorted(h, key=lambda e: e["viewed_at"], reverse=True)[:3]
        print(f" Gần nhất:")
        for e in recent:
            r = bundle.room_cache.get(e["room_id"], {})
            print(f"   · {r.get('room_type','?'):16} {r.get('district','?'):16} {r.get('price',0):>12,} VND")
    print(SEP)

    # ── Tầng 1: FAISS only ────────────────────────────────────────────────────
    t0 = time.perf_counter()
    faiss_results = faiss_recommend(user, h, bundle, k=10)
    t1 = (time.perf_counter() - t0) * 1000

    # ── Tầng 2: FAISS + LightGBM ──────────────────────────────────────────────
    t0 = time.perf_counter()
    lgbm_results = lgbm_rerank(user, h, bundle, k=10)
    t2 = (time.perf_counter() - t0) * 1000

    # ── In song song ──────────────────────────────────────────────────────────
    print(f"\n {'#':>2}  {'──── Tầng 1: FAISS only':<42}  {'──── Tầng 2: FAISS + LightGBM'}")
    print(f" {'':>2}  {'Score':>6}  {'Loại':<14} {'Quận':<13} {'Giá':>10}  {'LGBM':>6}  {'Loại':<14} {'Quận':<13} {'Giá':>10}")
    print(f" {'-'*115}")

    for i in range(10):
        fr = faiss_results[i].room if i < len(faiss_results) else None
        lr = lgbm_results[i].room  if i < len(lgbm_results)  else None
        fs = faiss_results[i].score if fr else 0
        ls = lgbm_results[i].lgbm_score if lr else 0

        same = "✓" if fr and lr and fr["id"] == lr["id"] else " "
        f_line = f"{fs:>6.4f}  {fr['room_type']:<14} {fr['district']:<13} {fr['price']:>10,}" if fr else " " * 48
        l_line = f"{ls:>6.4f}  {lr['room_type']:<14} {lr['district']:<13} {lr['price']:>10,}" if lr else " " * 48
        print(f" {i+1:>2}{same} {f_line}  {l_line}")

    print(f"\n Tầng 1 (FAISS only)     : {t1:.1f} ms")
    print(f" Tầng 2 (FAISS+LightGBM) : {t2:.1f} ms")

    # Phân tích sự khác biệt
    faiss_ids = [r.room["id"] for r in faiss_results]
    lgbm_ids  = [r.room["id"] for r in lgbm_results]
    overlap   = len(set(faiss_ids) & set(lgbm_ids))
    changed   = 10 - overlap
    print(f" Thay đổi sau re-rank    : {changed}/10 vị trí")


# ── Test 3 loại user ──────────────────────────────────────────────────────────

# Case 1: User nhiều lịch sử
heavy = max(users, key=lambda u: len(get_history(u["id"])))
print_comparison(heavy, get_history(heavy["id"]))

# Case 2: User ít lịch sử (5-10 views)
light_users = [u for u in users if 5 <= len(get_history(u["id"])) <= 10]
if light_users:
    light = light_users[0]
    print_comparison(light, get_history(light["id"]))

# Case 3: Cold start
new_user = {"id": "new-001", "email": "new@ex.com",
            "city": "Ho Chi Minh", "district": "Bình Thạnh"}
print_comparison(new_user, [])

# ── Benchmark 100 requests ────────────────────────────────────────────────────
print(f"\n{SEP}")
print(f" BENCHMARK — 100 requests")
print(SEP)

t0 = time.perf_counter()
for user in users:
    faiss_recommend(user, get_history(user["id"]), bundle, k=10)
t_faiss = (time.perf_counter() - t0) * 1000

t0 = time.perf_counter()
for user in users:
    lgbm_rerank(user, get_history(user["id"]), bundle, k=10)
t_lgbm = (time.perf_counter() - t0) * 1000

print(f"\n {'':30} {'Total':>8}  {'Per req':>8}  {'RPS':>6}")
print(f" {'-'*60}")
print(f" {'Tầng 1 (FAISS only)':<30} {t_faiss:>7.1f}ms  {t_faiss/100:>7.2f}ms  {100000/t_faiss:>5.0f}")
print(f" {'Tầng 2 (FAISS + LightGBM)':<30} {t_lgbm:>7.1f}ms  {t_lgbm/100:>7.2f}ms  {100000/t_lgbm:>5.0f}")
print(f" Overhead LightGBM: +{(t_lgbm-t_faiss)/100:.1f}ms/request")
