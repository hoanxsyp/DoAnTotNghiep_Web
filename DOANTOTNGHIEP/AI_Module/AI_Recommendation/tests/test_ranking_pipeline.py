import unittest

from app.features.extractor import get_extractor
from app.reranker.features import AMENITIES, CITIES, FEATURE_DIM, ROOM_TYPES
from app.reranker.trainer import _temporal_split


class RankingPipelineTests(unittest.TestCase):
    def test_reranker_vocab_matches_room_feature_config(self):
        config = get_extractor().config
        categorical = {item["field"]: item["vocab"] for item in config["categorical"]}
        multi_label = {item["field"]: item["vocab"] for item in config["multi_label"]}
        self.assertEqual(CITIES, categorical["city"])
        self.assertEqual(ROOM_TYPES, categorical["room_type"])
        self.assertEqual(AMENITIES, multi_label["amenities"])
        self.assertEqual(FEATURE_DIM, 23 + len(AMENITIES))

    def test_temporal_split_keeps_room_events_together(self):
        events = [
            {
                "room_id": f"r{room}",
                "event_type": "view",
                "occurred_at": f"2026-09-{10 + room:02d}T08:00:00+00:00",
            }
            for room in range(1, 7)
        ]
        events.append({
            "room_id": "r6",
            "event_type": "favorite",
            "occurred_at": "2026-09-16T09:00:00+00:00",
        })

        train_events, test_events = _temporal_split(events)
        train_rooms = {event["room_id"] for event in train_events}
        test_rooms = {event["room_id"] for event in test_events}
        self.assertFalse(train_rooms & test_rooms)
        self.assertIn("r6", test_rooms)


if __name__ == "__main__":
    unittest.main()
