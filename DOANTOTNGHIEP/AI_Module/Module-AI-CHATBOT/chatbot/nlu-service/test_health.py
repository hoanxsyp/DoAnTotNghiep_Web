import unittest

from fastapi import Response

import app


class HealthEndpointTest(unittest.TestCase):
    def setUp(self):
        self.original = dict(app._M)

    def tearDown(self):
        app._M.clear()
        app._M.update(self.original)

    def test_live_is_ok(self):
        self.assertEqual({"status": "ok"}, app.live())

    def test_ready_returns_200_when_models_are_ready(self):
        app._M["ready"] = True
        response = Response()
        payload = app.ready(response)
        self.assertEqual(200, response.status_code)
        self.assertEqual("ok", payload["status"])

    def test_ready_returns_503_when_models_are_not_ready(self):
        app._M["ready"] = False
        response = Response()
        payload = app.ready(response)
        self.assertEqual(503, response.status_code)
        self.assertEqual("degraded", payload["status"])


if __name__ == "__main__":
    unittest.main()
