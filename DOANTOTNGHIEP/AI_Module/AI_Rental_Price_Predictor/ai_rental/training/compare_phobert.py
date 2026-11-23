"""CPU-friendly PhoBERT regression experiment on the existing grouped holdout.

Install optional dependencies: pip install torch transformers underthesea
Run from any directory: python training/compare_phobert.py --epochs 3

This experiment never overwrites the production model. Both candidates receive
the same 29 structured features and the same train/test listing groups.
"""

import argparse
import json
import random
import time
from pathlib import Path

import numpy as np
import pandas as pd
import torch
from sklearn.model_selection import GroupShuffleSplit
from sklearn.pipeline import Pipeline
from transformers import AutoModelForSequenceClassification, AutoTokenizer
from underthesea import word_tokenize

from train_compare import BIN, CAT, NUM, TARGET, base_models, ensure_derived_columns, listing_groups, prep, scores


ROOT = Path(__file__).resolve().parents[1]
MODEL_NAME = "vinai/phobert-base-v2"

LABELS = {
    "area_m2": "diện tích m2", "number_of_amenities": "số tiện ích",
    "distance_to_center_km": "cách trung tâm km", "floor": "tầng",
    "posted_month": "tháng đăng", "listing_age_days": "tuổi tin ngày",
    "latitude": "vĩ độ", "longitude": "kinh độ",
    "population_density_km2": "mật độ dân số",
    "market_unit_price_million_m2": "giá khu vực trên m2",
    "market_price_million": "giá tham khảo khu vực triệu đồng",
    "market_sample_count": "số mẫu khu vực",
    "market_frecency_score": "độ mới mẫu khu vực",
    "market_freshness_days": "tuổi mẫu khu vực ngày",
    "market_scope_level": "phạm vi khu vực",
    "district": "quận", "ward": "phường", "room_type": "loại phòng",
    "has_dieu_hoa": "điều hòa", "has_khep_kin": "khép kín",
    "has_ban_cong": "ban công", "has_thang_may": "thang máy",
    "has_full_do": "đầy đủ nội thất", "has_gac": "gác",
    "has_may_giat": "máy giặt", "has_nong_lanh": "bình nóng lạnh",
    "has_wifi": "wifi", "has_de_xe": "chỗ để xe",
    "has_coordinates": "có tọa độ",
}


def format_value(value):
    if pd.isna(value):
        return "không rõ"
    if isinstance(value, (float, np.floating)):
        return f"{value:.4f}".rstrip("0").rstrip(".")
    return str(value)


def record_text(row):
    fields = NUM + CAT + BIN
    return "; ".join(f"{LABELS[key]} {format_value(row[key])}" for key in fields) + "."


def encode_rows(df, tokenizer, max_length):
    texts = [word_tokenize(record_text(row), format="text") for _, row in df.iterrows()]
    result = tokenizer(texts, truncation=True, padding="max_length",
                       max_length=max_length, return_tensors="pt")
    lengths = tokenizer(texts, truncation=False, return_length=True)["length"]
    print(f"PhoBERT tokens: median={np.median(lengths):.0f}, max={max(lengths)}, "
          f"truncated={sum(n > max_length for n in lengths)}/{len(lengths)}", flush=True)
    return result, int(sum(n > max_length for n in lengths))


def predict(model, encoded, indices, batch_size, y_mean, y_std):
    model.eval()
    out = []
    with torch.inference_mode():
        for start in range(0, len(indices), batch_size):
            ids = indices[start:start + batch_size]
            batch = {key: value[ids] for key, value in encoded.items()}
            out.extend(model(**batch).logits.view(-1).numpy().tolist())
    return np.asarray(out) * y_std + y_mean


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--epochs", type=int, default=3)
    parser.add_argument("--batch-size", type=int, default=8)
    parser.add_argument("--max-length", type=int, default=192)
    parser.add_argument("--train-last-layers", type=int, default=2)
    parser.add_argument("--model", default=MODEL_NAME,
                        help="PhoBERT model ID or a local snapshot directory")
    parser.add_argument("--output", type=Path, default=ROOT / "experiments" / "phobert_comparison.json")
    args = parser.parse_args()
    if args.epochs < 1 or args.train_last_layers < 0 or args.train_last_layers > 12:
        parser.error("epochs must be >=1 and train-last-layers must be 0..12")

    random.seed(42); np.random.seed(42); torch.manual_seed(42)
    torch.set_num_threads(min(4, torch.get_num_threads()))
    started = time.time()
    df = pd.read_csv(ROOT / "data" / "processed" / "hanoi_all_clean.csv")
    ensure_derived_columns(df)
    X, y = df[NUM + CAT + BIN], df[TARGET]
    groups = listing_groups(df)
    weights = pd.to_numeric(df["sample_weight"], errors="coerce").fillna(1.0)
    split = GroupShuffleSplit(n_splits=1, test_size=0.2, random_state=42)
    train_idx, test_idx = next(split.split(X, y, groups))

    metadata = json.loads((ROOT / "models" / "hanoi_all" / "metadata.json").read_text(encoding="utf-8"))
    baseline = Pipeline([("prep", prep()), ("model", base_models()["LightGBM"])])
    baseline.set_params(**{f"model__{key}": value for key, value in metadata["params"].items()})
    baseline.fit(X.iloc[train_idx], y.iloc[train_idx], model__sample_weight=weights.iloc[train_idx])
    baseline_metrics = {k: float(v) for k, v in scores(y.iloc[test_idx], baseline.predict(X.iloc[test_idx])).items()}
    print(f"LightGBM holdout: {baseline_metrics}", flush=True)

    tokenizer = AutoTokenizer.from_pretrained(args.model, use_fast=False)
    encoded, truncated = encode_rows(X, tokenizer, args.max_length)
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model, num_labels=1, problem_type="regression")
    if hasattr(model, "roberta"):
        for p in model.roberta.parameters():
            p.requires_grad = False
        for layer in model.roberta.encoder.layer[-args.train_last_layers:] if args.train_last_layers else []:
            for p in layer.parameters():
                p.requires_grad = True
    else:
        raise RuntimeError("Expected PhoBERT/RoBERTa backbone")

    inner_split = GroupShuffleSplit(n_splits=1, test_size=0.1, random_state=43)
    inner_tr, inner_val = next(inner_split.split(X.iloc[train_idx], y.iloc[train_idx], groups.iloc[train_idx]))
    fit_idx, val_idx = train_idx[inner_tr], train_idx[inner_val]
    baseline_same_fit = Pipeline([("prep", prep()), ("model", base_models()["LightGBM"])])
    baseline_same_fit.set_params(**{f"model__{key}": value for key, value in metadata["params"].items()})
    baseline_same_fit.fit(X.iloc[fit_idx], y.iloc[fit_idx], model__sample_weight=weights.iloc[fit_idx])
    baseline_same_fit_metrics = {k: float(v) for k, v in scores(
        y.iloc[test_idx], baseline_same_fit.predict(X.iloc[test_idx])).items()}
    print(f"LightGBM same-fit holdout: {baseline_same_fit_metrics}", flush=True)
    y_mean, y_std = float(y.iloc[fit_idx].mean()), float(y.iloc[fit_idx].std())
    optimizer = torch.optim.AdamW((p for p in model.parameters() if p.requires_grad), lr=2e-5 if args.train_last_layers else 1e-3)
    best_mae, best_epoch, best_state = float("inf"), 0, None
    history = []
    for epoch in range(1, args.epochs + 1):
        model.train()
        shuffled = np.random.permutation(fit_idx)
        losses = []
        for start in range(0, len(shuffled), args.batch_size):
            ids = shuffled[start:start + args.batch_size]
            batch = {key: value[ids] for key, value in encoded.items()}
            labels = torch.tensor(((y.iloc[ids].to_numpy() - y_mean) / y_std), dtype=torch.float32)
            sample_weights = torch.tensor(weights.iloc[ids].to_numpy(), dtype=torch.float32)
            optimizer.zero_grad()
            output = model(**batch).logits.view(-1)
            loss = ((output - labels) ** 2 * sample_weights).sum() / sample_weights.sum()
            loss.backward()
            optimizer.step()
            losses.append(float(loss.item()))
            if len(losses) % 100 == 0:
                print(f"Epoch {epoch}: {len(losses)} batches, elapsed={time.time() - started:.0f}s", flush=True)
        val_predictions = predict(model, encoded, val_idx, args.batch_size, y_mean, y_std)
        val_mae = float(scores(y.iloc[val_idx], val_predictions)["MAE"])
        history.append({"epoch": epoch, "train_loss": float(np.mean(losses)), "validation_MAE": val_mae})
        print(f"Epoch {epoch}: train_loss={np.mean(losses):.4f}, validation_MAE={val_mae:.4f}", flush=True)
        if val_mae < best_mae:
            best_mae, best_epoch = val_mae, epoch
            best_state = {k: v.detach().cpu().clone() for k, v in model.state_dict().items()}

    model.load_state_dict(best_state)
    predictions = predict(model, encoded, test_idx, args.batch_size, y_mean, y_std)
    phobert_metrics = {k: float(v) for k, v in scores(y.iloc[test_idx], predictions).items()}
    result = {
        "model": MODEL_NAME, "method": "word-segmented structured features; last transformer layers and regression head fine-tuned",
        "features": NUM + CAT + BIN, "dataset_rows": len(df),
        "train_rows": len(train_idx), "test_rows": len(test_idx),
        "fit_rows": len(fit_idx), "validation_rows": len(val_idx),
        "split": "GroupShuffleSplit(test_size=0.2, random_state=42) by listing_group",
        "inner_split": "GroupShuffleSplit(test_size=0.1, random_state=43) within training groups",
        "lightgbm": baseline_metrics, "lightgbm_same_fit": baseline_same_fit_metrics,
        "phobert": phobert_metrics,
        "epochs": args.epochs, "best_epoch": best_epoch, "batch_size": args.batch_size,
        "max_length": args.max_length, "train_last_layers": args.train_last_layers,
        "truncated_rows": truncated, "history": history,
        "seconds": round(time.time() - started, 1),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"PhoBERT holdout: {phobert_metrics}", flush=True)
    print(f"Saved {args.output}", flush=True)


if __name__ == "__main__":
    main()
