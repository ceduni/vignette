"""Lightweight tests that do not require PostgreSQL."""

from __future__ import annotations

import unittest

from glottolog_worker.pipeline import PipelineError, validate_rows


class ValidateRowsTest(unittest.TestCase):
    def test_empty_rejected(self) -> None:
        with self.assertRaises(PipelineError):
            validate_rows([])

    def test_missing_id_rejected(self) -> None:
        with self.assertRaises(PipelineError):
            validate_rows([{"id": "", "name": "French", "level": "language"}])

    def test_valid_accepted(self) -> None:
        validate_rows([{"id": "fra", "name": "French", "level": "language"}])


if __name__ == "__main__":
    unittest.main()
