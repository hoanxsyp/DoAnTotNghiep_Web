"""Sinh cache intent của ViSoBERT trên GOLD cho eval_nlu_compare (phương án B).

Tái lập ĐÚNG `classify_intent` của nlu-service/app.py (văn bản thô + tokenizer
theo `max_length` lưu trong config.json + softmax argmax) mà không phải dựng
FastAPI/SBERT — phần intent không dùng embedding. Xuất
`eval-results/visobert_intent_gold.jsonl` theo đúng format mà
`eval_nlu_compare.report` kỳ vọng (có `intent` và `gold_intent`).

Chạy:
    python eval_local_intent_gold.py
    python eval_local_intent_gold.py --model-dir out-intent-smoke
"""
import argparse
import json
import time
from pathlib import Path

import torch
from transformers import AutoModelForSequenceClassification, AutoTokenizer

from nlu_encoding import resolve_max_length

ROOT = Path(__file__).parent
GOLD = ROOT / "data" / "intent_test_gold.jsonl"
SIDE = "visobert"  # khớp tên cột trong eval_nlu_compare.SIDES


def main(model_dir: Path):
    out = ROOT / "eval-results" / f"{SIDE}_intent_gold.jsonl"
    tok = AutoTokenizer.from_pretrained(str(model_dir))
    model = AutoModelForSequenceClassification.from_pretrained(str(model_dir)).eval()

    max_length = resolve_max_length(model.config)
    print(f"model={model_dir} | max_length={max_length}")

    rows = [json.loads(l) for l in GOLD.open(encoding="utf-8")]
    out.parent.mkdir(exist_ok=True)
    with out.open("w", encoding="utf-8") as f:
        for r in rows:
            enc = tok(r["text"], truncation=True, max_length=max_length, return_tensors="pt")
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
              for l in out.open(encoding="utf-8")) / len(rows)
    print(f"ViSoBERT GOLD intent: {len(rows)} cau, acc = {acc:.3f} -> {out.name}")
    print(f"LUU Y: GOLD chi {len(rows)} cau -> 1 cau = {100 / len(rows):.1f} diem acc. "
          "Chenh 1-2 cau giua cac lan train la NHIEU, khong phai cai thien.")


if __name__ == "__main__":
    import io
    import sys
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8")

    ap = argparse.ArgumentParser()
    ap.add_argument("--model-dir", default="out-intent")
    a = ap.parse_args()
    main(ROOT / a.model_dir)
