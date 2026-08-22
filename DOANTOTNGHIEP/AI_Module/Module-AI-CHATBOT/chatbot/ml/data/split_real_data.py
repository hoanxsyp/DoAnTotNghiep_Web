"""Tách DỮ LIỆU THẬT (đã đánh nhãn) thành train/test cho intent.

Nguồn (data thật thu thập từ post/chat, KHÁC với intent_train.jsonl synthetic):
  - intent_data_md_labeled.jsonl   (56 câu, toàn search_room)
  - intent_data2_labeled.jsonl     (87 câu, 4 intent hội thoại + vài out_of_scope/policy)

Xuất:
  intent_train_real.jsonl  -> phần train (nối vào synthetic khi train)
  intent_test_gold.jsonl   -> tập test THẬT held-out (đánh giá model)

## TẬP GOLD ĐƯỢC "ĐÓNG BĂNG" (mặc định)

`intent_test_gold.jsonl` là benchmark cố định. Một khi đã tồn tại, chạy lại script
này KHÔNG xáo lại nó: chỉ dựng lại `intent_train_real.jsonl` = toàn bộ data thật
TRỪ các câu đã nằm trong GOLD (đối chiếu theo `text`). Nhờ vậy khi bổ sung data
thật mới, dữ liệu mới CHỈ chảy vào train — GOLD giữ nguyên nên số DoD đo qua các
lần train so sánh được với nhau (không lẫn "đổi test set" vào "thêm data").

Tạo GOLD lần đầu (hoặc dựng lại benchmark từ đầu) bằng `--refreeze`: tách phân tầng
(stratified) theo intent, seed cố định -> tái lập được, giữ ~20% mỗi intent làm test.

    python data/split_real_data.py              # mặc định: GIỮ NGUYÊN gold, chỉ build lại train
    python data/split_real_data.py --refreeze   # dựng lại gold từ đầu (ghi đè benchmark cũ!)
"""
import argparse
import json
import random
from collections import defaultdict, Counter
from pathlib import Path

HERE = Path(__file__).parent
SOURCES = ["intent_data_md_labeled.jsonl", "intent_data2_labeled.jsonl"]
GOLD_PATH = HERE / "intent_test_gold.jsonl"
TRAIN_PATH = HERE / "intent_train_real.jsonl"
TEST_RATIO = 0.20
SEED = 42


def load_jsonl(path):
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def write_jsonl(path, rows):
    with path.open("w", encoding="utf-8") as f:
        for r in rows:
            f.write(json.dumps(r, ensure_ascii=False) + "\n")


def load_sources():
    rows = []
    for name in SOURCES:
        rows += load_jsonl(HERE / name)
    return rows


def stratified_split(rows):
    """Tách phân tầng theo intent (chỉ dùng khi tạo/refreeze GOLD)."""
    by_intent = defaultdict(list)
    for r in rows:
        by_intent[r["intent"]].append(r)

    rng = random.Random(SEED)
    train, test = [], []
    for intent, items in sorted(by_intent.items()):
        items = items[:]
        rng.shuffle(items)
        n_test = round(len(items) * TEST_RATIO)
        # >=2 mẫu thì giữ tối thiểu 1 câu test; lớp quá ít (1 mẫu) dồn vào train
        if len(items) >= 2:
            n_test = max(1, n_test)
        test += items[:n_test]
        train += items[n_test:]

    rng.shuffle(train)
    rng.shuffle(test)
    return train, test


def build_frozen(rows, gold_rows):
    """GOLD giữ nguyên; train = toàn bộ data thật trừ các câu có text trùng GOLD."""
    gold_texts = {r["text"] for r in gold_rows}
    train = [r for r in rows if r["text"] not in gold_texts]

    # Cảnh báo nếu có câu GOLD không còn trong nguồn (nguồn bị sửa/xóa sau khi
    # đóng băng) — GOLD vẫn đứng độc lập, chỉ báo để biết có lệch.
    src_texts = {r["text"] for r in rows}
    missing = [r["text"] for r in gold_rows if r["text"] not in src_texts]
    if missing:
        print(f"[CANH BAO] {len(missing)} cau GOLD khong con trong nguon "
              f"(vd: {missing[0][:40]!r}). GOLD van giu nguyen (da dong bang).")
    return train


def main(refreeze):
    rows = load_sources()

    if refreeze or not GOLD_PATH.exists():
        if refreeze and GOLD_PATH.exists():
            print("[REFREEZE] Dung lai GOLD tu dau — GHI DE benchmark cu.")
        else:
            print("[INIT] Chua co GOLD — tao lan dau bang stratified split.")
        train, gold = stratified_split(rows)
        write_jsonl(GOLD_PATH, gold)
    else:
        gold = load_jsonl(GOLD_PATH)
        print(f"[FROZEN] Giu nguyen GOLD hien co ({len(gold)} cau) — chi build lai train.")
        train = build_frozen(rows, gold)

    write_jsonl(TRAIN_PATH, train)

    print(f"Tong data that: {len(rows)}  ->  train {len(train)} | gold {len(gold)}")
    print("Train theo intent:", dict(Counter(r['intent'] for r in train)))
    print("Gold  theo intent:", dict(Counter(r['intent'] for r in gold)))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--refreeze", action="store_true",
        help="Dung lai tap GOLD tu dau (ghi de benchmark cu). Mac dinh: giu nguyen GOLD.")
    args = parser.parse_args()
    main(args.refreeze)
