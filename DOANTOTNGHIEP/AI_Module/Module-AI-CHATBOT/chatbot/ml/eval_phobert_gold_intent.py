"""Sinh cache intent của PhoBERT trên GOLD cho eval_nlu_compare (phương án B).

Tái lập ĐÚNG `classify_intent` của nlu-service/app.py (segment_plain + tokenizer
max_length=64 + softmax argmax) mà không cần dựng FastAPI/SBERT — phần intent
không dùng embedding. Xuất eval-results/phobert_intent_gold.jsonl theo đúng format
mà eval_nlu_compare.report kỳ vọng (có `intent` và `gold_intent`).

Chạy: python eval_phobert_gold_intent.py
"""
import json
import time
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from vncorenlp_util import get_segmenter, segment_plain

ROOT = Path(__file__).parent
GOLD = ROOT / "data" / "intent_test_gold.jsonl"
INTENT_DIR = ROOT / "out-intent"
OUT = ROOT / "eval-results" / "phobert_intent_gold.jsonl"
MAX_LENGTH = 64


def main():
    segmenter = get_segmenter(ROOT / "vncorenlp")
    tok = AutoTokenizer.from_pretrained(str(INTENT_DIR))
    model = AutoModelForSequenceClassification.from_pretrained(str(INTENT_DIR)).eval()

    rows = [json.loads(l) for l in GOLD.open(encoding="utf-8")]
    OUT.parent.mkdir(exist_ok=True)
    with OUT.open("w", encoding="utf-8") as f:
        for r in rows:
            seg = segment_plain(segmenter, r["text"])
            enc = tok(seg, truncation=True, max_length=MAX_LENGTH, return_tensors="pt")
            t0 = time.perf_counter()
            with torch.no_grad():
                logits = model(**enc).logits[0]
            ms = (time.perf_counter() - t0) * 1000
            probs = torch.softmax(logits, dim=-1)
            idx = int(probs.argmax())
            rec = {
                "text": r["text"],
                "intent": model.config.id2label[idx],
                "slots": [],
                "latency_ms": ms,
                "tokens_in": 0, "tokens_out": 0,
                "gold_intent": r["intent"],
            }
            f.write(json.dumps(rec, ensure_ascii=False) + "\n")
    acc = sum(json.loads(l)["intent"] == json.loads(l)["gold_intent"]
              for l in OUT.open(encoding="utf-8")) / len(rows)
    print(f"PhoBERT GOLD intent: {len(rows)} cau, acc = {acc:.3f} -> {OUT.name}")


if __name__ == "__main__":
    import io
    import sys
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")
    main()
