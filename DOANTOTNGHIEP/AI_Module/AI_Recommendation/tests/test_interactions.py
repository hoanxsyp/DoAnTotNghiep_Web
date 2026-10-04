import unittest

from app.recommender.interactions import (
    aggregate_interactions,
    profile_weight,
    relevance_label,
)


class InteractionAggregationTests(unittest.TestCase):
    def test_legacy_event_is_a_single_view(self):
        events = [{
            "room_id": "r1",
            "viewed_at": "2026-09-20T08:00:00+00:00",
        }]
        interaction = aggregate_interactions(events)["r1"]
        self.assertEqual(interaction["view_count"], 1)
        self.assertEqual(relevance_label(interaction), 1)
        self.assertEqual(profile_weight(interaction), 1.0)

    def test_same_session_is_counted_once(self):
        events = [
            {
                "room_id": "r1",
                "event_type": "view",
                "occurred_at": "2026-09-20T08:00:00+00:00",
                "session_id": "s1",
            },
            {
                "room_id": "r1",
                "event_type": "view",
                "occurred_at": "2026-09-20T08:10:00+00:00",
                "session_id": "s1",
            },
        ]
        self.assertEqual(aggregate_interactions(events)["r1"]["view_count"], 1)

    def test_three_sessions_are_many_views(self):
        events = [
            {
                "room_id": "r1",
                "event_type": "view",
                "occurred_at": f"2026-09-{day:02d}T08:00:00+00:00",
                "session_id": f"s{day}",
            }
            for day in (18, 19, 20)
        ]
        interaction = aggregate_interactions(events)["r1"]
        self.assertEqual(relevance_label(interaction), 2)
        self.assertEqual(profile_weight(interaction), 2.0)

    def test_favorite_has_highest_relevance_and_weight(self):
        events = [
            {
                "room_id": "r1",
                "event_type": "view",
                "occurred_at": "2026-09-20T08:00:00+00:00",
            },
            {
                "room_id": "r1",
                "event_type": "favorite",
                "occurred_at": "2026-09-20T09:00:00+00:00",
            },
        ]
        interaction = aggregate_interactions(events)["r1"]
        self.assertEqual(relevance_label(interaction), 3)
        self.assertEqual(profile_weight(interaction), 4.0)

    def test_unfavorite_restores_view_relevance(self):
        events = [
            {
                "room_id": "r1",
                "event_type": "view",
                "occurred_at": "2026-09-20T08:00:00+00:00",
            },
            {
                "room_id": "r1",
                "event_type": "favorite",
                "occurred_at": "2026-09-20T09:00:00+00:00",
            },
            {
                "room_id": "r1",
                "event_type": "unfavorite",
                "occurred_at": "2026-09-20T10:00:00+00:00",
            },
        ]
        interaction = aggregate_interactions(events)["r1"]
        self.assertFalse(interaction["is_favorite"])
        self.assertEqual(relevance_label(interaction), 1)
        self.assertEqual(profile_weight(interaction), 1.0)


if __name__ == "__main__":
    unittest.main()
