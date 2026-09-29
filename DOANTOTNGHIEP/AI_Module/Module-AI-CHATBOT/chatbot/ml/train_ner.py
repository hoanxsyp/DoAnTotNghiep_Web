"""Huấn luyện ViSoBERT NER (BIO) — SPEC §11 bước 2.2.

Base model `uitnlp/visobert`. Cách encode (xem `nlu_encoding.py`): đưa văn bản
THÔ vào fast tokenizer với `offset_mapping`, rồi gán nhãn BIO trực tiếp lên từng
subword theo overlap offset. Không cần word-segment.

Dữ liệu nguồn (`data/*.jsonl`) lưu entity theo OFFSET KÝ TỰ trên văn bản gốc
(xem `generate_dataset.py`) — độc lập với cách tokenize.

LƯU Ý khi đọc số: F1 seqeval in ra dưới đây tính theo ĐƠN VỊ SUBWORD, không phải
theo entity trên văn bản gốc. Số dùng để báo cáo là **Span-F1 strict** (khớp đúng
label+start+end trên offset ký tự) do `eval_nlu_compare.py` đo.

Chạy:
    python train_ner.py
    python train_ner.py --max-train 100 --epochs 1 --output-dir out-ner-smoke
"""

import argparse
import json
from pathlib import Path

import evaluate
import numpy as np
from datasets import Dataset
from transformers import (
    AutoModelForTokenClassification,
    AutoTokenizer,
    DataCollatorForTokenClassification,
    Trainer,
    TrainingArguments,
)

from nlu_encoding import (
    CONFIG_MAX_LEN,
    DEFAULT_BASE_MODEL,
    MAX_LENGTH,
    bio_labels_from_offsets,
    encode_with_offsets,
)

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"

ENTITY_LABELS = [
    "PRICE_MAX", "PRICE_MIN", "LOCATION", "POI", "RADIUS", "AREA_MIN",
    "UTILITY", "ROOM_TYPE", "DATETIME", "ROOM_REF",
]
BIO_LABELS = ["O"] + [f"{p}-{l}" for l in ENTITY_LABELS for p in ("B", "I")]
LABEL2ID = {l: i for i, l in enumerate(BIO_LABELS)}
ID2LABEL = {i: l for i, l in enumerate(BIO_LABELS)}


def load_jsonl(path: Path):
    with path.open(encoding="utf-8") as f:
        return [json.loads(line) for line in f]


def build_dataset(tokenizer, rows, max_length=MAX_LENGTH):
    """Encode + gán nhãn trong CÙNG một bước (cần `text` để co span khoảng trắng)."""
    cols = {"input_ids": [], "attention_mask": [], "labels": []}
    for r in rows:
        enc, spans = encode_with_offsets(tokenizer, r["text"], max_length)
        cols["input_ids"].append(enc["input_ids"])
        cols["attention_mask"].append(enc["attention_mask"])
        cols["labels"].append(bio_labels_from_offsets(spans, r["entities"], LABEL2ID))
    return Dataset.from_dict(cols)


def make_training_args(output_dir, num_epochs, batch_size):
    common = dict(
        output_dir=output_dir,
        num_train_epochs=num_epochs,
        per_device_train_batch_size=batch_size,
        per_device_eval_batch_size=batch_size * 2,
        learning_rate=3e-5,
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


def main(max_train, num_epochs, output_dir, batch_size, base_model, max_length):
    print(f"Base model: {base_model} | max_length: {max_length}")

    train_rows = load_jsonl(DATA_DIR / "ner_train.jsonl")
    test_rows = load_jsonl(DATA_DIR / "ner_test_real.jsonl")
    if max_train:
        train_rows = train_rows[:max_train]

    tokenizer = AutoTokenizer.from_pretrained(base_model)
    if not tokenizer.is_fast:
        raise RuntimeError(
            f"{base_model} khong co fast tokenizer nen khong lay duoc "
            "offset_mapping. Voi ViSoBERT, fast tokenizer duoc convert tu "
            "sentencepiece.bpe.model nen can ca 'sentencepiece' lan 'protobuf' "
            "(xem requirements.txt)."
        )
    model = AutoModelForTokenClassification.from_pretrained(
        base_model, num_labels=len(BIO_LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )
    # nlu-service đọc khoá này từ config.json để encode y hệt lúc train.
    setattr(model.config, CONFIG_MAX_LEN, max_length)

    train_ds = build_dataset(tokenizer, train_rows, max_length)
    test_ds = build_dataset(tokenizer, test_rows, max_length)

    seqeval = evaluate.load("seqeval")

    def compute_metrics(pred):
        preds = np.argmax(pred.predictions, axis=2)
        true_labels, true_preds = [], []
        for pred_row, label_row in zip(preds, pred.label_ids):
            cur_labels, cur_preds = [], []
            for p, l in zip(pred_row, label_row):
                if l == -100:
                    continue
                cur_labels.append(ID2LABEL[l])
                cur_preds.append(ID2LABEL[p])
            true_labels.append(cur_labels)
            true_preds.append(cur_preds)
        result = seqeval.compute(predictions=true_preds, references=true_labels)
        return {
            "precision": result["overall_precision"],
            "recall": result["overall_recall"],
            "f1": result["overall_f1"],
            "accuracy": result["overall_accuracy"],
        }

    trainer = Trainer(
        model=model,
        args=make_training_args(output_dir, num_epochs, batch_size),
        train_dataset=train_ds,
        eval_dataset=test_ds,
        data_collator=DataCollatorForTokenClassification(tokenizer),
        compute_metrics=compute_metrics,
    )
    trainer.train()
    metrics = trainer.evaluate()
    print("=== Ket qua tren ner_test_real.jsonl (PROXY, xem canh bao trong build_proxy_testset.py) ===")
    print("(F1 theo don vi SUBWORD — so de bao cao la Span-F1 cua eval_nlu_compare.py)")
    print(metrics)
    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    return metrics


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    parser = argparse.ArgumentParser()
    parser.add_argument("--max-train", type=int, default=None, help="Gioi han so cau train (dung cho smoke test)")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--output-dir", default=str(ROOT / "out-ner"))
    parser.add_argument("--base-model", default=DEFAULT_BASE_MODEL,
                        help=f"mac dinh {DEFAULT_BASE_MODEL}; model khac phai co fast tokenizer")
    parser.add_argument("--max-length", type=int, default=MAX_LENGTH,
                        help=f"mac dinh {MAX_LENGTH}; nang len 128/256 khi train tren data that dai")
    args = parser.parse_args()
    main(args.max_train, args.epochs, args.output_dir, args.batch_size, args.base_model,
         args.max_length)
