"""Shared training and artifact handling for offline room ranker experiments."""

import argparse
import json
import sys
from importlib import import_module
from pathlib import Path
from tempfile import TemporaryDirectory
from time import perf_counter

ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = ROOT / "data"
EXPERIMENT_DIR = ROOT / "storage" / "experiments"
SPLIT_SEED = 42
SPLIT_NAMES = ("train", "validation", "test")


def _user_split(users: list[dict], split_from: Path | None) -> dict:
    if split_from is not None:
        split = json.loads(split_from.read_text(encoding="utf-8"))["user_split"]
        for name in SPLIT_NAMES:
            if not isinstance(split[name], list) or not split[name]:
                raise ValueError(f"Expected a non-empty list for split {name!r}.")
        assigned = [user_id for name in SPLIT_NAMES for user_id in split[name]]
        if len(assigned) != len(users) or set(assigned) != {user["id"] for user in users}:
            raise ValueError("Saved user split must assign each current user exactly once.")
        return split

    from sklearn.model_selection import train_test_split

    # Split users first. Each subset then gets its own chronological room split.
    ordered_ids = sorted(user["id"] for user in users)
    train_ids, holdout_ids = train_test_split(ordered_ids, test_size=0.30, random_state=SPLIT_SEED)
    validation_ids, test_ids = train_test_split(
        holdout_ids, test_size=0.50, random_state=SPLIT_SEED
    )
    return {"seed": SPLIT_SEED, "train": train_ids, "validation": validation_ids, "test": test_ids}


def main(model_name: str, model_filename: str, *, trainer_module: str = "trainer") -> None:
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=f"Train the {model_name} ranker using local data.")
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=EXPERIMENT_DIR / model_name,
        help=f"Directory for {model_filename} and training_meta.json. Use a new path for each run.",
    )
    parser.add_argument(
        "--split-from",
        type=Path,
        help="Reuse the user split from a previous training_meta.json file.",
    )
    args = parser.parse_args()
    output_dir = args.output_dir.resolve()
    if output_dir.exists():
        parser.error(f"Output directory already exists: {output_dir}")

    from app.recommender.faiss.indexer import build_index
    from app.reranker.dataset import generate_training_data

    users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    try:
        split = _user_split(users, args.split_from)
    except (OSError, KeyError, TypeError, ValueError) as exc:
        parser.error(f"Could not prepare the user split: {exc}")
    users_by_id = {user["id"]: user for user in users}
    train_users = [users_by_id[user_id] for user_id in split["train"]]
    validation_users = [users_by_id[user_id] for user_id in split["validation"]]
    events = json.loads((DATA_DIR / "view_history.json").read_text(encoding="utf-8"))
    rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))
    trainer = import_module(f"app.reranker.{model_name}.{trainer_module}")
    bundle = build_index(rooms)

    history_by_user = {}
    for event in events:
        history_by_user.setdefault(event["user_id"], []).append(event)

    print("Preparing training samples...")
    training_data = generate_training_data(train_users, history_by_user, bundle)
    print("Preparing validation samples...")
    validation_data = generate_training_data(validation_users, history_by_user, bundle)

    started = perf_counter()
    model, meta = trainer.train_model(*training_data, validation_data=validation_data)
    meta["train_time_sec"] = round(perf_counter() - started, 3)
    meta["user_split"] = split
    meta["validation"]["candidate_source"] = "training-style samples with held-out positives"

    output_dir.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(dir=output_dir.parent) as temporary:
        staged = Path(temporary)
        model_path = staged / model_filename
        if hasattr(model, "model_to_string"):
            # LightGBM's native file writer can fail on Unicode Windows paths.
            model_path.write_text(model.model_to_string(), encoding="utf-8")
        else:
            model.save_model(str(model_path))
        (staged / "training_meta.json").write_text(
            json.dumps(meta, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        staged.rename(output_dir)
    print(f"Saved model and training metadata to {output_dir}")
