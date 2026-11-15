"""Entry point: ``python -m glottolog_worker`` from scripts/, or this file directly."""

from __future__ import annotations

import sys
from pathlib import Path


def _bootstrap_and_run() -> int:
    # Support: python scripts/glottolog_worker/__main__.py
    if __package__ is None or __package__ == "":
        scripts_dir = Path(__file__).resolve().parent.parent
        if str(scripts_dir) not in sys.path:
            sys.path.insert(0, str(scripts_dir))
        from glottolog_worker.worker import main

        return main(sys.argv[1:])

    from .worker import main

    return main(sys.argv[1:])


if __name__ == "__main__":
    raise SystemExit(_bootstrap_and_run())
