"""Notify the configured admin email after each Glottolog update.

Uses Gmail SMTP when credentials are set in .env; otherwise a local catcher
on 127.0.0.1:1025. Failures are archived under data/glottolog/outbox/ and
logged in glottolog_notification.
"""

from __future__ import annotations

import smtplib
from datetime import datetime, timezone
from email.message import EmailMessage
from pathlib import Path

import psycopg

from .config import Settings, _REPO_ROOT


def _outbox_dir(settings: Settings) -> Path:
    path = settings.data_dir / "outbox"
    path.mkdir(parents=True, exist_ok=True)
    return path


def build_notification_body(
    *,
    trigger_type: str,
    pipeline_status: str,
    triggered_by: str,
    history_id: int | None,
    message: str,
    inserted: int | None = None,
    updated: int | None = None,
    database_count: int | None = None,
    next_run_at: datetime | None = None,
    finished_at: datetime | None = None,
) -> str:
    finished = finished_at or datetime.now(timezone.utc)
    status_label = {
        "NO_CHANGE": "No change (Zenodo checksum unchanged)",
        "COMPLETED": "Import completed",
        "FAILED_FETCH": "Failed while fetching Zenodo",
        "FAILED_VALIDATION": "Failed validation",
        "FAILED_TRANSFORM": "Failed transform",
        "FAILED_IMPORT": "Failed import",
    }.get(pipeline_status, pipeline_status)

    lines = [
        "Vignette — Glottolog update summary",
        "=" * 40,
        "",
        f"Finished at:     {finished.isoformat()}",
        f"Trigger:         {trigger_type}",
        f"Result:          {status_label}",
        f"Triggered by:    {triggered_by}",
    ]
    if history_id is not None:
        lines.append(f"History id:      {history_id}")
    lines.append("")
    lines.append("Counts")
    lines.append("-" * 40)
    lines.append(f"Languages in DB: {database_count if database_count is not None else '—'}")
    lines.append(f"Added:           {inserted if inserted is not None else '—'}")
    lines.append(f"Updated:         {updated if updated is not None else '—'}")
    lines.append("")
    lines.append("Schedule")
    lines.append("-" * 40)
    if next_run_at is not None:
        lines.append(f"Next automatic update: {next_run_at.isoformat()}")
    else:
        lines.append("Next automatic update: not scheduled (auto-update off or unknown)")
    lines.append("")
    lines.append("Details")
    lines.append("-" * 40)
    lines.append(message)
    lines.append("")
    lines.append("— Vignette Glottolog worker")
    return "\n".join(lines)


def _send_smtp(settings: Settings, *, to_email: str, subject: str, body: str) -> str:
    smtp = settings.smtp
    msg = EmailMessage()
    msg["Subject"] = subject
    msg["From"] = smtp.from_addr
    msg["To"] = to_email
    msg.set_content(body)
    with smtplib.SMTP(smtp.host, smtp.port, timeout=30) as client:
        if smtp.starttls:
            client.starttls()
        if smtp.username:
            client.login(smtp.username, smtp.password)
        client.send_message(msg)
    return f"smtp://{smtp.host}:{smtp.port} ({smtp.provider}) → {to_email}"


def _write_outbox(settings: Settings, *, to_email: str, subject: str, body: str) -> str:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    safe = "".join(ch if ch.isalnum() or ch in "._-" else "_" for ch in to_email)
    out_path = _outbox_dir(settings) / f"{stamp}_{safe}.txt"
    out_path.write_text(f"To: {to_email}\nSubject: {subject}\n\n{body}\n", encoding="utf-8")
    latest = _outbox_dir(settings) / "latest.txt"
    latest.write_text(out_path.read_text(encoding="utf-8"), encoding="utf-8")
    try:
        shown = out_path.relative_to(_REPO_ROOT)
    except ValueError:
        shown = out_path
    return f"outbox → {shown}"


def persist_notification(
    conn: psycopg.Connection,
    *,
    to_email: str,
    subject: str,
    body: str,
    delivery_status: str,
    delivery_detail: str | None,
    history_id: int | None = None,
    trigger_type: str | None = None,
    pipeline_status: str | None = None,
) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO glottolog_notification (
                created_at, to_email, subject, body,
                delivery_status, delivery_detail,
                history_id, trigger_type, pipeline_status
            )
            VALUES (now(), %s, %s, %s, %s, %s, %s, %s, %s)
            """,
            (
                to_email[:255],
                subject[:500],
                body[:8000],
                delivery_status[:32],
                (delivery_detail or "")[:1000] or None,
                history_id,
                trigger_type,
                pipeline_status,
            ),
        )
    conn.commit()


def send_update_notification(
    settings: Settings,
    conn: psycopg.Connection | None,
    *,
    to_email: str,
    subject: str,
    body: str,
    history_id: int | None = None,
    trigger_type: str | None = None,
    pipeline_status: str | None = None,
) -> str:
    """Send email and persist a notification row."""
    to_email = (to_email or "").strip()
    if not to_email:
        return "skipped (no notification email)"

    delivery_status = "FAILED"
    delivery_detail: str
    try:
        if settings.smtp.provider in {"gmail", "google"} and not settings.smtp.configured:
            raise RuntimeError(
                "Gmail SMTP selected but GLOTTOLOG_SMTP_USER / "
                "GLOTTOLOG_SMTP_PASSWORD are missing."
            )
        delivery_detail = _send_smtp(
            settings, to_email=to_email, subject=subject, body=body
        )
        delivery_status = "SENT"
    except Exception as smtp_exc:  # noqa: BLE001
        try:
            outbox_detail = _write_outbox(
                settings, to_email=to_email, subject=subject, body=body
            )
            delivery_status = "OUTBOX"
            delivery_detail = f"smtp failed ({smtp_exc}); {outbox_detail}"
        except Exception as outbox_exc:  # noqa: BLE001
            delivery_status = "FAILED"
            delivery_detail = f"smtp failed ({smtp_exc}); outbox failed ({outbox_exc})"

    if conn is not None:
        try:
            persist_notification(
                conn,
                to_email=to_email,
                subject=subject,
                body=body,
                delivery_status=delivery_status,
                delivery_detail=delivery_detail,
                history_id=history_id,
                trigger_type=trigger_type,
                pipeline_status=pipeline_status,
            )
        except Exception as db_exc:  # noqa: BLE001
            delivery_detail = f"{delivery_detail}; db log failed ({db_exc})"

    return f"{delivery_status}: {delivery_detail}"
