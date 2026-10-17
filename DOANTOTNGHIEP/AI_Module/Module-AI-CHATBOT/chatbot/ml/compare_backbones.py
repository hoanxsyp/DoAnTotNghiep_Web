"""So sánh ViSoBERT và PhoBERT trên CÙNG dữ liệu, CÙNG cấu hình, CÙNG cách chấm.

Khác `train_intent.py` / `train_ner.py` ở 3 điểm để phép so sánh công bằng:

1. KHÔNG chọn checkpoint trên bộ test. Train đủ số epoch cố định rồi lấy model
   cuối — không model nào được "nhìn đề" (train_*.py chọn best epoch theo GOLD /
   proxy nên số của chúng lạc quan hơn thực tế).
2. Chạy nhiều seed (mặc định 3) rồi lấy trung bình ± độ lệch chuẩn — GOLD chỉ 28
   câu, 1 câu = 3,6 điểm, một lần chạy đơn lẻ dễ thắng/thua do may rủi.
3. NER chấm bằng Span-F1 strict trên offset KÝ TỰ của văn bản gốc (khớp đúng
   label+start+end) — độc lập với cách tokenize nên so được 2 model dù ViSoBERT
   cắt subword trên văn bản thô còn PhoBERT cắt trên văn bản đã word-segment.

Cách encode từng model:
  - ViSoBERT: văn bản THÔ -> fast tokenizer + offset_mapping (`nlu_encoding.py`),
    gán nhãn BIO cho MỌI subword — y hệt train_ner.py / nlu-service.
  - PhoBERT: VnCoreNLP word-segment (`vncorenlp_util.py`) -> mỗi từ tách BPE,
    gán nhãn BIO cho subword ĐẦU của mỗi từ, các subword sau = -100 (cách làm
    chuẩn của PhoBERT NER). Ranh giới entity vì thế chỉ rơi đúng ranh giới TỪ —
    dòng "trần round-trip" trong báo cáo cho biết điều đó mất bao nhiêu entity.

Model KHÔNG được lưu (chỉ lưu số đo) — đây là thí nghiệm, không thay `out-*`.

Chạy:
    python compare_backbones.py                                  # đầy đủ, ~1-2 giờ trên RTX 3050
    python compare_backbones.py --max-train 40 --seeds 42 --epochs-intent 1 --epochs-ner 1 --out smoke
"""

import argparse
import json
import os
import statistics
import time
from pathlib import Path

import numpy as np
import torch
from datasets import Dataset
from sklearn.metrics import f1_score
from transformers import (
    AutoModelForSequenceClassification,
    AutoModelForTokenClassification,
    AutoTokenizer,
    DataCollatorForTokenClassification,
    DataCollatorWithPadding,
    Trainer,
    TrainingArguments,
    logging as hf_logging,
)

from nlu_encoding import bio_labels_from_offsets, decode_bio_offsets, encode_with_offsets, span_prf
from phobert_encoding import PhoBertEncoder
from train_intent import LABELS as INTENT_LABELS
from train_ner import BIO_LABELS, ID2LABEL as NER_ID2LABEL, LABEL2ID as NER_LABEL2ID

ROOT = Path(__file__).parent
DATA_DIR = ROOT / "data"
OUT_DIR = ROOT / "eval-results"
INTENT2ID = {l: i for i, l in enumerate(INTENT_LABELS)}

BACKBONES = {"visobert": "uitnlp/visobert", "phobert": "vinai/phobert-base-v2",
             "visobert_word": "uitnlp/visobert"}


def load_jsonl(name):
    with (DATA_DIR / name).open(encoding="utf-8") as f:
        return [json.loads(line) for line in f]


# ---------------------------------------------------------------- encoders

class VisoEncoder:
    def __init__(self, model_id):
        self.tok = AutoTokenizer.from_pretrained(model_id)
        assert self.tok.is_fast, "ViSoBERT can fast tokenizer (sentencepiece + protobuf)"

    def n_tokens(self, text):
        return len(self.tok(text)["input_ids"])

    def intent(self, text, max_len):
        enc = self.tok(text, truncation=True, max_length=max_len)
        return enc["input_ids"], enc["attention_mask"]

    def ner(self, text, max_len):
        enc, spans = encode_with_offsets(self.tok, text, max_len)
        return enc["input_ids"], enc["attention_mask"], spans


class PhoEncoder(PhoBertEncoder):
    """Đúng encoder mà train_*.py / nlu-service dùng (`phobert_encoding.py`)."""

    def __init__(self, model_id):
        enc = PhoBertEncoder.from_pretrained(model_id)
        super().__init__(enc.tok, enc.seg)


class VisoWordEncoder(PhoEncoder):
    """ViSoBERT nhưng gán nhãn NER theo TỪ y hệt PhoBERT: cùng ranh giới từ của
    VnCoreNLP, chỉ subword đầu mỗi từ mang nhãn. Khác PhoBERT duy nhất ở model
    (và mỗi từ đưa vào ở dạng THÔ `text[start:end]`, không có "_" — ViSoBERT
    không được pretrain trên văn bản đã segment). Intent giữ nguyên văn bản thô.

    Dùng để tách bạch: chênh lệch NER đến từ MODEL hay từ CÁCH GÁN NHÃN.
    """

    def _word_pieces(self, text, start, end):
        ids = self.tok(text[start:end], add_special_tokens=False)["input_ids"]
        return ids or [self.tok.unk_token_id]

    def n_tokens(self, text):
        return 2 + sum(len(self._word_pieces(text, s, e)) for _, s, e in self.words(text))

    def intent(self, text, max_len):
        enc = self.tok(text, truncation=True, max_length=max_len)
        return enc["input_ids"], enc["attention_mask"]

    def ner(self, text, max_len):
        ids, spans = [self.tok.cls_token_id], [None]
        for _, start, end in self.words(text):
            for j, pid in enumerate(self._word_pieces(text, start, end)):
                ids.append(pid)
                spans.append((start, end) if j == 0 else None)
        ids, spans = ids[: max_len - 1] + [self.tok.sep_token_id], spans[: max_len - 1] + [None]
        return ids, [1] * len(ids), spans


ENCODERS = {"visobert": VisoEncoder, "phobert": PhoEncoder, "visobert_word": VisoWordEncoder}


# ---------------------------------------------------------------- data

def build_intent(enc, rows, max_len):
    cols = {"input_ids": [], "attention_mask": [], "labels": []}
    for r in rows:
        ids, mask = enc.intent(r["text"], max_len)
        cols["input_ids"].append(ids)
        cols["attention_mask"].append(mask)
        cols["labels"].append(INTENT2ID[r["intent"]])
    return Dataset.from_dict(cols)


def build_ner(enc, rows, max_len):
    cols = {"input_ids": [], "attention_mask": [], "labels": []}
    all_spans = []
    for r in rows:
        ids, mask, spans = enc.ner(r["text"], max_len)
        cols["input_ids"].append(ids)
        cols["attention_mask"].append(mask)
        cols["labels"].append(bio_labels_from_offsets(spans, r["entities"], NER_LABEL2ID))
        all_spans.append(spans)
    return Dataset.from_dict(cols), all_spans


def round_trip(enc, rows, max_len):
    """Encode nhãn gold rồi decode ngược: trần Span-F1 mà cách encode cho phép."""
    preds = []
    for r in rows:
        _, _, spans = enc.ner(r["text"], max_len)
        labels = bio_labels_from_offsets(spans, r["entities"], NER_LABEL2ID)
        preds.append(decode_bio_offsets(
            r["text"], spans, ["O" if l == -100 else NER_ID2LABEL[l] for l in labels]))
    return span_prf(rows, preds)


# ---------------------------------------------------------------- train / predict

def make_args(run_dir, epochs, lr, batch_size, seed):
    common = dict(
        output_dir=str(run_dir), num_train_epochs=epochs, learning_rate=lr,
        per_device_train_batch_size=batch_size, per_device_eval_batch_size=batch_size * 2,
        save_strategy="no", logging_steps=50, report_to=[], seed=seed,
        disable_tqdm=True,
    )
    try:
        return TrainingArguments(eval_strategy="no", **common)
    except TypeError:
        return TrainingArguments(evaluation_strategy="no", **common)


def train_model(cls, model_id, num_labels, id2label, train_ds, collator, args):
    model = cls.from_pretrained(model_id, num_labels=num_labels, id2label=id2label,
                                label2id={v: k for k, v in id2label.items()})
    trainer = Trainer(model=model, args=args, train_dataset=train_ds, data_collator=collator)
    t0 = time.time()
    out = trainer.train()
    return trainer, time.time() - t0, out.training_loss


def predict_intent(trainer, ds):
    return np.argmax(trainer.predict(ds).predictions, axis=-1).tolist()


def predict_ner(trainer, ds, rows, all_spans):
    logits = trainer.predict(ds).predictions
    preds = []
    for r, spans, lg in zip(rows, all_spans, logits):
        labels = [NER_ID2LABEL[int(i)] for i in np.argmax(lg[: len(spans)], axis=-1)]
        preds.append(decode_bio_offsets(r["text"], spans, labels))
    return preds


def intent_scores(rows, pred_ids):
    gold = [INTENT2ID[r["intent"]] for r in rows]
    return {"accuracy": float(np.mean([g == p for g, p in zip(gold, pred_ids)])),
            "macro_f1": float(f1_score(gold, pred_ids, average="macro")),
            "n_correct": int(sum(g == p for g, p in zip(gold, pred_ids))), "n": len(rows)}


def measure_latency(enc, intent_model, ner_model, texts, max_len):
    """ms/câu trên CPU, batch 1, gồm cả word-segment với PhoBERT — giống nlu-service."""
    torch.set_num_threads(max(1, (os.cpu_count() or 4) // 2))
    intent_model, ner_model = intent_model.to("cpu").eval(), ner_model.to("cpu").eval()
    if hasattr(enc, "_cache"):
        enc._cache.clear()  # tính cả thời gian VnCoreNLP
    times = []
    with torch.no_grad():
        for i, text in enumerate(texts):
            t0 = time.perf_counter()
            ids, mask = enc.intent(text, max_len)
            intent_model(input_ids=torch.tensor([ids]), attention_mask=torch.tensor([mask]))
            ids, mask, _ = enc.ner(text, max_len)
            ner_model(input_ids=torch.tensor([ids]), attention_mask=torch.tensor([mask]))
            if i >= 5:  # bỏ 5 câu khởi động
                times.append((time.perf_counter() - t0) * 1000)
    times.sort()
    return {"mean_ms": statistics.mean(times), "p95_ms": times[int(0.95 * (len(times) - 1))]}


# ---------------------------------------------------------------- main

def main(a):
    hf_logging.set_verbosity_error()
    intent_train = load_jsonl("intent_train_v2.jsonl") + load_jsonl("intent_train_real.jsonl") * a.intent_real_repeat
    ner_train = load_jsonl("ner_train_v2.jsonl") + load_jsonl("ner_data_md_labeled.jsonl") * a.ner_real_repeat
    if a.max_train:
        intent_train, ner_train = intent_train[: a.max_train], ner_train[: a.max_train]
    tests = {"gold": load_jsonl("intent_test_gold.jsonl"), "proxy": load_jsonl("intent_test_real.jsonl")}
    ner_test = load_jsonl("ner_test_real.jsonl")
    print(f"Train intent {len(intent_train)} cau | NER {len(ner_train)} cau | seeds {a.seeds}")

    run_root = ROOT / "cmp-runs" / a.out
    results = {"config": vars(a), "backbones": {}}
    for name in a.backbones:
        model_id = BACKBONES[name]
        print(f"\n===== {name} ({model_id}) =====")
        t0 = time.time()
        enc = ENCODERS[name](model_id)
        all_text = [r["text"] for r in intent_train + ner_train + tests["gold"] + tests["proxy"] + ner_test]
        lens = [enc.n_tokens(t) for t in dict.fromkeys(all_text)]
        prep = {
            "encode_seconds": time.time() - t0,
            "truncated_sentences": sum(n > a.max_length for n in lens),
            "unique_sentences": len(lens),
            "ner_round_trip_test": round_trip(enc, ner_test, a.max_length),
            "ner_round_trip_train_real": round_trip(enc, load_jsonl("ner_data_md_labeled.jsonl"), a.max_length),
        }
        print("chuan bi:", {k: (round(v["f1"], 4) if isinstance(v, dict) else v) for k, v in prep.items()})

        i_train = build_intent(enc, intent_train, a.max_length)
        i_tests = {k: build_intent(enc, rows, a.max_length) for k, rows in tests.items()}
        n_train, _ = build_ner(enc, ner_train, a.max_length)
        n_test, n_test_spans = build_ner(enc, ner_test, a.max_length)

        runs = []
        for k, seed in enumerate(a.seeds):
            print(f"-- seed {seed}")
            run = {"seed": seed}
            if not a.ner_only:
                run.update(train_eval_intent(a, enc, model_id, name, seed, run_root, i_train, i_tests, tests))
                it = run.pop("_trainer")

            nt, secs, loss = train_model(
                AutoModelForTokenClassification, model_id, len(BIO_LABELS), dict(NER_ID2LABEL),
                n_train, DataCollatorForTokenClassification(enc.tok),
                make_args(run_root / name / "ner", a.epochs_ner, a.lr_ner, a.batch_size, seed))
            run["ner_train_seconds"], run["ner_train_loss"] = secs, loss
            run["ner_proxy"] = span_prf(ner_test, predict_ner(nt, n_test, ner_test, n_test_spans))
            print(f"   NER span-F1 {run['ner_proxy']['f1']:.3f} | {secs:.0f}s")

            if k == 0 and not a.ner_only:
                run["latency_cpu"] = measure_latency(
                    enc, it.model, nt.model, [r["text"] for r in tests["proxy"]], a.max_length)
                print(f"   latency CPU {run['latency_cpu']['mean_ms']:.0f} ms/cau")
            runs.append(run)
            it = nt = None
            torch.cuda.empty_cache()

        results["backbones"][name] = {"model_id": model_id, "prep": prep, "runs": runs}

    OUT_DIR.mkdir(exist_ok=True)
    json_path = OUT_DIR / f"backbone_compare_{a.out}.json"
    json_path.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    md = render_markdown(results)
    (OUT_DIR / f"backbone_compare_{a.out}.md").write_text(md, encoding="utf-8")
    print("\n" + md)
    print(f"-> {json_path}")


def train_eval_intent(a, enc, model_id, name, seed, run_root, i_train, i_tests, tests):
    run = {}
    it, secs, loss = train_model(
        AutoModelForSequenceClassification, model_id, len(INTENT_LABELS), dict(enumerate(INTENT_LABELS)),
        i_train, DataCollatorWithPadding(enc.tok),
        make_args(run_root / name / "intent", a.epochs_intent, a.lr_intent, a.batch_size, seed))
    run["intent_train_seconds"], run["intent_train_loss"] = secs, loss
    for key, rows in tests.items():
        pred = predict_intent(it, i_tests[key])
        run[f"intent_{key}"] = intent_scores(rows, pred)
        if key == "gold":
            run["gold_errors"] = [
                {"text": r["text"], "gold": r["intent"], "pred": INTENT_LABELS[p]}
                for r, p in zip(rows, pred) if INTENT_LABELS[p] != r["intent"]]
    print(f"   intent GOLD acc {run['intent_gold']['accuracy']:.3f} | proxy {run['intent_proxy']['accuracy']:.3f} | {secs:.0f}s")
    run["_trainer"] = it
    return run


def _ms(values, digits=3):
    if not values:
        return "—"
    if len(values) == 1:
        return f"{values[0]:.{digits}f}"
    return f"{statistics.mean(values):.{digits}f} ± {statistics.stdev(values):.{digits}f}"


def render_markdown(res):
    c = res["config"]
    lines = [
        "# So sánh ViSoBERT và PhoBERT — cùng dữ liệu, cùng cách chấm",
        "",
        f"- Train intent: `intent_train_v2` + câu thật ×{c['intent_real_repeat']}, {c['epochs_intent']} epoch, lr {c['lr_intent']}",
        f"- Train NER: `ner_train_v2` + `ner_data_md_labeled` ×{c['ner_real_repeat']}, {c['epochs_ner']} epoch, lr {c['lr_ner']}",
        f"- batch {c['batch_size']}, max_length {c['max_length']}, seeds {c['seeds']}"
        + (f", **max_train {c['max_train']} (smoke)**" if c["max_train"] else ""),
        "- Lấy model ở epoch CUỐI, không chọn checkpoint trên bộ test.",
        "",
        "## Kết quả (trung bình ± độ lệch chuẩn qua các seed)",
        "",
        "| Model | Intent GOLD acc | Intent GOLD macro-F1 | Intent proxy acc | NER Span-F1 (proxy) | NER P | NER R | Latency CPU |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for name, b in res["backbones"].items():
        r = b["runs"]
        lat = r[0].get("latency_cpu", {})
        lines.append(
            f"| {name} | {_ms([x['intent_gold']['accuracy'] for x in r if 'intent_gold' in x])} "
            f"| {_ms([x['intent_gold']['macro_f1'] for x in r if 'intent_gold' in x])} "
            f"| {_ms([x['intent_proxy']['accuracy'] for x in r if 'intent_proxy' in x])} "
            f"| {_ms([x['ner_proxy']['f1'] for x in r])} "
            f"| {_ms([x['ner_proxy']['precision'] for x in r])} "
            f"| {_ms([x['ner_proxy']['recall'] for x in r])} "
            + (f"| {lat['mean_ms']:.0f} ms (p95 {lat['p95_ms']:.0f}) |" if lat else "| — |"))
    lines += ["", "## Từng seed", "", "| Model | Seed | GOLD đúng | proxy acc | NER Span-F1 | Train intent | Train NER |",
              "|---|---:|---:|---:|---:|---:|---:|"]
    for name, b in res["backbones"].items():
        for x in b["runs"]:
            gold = f"{x['intent_gold']['n_correct']}/{x['intent_gold']['n']}" if "intent_gold" in x else "—"
            proxy = f"{x['intent_proxy']['accuracy']:.3f}" if "intent_proxy" in x else "—"
            t_int = f"{x['intent_train_seconds']:.0f}s" if "intent_train_seconds" in x else "—"
            lines.append(
                f"| {name} | {x['seed']} | {gold} | {proxy} | {x['ner_proxy']['f1']:.3f} "
                f"| {t_int} | {x['ner_train_seconds']:.0f}s |")
    lines += ["", "## Chuẩn bị dữ liệu", "",
              "| Model | Câu bị cắt (>max_length) | Trần NER round-trip (test proxy) | Trần NER round-trip (56 câu thật) |",
              "|---|---:|---:|---:|"]
    for name, b in res["backbones"].items():
        p = b["prep"]
        lines.append(
            f"| {name} | {p['truncated_sentences']}/{p['unique_sentences']} "
            f"| {p['ner_round_trip_test']['f1']:.3f} | {p['ner_round_trip_train_real']['f1']:.3f} |")
    lines += ["", "Trần round-trip = encode nhãn gold rồi decode ngược. < 1.000 nghĩa là cách tách "
              "token không biểu diễn được một số ranh giới entity, model có giỏi mấy cũng không đạt."]
    return "\n".join(lines) + "\n"


if __name__ == "__main__":
    import io
    import sys

    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", line_buffering=True)
    ap = argparse.ArgumentParser()
    ap.add_argument("--backbones", nargs="+", default=list(BACKBONES), choices=list(BACKBONES))
    ap.add_argument("--seeds", nargs="+", type=int, default=[42, 43, 44])
    ap.add_argument("--epochs-intent", type=int, default=5)
    ap.add_argument("--epochs-ner", type=int, default=8)
    ap.add_argument("--lr-intent", type=float, default=2e-5)
    ap.add_argument("--lr-ner", type=float, default=3e-5)
    ap.add_argument("--batch-size", type=int, default=16)
    ap.add_argument("--max-length", type=int, default=128)
    ap.add_argument("--intent-real-repeat", type=int, default=10)
    ap.add_argument("--ner-real-repeat", type=int, default=1)
    ap.add_argument("--max-train", type=int, default=None, help="smoke test")
    ap.add_argument("--ner-only", action="store_true", help="bo qua intent (va latency)")
    ap.add_argument("--out", default="full", help="hau to ten file ket qua")
    main(ap.parse_args())
