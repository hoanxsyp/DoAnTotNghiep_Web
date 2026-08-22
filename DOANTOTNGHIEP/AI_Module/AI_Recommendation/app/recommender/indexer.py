"""
FAISS Index Builder — Bước 2 của pipeline.

Chiến lược chọn index type theo số lượng phòng:
  N < 5,000   → IndexFlatIP    (exact search, không cần train, tốt nhất cho dataset nhỏ)
  N >= 5,000  → IndexIVFFlat   (approximate, nhanh hơn 50-100x, cần train)

Tất cả vector được L2-normalize trước khi đưa vào index
→ inner product == cosine similarity.
"""

import json
import os
import pickle
import time
from dataclasses import dataclass, field
from pathlib import Path

import faiss
import numpy as np

from app.features.extractor import get_extractor

# ─── Paths ────────────────────────────────────────────────────────────────────

STORAGE_DIR  = Path(__file__).parent.parent.parent / "storage"
INDEX_PATH   = STORAGE_DIR / "rooms.index"
ID_MAP_PATH  = STORAGE_DIR / "id_map.pkl"       # list[str]: position → room_id
CACHE_PATH   = STORAGE_DIR / "rooms_cache.pkl"  # dict[room_id → room dict]
META_PATH    = STORAGE_DIR / "index_meta.json"

# Ngưỡng chuyển từ Flat sang IVF
IVF_THRESHOLD = 5_000

# IVF params
NLIST_FACTOR = 4    # nlist = sqrt(N) * factor, tăng → nhanh hơn nhưng kém chính xác hơn
DEFAULT_NPROBE = 16  # số cluster tìm lúc query; tăng → chính xác hơn, chậm hơn


# ─── Data structures ──────────────────────────────────────────────────────────

@dataclass
class IndexBundle:
    """Gói tất cả thứ cần cho search vào một object duy nhất."""
    index: faiss.Index
    id_map: list[str]             # vị trí → room_id
    room_cache: dict              # room_id → room dict (trả về kết quả không cần query DB)
    meta: dict = field(default_factory=dict)

    def __len__(self):
        return len(self.id_map)


# ─── Index selection ──────────────────────────────────────────────────────────

def _choose_index(n: int, dim: int) -> faiss.Index:
    """
    Chọn loại index phù hợp với kích thước dataset.
    Tất cả đều dùng METRIC_INNER_PRODUCT vì vector đã được L2-normalize.
    """
    if n < IVF_THRESHOLD:
        # Exact search — không cần train, đảm bảo 100% chính xác
        index = faiss.IndexFlatIP(dim)
        index_type = "FlatIP (exact)"
    else:
        # Approximate search — nhanh hơn nhiều khi N lớn
        nlist = max(1, int(np.sqrt(n) * NLIST_FACTOR))
        nlist = min(nlist, n // 10)  # không quá 1/10 số điểm

        quantizer = faiss.IndexFlatIP(dim)
        index = faiss.IndexIVFFlat(quantizer, dim, nlist, faiss.METRIC_INNER_PRODUCT)
        index.nprobe = DEFAULT_NPROBE
        index_type = f"IVFFlat (nlist={nlist}, nprobe={DEFAULT_NPROBE})"

    print(f"  Index type : {index_type}")
    return index


# ─── Core build ───────────────────────────────────────────────────────────────

def build_index(rooms: list[dict]) -> IndexBundle:
    """
    Pipeline đầy đủ: rooms → vectors → FAISS index → IndexBundle.

    Steps:
      1. Lọc phòng hợp lệ
      2. Extract feature vectors
      3. L2-normalize (để dùng cosine similarity)
      4. Chọn & build FAISS index
      5. Đóng gói vào IndexBundle
    """
    t0 = time.perf_counter()

    # 1. Lọc phòng có đủ thông tin (bỏ qua phòng thiếu field quan trọng)
    valid_rooms = [
        r for r in rooms
        if r.get("price") and r.get("area") and r.get("district")
    ]
    n_skipped = len(rooms) - len(valid_rooms)
    if n_skipped:
        print(f"  Skipped {n_skipped} invalid rooms (missing required fields)")

    if not valid_rooms:
        raise ValueError("No valid rooms to index.")

    # 2. Extract vectors
    extractor = get_extractor()
    matrix = extractor.extract_batch(valid_rooms)   # (N, dim) float32
    dim = matrix.shape[1]

    print(f"  Rooms      : {len(valid_rooms)}")
    print(f"  Vector dim : {dim}")

    # 3. L2-normalize → cosine similarity
    faiss.normalize_L2(matrix)

    # 4. Build index
    index = _choose_index(len(valid_rooms), dim)

    if hasattr(index, "is_trained") and not index.is_trained:
        print("  Training index...")
        t_train = time.perf_counter()
        index.train(matrix)
        print(f"  Training done in {time.perf_counter() - t_train:.2f}s")

    # Thêm vector với ID tăng dần (0, 1, 2, ...)
    index.add(matrix)

    # 5. Đóng gói metadata
    id_map = [r["id"] for r in valid_rooms]
    room_cache = {r["id"]: r for r in valid_rooms}

    elapsed = time.perf_counter() - t0
    meta = {
        "built_at":   time.strftime("%Y-%m-%dT%H:%M:%S"),
        "n_rooms":    len(valid_rooms),
        "vector_dim": dim,
        "index_type": type(index).__name__,
        "build_time_sec": round(elapsed, 3),
    }
    if isinstance(index, faiss.IndexIVFFlat):
        meta["nlist"]  = index.nlist
        meta["nprobe"] = index.nprobe

    print(f"  Build time : {elapsed:.3f}s")
    return IndexBundle(index=index, id_map=id_map, room_cache=room_cache, meta=meta)


# ─── Persistence ──────────────────────────────────────────────────────────────

def save_index(bundle: IndexBundle) -> None:
    """
    Lưu index xuống disk an toàn bằng atomic swap (.tmp → final).
    Đảm bảo app không đọc file đang ghi dở.
    """
    STORAGE_DIR.mkdir(exist_ok=True)

    tmp_index  = INDEX_PATH.with_suffix(".index.tmp")
    tmp_id     = ID_MAP_PATH.with_suffix(".pkl.tmp")
    tmp_cache  = CACHE_PATH.with_suffix(".pkl.tmp")

    # FAISS C++ không xử lý được Unicode path → chdir vào storage/ trước khi ghi
    original_dir = os.getcwd()
    os.chdir(STORAGE_DIR)
    try:
        faiss.write_index(bundle.index, tmp_index.name)
        os.replace(tmp_index.name, INDEX_PATH.name)
    finally:
        os.chdir(original_dir)

    tmp_id.write_bytes(pickle.dumps(bundle.id_map))
    tmp_cache.write_bytes(pickle.dumps(bundle.room_cache))
    os.replace(tmp_id, ID_MAP_PATH)
    os.replace(tmp_cache, CACHE_PATH)

    META_PATH.write_text(
        json.dumps(bundle.meta, ensure_ascii=False, indent=2),
        encoding="utf-8"
    )

    print(f"  Saved to   : {STORAGE_DIR.resolve()}")


def load_index() -> IndexBundle:
    """Load IndexBundle từ disk. Gọi một lần khi app khởi động."""
    if not INDEX_PATH.exists():
        raise FileNotFoundError(
            f"Index not found at {INDEX_PATH}. Run build_and_save() first."
        )

    original_dir = os.getcwd()
    os.chdir(STORAGE_DIR)
    try:
        index = faiss.read_index(INDEX_PATH.name)
    finally:
        os.chdir(original_dir)
    id_map     = pickle.loads(ID_MAP_PATH.read_bytes())
    room_cache = pickle.loads(CACHE_PATH.read_bytes())
    meta       = json.loads(META_PATH.read_text(encoding="utf-8")) if META_PATH.exists() else {}

    # Restore nprobe cho IVF index (không được lưu trong file)
    if isinstance(index, faiss.IndexIVFFlat):
        index.nprobe = meta.get("nprobe", DEFAULT_NPROBE)

    return IndexBundle(index=index, id_map=id_map, room_cache=room_cache, meta=meta)


# ─── High-level API ───────────────────────────────────────────────────────────

def build_and_save(data_path: Path | None = None) -> IndexBundle:
    """
    Pipeline đầy đủ: đọc rooms.json → build → save.
    Đây là hàm được gọi bởi background job rebuild định kỳ.
    """
    if data_path is None:
        data_path = Path(__file__).parent.parent.parent / "data" / "rooms.json"

    print(f"[Indexer] Loading rooms from {data_path.name}...")
    rooms = json.loads(data_path.read_text(encoding="utf-8"))

    print("[Indexer] Building index...")
    bundle = build_index(rooms)

    print("[Indexer] Saving index...")
    save_index(bundle)

    print(f"[Indexer] Done. {len(bundle)} rooms indexed.")
    return bundle


def tune_nprobe(bundle: IndexBundle, target_recall: float = 0.95) -> int:
    """
    Tự động tìm nprobe tối ưu cho IVF index bằng cách so sánh
    kết quả approximate vs exact trên sample nhỏ.

    Chỉ có tác dụng với IndexIVFFlat. Với FlatIP thì luôn recall=1.0.
    """
    if not isinstance(bundle.index, faiss.IndexIVFFlat):
        print("  Index is Flat (exact) — nprobe tuning not needed.")
        return 1

    # IVFFlat khong co DirectMap nen dung room_cache de lay vector truc tiep
    from app.features.extractor import get_extractor
    extractor = get_extractor()
    sample_ids = list(bundle.id_map[:min(200, len(bundle))])
    sample_rooms = [bundle.room_cache[rid] for rid in sample_ids if rid in bundle.room_cache]
    matrix = extractor.extract_batch(sample_rooms).astype(np.float32)
    faiss.normalize_L2(matrix)

    # Tính ground truth bằng FlatIP
    flat = faiss.IndexFlatIP(bundle.index.d)
    flat.add(matrix)
    _, gt = flat.search(matrix, 10)

    best_nprobe = DEFAULT_NPROBE
    for nprobe in [1, 2, 4, 8, 16, 32, 64]:
        if nprobe > bundle.index.nlist:
            break
        bundle.index.nprobe = nprobe
        _, approx = bundle.index.search(matrix, 10)

        # Recall@10: tỉ lệ kết quả đúng
        hits = sum(
            len(set(gt[i]) & set(approx[i])) for i in range(len(matrix))
        )
        recall = hits / (len(matrix) * 10)
        print(f"  nprobe={nprobe:3d}  recall@10={recall:.3f}")

        if recall >= target_recall:
            best_nprobe = nprobe
            break

    bundle.index.nprobe = best_nprobe
    print(f"  → Best nprobe: {best_nprobe} (recall >= {target_recall})")
    return best_nprobe


# ─── Singleton (dùng trong app) ───────────────────────────────────────────────

_bundle: IndexBundle | None = None

def get_index() -> IndexBundle:
    """Trả về IndexBundle đã load. Load lazily lần đầu tiên."""
    global _bundle
    if _bundle is None:
        _bundle = load_index()
    return _bundle

def reload_index() -> None:
    """Gọi sau khi background job rebuild xong để app dùng index mới."""
    global _bundle
    _bundle = load_index()


# ─── CLI / Quick test ─────────────────────────────────────────────────────────

if __name__ == "__main__":
    import sys
    sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

    print("=" * 50)
    print("BUILD")
    print("=" * 50)
    bundle = build_and_save()

    print()
    print("=" * 50)
    print("METADATA")
    print("=" * 50)
    for k, v in bundle.meta.items():
        print(f"  {k:<20}: {v}")

    print()
    print("=" * 50)
    print("NPROBE TUNING")
    print("=" * 50)
    tune_nprobe(bundle, target_recall=0.95)

    print()
    print("=" * 50)
    print("SEARCH TEST")
    print("=" * 50)
    # Lấy vector của phòng đầu tiên làm query
    extractor = get_extractor()
    sample_room = json.loads(
        (Path(__file__).parent.parent.parent / "data" / "rooms.json")
        .read_text(encoding="utf-8")
    )[0]
    query_vec = extractor.extract(sample_room).reshape(1, -1)
    faiss.normalize_L2(query_vec)

    D, I = bundle.index.search(query_vec, k=6)

    print(f"Query: {sample_room['title']} | {sample_room['district']} | {sample_room['price']:,} VND")
    print(f"Top 5 similar rooms (bỏ qua vị trí 0 = chính nó):")
    for rank, (pos, score) in enumerate(zip(I[0][1:6], D[0][1:6]), 1):
        room = bundle.room_cache[bundle.id_map[pos]]
        print(f"  [{rank}] score={score:.4f}  {room['title']:<40} {room['district']:<15} {room['price']:>12,} VND")

    print()
    print("=" * 50)
    print("LOAD TEST")
    print("=" * 50)
    t = time.perf_counter()
    loaded = load_index()
    print(f"  Load time  : {time.perf_counter() - t:.4f}s")
    print(f"  Rooms      : {len(loaded)}")
    print(f"  Index type : {type(loaded.index).__name__}")
