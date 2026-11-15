#!/usr/bin/env python3
"""Local SMTP catcher on 127.0.0.1:1025 for Glottolog notification tests.

Stores .eml files under data/glottolog/mail-catcher/.
Requires: pip install aiosmtpd
"""
from __future__ import annotations

import time
from email import policy
from email.parser import BytesParser
from pathlib import Path

from aiosmtpd.controller import Controller

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "data" / "glottolog" / "mail-catcher"
OUT.mkdir(parents=True, exist_ok=True)


class Handler:
    async def handle_DATA(self, server, session, envelope):  # noqa: ANN001,N802
        data = envelope.content
        msg = BytesParser(policy=policy.default).parsebytes(data)
        stamp = time.strftime("%Y%m%dT%H%M%SZ", time.gmtime())
        path = OUT / f"{stamp}.eml"
        path.write_bytes(data)
        (OUT / "latest.eml").write_bytes(data)
        print(
            f"SMTP caught → {path} | To={envelope.rcpt_tos} | Subject={msg.get('subject')}",
            flush=True,
        )
        return "250 OK"


def main() -> None:
    controller = Controller(Handler(), hostname="127.0.0.1", port=1025)
    controller.start()
    print("Local SMTP catcher on 127.0.0.1:1025", flush=True)
    print(f"Messages: {OUT}", flush=True)
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass
    finally:
        controller.stop()


if __name__ == "__main__":
    main()
