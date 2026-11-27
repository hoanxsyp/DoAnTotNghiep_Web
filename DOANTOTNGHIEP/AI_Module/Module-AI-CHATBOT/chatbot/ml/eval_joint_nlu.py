"""Evaluate the default local Intent and NER checkpoints on labeled JSONL."""

import argparse
import hashlib
import json
import sys
from collections import Counter
from pathlib import Path

from sklearn.metrics import classification_report, confusion_matrix
from transformers import AutoModelForSequenceClassification, AutoModelForTokenClassification

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT.parent / "nlu-service"))
import app as nlu


def load_models():
    nlu.torch.set_num_threads(2)
    for task, loader in (
        ("intent", AutoModelForSequenceClassification),
        ("ner", AutoModelForTokenClassification),
    ):
        checkpoint = ROOT / f"out-{task}"
        model = loader.from_pretrained(checkpoint, local_files_only=True).eval()
        if getattr(model.config, nlu.CONFIG_ENCODING, None) != nlu.ENCODING_NAME:
            raise ValueError(f"Unexpected encoding in {checkpoint}")
        nlu._M[f"{task}_model"] = model
        nlu._M[f"{task}_len"] = nlu.resolve_max_length(model.config)
        nlu._M[f"{task}_enc"] = nlu.PhoBertEncoder.from_pretrained(checkpoint, cache=False)


def spans(entities):
    return {(item["label"], item["start"], item["end"]) for item in entities}


def entity_scores(tp, fp, fn):
    return {
        "tp": tp, "fp": fp, "fn": fn, "support": tp + fn,
        "precision": tp / (tp + fp) if tp + fp else 0.0,
        "recall": tp / (tp + fn) if tp + fn else 0.0,
        "f1": 2 * tp / (2 * tp + fp + fn) if 2 * tp + fp + fn else 0.0,
    }


def evaluate(rows):
    labels = sorted(nlu._M["intent_model"].config.id2label.values())
    kinds = sorted({
        label[2:] for label in nlu._M["ner_model"].config.id2label.values()
        if label.startswith("B-")
    })
    intent_gold, intent_pred = [], []
    tp, fp, fn = Counter(), Counter(), Counter()
    exact = multi = multi_one_hit = 0
    for row in rows:
        text = row["text"]
        if not text or text != text.strip():
            raise ValueError(f"Blank text or untrimmed text at {row['id']}")
        expected_intents = row["intents"]
        gold = spans(row["entities"])
        if not expected_intents or set(expected_intents) - set(labels):
            raise ValueError(f"Invalid intent labels at {row['id']}")
        if len(gold) != len(row["entities"]) or any(
            label not in kinds or not 0 <= start < end <= len(text)
            for label, start, end in gold
        ):
            raise ValueError(f"Invalid entity labels or offsets at {row['id']}")
        predicted_intent, _ = nlu.classify_intent(text)
        pred = spans([entity.model_dump() for entity in nlu.extract_entities(text)])
        if len(expected_intents) == 1:
            intent_gold.append(expected_intents[0])
            intent_pred.append(predicted_intent)
        else:
            multi += 1
            multi_one_hit += predicted_intent in expected_intents
        tp.update(label for label, _, _ in gold & pred)
        fp.update(label for label, _, _ in pred - gold)
        fn.update(label for label, _, _ in gold - pred)
        exact += gold == pred
    report = classification_report(
        intent_gold, intent_pred, labels=labels, zero_division=0, output_dict=True,
    )
    return {
        "intent": {
            "sentences": len(intent_gold),
            "correct": sum(gold == pred for gold, pred in zip(intent_gold, intent_pred)),
            "accuracy": report["accuracy"],
            "macro_f1": report["macro avg"]["f1-score"],
            "labels": {label: report[label] for label in labels},
            "confusion_labels": labels,
            "confusion_matrix": confusion_matrix(intent_gold, intent_pred, labels=labels).tolist(),
            "multi_intent": {"sentences": multi, "at_least_one_correct": multi_one_hit},
        },
        "ner": {
            "sentences": len(rows), "exact_sentences": exact,
            "strict_span": entity_scores(sum(tp.values()), sum(fp.values()), sum(fn.values())),
            "labels": {kind: entity_scores(tp[kind], fp[kind], fn[kind]) for kind in kinds},
        },
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    dataset_bytes = args.dataset.read_bytes()
    rows = [json.loads(line) for line in dataset_bytes.decode("utf-8-sig").splitlines()]
    if not rows or len({row["id"] for row in rows}) != len(rows):
        raise ValueError("Expected a non-empty dataset with unique row IDs")
    load_models()
    result = {
        "dataset": args.dataset.name,
        "dataset_sha256": hashlib.sha256(dataset_bytes).hexdigest(),
        "models": {"intent": "out-intent", "ner": "out-ner"},
        **evaluate(rows),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
