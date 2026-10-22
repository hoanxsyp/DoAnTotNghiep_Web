"""Huấn luyện PhoBERT NER (BIO) — SPEC §11 bước 2.2.

Base model `vinai/phobert-base-v2`. Cách encode (xem `phobert_encoding.py`):
word-segment bằng VnCoreNLP, mỗi từ tách BPE, nhãn BIO gán cho subword ĐẦU của
mỗi từ theo overlap offset ký tự với entity.

Dữ liệu nguồn (`data/*.jsonl`) lưu entity theo OFFSET KÝ TỰ trên văn bản gốc
(xem `generate_dataset_v2.py`) — độc lập với cách tokenize.

Lấy model ở epoch CUỐI (không chọn checkpoint trên bộ test — bộ test chỉ để
xem). Cấu hình mặc định = cấu hình đã so sánh trong `compare_backbones.py`.

Dữ liệu: train = synthetic (`ner_train_v2.jsonl`) + data THẬT phần train
(`ner_train_real.jsonl`, sinh bởi `data/split_real_data.py`). Đánh giá trên 2 bộ:
  - GOLD  (`ner_test_gold.jsonl`): câu THẬT held-out, cùng tập câu với GOLD intent
    — tín hiệu chính cho DoD-2.
  - PROXY (`ner_test_real.jsonl`): bộ tay viết cũ, giữ để so với các lần đo trước.

Số báo cáo là **Span-F1 strict** (khớp label+start+end trên văn bản gốc) in ở
cuối, kèm F1 theo từng nhãn và danh sách span sai trên GOLD. F1 seqeval in mỗi
epoch tính theo TỪ, chỉ để theo dõi.

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
    DataCollatorForTokenClassification,
    Trainer,
    TrainingArguments,
)

from nlu_encoding import (
    CONFIG_MAX_LEN,
    DEFAULT_BASE_MODEL,
    MAX_LENGTH,
    bio_labels_from_offsets,
    decode_bio_offsets,
    span_prf,
)
from phobert_encoding import CONFIG_ENCODING, ENCODING_NAME, PhoBertEncoder

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


def load_jsonl_optional(path: Path, what: str):
    """Data that co the chua ton tai -> canh bao thay vi chet."""
    if not path.exists():
        print(f"[CANH BAO] Khong thay {path.name} ({what}) -> train KHONG co data that.")
        return []
    return load_jsonl(path)


def build_dataset(encoder, rows, max_length=MAX_LENGTH):
    """Encode + gán nhãn; trả kèm `spans` từng câu để decode lại ra span ký tự."""
    cols = {"input_ids": [], "attention_mask": [], "labels": []}
    all_spans = []
    for r in rows:
        ids, mask, spans = encoder.ner(r["text"], max_length)
        cols["input_ids"].append(ids)
        cols["attention_mask"].append(mask)
        cols["labels"].append(bio_labels_from_offsets(spans, r["entities"], LABEL2ID))
        all_spans.append(spans)
    return Dataset.from_dict(cols), all_spans


def predict_spans(trainer, ds, rows, spans_list):
    logits = trainer.predict(ds).predictions
    preds = []
    for r, spans, lg in zip(rows, spans_list, logits):
        labels = [ID2LABEL[int(i)] for i in np.argmax(lg[: len(spans)], axis=-1)]
        preds.append(decode_bio_offsets(r["text"], spans, labels))
    return preds


def per_label_f1(rows, preds):
    out = {}
    for lab in ENTITY_LABELS:
        g = [{**r, "entities": [e for e in r["entities"] if e["label"] == lab]} for r in rows]
        p = [[e for e in pr if e["label"] == lab] for pr in preds]
        n_gold = sum(len(r["entities"]) for r in g)
        if n_gold or any(p):
            m = span_prf(g, p)
            out[lab] = {"f1": round(m["f1"], 3), "gold": n_gold, "tp": m["tp"], "fp": m["fp"], "fn": m["fn"]}
    return out


def print_errors(rows, preds):
    for r, pr in zip(rows, preds):
        gold = {(e["label"], e["start"], e["end"]) for e in r["entities"]}
        got = {(e["label"], e["start"], e["end"]) for e in pr}
        if gold == got:
            continue
        t = r["text"]
        miss = [f"{t[s:e]!r}/{l}" for l, s, e in sorted(gold - got, key=lambda x: x[1])]
        extra = [f"{t[s:e]!r}/{l}" for l, s, e in sorted(got - gold, key=lambda x: x[1])]
        print(f"   {t[:70]!r}")
        print(f"      thieu: {miss}")
        print(f"      thua : {extra}")


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

    # Train = synthetic + phan train cua DU LIEU THAT (GOLD da tach rieng).
    synth_rows = load_jsonl(DATA_DIR / synth_file)
    real_rows = load_jsonl_optional(DATA_DIR / "ner_train_real.jsonl", "data that NER train")
    train_rows = synth_rows + real_rows * real_repeat
    gold_rows = load_jsonl_optional(DATA_DIR / "ner_test_gold.jsonl", "test NER that held-out")
    proxy_rows = load_jsonl(DATA_DIR / "ner_test_real.jsonl")
    # GOLD (data that) la bo danh gia chinh; neu chua co thi tam dung PROXY.
    test_rows = gold_rows if gold_rows else proxy_rows
    eval_name = "ner_test_gold.jsonl (GOLD, data that held-out)" if gold_rows else         "ner_test_real.jsonl (PROXY)"
    n_ent_synth = sum(len(r["entities"]) for r in synth_rows)
    n_ent_real = sum(len(r["entities"]) for r in real_rows)
    print(f"Train: {len(train_rows)} cau (synthetic {len(synth_rows)} + that {len(real_rows)}"
          f"x{real_repeat}) | entity: synthetic {n_ent_synth} + that {n_ent_real}x{real_repeat}")
    print(f"Eval chinh: {eval_name} — {len(test_rows)} cau / "
          f"{sum(len(r['entities']) for r in test_rows)} entity")
    if max_train:
        train_rows = train_rows[:max_train]

    encoder = PhoBertEncoder.from_pretrained(base_model)
    tokenizer = encoder.tok
    model = AutoModelForTokenClassification.from_pretrained(
        base_model, num_labels=len(BIO_LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )
    # nlu-service đọc 2 khoá này từ config.json để encode y hệt lúc train.
    setattr(model.config, CONFIG_MAX_LEN, max_length)
    setattr(model.config, CONFIG_ENCODING, ENCODING_NAME)

    train_ds, _ = build_dataset(encoder, train_rows, max_length)
    test_ds, test_spans = build_dataset(encoder, test_rows, max_length)
    proxy_ds, proxy_spans = build_dataset(encoder, proxy_rows, max_length)
    if encoder.fallbacks:
        print(f"[CANH BAO] {encoder.fallbacks} cau VnCoreNLP khong do lai duoc offset -> tach theo khoang trang")

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
        args=make_training_args(output_dir, num_epochs, batch_size, learning_rate, seed),
        train_dataset=train_ds,
        eval_dataset=test_ds,
        data_collator=DataCollatorForTokenClassification(tokenizer),
        compute_metrics=compute_metrics,
    )
    trainer.train()

    preds = predict_spans(trainer, test_ds, test_rows, test_spans)
    span = span_prf(test_rows, preds)
    print(f"=== Span-F1 strict tren {eval_name} — bo danh gia CHINH ===")
    print({k: round(v, 4) if isinstance(v, float) else v for k, v in span.items()})
    print("Theo nhan:", per_label_f1(test_rows, preds))
    print("Cau sai:")
    print_errors(test_rows, preds)

    # Luon bao cao them tren PROXY de so voi cac lan do truoc.
    proxy_preds = predict_spans(trainer, proxy_ds, proxy_rows, proxy_spans)
    proxy_span = span_prf(proxy_rows, proxy_preds)
    print("=== Span-F1 strict tren ner_test_real.jsonl (PROXY, xem build_proxy_testset.py) ===")
    print({k: round(v, 4) if isinstance(v, float) else v for k, v in proxy_span.items()})

    trainer.save_model(output_dir)
    tokenizer.save_pretrained(output_dir)
    return span


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
                        help=f"mac dinh {DEFAULT_BASE_MODEL}")
    parser.add_argument("--learning-rate", type=float, default=3e-5)
    parser.add_argument("--synth-file", default="ner_train_v2.jsonl",
                        help="file synthetic (ner_train.jsonl = bo v1 van phong sach)")
    parser.add_argument("--real-repeat", type=int, default=1,
                        help="nhan ban data that N lan de tang trong so (mac dinh 1 = khong nhan)")
    parser.add_argument("--max-length", type=int, default=MAX_LENGTH)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    main(args.max_train, args.epochs, args.output_dir, args.batch_size, args.base_model,
         args.max_length, args.learning_rate, args.real_repeat, args.synth_file, args.seed)
