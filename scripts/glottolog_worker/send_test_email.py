#!/usr/bin/env python3
"""Send a one-shot Glottolog notification email.

  cd scripts
  python3 -m glottolog_worker.send_test_email --to you@example.com
"""

from __future__ import annotations

import argparse
import sys

from . import db
from .config import load_settings
from .notify import send_update_notification


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Send a test Glottolog admin email.")
    parser.add_argument(
        "--to",
        default=None,
        help="Destination email (default: notification_email from admin settings).",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    settings = load_settings()

    to_email = (args.to or "").strip()
    with db.connect(settings) as conn:
        if not to_email:
            admin = db.fetch_admin_settings(conn)
            to_email = (admin.notification_email if admin else None) or ""
            to_email = to_email.strip()
        if not to_email:
            print(
                "No destination email. Pass --to you@example.com "
                "or set Notification email in the admin Schedule panel.",
                file=sys.stderr,
            )
            return 2

        subject = "[Vignette] Glottolog notification test"
        body = (
            "This is a test email from the Vignette Glottolog worker.\n\n"
            "If you received this, admin notifications are configured correctly.\n"
        )
        result = send_update_notification(
            settings,
            conn,
            to_email=to_email,
            subject=subject,
            body=body,
            history_id=None,
            trigger_type="TEST",
            pipeline_status="TEST",
        )
        print(f"Result: {result}", flush=True)
        if result.startswith("SENT:"):
            print(f"Check the inbox for {to_email} (and spam folder).", flush=True)
            return 0
        print(
            "Email was NOT delivered via SMTP. Check GLOTTOLOG_SMTP_* in .env.",
            file=sys.stderr,
        )
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
