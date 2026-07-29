"""Tests for wait scheduling (no PostgreSQL required for these cases)."""

from __future__ import annotations

import unittest
from datetime import datetime, timedelta, timezone
from types import SimpleNamespace
from unittest.mock import MagicMock

from glottolog_worker.config import Settings
from glottolog_worker import db
from glottolog_worker.worker import compute_wait_seconds


def _settings(**overrides) -> Settings:
    from glottolog_worker.config import SmtpSettings

    base = dict(
        database_url=None,
        pghost="localhost",
        pgport=5432,
        pgdatabase="vignette",
        pguser="vignette",
        pgpassword="",
        safety_check_seconds=6 * 3600,
        lock_ttl_seconds=600,
        request_stale_seconds=300,
        heartbeat_interval_seconds=30,
        retry_base_seconds=60,
        retry_max_seconds=3600,
        glottolog_version="5.3",
        worker_id="test",
        cache_dir=MagicMock(),
        data_dir=MagicMock(),
        smtp=SmtpSettings(
            host="127.0.0.1",
            port=1025,
            username="",
            password="",
            from_addr="vignette@localhost",
            starttls=False,
            provider="local",
        ),
    )
    base.update(overrides)
    return Settings(**base)


class ComputeWaitSecondsTest(unittest.TestCase):
    def test_pending_request_waits_zero(self) -> None:
        conn = MagicMock()
        admin = db.AdminSettings(True, 14, None)
        state = SimpleNamespace(
            next_run_at=datetime.now(timezone.utc) + timedelta(days=14),
            next_retry_at=None,
            retry_count=0,
        )
        with unittest.mock.patch(
            "glottolog_worker.worker.db.peek_claimable_request", return_value=True
        ):
            self.assertEqual(compute_wait_seconds(conn, _settings(), admin, state), 0.0)

    def test_uses_next_run_when_sooner_than_safety(self) -> None:
        conn = MagicMock()
        admin = db.AdminSettings(True, 14, None)
        soon = datetime.now(timezone.utc) + timedelta(minutes=30)
        state = SimpleNamespace(next_run_at=soon, next_retry_at=None, retry_count=0)
        with unittest.mock.patch(
            "glottolog_worker.worker.db.peek_claimable_request", return_value=False
        ):
            wait = compute_wait_seconds(conn, _settings(), admin, state)
        self.assertLess(wait, 31 * 60)
        self.assertGreater(wait, 0)

    def test_auto_off_falls_back_to_safety(self) -> None:
        conn = MagicMock()
        admin = db.AdminSettings(False, 14, None)
        state = SimpleNamespace(
            next_run_at=datetime.now(timezone.utc) + timedelta(days=14),
            next_retry_at=None,
            retry_count=0,
        )
        with unittest.mock.patch(
            "glottolog_worker.worker.db.peek_claimable_request", return_value=False
        ):
            wait = compute_wait_seconds(
                conn, _settings(safety_check_seconds=7200), admin, state
            )
        self.assertEqual(wait, 7200.0)


if __name__ == "__main__":
    unittest.main()
