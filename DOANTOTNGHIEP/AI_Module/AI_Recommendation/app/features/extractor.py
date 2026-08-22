"""
Config-driven feature extractor.

Thêm field mới: chỉ cần cập nhật feature_config.json, không sửa file này.
Khi config thay đổi → vector dim thay đổi → chạy lại indexer để rebuild FAISS.
"""

import json
import numpy as np
from pathlib import Path

CONFIG_PATH = Path(__file__).parent / "feature_config.json"


class FeatureExtractor:

    def __init__(self, config_path: Path = CONFIG_PATH):
        self.config = json.loads(config_path.read_text(encoding="utf-8"))
        self._build_lookups()

    def _build_lookups(self):
        cfg = self.config

        # lookup index cho categorical và multi_label
        self._cat_lookup  = {
            f["field"]: {v: i for i, v in enumerate(f["vocab"])}
            for f in cfg["categorical"]
        }
        self._ml_lookup = {
            f["field"]: {v: i for i, v in enumerate(f["vocab"])}
            for f in cfg["multi_label"]
        }

        # tính tổng số chiều
        n_num  = len(cfg["numerical"])
        n_cat  = sum(len(f["vocab"]) for f in cfg["categorical"])
        n_ml   = sum(len(f["vocab"]) for f in cfg["multi_label"])
        self.vector_dim = n_num + n_cat + n_ml

    # ─── Public API ───────────────────────────────────────────────────────────

    def extract(self, room: dict) -> np.ndarray:
        """Room dict → vector float32."""
        parts = []

        for f in self.config["numerical"]:
            raw = float(room.get(f["field"], 0) or 0)
            norm = (raw - f["min"]) / (f["max"] - f["min"])
            parts.append(np.clip(norm, 0.0, 1.0) * f["weight"])

        for f in self.config["categorical"]:
            lookup = self._cat_lookup[f["field"]]
            one_hot = np.zeros(len(lookup), dtype=np.float32)
            val = room.get(f["field"], "")
            if val in lookup:
                one_hot[lookup[val]] = f["weight"]
            parts.append(one_hot)

        for f in self.config["multi_label"]:
            lookup = self._ml_lookup[f["field"]]
            binary = np.zeros(len(lookup), dtype=np.float32)
            for val in room.get(f["field"], []):
                if val in lookup:
                    binary[lookup[val]] = f["weight"]
            parts.append(binary)

        scalars = [p for p in parts if np.isscalar(p)]
        arrays  = [p for p in parts if not np.isscalar(p)]
        vec = np.concatenate(
            [np.array(scalars, dtype=np.float32)] + arrays
        )
        return vec.astype(np.float32)

    def extract_batch(self, rooms: list[dict]) -> np.ndarray:
        """Danh sách room → ma trận (N, dim) float32."""
        return np.stack([self.extract(r) for r in rooms]).astype(np.float32)

    def get_feature_names(self) -> list[str]:
        """Tên từng chiều — dùng để debug."""
        names = []
        for f in self.config["numerical"]:
            names.append(f["field"] + "_norm")
        for f in self.config["categorical"]:
            for v in f["vocab"]:
                names.append(f"{f['field']}_{v}")
        for f in self.config["multi_label"]:
            for v in f["vocab"]:
                names.append(f"{f['field']}_{v}")
        return names

    def update_vocab(self, rooms: list[dict], save: bool = True):
        """
        Tự động cập nhật vocab trong config từ data thực tế.
        Gọi hàm này khi BE gửi data mới có district/amenities chưa có trong config.
        """
        cfg = self.config

        for f in cfg["categorical"]:
            seen = sorted(set(
                r[f["field"]] for r in rooms
                if f["field"] in r and r[f["field"]]
            ))
            new_vals = [v for v in seen if v not in set(f["vocab"])]
            if new_vals:
                f["vocab"] = sorted(set(f["vocab"]) | set(seen))
                print(f"[vocab update] {f['field']}: +{len(new_vals)} values → {new_vals}")

        for f in cfg["multi_label"]:
            seen = sorted(set(
                v for r in rooms
                for v in r.get(f["field"], [])
            ))
            new_vals = [v for v in seen if v not in set(f["vocab"])]
            if new_vals:
                f["vocab"] = sorted(set(f["vocab"]) | set(seen))
                print(f"[vocab update] {f['field']}: +{len(new_vals)} values → {new_vals}")

        if save:
            CONFIG_PATH.write_text(
                json.dumps(cfg, ensure_ascii=False, indent=2),
                encoding="utf-8"
            )
            self._build_lookups()
            print(f"Config saved. New vector dim: {self.vector_dim}")


# Singleton — dùng chung toàn app, tránh đọc file nhiều lần
_extractor: FeatureExtractor | None = None

def get_extractor() -> FeatureExtractor:
    global _extractor
    if _extractor is None:
        _extractor = FeatureExtractor()
    return _extractor


# ─── Quick test ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    import sys
    sys.stdout = open(sys.stdout.fileno(), mode="w", encoding="utf-8", buffering=1)

    data_path = Path(__file__).parent.parent.parent / "data" / "rooms.json"
    rooms = json.loads(data_path.read_text(encoding="utf-8"))

    ext = FeatureExtractor()
    matrix = ext.extract_batch(rooms)

    print(f"Rooms loaded  : {len(rooms)}")
    print(f"Matrix shape  : {matrix.shape}")
    print(f"Vector dim    : {ext.vector_dim}")
    print(f"NaN present   : {np.isnan(matrix).any()}")
    print(f"All finite    : {np.isfinite(matrix).all()}")
    print()
    print("Feature names:")
    for i, name in enumerate(ext.get_feature_names()):
        print(f"  [{i:2d}] {name}")
    print()

    sample = rooms[0]
    vec = ext.extract(sample)
    print(f"Sample: {sample['title']}")
    print(f"  vector[:5] = {vec[:5]}")
    print(f"  non-zero dims: {np.nonzero(vec)[0].tolist()}")
