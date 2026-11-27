"""Run the offline LightGBM training experiment."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from scripts.train_ranker_experiment import main


if __name__ == "__main__":
    main("lightgbm", "model.txt", trainer_module="experiment")
