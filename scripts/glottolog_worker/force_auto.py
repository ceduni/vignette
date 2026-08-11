#!/usr/bin/env python3
"""Force an automatic Glottolog run for local testing.

Requires a running worker on the same PostgreSQL database.

  python3 -m glottolog_worker.force_auto --due-now
  python3 -m glottolog_worker.force_auto --due-in 15
  python3 -m glottolog_worker.force_auto --due-now --simulate-change
"""

from __future__ import annotations

import argparse
import sys
import time
from datetime import timedelta

from . import db
from .config import load_settings


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Force a Python-worker automatic Glottolog update for testing."
    )
    when = parser.add_mutually_exclusive_group()
    when.add_argument(
        "--due-now",
        action="store_true",
        help="Set next_run_at a few seconds in the past (default).",
    )
    when.add_argument(
        "--due-in",
        type=int,
        metavar="SECONDS",
        help="Set next_run_at this many seconds in the future.",
    )
    parser.add_argument(
        "--frequency-days",
        type=int,
        default=None,
        help="Optional: set admin frequency_days before scheduling.",
    )
    parser.add_argument(
        "--simulate-change",
        action="store_true",
        help="Alter last_imported_checksum so the next run downloads again.",
    )
    parser.add_argument(
        "--no-notify",
        action="store_true",
        help="Skip pg_notify wake.",
    )
    return parser.parse_args(argv)


def _notify(cur, payload: str) -> None:
    cur.execute("SELECT pg_notify('glottolog_wake', %s)", (payload,))


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    settings = load_settings()
    due_in = args.due_in
    if due_in is None and not args.due_now:
        due_in = -5
    elif args.due_now:
        due_in = -5

    if due_in is not None and due_in < -86400:
        print("--due-in is unreasonably negative", file=sys.stderr)
        return 2
    if args.frequency_days is not None and args.frequency_days < 1:
        print("--frequency-days must be >= 1", file=sys.stderr)
        return 2

    with db.connect(settings) as conn:
        admin = db.fetch_admin_settings(conn)
        if admin is None:
            print("No glottolog_admin_settings row — start Java/pgdev once first.", file=sys.stderr)
            return 1

        must_touch_settings = (not admin.auto_update_enabled) or (
            args.frequency_days is not None and args.frequency_days != admin.frequency_days
        )

        with conn.cursor() as cur:
            if must_touch_settings:
                now = db.utcnow()
                freq = args.frequency_days if args.frequency_days is not None else admin.frequency_days
                cur.execute(
                    """
                    UPDATE glottolog_admin_settings
                    SET auto_update_enabled = TRUE,
                        frequency_days = COALESCE(%s, frequency_days),
                        updated_at = %s,
                        updated_by_username = COALESCE(updated_by_username, 'force_auto')
                    WHERE id = 1
                    """,
                    (freq, now),
                )
                if not args.no_notify:
                    _notify(cur, "force_auto_enable")
                conn.commit()
                print("Enabled auto-update; waiting 2s for worker config resync…", flush=True)
                time.sleep(2)

            now = db.utcnow()
            next_run_at = now + timedelta(seconds=due_in)
            checksum_sql = ""
            params: list = [
                next_run_at,
                f"Test: auto run forced for {next_run_at.isoformat()}",
                now,
            ]
            if args.simulate_change:
                checksum_sql = ", last_imported_checksum = %s"
                params.append(f"test:force-auto-{int(now.timestamp())}")
            params.append(1)

            cur.execute(
                f"""
                UPDATE glottolog_pipeline_state
                SET next_run_at = %s,
                    current_status = 'WAITING',
                    status_message = %s,
                    updated_at = %s
                    {checksum_sql}
                WHERE id = %s
                """,
                params,
            )
            if not args.no_notify:
                _notify(cur, "force_auto")
            conn.commit()

        admin = db.fetch_admin_settings(conn)
        state = db.fetch_pipeline_state(conn)

    print("Forced automatic schedule:", flush=True)
    print(f"  auto_update_enabled = {admin.auto_update_enabled}", flush=True)
    print(f"  frequency_days      = {admin.frequency_days}", flush=True)
    print(f"  next_run_at         = {state.next_run_at}", flush=True)
    print(f"  last_imported_checksum = {state.last_imported_checksum}", flush=True)
    if due_in >= 0:
        print(f"\nAUTO should fire in ~{due_in}s.", flush=True)
    else:
        print("\nAUTO should fire immediately.", flush=True)
    if args.simulate_change:
        print("Checksum altered → expect DOWNLOAD/IMPORT.", flush=True)
    else:
        print("Checksum unchanged → expect a fast NO_CHANGE check.", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
