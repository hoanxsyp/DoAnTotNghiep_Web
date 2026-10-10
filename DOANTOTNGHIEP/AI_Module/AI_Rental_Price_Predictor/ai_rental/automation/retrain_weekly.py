# -*- coding: utf-8 -*-
"""
Run one weekly refresh of the rental price model.

Flow:
1. Crawl the configured rental sources.
2. Enrich detail pages.
3. Rebuild the cleaned dataset.
4. Train the comparison model and the monotonic serving model.
5. Save a run log and compact retrain history.

Schedule this script every 7 days with Windows Task Scheduler or cron.
"""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
LOG_DIR = ROOT / "logs"
RUN_HISTORY = ROOT / "models" / "hanoi_all" / "retrain_runs.jsonl"

DISTRICT_SLUGS = [
    "quan-ba-dinh",
    "quan-bac-tu-liem",
    "quan-cau-giay",
    "quan-dong-da",
    "quan-ha-dong",
    "quan-hai-ba-trung",
    "quan-hoan-kiem",
    "quan-hoang-mai",
    "quan-long-bien",
    "quan-nam-tu-liem",
    "quan-tay-ho",
    "quan-thanh-xuan",
]


def now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def rel(path: str) -> str:
    return str(ROOT / path)


def append_jsonl(path: Path, payload: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(payload, ensure_ascii=False) + "\n")


def load_json(path: Path) -> dict:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as f:
        return json.load(f)


def count_csv_rows(path: Path) -> int | None:
    if not path.exists():
        return None
    with path.open(encoding="utf-8-sig", errors="replace") as f:
        return max(sum(1 for _ in f) - 1, 0)


def run_step(name: str, command: list[str], log_file, status: dict) -> None:
    started = time.time()
    entry = {
        "name": name,
        "command": command,
        "started_at": now_iso(),
        "return_code": None,
        "duration_seconds": None,
    }
    status["commands"].append(entry)

    line = f"\n=== {name} ===\n$ {' '.join(command)}\n"
    print(line, end="", flush=True)
    log_file.write(line)
    log_file.flush()

    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    assert process.stdout is not None
    for output_line in process.stdout:
        print(output_line, end="", flush=True)
        log_file.write(output_line)

    entry["return_code"] = process.wait()
    entry["duration_seconds"] = round(time.time() - started, 2)
    log_file.flush()

    if entry["return_code"] != 0:
        raise RuntimeError(f"Step failed: {name} (exit {entry['return_code']})")


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max-pages", type=int, default=20,
                    help="Max pages per phongtro123 district crawl.")
    ap.add_argument("--mogi-max-details", type=int, default=450,
                    help="Max Mogi detail pages to crawl; set 0 to skip Mogi.")
    ap.add_argument("--workers", type=int, default=6,
                    help="Workers for phongtro123 detail enrichment.")
    ap.add_argument("--districts", nargs="*", default=None,
                    help="Optional district slug subset for phongtro123.")
    ap.add_argument("--skip-crawl", action="store_true",
                    help="Only preprocess and retrain using existing raw data.")
    ap.add_argument("--skip-enrich", action="store_true",
                    help="Skip detail enrichment after crawling.")
    ap.add_argument("--train-keras", action="store_true",
                    help="Also retrain the optional Keras MLP model.")
    return ap.parse_args()


def main() -> int:
    args = parse_args()
    run_id = datetime.now().strftime("%Y%m%d_%H%M%S")
    LOG_DIR.mkdir(parents=True, exist_ok=True)
    log_path = LOG_DIR / f"retrain_{run_id}.log"

    status = {
        "run_id": run_id,
        "started_at": now_iso(),
        "ended_at": None,
        "status": "running",
        "log_path": str(log_path),
        "commands": [],
        "dataset_rows": None,
        "metadata": {},
        "monotonic_metadata": {},
        "error": None,
    }

    try:
        with log_path.open("w", encoding="utf-8") as log_file:
            if not args.skip_crawl:
                districts = args.districts or DISTRICT_SLUGS
                for district in districts:
                    run_step(
                        f"crawl phongtro123 {district}",
                        [sys.executable, rel("crawlers/crawl_phongtro123.py"),
                         "--district", district, "--max-pages", str(args.max_pages)],
                        log_file,
                        status,
                    )

                if args.mogi_max_details > 0:
                    run_step(
                        "crawl mogi",
                        [sys.executable, rel("crawlers/crawl_mogi.py"),
                         "--max-details", str(args.mogi_max_details)],
                        log_file,
                        status,
                    )

            if not args.skip_enrich:
                run_step(
                    "enrich phongtro123 detail",
                    [sys.executable, rel("crawlers/enrich_detail.py"),
                     "--workers", str(args.workers)],
                    log_file,
                    status,
                )

            run_step(
                "preprocess all raw data",
                [sys.executable, rel("preprocessing/preprocess_all.py")],
                log_file,
                status,
            )
            run_step(
                "train comparison model",
                [sys.executable, rel("training/train_compare.py"),
                 "--data", "data/processed/hanoi_all_clean.csv", "--name", "hanoi_all"],
                log_file,
                status,
            )
            run_step(
                "train monotonic serving model",
                [sys.executable, rel("training/train_monotonic.py"),
                 "--data", "data/processed/hanoi_all_clean.csv", "--name", "hanoi_all"],
                log_file,
                status,
            )

            if args.train_keras:
                run_step(
                    "train keras model",
                    [sys.executable, rel("training/train_keras.py")],
                    log_file,
                    status,
                )

        status["dataset_rows"] = count_csv_rows(ROOT / "data" / "processed" / "hanoi_all_clean.csv")
        status["metadata"] = load_json(ROOT / "models" / "hanoi_all" / "metadata.json")
        status["monotonic_metadata"] = load_json(ROOT / "models" / "hanoi_all" / "monotonic_metadata.json")
        status["status"] = "success"
        return 0
    except Exception as exc:
        status["status"] = "failed"
        status["error"] = str(exc)
        print(f"\nFAILED: {exc}", file=sys.stderr)
        return 1
    finally:
        status["ended_at"] = now_iso()
        append_jsonl(RUN_HISTORY, status)
        print(f"\nRetrain status: {status['status']}")
        print(f"Log: {log_path}")
        print(f"History: {RUN_HISTORY}")


if __name__ == "__main__":
    raise SystemExit(main())
