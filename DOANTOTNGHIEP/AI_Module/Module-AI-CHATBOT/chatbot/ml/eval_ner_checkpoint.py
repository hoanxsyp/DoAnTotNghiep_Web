"""Compare a candidate NER checkpoint against the configured default."""

import argparse
import hashlib
import json
from collections import Counter
from pathlib import Path

from transformers import AutoModelForTokenClassification, DataCollatorForTokenClassification, Trainer, TrainingArguments

from nlu_encoding import resolve_max_length, span_prf
from phobert_encoding import CONFIG_ENCODING, ENCODING_NAME, PhoBertEncoder
from train_ner import ENTITY_LABELS, ID2LABEL, ROOT, build_dataset, predict_spans


def entity_key(entity):
    return entity["label"], entity["start"], entity["end"]


def results(rows, preds):
    return {
        "sentences": len(rows),
        "exact_sentences": sum(
            set(map(entity_key, row["entities"])) == set(map(entity_key, pred))
            for row, pred in zip(rows, preds)
        ),
        "strict_span": span_prf(rows, preds),
        "labels": {
            label: span_prf(
                [{"entities": [e for e in row["entities"] if e["label"] == label]} for row in rows],
                [[e for e in pred if e["label"] == label] for pred in preds],
            )
            for label in ENTITY_LABELS
        },
    }


def evaluate(model_dir, rows, eval_dir, batch_size):
    model = AutoModelForTokenClassification.from_pretrained(model_dir, local_files_only=True).eval()
    if getattr(model.config, CONFIG_ENCODING, None) != ENCODING_NAME or model.config.id2label != ID2LABEL:
        raise ValueError(f"Unexpected NER configuration in {model_dir}")
    encoder = PhoBertEncoder.from_pretrained(model_dir)
    dataset, offsets = build_dataset(encoder, rows, resolve_max_length(model.config))
    trainer = Trainer(
        model=model,
        args=TrainingArguments(
            output_dir=str(eval_dir), report_to=[], per_device_eval_batch_size=batch_size,
            disable_tqdm=True,
        ),
        data_collator=DataCollatorForTokenClassification(encoder.tok),
    )
    return results(rows, predict_spans(trainer, dataset, rows, offsets))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--batch-size", type=int, default=24)
    args = parser.parse_args()
    dataset_bytes = args.dataset.read_bytes()
    rows = [json.loads(line) for line in dataset_bytes.decode("utf-8-sig").splitlines()]
    report = {
        "dataset": args.dataset.name,
        "dataset_sha256": hashlib.sha256(dataset_bytes).hexdigest(),
        "annotation_revisions": dict(Counter(row.get("annotation_revision") for row in rows)),
    }
    for name, checkpoint in (("default", ROOT / "out-ner"), ("candidate", args.candidate)):
        report[name] = {
            "checkpoint": checkpoint.resolve().relative_to(ROOT.resolve()).as_posix(),
            **evaluate(checkpoint, rows, args.output.parent, args.batch_size),
        }
        print(name, report[name]["strict_span"], flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
