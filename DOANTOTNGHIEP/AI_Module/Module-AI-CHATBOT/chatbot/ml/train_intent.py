"""Huấn luyện PhoBERT intent classifier — SPEC §11 bước 2.1.

Dữ liệu train = synthetic (`data/intent_train.jsonl`) + DỮ LIỆU THẬT đã đánh nhãn
(`data/intent_train_real.jsonl`, sinh bởi `data/split_real_data.py`).

Đánh giá trên 2 bộ, in riêng để so sánh:
  - GOLD  (`data/intent_test_gold.jsonl`): data THẬT held-out — tín hiệu chính.
  - PROXY (`data/intent_test_real.jsonl`): bộ tay viết cũ (xem cảnh báo "PROXY"
    trong `data/build_proxy_testset.py`, chưa phải dữ liệu thật thu thập).
Model dùng GOLD để chọn best checkpoint (metric_for_best_model=f1).

Lưu ý độ phủ tập GOLD: không có calculate_cost (0 câu thật) và rất ít
policy_inquiry/out_of_scope -> các lớp này vẫn chủ yếu dựa vào PROXY.

Chạy:
    python data/split_real_data.py                # (chạy trước) tách train/test thật
    python train_intent.py                        # full training
    python train_intent.py --max-train 100 --epochs 1 --output-dir out-intent-smoke
"""

import argparse
import json
from pathlib import Path

import evaluate
import numpy as np
from datasets import Dataset
from transformers import (
    AutoModelForSequenceClassification,
    AutoTokenizer,
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
)

from vncorenlp_util import get_segmenter, segment_plain

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"
VNCORENLP_DIR = ROOT / "vncorenlp"
MODEL_NAME = "vinai/phobert-base-v2"

LABELS = [
    "search_room", "refine_search", "room_detail", "compare_rooms",
    "book_appointment", "calculate_cost", "policy_inquiry", "out_of_scope",
]
LABEL2ID = {l: i for i, l in enumerate(LABELS)}
ID2LABEL = {i: l for i, l in enumerate(LABELS)}


def load_jsonl(path: Path):
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def load_jsonl_optional(path: Path, what: str):
    """Đọc file nếu có; nếu thiếu thì cảnh báo và trả [] để pipeline không gãy."""
    if not path.exists():
        print(f"[CANH BAO] Thieu {path.name} ({what}) -> bo qua. "
              f"Chay 'python data/split_real_data.py' de sinh data that.")
        return []
    return load_jsonl(path)


def to_dataset(segmenter, rows):
    texts = [segment_plain(segmenter, r["text"]) for r in rows]
    labels = [LABEL2ID[r["intent"]] for r in rows]
    return Dataset.from_dict({"text": texts, "label": labels})


def make_training_args(output_dir, num_epochs, batch_size):
    common = dict(
        output_dir=output_dir,
        num_train_epochs=num_epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size * 2,
        learning_rate=2e-5,
        save_strategy="epoch",
        load_best_model_at_end=True,
        metric_for_best_model="f1",
        logging_steps=20,
        report_to=[],
    )
    try:
        return TrainingArguments(eval_strategy="epoch", **common)
    except TypeError:
        return TrainingArguments(evaluation_strategy="epoch", **common)


def main(max_train, num_epochs, output_dir, batch_size):
    segmenter = get_segmenter(VNCORENLP_DIR)

    # Train = synthetic + data thật (nếu có). Data thật để ở file riêng nên
    # generate_dataset.py chạy lại KHONG ghi de len no.
    synth_rows = load_jsonl(DATA_DIR / "intent_train.jsonl")
    real_train_rows = load_jsonl_optional(DATA_DIR / "intent_train_real.jsonl", "data that train")
    train_rows = synth_rows + real_train_rows
    if max_train:
        train_rows = train_rows[:max_train]

    gold_rows = load_jsonl_optional(DATA_DIR / "intent_test_gold.jsonl", "test that held-out")
    proxy_rows = load_jsonl(DATA_DIR / "intent_test_real.jsonl")
    # GOLD (data thật) là bộ đánh giá chính; nếu chưa có thì tạm dùng PROXY.
    eval_rows = gold_rows if gold_rows else proxy_rows
    print(f"Train: {len(train_rows)} cau (synthetic {len(synth_rows)} + that {len(real_train_rows)}) "
          f"| eval chinh: {'GOLD' if gold_rows else 'PROXY'} {len(eval_rows)} cau")

    train_ds = to_dataset(segmenter, train_rows)
    test_ds = to_dataset(segmenter, eval_rows)
    proxy_ds = to_dataset(segmenter, proxy_rows)

    tokenizer = AutoTokenizer.from_pretrained(MODEL_NAME)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_NAME, num_labels=len(LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )

    def prep(batch):
        return tokenizer(batch["text"], truncation=True, max_length=64)

    train_ds = train_ds.map(prep, batched=True)
    test_ds = test_ds.map(prep, batched=True)
    proxy_ds = proxy_ds.map(prep, batched=True)

    acc_metric = evaluate.load("accuracy")
    f1_metric = evaluate.load("f1")

    def compute_metrics(pred):
        preds = np.argmax(pred.predictions, axis=1)
        return {
            **acc_metric.compute(predictions=preds, references=pred.label_ids),
            **f1_metric.compute(predictions=preds, references=pred.label_ids, average="macro"),
        }

    trainer = Trainer(
        model=model,
        args=make_training_args(output_dir, num_epochs, batch_size),
        train_dataset=train_ds,
        eval_dataset=test_ds,
        data_collator=DataCollatorWithPadding(tokenizer),
        compute_metrics=compute_metrics,
    )
    trainer.train()

    eval_name = "GOLD (data that held-out)" if gold_rows else "PROXY (tam thay GOLD)"
    metrics = trainer.evaluate()
    print(f"=== Ket qua tren {eval_name} — bo danh gia CHINH ===")
    print(metrics)

    # Luon bao cao them tren PROXY de so sanh (bo cu tay viet).
    proxy_metrics = trainer.evaluate(proxy_ds, metric_key_prefix="proxy")
    print("=== Ket qua tren intent_test_real.jsonl (PROXY, xem build_proxy_testset.py) ===")
    print(proxy_metrics)

    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    return metrics


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    parser = argparse.ArgumentParser()
    parser.add_argument("--max-train", type=int, default=None, help="Gioi han so cau train (dung cho smoke test)")
    parser.add_argument("--epochs", type=int, default=5)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--output-dir", default=str(ROOT / "out-intent"))
    args = parser.parse_args()
    main(args.max_train, args.epochs, args.output_dir, args.batch_size)
