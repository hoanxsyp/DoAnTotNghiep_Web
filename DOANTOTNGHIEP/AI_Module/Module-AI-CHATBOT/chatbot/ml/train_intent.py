"""Huấn luyện PhoBERT intent classifier — SPEC §11 bước 2.1.

Base model `vinai/phobert-base-v2`. Câu được word-segment bằng VnCoreNLP trước
khi tokenize (xem `phobert_encoding.py`) — PhoBERT được pretrain trên văn bản
đã segment. Chọn PhoBERT thay ViSoBERT theo `eval-results/backbone_compare_full.md`.

Dữ liệu train = synthetic (`data/intent_train_v2.jsonl`) + DỮ LIỆU THẬT đã đánh
nhãn (`data/intent_train_real.jsonl`, sinh bởi `data/split_real_data.py`) nhân
`--real-repeat` lần.

Đánh giá trên 2 bộ, in riêng để so sánh:
  - GOLD  (`data/intent_test_gold.jsonl`): data THẬT held-out — tín hiệu chính.
  - PROXY (`data/intent_test_real.jsonl`): bộ tay viết cũ (xem cảnh báo "PROXY"
    trong `data/build_proxy_testset.py`, chưa phải dữ liệu thật thu thập).
Lấy model ở epoch CUỐI — KHÔNG chọn checkpoint theo GOLD (chọn trên bộ test làm
số đo lạc quan hơn thực tế). GOLD chỉ được xem mỗi epoch để theo dõi.

6 intent: calculate_cost / policy_inquiry đã gộp vào room_detail (0 và 1 câu
thật, không đủ học riêng — xem MERGED_INTENTS trong data/generate_dataset_v2.py).

Lưu ý độ phủ tập GOLD: rất ít out_of_scope (1 câu) -> lớp này vẫn chủ yếu dựa
vào PROXY. GOLD chỉ 28 câu nên 1 câu = 3,6 điểm accuracy — chênh lệch 1-2 câu
giữa 2 lần train là NHIỄU, không phải cải thiện.

Chạy:
    python data/split_real_data.py                # (chạy trước) tách train/test thật
    python train_intent.py
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
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
)

from nlu_encoding import CONFIG_MAX_LEN, DEFAULT_BASE_MODEL, MAX_LENGTH
from phobert_encoding import CONFIG_ENCODING, ENCODING_NAME, PhoBertEncoder

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"

LABELS = [
    "search_room", "refine_search", "room_detail", "compare_rooms",
    "book_appointment", "out_of_scope",
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


def to_dataset(encoder, rows, max_length):
    cols = {"input_ids": [], "attention_mask": [], "label": []}
    for r in rows:
        ids, mask = encoder.intent(r["text"], max_length)
        cols["input_ids"].append(ids)
        cols["attention_mask"].append(mask)
        cols["label"].append(LABEL2ID[r["intent"]])
    return Dataset.from_dict(cols)


def make_training_args(output_dir, num_epochs, batch_size, learning_rate, seed):
    common = dict(
        output_dir=output_dir,
        num_train_epochs=num_epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size * 2,
        learning_rate=learning_rate,
        # Không lưu checkpoint: mỗi cái ~1.6GB và ta chỉ cần model cuối.
        save_strategy="no",
        logging_steps=20,
        report_to=[],
        seed=seed,
    )
    try:
        return TrainingArguments(eval_strategy="epoch", **common)
    except TypeError:
        return TrainingArguments(evaluation_strategy="epoch", **common)


def main(max_train, num_epochs, output_dir, batch_size, base_model, max_length,
         learning_rate, real_repeat, synth_file, seed):
    print(f"Base model: {base_model} | max_length: {max_length} | lr: {learning_rate} "
          f"| real_repeat: {real_repeat} | synth: {synth_file} | seed: {seed}")

    # Train = synthetic + data thật (nếu có). Data thật để ở file riêng nên
    # generate_dataset*.py chạy lại KHONG ghi de len no.
    synth_rows = load_jsonl(DATA_DIR / synth_file)
    real_train_rows = load_jsonl_optional(DATA_DIR / "intent_train_real.jsonl", "data that train")
    # Data that it hon synthetic ~22 lan nen chim mat trong loss; --real-repeat
    # nhan ban len de no co trong so thuc su.
    train_rows = synth_rows + real_train_rows * real_repeat
    if max_train:
        train_rows = train_rows[:max_train]

    gold_rows = load_jsonl_optional(DATA_DIR / "intent_test_gold.jsonl", "test that held-out")
    proxy_rows = load_jsonl(DATA_DIR / "intent_test_real.jsonl")
    # GOLD (data thật) là bộ đánh giá chính; nếu chưa có thì tạm dùng PROXY.
    eval_rows = gold_rows if gold_rows else proxy_rows
    print(f"Train: {len(train_rows)} cau (synthetic {len(synth_rows)} + that {len(real_train_rows)}"
          f"x{real_repeat}) | eval chinh: {'GOLD' if gold_rows else 'PROXY'} {len(eval_rows)} cau")

    encoder = PhoBertEncoder.from_pretrained(base_model)
    tokenizer = encoder.tok
    model = AutoModelForSequenceClassification.from_pretrained(
        base_model, num_labels=len(LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )
    # nlu-service đọc 2 khoá này từ config.json để encode y hệt lúc train.
    setattr(model.config, CONFIG_MAX_LEN, max_length)
    setattr(model.config, CONFIG_ENCODING, ENCODING_NAME)

    train_ds = to_dataset(encoder, train_rows, max_length)
    test_ds = to_dataset(encoder, eval_rows, max_length)
    proxy_ds = to_dataset(encoder, proxy_rows, max_length)
    if encoder.fallbacks:
        print(f"[CANH BAO] {encoder.fallbacks} cau VnCoreNLP khong do lai duoc offset -> tach theo khoang trang")

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
        args=make_training_args(output_dir, num_epochs, batch_size, learning_rate, seed),
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
    preds = np.argmax(trainer.predict(test_ds).predictions, axis=1)
    for r, p in zip(eval_rows, preds):
        if LABELS[p] != r["intent"]:
            print(f"   SAI: {r['text'][:70]!r} | dung={r['intent']} doan={LABELS[p]}")

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
    parser.add_argument("--base-model", default=DEFAULT_BASE_MODEL,
                        help=f"mac dinh {DEFAULT_BASE_MODEL}")
    parser.add_argument("--max-length", type=int, default=MAX_LENGTH)
    parser.add_argument("--learning-rate", type=float, default=2e-5)
    parser.add_argument("--synth-file", default="intent_train_v2.jsonl",
                        help="file synthetic (intent_train.jsonl = bo v1 van phong sach)")
    parser.add_argument("--real-repeat", type=int, default=10,
                        help="nhan ban data that N lan de tang trong so (mac dinh 10)")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    main(args.max_train, args.epochs, args.output_dir, args.batch_size, args.base_model,
         args.max_length, args.learning_rate, args.real_repeat, args.synth_file, args.seed)
