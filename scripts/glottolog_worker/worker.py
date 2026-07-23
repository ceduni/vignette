"""Event-driven worker loop: sleep until next_run_at, wake on NOTIFY or safety check."""

from __future__ import annotations

import signal
import sys
import time
import traceback
from datetime import datetime, timedelta, timezone

from . import db
from .config import Settings, load_settings
from .notify import build_notification_body, send_update_notification
from .pipeline import PipelineError, run_pipeline

NOTIFY_CHANNEL = "glottolog_wake"

_stop = False


def _handle_signal(signum, _frame) -> None:  # noqa: ANN001
    global _stop
    print(f"Signal {signum} reçu — arrêt après le cycle en cours.", flush=True)
    _stop = True


def _as_aware(value: datetime | None) -> datetime | None:
    if value is None:
        return None
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)
    return value


def sync_admin_config(conn, settings: Settings) -> db.AdminSettings | None:
    admin = db.fetch_admin_settings(conn)
    if admin is None:
        print("glottolog_admin_settings id=1 introuvable — en attente.", flush=True)
        return None

    state = db.fetch_pipeline_state(conn)
    if db.config_needs_resync(admin, state):
        next_run = db.apply_config_snapshot(conn, admin)
        print(
            f"Config resynced: auto={admin.auto_update_enabled} "
            f"frequency_days={admin.frequency_days} next_run_at={next_run}",
            flush=True,
        )
    return admin


def compute_next_retry(settings: Settings, retry_count: int):
    delay = min(
        settings.retry_max_seconds,
        settings.retry_base_seconds * (2 ** max(0, retry_count - 1)),
    )
    return db.utcnow() + timedelta(seconds=delay)


def make_heartbeat(conn, settings: Settings, request_id: int | None):
    last = [0.0]

    def heartbeat() -> None:
        now = time.monotonic()
        if now - last[0] < settings.heartbeat_interval_seconds:
            return
        last[0] = now
        if not db.heartbeat_lock(conn, settings.worker_id, settings.lock_ttl_seconds):
            raise PipelineError(db.STATUS_FAILED_IMPORT, "Lost pipeline lock during run")
        if request_id is not None:
            db.heartbeat_request(conn, request_id)

    return heartbeat


def decide_work(
    conn,
    settings: Settings,
    admin: db.AdminSettings,
    state: db.PipelineState,
) -> tuple[str, str] | None:
    """
    Decide whether to run and how.
    Returns (trigger_type, triggered_by) or None if idle.
    Manual requests win over retry/auto. Lock is acquired separately.
    """
    now = db.utcnow()

    if db.peek_claimable_request(conn, settings.request_stale_seconds):
        return db.TRIGGER_MANUAL, "admin"  # username filled after claim

    if db.has_active_lock(state, now) or db.has_active_request(conn):
        return None

    next_retry = _as_aware(state.next_retry_at)
    if state.retry_count > 0 and next_retry is not None and now >= next_retry:
        return db.TRIGGER_RETRY, "system:retry-glottolog"

    if (
        admin.auto_update_enabled
        and admin.frequency_days
        and admin.frequency_days > 0
        and state.next_run_at is not None
        and now >= _as_aware(state.next_run_at)
    ):
        return db.TRIGGER_AUTO, "system:auto-glottolog"

    return None


def compute_wait_seconds(
    conn,
    settings: Settings,
    admin: db.AdminSettings,
    state: db.PipelineState,
) -> float:
    """
    How long to sleep before the next wake.
    Never used to "poll every N seconds for schedule" — schedule due-ness is
    decided only when we wake (next_run overdue, NOTIFY, safety, restart).
    """
    if db.peek_claimable_request(conn, settings.request_stale_seconds):
        return 0.0

    now = db.utcnow()
    waits: list[float] = [float(max(1, settings.safety_check_seconds))]

    next_retry = _as_aware(state.next_retry_at)
    if state.retry_count > 0 and next_retry is not None:
        waits.append(max(0.0, (next_retry - now).total_seconds()))

    next_run = _as_aware(state.next_run_at)
    if (
        admin.auto_update_enabled
        and admin.frequency_days
        and admin.frequency_days > 0
        and next_run is not None
    ):
        waits.append(max(0.0, (next_run - now).total_seconds()))

    return min(waits)


def wait_for_wake(listen_conn, timeout_seconds: float) -> str:
    """
    Block until NOTIFY on glottolog_wake, timeout, or stop signal.
    Returns: 'notify' | 'timeout' | 'stop'
    """
    if _stop:
        return "stop"
    if timeout_seconds <= 0:
        return "timeout"

    deadline = time.monotonic() + timeout_seconds
    while not _stop:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return "timeout"
        # Chunk so SIGINT can stop the worker without waiting hours.
        chunk = min(1.0, remaining)
        for notify in listen_conn.notifies(timeout=chunk, stop_after=1):
            print(
                f"Woken by NOTIFY {notify.channel} payload={notify.payload!r}",
                flush=True,
            )
            return "notify"
    return "stop"


def run_once(conn, settings: Settings, admin: db.AdminSettings) -> bool:
    """Attempt one unit of work. Returns True if a pipeline run was executed."""
    state = db.fetch_pipeline_state(conn)
    decision = decide_work(conn, settings, admin, state)
    if decision is None:
        if not admin.auto_update_enabled and state.current_status not in {
            db.STATUS_AUTO_DISABLED,
            db.STATUS_IDLE,
            db.STATUS_NO_CHANGE,
            db.STATUS_COMPLETED,
            db.STATUS_WAITING,
            db.STATUS_RETRY_SCHEDULED,
            db.STATUS_FAILED_FETCH,
            db.STATUS_FAILED_VALIDATION,
            db.STATUS_FAILED_TRANSFORM,
            db.STATUS_FAILED_IMPORT,
            db.STATUS_LOCKED,
        }:
            db.set_pipeline_status(
                conn,
                db.STATUS_AUTO_DISABLED,
                "Auto-update disabled; waiting for manual request",
            )
        return False

    trigger_type, triggered_by = decision

    if not db.try_acquire_lock(conn, settings.worker_id, settings.lock_ttl_seconds):
        print("Could not acquire pipeline lock — skipping.", flush=True)
        return False

    request = None
    request_id: int | None = None
    history_id: int | None = None

    try:
        if trigger_type == db.TRIGGER_MANUAL:
            request = db.claim_update_request(
                conn, settings.worker_id, settings.request_stale_seconds
            )
            if request is None:
                state = db.fetch_pipeline_state(conn)
                decision = decide_work(conn, settings, admin, state)
                if decision is None or decision[0] == db.TRIGGER_MANUAL:
                    print("No claimable request after lock — releasing.", flush=True)
                    return False
                trigger_type, triggered_by = decision
            else:
                request_id = request.id
                triggered_by = request.requested_by_username or "admin"
                print(f"Claimed update request id={request.id} by={triggered_by}", flush=True)
        else:
            print(f"Starting {trigger_type} run", flush=True)

        state = db.fetch_pipeline_state(conn)
        history_id = db.insert_history(
            conn,
            triggered_by=triggered_by,
            trigger_type=trigger_type,
            glottolog_version=settings.glottolog_version,
            worker_id=settings.worker_id,
            retry_count=state.retry_count,
        )
        if request_id is not None:
            db.mark_request_running(conn, request_id, history_id)

        heartbeat = make_heartbeat(conn, settings, request_id)
        result = run_pipeline(conn, settings, history_id=history_id, heartbeat=heartbeat)

        if request_id is not None:
            db.finish_request(conn, request_id, success=True)

        next_run = db.schedule_next_run(conn, admin)
        idle_message = result.message
        if next_run is not None:
            idle_message = f"{result.message} Next auto run at {next_run.isoformat()}."

        if result.pipeline_status == db.STATUS_NO_CHANGE:
            idle_status = (
                db.STATUS_WAITING if admin.auto_update_enabled else db.STATUS_AUTO_DISABLED
            )
            db.set_pipeline_status(conn, idle_status, idle_message)
        elif result.pipeline_status == db.STATUS_COMPLETED:
            idle_status = (
                db.STATUS_WAITING if admin.auto_update_enabled else db.STATUS_IDLE
            )
            db.set_pipeline_status(conn, idle_status, idle_message)

        print(f"Run finished: {result.pipeline_status} — {idle_message}", flush=True)
        _notify_admin(
            settings,
            admin,
            conn,
            trigger_type=trigger_type,
            pipeline_status=result.pipeline_status,
            triggered_by=triggered_by,
            history_id=history_id,
            message=idle_message,
            inserted=result.inserted_count,
            updated=result.updated_count,
            database_count=result.database_count,
            next_run_at=next_run,
        )
        return True

    except PipelineError as exc:
        print(f"Pipeline error [{exc.status}]: {exc.message}", flush=True)
        conn.rollback()
        state = db.fetch_pipeline_state(conn)
        retry_count = state.retry_count + 1
        next_retry_at = compute_next_retry(settings, retry_count)
        if history_id is not None:
            db.update_history(
                conn,
                history_id,
                status=db.HISTORY_FAILED,
                finished_at=db.utcnow(),
                pipeline_status=exc.status,
                error_message=exc.message,
                retry_count=retry_count,
            )
        db.schedule_retry(
            conn,
            error_status=exc.status,
            error_message=exc.message,
            retry_count=retry_count,
            next_retry_at=next_retry_at,
        )
        if request_id is not None:
            db.finish_request(conn, request_id, success=False, error_message=exc.message)
        _notify_admin(
            settings,
            admin,
            conn,
            trigger_type=trigger_type,
            pipeline_status=exc.status,
            triggered_by=triggered_by,
            history_id=history_id,
            message=exc.message,
        )
        return True

    except Exception as exc:  # noqa: BLE001
        print(f"Unexpected error: {exc}", flush=True)
        traceback.print_exc()
        conn.rollback()
        state = db.fetch_pipeline_state(conn)
        retry_count = state.retry_count + 1
        next_retry_at = compute_next_retry(settings, retry_count)
        message = str(exc) or exc.__class__.__name__
        if history_id is not None:
            db.update_history(
                conn,
                history_id,
                status=db.HISTORY_FAILED,
                finished_at=db.utcnow(),
                pipeline_status=db.STATUS_FAILED_IMPORT,
                error_message=message,
                retry_count=retry_count,
            )
        db.schedule_retry(
            conn,
            error_status=db.STATUS_FAILED_IMPORT,
            error_message=message,
            retry_count=retry_count,
            next_retry_at=next_retry_at,
        )
        if request_id is not None:
            db.finish_request(conn, request_id, success=False, error_message=message)
        _notify_admin(
            settings,
            admin,
            conn,
            trigger_type=trigger_type,
            pipeline_status=db.STATUS_FAILED_IMPORT,
            triggered_by=triggered_by,
            history_id=history_id,
            message=message,
        )
        return True

    finally:
        db.release_lock(conn, settings.worker_id)


def _format_wait(seconds: float) -> str:
    if seconds <= 0:
        return "immediately"
    if seconds < 120:
        return f"{seconds:.0f}s"
    if seconds < 3600:
        return f"{seconds / 60:.1f} min"
    if seconds < 86400:
        return f"{seconds / 3600:.1f} h"
    return f"{seconds / 86400:.1f} d"


def _notify_admin(
    settings: Settings,
    admin: db.AdminSettings,
    conn,
    *,
    trigger_type: str,
    pipeline_status: str,
    triggered_by: str,
    history_id: int | None,
    message: str,
    inserted: int | None = None,
    updated: int | None = None,
    database_count: int | None = None,
    next_run_at=None,
) -> None:
    email = (admin.notification_email or "").strip()
    if not email:
        return
    subject = f"[Vignette] Glottolog {trigger_type} · {pipeline_status}"
    body = build_notification_body(
        trigger_type=trigger_type,
        pipeline_status=pipeline_status,
        triggered_by=triggered_by,
        history_id=history_id,
        message=message,
        inserted=inserted,
        updated=updated,
        database_count=database_count,
        next_run_at=next_run_at,
        finished_at=db.utcnow(),
    )
    try:
        delivery = send_update_notification(
            settings,
            conn,
            to_email=email,
            subject=subject,
            body=body,
            history_id=history_id,
            trigger_type=trigger_type,
            pipeline_status=pipeline_status,
        )
        print(f"Admin notification: {delivery}", flush=True)
    except Exception as exc:  # noqa: BLE001
        print(f"Admin notification failed: {exc}", flush=True)


def event_loop(settings: Settings) -> int:
    print(
        f"Glottolog worker starting "
        f"(id={settings.worker_id}, version={settings.glottolog_version}, "
        f"safety_check={settings.safety_check_seconds}s)",
        flush=True,
    )
    print(f"cache_dir={settings.cache_dir}", flush=True)
    print(f"data_dir={settings.data_dir}", flush=True)
    smtp = settings.smtp
    if smtp.configured:
        print(
            f"SMTP: provider={smtp.provider} host={smtp.host}:{smtp.port} "
            f"user={smtp.username} starttls={smtp.starttls}",
            flush=True,
        )
    else:
        print(
            f"SMTP: provider={smtp.provider} host={smtp.host}:{smtp.port} "
            "(no Gmail credentials — local catcher or set "
            "scripts/glottolog_worker/.env from .env.example)",
            flush=True,
        )
    print(
        "Wait model: sleep until next_run_at / next_retry_at, "
        "wake on NOTIFY (manual/config), or safety check.",
        flush=True,
    )

    while not _stop:
        try:
            with db.connect(settings) as work_conn, db.connect(settings) as listen_conn:
                listen_conn.autocommit = True
                with listen_conn.cursor() as cur:
                    cur.execute(f"LISTEN {NOTIFY_CHANNEL}")

                db.ensure_pipeline_row(work_conn)
                admin = sync_admin_config(work_conn, settings)
                if admin is None:
                    reason = wait_for_wake(listen_conn, float(settings.safety_check_seconds))
                    print(f"No admin settings — woke ({reason}), retrying.", flush=True)
                    continue

                # On restart / wake: run everything that is already due (overdue next_run,
                # pending manual, retry). Drain until idle.
                while not _stop:
                    ran = run_once(work_conn, settings, admin)
                    if not ran:
                        break
                    admin = sync_admin_config(work_conn, settings) or admin

                if _stop:
                    break

                state = db.fetch_pipeline_state(work_conn)
                wait_s = compute_wait_seconds(work_conn, settings, admin, state)
                next_run = _as_aware(state.next_run_at)
                print(
                    f"Sleeping {_format_wait(wait_s)} "
                    f"(next_run_at={next_run}, auto={admin.auto_update_enabled}) "
                    f"— wake on NOTIFY or safety",
                    flush=True,
                )
                reason = wait_for_wake(listen_conn, wait_s)
                if reason == "stop":
                    break
                if reason == "timeout":
                    print("Safety/schedule wake — re-reading PostgreSQL.", flush=True)

        except Exception as exc:  # noqa: BLE001 — keep running
            print(f"Worker loop error: {exc}", flush=True)
            traceback.print_exc()
            # Brief backoff before reconnect (not a schedule poll).
            for _ in range(min(60, max(1, settings.safety_check_seconds))):
                if _stop:
                    break
                time.sleep(1)

    print("Worker stopped.", flush=True)
    return 0


def main(argv: list[str] | None = None) -> int:
    args = list(argv or [])
    once = "--once" in args
    signal.signal(signal.SIGINT, _handle_signal)
    signal.signal(signal.SIGTERM, _handle_signal)
    settings = load_settings()
    if once:
        print("Running a single poll cycle (--once).", flush=True)
        try:
            with db.connect(settings) as conn:
                db.ensure_pipeline_row(conn)
                admin = sync_admin_config(conn, settings)
                if admin is None:
                    print("No admin settings row — nothing to do.", flush=True)
                    return 1
                ran = run_once(conn, settings, admin)
                print("Work executed." if ran else "No work due this cycle.", flush=True)
            return 0
        except Exception as exc:
            print(
                "Cannot reach PostgreSQL. Set DATABASE_URL or PGHOST/PGPORT/PGDATABASE/PGUSER/PGPASSWORD.\n"
                f"Detail: {exc}",
                flush=True,
            )
            return 2
    return event_loop(settings)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
