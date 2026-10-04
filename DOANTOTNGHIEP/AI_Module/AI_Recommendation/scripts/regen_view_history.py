"""Regenerate weighted interaction events while keeping rooms and users."""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(ROOT))

from scripts.generate_interactions import generate_interactions, summarize_interactions


DATA_DIR = ROOT / "data"


def main() -> None:
    users = json.loads((DATA_DIR / "users.json").read_text(encoding="utf-8"))
    rooms = json.loads((DATA_DIR / "rooms.json").read_text(encoding="utf-8"))
    events = generate_interactions(users, rooms, seed=42)

    (DATA_DIR / "view_history.json").write_text(
        json.dumps(events, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print(f"Users : {len(users)}")
    print(f"Rooms : {len(rooms)}")
    for name, value in summarize_interactions(events).items():
        print(f"{name:<16}: {value}")


if __name__ == "__main__":
    main()
