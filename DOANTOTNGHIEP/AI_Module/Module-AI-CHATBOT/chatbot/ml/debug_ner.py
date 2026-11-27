"""Trace one real Trainer update on rows from a NER training JSONL file."""

import argparse
import json
from tempfile import TemporaryDirectory

import torch
from transformers import (
    AutoModelForTokenClassification,
    DataCollatorForTokenClassification,
    Trainer,
    set_seed,
)

from nlu_encoding import DEFAULT_BASE_MODEL, MAX_LENGTH, decode_bio_offsets
from phobert_encoding import PhoBertEncoder
from train_ner import BIO_LABELS, DATA_DIR, ID2LABEL, LABEL2ID, build_dataset, load_jsonl, make_training_args


def print_tokens(tokenizer, sample, spans, text, predicted=None):
    tokens = tokenizer.convert_ids_to_tokens(sample["input_ids"])
    print(f"{'#':>2} {'token':<19} {'id':>6} {'offset':<11} {'gold':<18} "
          + ("prediction" if predicted is not None else "original text"))
    for i, (token, token_id, span, label) in enumerate(
        zip(tokens, sample["input_ids"], spans, sample["labels"])
    ):
        offset = f"{span[0]}:{span[1]}" if span is not None else "-"
        gold = ID2LABEL.get(label, "-100 (ignore)")
        value = text[span[0]:span[1]] if span is not None else ""
        if predicted is not None:
            value = predicted[i]
        print(f"{i:2d} {token:<19} {token_id:6d} {offset:<11} {gold:<18} {value}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--synth-file", default="ner_train_chat_synthetic_2750.jsonl")
    parser.add_argument("--row", type=int, default=3, help="One-based line number in the JSONL file")
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--base-model", default=DEFAULT_BASE_MODEL)
    parser.add_argument("--max-length", type=int, default=MAX_LENGTH)
    options = parser.parse_args()

    path = DATA_DIR / options.synth_file
    rows = load_jsonl(path)
    if not 1 <= options.row <= len(rows):
        parser.error(f"--row must be between 1 and {len(rows)}")
    if options.batch_size < 1 or options.max_length < 3:
        parser.error("--batch-size must be positive and --max-length at least 3")

    index = options.row - 1
    begin = index // options.batch_size * options.batch_size
    batch_rows = rows[begin:begin + options.batch_size]
    row = rows[index]
    text = row["text"]
    print(f"\n[1] JSONL input: {path.name}, row {options.row}")
    print(json.dumps(row, ensure_ascii=False))

    seed = 42
    learning_rate = 3e-5
    set_seed(seed)
    encoder = PhoBertEncoder.from_pretrained(options.base_model)
    dataset, spans_by_row = build_dataset(encoder, batch_rows, options.max_length)
    sample = dataset[index - begin]
    spans = spans_by_row[index - begin]
    if all(label == -100 for record in dataset for label in record["labels"]):
        parser.error("the selected batch contains no tokens with a training label")
    print("\n[2] Input tensors before padding")
    print("VnCoreNLP:", encoder.segmented(text))
    print_tokens(encoder.tok, sample, spans, text)

    model = AutoModelForTokenClassification.from_pretrained(
        options.base_model, num_labels=len(BIO_LABELS), id2label=ID2LABEL, label2id=LABEL2ID
    )
    collator = DataCollatorForTokenClassification(encoder.tok)
    weights_before = model.classifier.weight.detach().cpu().clone()
    trace = {}

    def capture_forward(_model, _args, inputs, outputs):
        trace["batch"] = {key: inputs[key].detach().cpu() for key in ("input_ids", "attention_mask", "labels")}
        trace["logits_shape"] = tuple(outputs.logits.shape)
        trace["predictions"] = outputs.logits.detach().argmax(dim=-1).cpu()
        trace["loss"] = outputs.loss.detach().item()

    def capture_gradient(gradient):
        trace["classifier_gradient_norm"] = gradient.detach().norm().item()

    forward_hook = model.register_forward_hook(capture_forward, with_kwargs=True)
    gradient_hook = model.classifier.weight.register_hook(capture_gradient)
    print("\n[3] Running Trainer for one optimizer update", flush=True)
    try:
        with TemporaryDirectory(prefix="webtro-ner-debug-") as output_dir:
            args = make_training_args(output_dir, 1, options.batch_size, learning_rate, seed)
            args.max_steps = 1
            args.do_eval = False
            if hasattr(args, "eval_strategy"):
                args.eval_strategy = "no"
            else:
                args.evaluation_strategy = "no"
            args.disable_tqdm = True
            trainer = Trainer(model=model, args=args, train_dataset=dataset, data_collator=collator)
            trainer.train()
    finally:
        forward_hook.remove()
        gradient_hook.remove()

    batch = trace["batch"]
    actual_size = len(sample["input_ids"])
    batch_position = next(
        i for i, (ids, mask, gold) in enumerate(zip(batch["input_ids"], batch["attention_mask"], batch["labels"]))
        if ids[:actual_size].tolist() == sample["input_ids"]
        and int(mask.sum()) == actual_size
        and gold[:actual_size].tolist() == sample["labels"]
    )
    print(f"\n[4] Actual batch from Trainer, rows {begin + 1}–{begin + len(batch_rows)}")
    print("Device:", trainer.args.device)
    for name, tensor in batch.items():
        print(f"{name}: {tuple(tensor.shape)}")
    print(f"Selected sample position in batch: {batch_position + 1}")
    print(f"Padding tokens in batch: {int((batch['attention_mask'] == 0).sum())}")
    print(f"Labels used in loss: {int((batch['labels'] != -100).sum())}")
    print("Logits shape:", trace["logits_shape"])

    predicted = [ID2LABEL[i] for i in trace["predictions"][batch_position, :actual_size].tolist()]
    print("\n[5] Prediction for the selected sample before the update")
    print_tokens(encoder.tok, sample, spans, text, predicted)
    entities = [{key: entity[key] for key in ("text", "label", "start", "end")}
                for entity in decode_bio_offsets(text, spans, predicted)]
    print("Decoded entities:", json.dumps(entities, ensure_ascii=False))

    weight_delta = (model.classifier.weight.detach().cpu() - weights_before).abs().max().item()
    print("\n[6] Loss, backward and optimizer update")
    print(f"Batch loss: {trace['loss']:.6f}")
    print(f"Classifier gradient norm before clipping: {trace['classifier_gradient_norm']:.6f}")
    print(f"Largest absolute classifier weight change: {weight_delta:.8f}")
    print(f"Optimizer steps: {trainer.state.global_step}")


if __name__ == "__main__":
    main()
