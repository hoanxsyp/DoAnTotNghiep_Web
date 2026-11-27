"""Compare a candidate Intent checkpoint on the shared NLU benchmark."""

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np
from sklearn.metrics import classification_report, confusion_matrix
from transformers import AutoModelForSequenceClassification, DataCollatorWithPadding, Trainer, TrainingArguments

from nlu_encoding import resolve_max_length
from phobert_encoding import CONFIG_ENCODING, ENCODING_NAME, PhoBertEncoder
from train_intent import ID2LABEL, LABELS, ROOT, to_dataset


def compare(checkpoint, rows, output_dir):
    model = AutoModelForSequenceClassification.from_pretrained(checkpoint, local_files_only=True).eval()
    if getattr(model.config, CONFIG_ENCODING, None) != ENCODING_NAME or model.config.id2label != ID2LABEL:
        raise ValueError(f"Unexpected Intent configuration in {checkpoint}")
    encoder = PhoBertEncoder.from_pretrained(checkpoint)
    # The first reference only supplies the shape expected by the train encoder.
    examples = [{"text": r["text"], "intent": r["intents"][0]} for r in rows]
    dataset = to_dataset(encoder, examples, resolve_max_length(model.config)).remove_columns("label")
    trainer = Trainer(
        model=model,
        args=TrainingArguments(
            output_dir=str(output_dir), report_to=[], per_device_eval_batch_size=24, disable_tqdm=True,
        ),
        data_collator=DataCollatorWithPadding(encoder.tok),
    )
    predictions = [ID2LABEL[int(i)] for i in np.argmax(trainer.predict(dataset).predictions, axis=1)]
    gold, pred = [], []
    multi_count = multi_hit = 0
    for row, prediction in zip(rows, predictions):
        expected = row["intents"]
        if len(expected) == 1:
            gold.append(expected[0])
            pred.append(prediction)
        else:
            multi_count += 1
            multi_hit += prediction in expected
    report = classification_report(gold, pred, labels=LABELS, zero_division=0, output_dict=True)
    return {
        "sentences": len(gold),
        "correct": sum(a == b for a, b in zip(gold, pred)),
        "accuracy": report["accuracy"],
        "macro_f1": report["macro avg"]["f1-score"],
        "labels": {label: report[label] for label in LABELS},
        "confusion_labels": LABELS,
        "confusion_matrix": confusion_matrix(gold, pred, labels=LABELS).tolist(),
        "multi_intent": {"sentences": multi_count, "at_least_one_correct": multi_hit},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("candidate", type=Path)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    contents = args.dataset.read_bytes()
    rows = [json.loads(line) for line in contents.decode("utf-8-sig").splitlines()]
    report = {"dataset": args.dataset.name, "dataset_sha256": hashlib.sha256(contents).hexdigest()}
    for name, path in (("default", ROOT / "out-intent"), ("candidate", args.candidate)):
        result = compare(path, rows, args.output.parent)
        report[name] = {"checkpoint": path.resolve().relative_to(ROOT.resolve()).as_posix(), **result}
        print(name, result["correct"], result["sentences"], result["accuracy"], result["macro_f1"], flush=True)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
