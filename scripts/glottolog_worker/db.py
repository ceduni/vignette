"""PostgreSQL access for the Glottolog worker (parameterized SQL only)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Any, Iterable, Sequence

import psycopg
from psycopg.rows import dict_row

from .config import Settings

PIPELINE_ID = 1
SETTINGS_ID = 1

# Pipeline current_status values
STATUS_IDLE = "IDLE"
STATUS_WAITING = "WAITING"
STATUS_CHECKING_REMOTE = "CHECKING_REMOTE"
STATUS_NO_CHANGE = "NO_CHANGE"
STATUS_DOWNLOADING = "DOWNLOADING"
STATUS_VALIDATING = "VALIDATING"
STATUS_TRANSFORMING = "TRANSFORMING"
STATUS_IMPORTING = "IMPORTING"
STATUS_COMPLETED = "COMPLETED"
STATUS_FAILED_FETCH = "FAILED_FETCH"
STATUS_FAILED_VALIDATION = "FAILED_VALIDATION"
STATUS_FAILED_TRANSFORM = "FAILED_TRANSFORM"
STATUS_FAILED_IMPORT = "FAILED_IMPORT"
STATUS_LOCKED = "LOCKED"
STATUS_AUTO_DISABLED = "AUTO_DISABLED"
STATUS_RETRY_SCHEDULED = "RETRY_SCHEDULED"

REQUEST_PENDING = "PENDING"
REQUEST_CLAIMED = "CLAIMED"
REQUEST_RUNNING = "RUNNING"
REQUEST_SUCCEEDED = "SUCCEEDED"
REQUEST_FAILED = "FAILED"

HISTORY_RUNNING = "RUNNING"
HISTORY_SUCCEEDED = "SUCCEEDED"
HISTORY_FAILED = "FAILED"

TRIGGER_MANUAL = "MANUAL"
TRIGGER_AUTO = "AUTO"
TRIGGER_RETRY = "RETRY"

LANGUAGE_UPSERT_COLUMNS = (
    "id",
    "family_id",
    "parent_id",
    "name",
    "bookkeeping",
    "level",
    "latitude",
    "longitude",
    "iso639_p3code",
    "description",
    "markup_description",
    "child_family_count",
    "child_language_count",
    "child_dialect_count",
    "country_ids",
    "present_in_latest_glottolog",
    "last_seen_in_glottolog_at",
)


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def connect(settings: Settings) -> psycopg.Connection:
    return psycopg.connect(**settings.connect_kwargs(), row_factory=dict_row)


@dataclass
class AdminSettings:
    auto_update_enabled: bool
    frequency_days: int | None
    updated_at: datetime | None
    notification_email: str | None = None


@dataclass
class PipelineState:
    next_run_at: datetime | None
    last_check_at: datetime | None
    last_success_at: datetime | None
    current_status: str
    status_message: str | None
    last_detected_checksum: str | None
    last_downloaded_checksum: str | None
    last_validated_checksum: str | None
    last_imported_checksum: str | None
    last_error: str | None
    retry_count: int
    next_retry_at: datetime | None
    lock_owner: str | None
    lock_expires_at: datetime | None
    lock_heartbeat_at: datetime | None
    active_history_id: int | None
    config_observed_at: datetime | None
    config_frequency_days: int | None
    config_auto_update_enabled: bool | None


@dataclass
class UpdateRequest:
    id: int
    requested_by_username: str
    requested_at: datetime
    status: str


def ensure_pipeline_row(conn: psycopg.Connection) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO glottolog_pipeline_state (id, current_status, retry_count, updated_at)
            VALUES (%s, %s, 0, %s)
            ON CONFLICT (id) DO NOTHING
            """,
            (PIPELINE_ID, STATUS_IDLE, utcnow()),
        )
    conn.commit()


def fetch_admin_settings(conn: psycopg.Connection) -> AdminSettings | None:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT auto_update_enabled, frequency_days, updated_at, notification_email
            FROM glottolog_admin_settings
            WHERE id = %s
            """,
            (SETTINGS_ID,),
        )
        row = cur.fetchone()
    if row is None:
        return None
    return AdminSettings(
        auto_update_enabled=bool(row["auto_update_enabled"]),
        frequency_days=row["frequency_days"],
        updated_at=row["updated_at"],
        notification_email=(row.get("notification_email") or None),
    )


def fetch_pipeline_state(conn: psycopg.Connection) -> PipelineState:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT next_run_at, last_check_at, last_success_at, current_status, status_message,
                   last_detected_checksum, last_downloaded_checksum, last_validated_checksum,
                   last_imported_checksum, last_error, retry_count, next_retry_at,
                   lock_owner, lock_expires_at, lock_heartbeat_at, active_history_id,
                   config_observed_at, config_frequency_days, config_auto_update_enabled
            FROM glottolog_pipeline_state
            WHERE id = %s
            """,
            (PIPELINE_ID,),
        )
        row = cur.fetchone()
    if row is None:
        raise RuntimeError("glottolog_pipeline_state id=1 is missing")
    return PipelineState(
        next_run_at=row["next_run_at"],
        last_check_at=row["last_check_at"],
        last_success_at=row["last_success_at"],
        current_status=row["current_status"] or STATUS_IDLE,
        status_message=row["status_message"],
        last_detected_checksum=row["last_detected_checksum"],
        last_downloaded_checksum=row["last_downloaded_checksum"],
        last_validated_checksum=row["last_validated_checksum"],
        last_imported_checksum=row["last_imported_checksum"],
        last_error=row["last_error"],
        retry_count=int(row["retry_count"] or 0),
        next_retry_at=row["next_retry_at"],
        lock_owner=row["lock_owner"],
        lock_expires_at=row["lock_expires_at"],
        lock_heartbeat_at=row["lock_heartbeat_at"],
        active_history_id=row["active_history_id"],
        config_observed_at=row["config_observed_at"],
        config_frequency_days=row["config_frequency_days"],
        config_auto_update_enabled=row["config_auto_update_enabled"],
    )


def config_needs_resync(settings: AdminSettings, state: PipelineState) -> bool:
    """True when admin settings changed vs the worker's observed snapshot."""
    if state.config_observed_at is None and settings.updated_at is not None:
        return True
    if settings.updated_at is not None and state.config_observed_at is not None:
        # Compare instants; treat naive as UTC.
        observed = state.config_observed_at
        updated = settings.updated_at
        if updated.tzinfo is None:
            updated = updated.replace(tzinfo=timezone.utc)
        if observed.tzinfo is None:
            observed = observed.replace(tzinfo=timezone.utc)
        if updated != observed:
            return True
    if state.config_auto_update_enabled != settings.auto_update_enabled:
        return True
    if state.config_frequency_days != settings.frequency_days:
        return True
    return False


def apply_config_snapshot(
    conn: psycopg.Connection,
    settings: AdminSettings,
    *,
    status: str | None = None,
    status_message: str | None = None,
) -> datetime | None:
    """Snapshot admin config into pipeline_state and recompute next_run_at."""
    now = utcnow()
    if settings.auto_update_enabled and settings.frequency_days and settings.frequency_days > 0:
        next_run_at: datetime | None = now + timedelta(days=settings.frequency_days)
        resolved_status = status or STATUS_WAITING
        resolved_message = status_message or (
            f"Auto-update every {settings.frequency_days} day(s); next run at {next_run_at.isoformat()}"
        )
    else:
        next_run_at = None
        resolved_status = status or STATUS_AUTO_DISABLED
        resolved_message = status_message or "Auto-update disabled"

    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET next_run_at = %s,
                config_observed_at = %s,
                config_frequency_days = %s,
                config_auto_update_enabled = %s,
                current_status = COALESCE(%s, current_status),
                status_message = COALESCE(%s, status_message),
                updated_at = %s
            WHERE id = %s
            """,
            (
                next_run_at,
                settings.updated_at or now,
                settings.frequency_days,
                settings.auto_update_enabled,
                resolved_status,
                resolved_message,
                now,
                PIPELINE_ID,
            ),
        )
    conn.commit()
    return next_run_at


def schedule_next_run(
    conn: psycopg.Connection,
    settings: AdminSettings,
) -> datetime | None:
    """After a completed check/import, push next_run_at forward when auto is on."""
    now = utcnow()
    if settings.auto_update_enabled and settings.frequency_days and settings.frequency_days > 0:
        next_run_at: datetime | None = now + timedelta(days=settings.frequency_days)
    else:
        next_run_at = None
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET next_run_at = %s, updated_at = %s
            WHERE id = %s
            """,
            (next_run_at, now, PIPELINE_ID),
        )
    conn.commit()
    return next_run_at


def has_active_lock(state: PipelineState, now: datetime | None = None) -> bool:
    now = now or utcnow()
    if not state.lock_owner:
        return False
    expires = state.lock_expires_at
    if expires is None:
        return True
    if expires.tzinfo is None:
        expires = expires.replace(tzinfo=timezone.utc)
    return expires > now


def has_active_request(conn: psycopg.Connection) -> bool:
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT 1
            FROM glottolog_update_request
            WHERE status IN (%s, %s, %s)
            LIMIT 1
            """,
            (REQUEST_PENDING, REQUEST_CLAIMED, REQUEST_RUNNING),
        )
        return cur.fetchone() is not None


def peek_claimable_request(
    conn: psycopg.Connection,
    stale_seconds: int,
) -> bool:
    """True if a PENDING or stale CLAIMED/RUNNING request exists (no lock taken)."""
    now = utcnow()
    stale_before = now - timedelta(seconds=stale_seconds)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT 1
            FROM glottolog_update_request
            WHERE status = %s
               OR (
                    status IN (%s, %s)
                    AND (heartbeat_at IS NULL OR heartbeat_at < %s)
                  )
            LIMIT 1
            """,
            (REQUEST_PENDING, REQUEST_CLAIMED, REQUEST_RUNNING, stale_before),
        )
        return cur.fetchone() is not None


def try_acquire_lock(
    conn: psycopg.Connection,
    worker_id: str,
    lock_ttl_seconds: int,
) -> bool:
    """Acquire or steal an expired lock on pipeline_state id=1."""
    now = utcnow()
    expires = now + timedelta(seconds=lock_ttl_seconds)
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET lock_owner = %s,
                lock_expires_at = %s,
                lock_heartbeat_at = %s,
                current_status = %s,
                status_message = %s,
                updated_at = %s
            WHERE id = %s
              AND (
                    lock_owner IS NULL
                 OR lock_expires_at IS NULL
                 OR lock_expires_at < %s
                 OR lock_owner = %s
              )
            RETURNING id
            """,
            (
                worker_id,
                expires,
                now,
                STATUS_LOCKED,
                f"Locked by {worker_id}",
                now,
                PIPELINE_ID,
                now,
                worker_id,
            ),
        )
        row = cur.fetchone()
    conn.commit()
    return row is not None


def heartbeat_lock(
    conn: psycopg.Connection,
    worker_id: str,
    lock_ttl_seconds: int,
) -> bool:
    now = utcnow()
    expires = now + timedelta(seconds=lock_ttl_seconds)
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET lock_expires_at = %s,
                lock_heartbeat_at = %s,
                updated_at = %s
            WHERE id = %s AND lock_owner = %s
            RETURNING id
            """,
            (expires, now, now, PIPELINE_ID, worker_id),
        )
        row = cur.fetchone()
    conn.commit()
    return row is not None


def release_lock(conn: psycopg.Connection, worker_id: str) -> None:
    now = utcnow()
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET lock_owner = NULL,
                lock_expires_at = NULL,
                lock_heartbeat_at = NULL,
                updated_at = %s
            WHERE id = %s AND lock_owner = %s
            """,
            (now, PIPELINE_ID, worker_id),
        )
    conn.commit()


def claim_update_request(
    conn: psycopg.Connection,
    worker_id: str,
    stale_seconds: int,
) -> UpdateRequest | None:
    """
    Claim the oldest PENDING request, or reclaim CLAIMED/RUNNING whose
    heartbeat is older than stale_seconds.
    """
    now = utcnow()
    stale_before = now - timedelta(seconds=stale_seconds)
    with conn.cursor() as cur:
        cur.execute(
            """
            SELECT id, requested_by_username, requested_at, status
            FROM glottolog_update_request
            WHERE status = %s
               OR (
                    status IN (%s, %s)
                    AND (heartbeat_at IS NULL OR heartbeat_at < %s)
                  )
            ORDER BY requested_at ASC
            LIMIT 1
            FOR UPDATE SKIP LOCKED
            """,
            (REQUEST_PENDING, REQUEST_CLAIMED, REQUEST_RUNNING, stale_before),
        )
        row = cur.fetchone()
        if row is None:
            conn.commit()
            return None
        cur.execute(
            """
            UPDATE glottolog_update_request
            SET status = %s,
                claimed_at = %s,
                claimed_by = %s,
                heartbeat_at = %s,
                error_message = NULL
            WHERE id = %s
            """,
            (REQUEST_CLAIMED, now, worker_id, now, row["id"]),
        )
    conn.commit()
    return UpdateRequest(
        id=row["id"],
        requested_by_username=row["requested_by_username"],
        requested_at=row["requested_at"],
        status=REQUEST_CLAIMED,
    )


def mark_request_running(conn: psycopg.Connection, request_id: int, history_id: int) -> None:
    now = utcnow()
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_update_request
            SET status = %s,
                started_at = COALESCE(started_at, %s),
                heartbeat_at = %s,
                history_id = %s
            WHERE id = %s
            """,
            (REQUEST_RUNNING, now, now, history_id, request_id),
        )
    conn.commit()


def heartbeat_request(conn: psycopg.Connection, request_id: int) -> None:
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_update_request
            SET heartbeat_at = %s
            WHERE id = %s
            """,
            (utcnow(), request_id),
        )
    conn.commit()


def finish_request(
    conn: psycopg.Connection,
    request_id: int,
    *,
    success: bool,
    error_message: str | None = None,
) -> None:
    now = utcnow()
    with conn.cursor() as cur:
        cur.execute(
            """
            UPDATE glottolog_update_request
            SET status = %s,
                finished_at = %s,
                heartbeat_at = %s,
                error_message = %s
            WHERE id = %s
            """,
            (
                REQUEST_SUCCEEDED if success else REQUEST_FAILED,
                now,
                now,
                (error_message or "")[:4000] if error_message else None,
                request_id,
            ),
        )
    conn.commit()


def insert_history(
    conn: psycopg.Connection,
    *,
    triggered_by: str,
    trigger_type: str,
    glottolog_version: str,
    worker_id: str,
    retry_count: int,
) -> int:
    now = utcnow()
    with conn.cursor() as cur:
        cur.execute(
            """
            INSERT INTO glottolog_update_history (
                triggered_by_username, status, started_at, glottolog_version,
                trigger_type, pipeline_status, retry_count, worker_id
            )
            VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
            RETURNING id
            """,
            (
                triggered_by,
                HISTORY_RUNNING,
                now,
                glottolog_version,
                trigger_type,
                STATUS_CHECKING_REMOTE,
                retry_count,
                worker_id,
            ),
        )
        history_id = int(cur.fetchone()["id"])
        cur.execute(
            """
            UPDATE glottolog_pipeline_state
            SET active_history_id = %s, updated_at = %s
            WHERE id = %s
            """,
            (history_id, now, PIPELINE_ID),
        )
    conn.commit()
    return history_id


def update_history(
    conn: psycopg.Connection,
    history_id: int,
    **fields: Any,
) -> None:
    if not fields:
        return
    allowed = {
        "status",
        "finished_at",
        "glottolog_version",
        "download_duration_ms",
        "sync_duration_ms",
        "source_rows",
        "selected_rows",
        "inserted_count",
        "updated_count",
        "unchanged_count",
        "database_count",
        "error_message",
        "trigger_type",
        "pipeline_status",
        "remote_checksum",
        "imported_checksum",
        "retry_count",
        "worker_id",
    }
    sets: list[str] = []
    values: list[Any] = []
    for key, value in fields.items():
        if key not in allowed:
            raise ValueError(f"Unsupported history field: {key}")
        if key == "error_message" and value is not None:
            value = str(value)[:4000]
        sets.append(f"{key} = %s")
        values.append(value)
    values.append(history_id)
    with conn.cursor() as cur:
        cur.execute(
            f"UPDATE glottolog_update_history SET {', '.join(sets)} WHERE id = %s",
            values,
        )
    conn.commit()


def set_pipeline_status(
    conn: psycopg.Connection,
    status: str,
    message: str | None = None,
    **extra: Any,
) -> None:
    """Update current_status and optional checksum / error fields."""
    now = utcnow()
    allowed_extra = {
        "last_check_at",
        "last_success_at",
        "last_detected_checksum",
        "last_downloaded_checksum",
        "last_validated_checksum",
        "last_imported_checksum",
        "last_error",
        "retry_count",
        "next_retry_at",
        "active_history_id",
        "next_run_at",
        "status_message",
    }
    sets = ["current_status = %s", "updated_at = %s"]
    values: list[Any] = [status, now]
    if message is not None:
        sets.append("status_message = %s")
        values.append(message[:2000])
    for key, value in extra.items():
        if key not in allowed_extra:
            raise ValueError(f"Unsupported pipeline field: {key}")
        if key == "last_error" and value is not None:
            value = str(value)[:4000]
        if key == "status_message" and value is not None:
            value = str(value)[:2000]
        sets.append(f"{key} = %s")
        values.append(value)
    values.append(PIPELINE_ID)
    with conn.cursor() as cur:
        cur.execute(
            f"UPDATE glottolog_pipeline_state SET {', '.join(sets)} WHERE id = %s",
            values,
        )
    conn.commit()


def schedule_retry(
    conn: psycopg.Connection,
    *,
    error_status: str,
    error_message: str,
    retry_count: int,
    next_retry_at: datetime,
) -> None:
    set_pipeline_status(
        conn,
        STATUS_RETRY_SCHEDULED,
        f"{error_status}; retry #{retry_count} at {next_retry_at.isoformat()}: {error_message}",
        last_error=f"{error_status}: {error_message}",
        retry_count=retry_count,
        next_retry_at=next_retry_at,
        active_history_id=None,
    )


def _empty_to_none(value: str | None) -> str | None:
    if value is None:
        return None
    text = value.strip()
    return text if text else None


def _parse_bool(value: str | bool | None) -> bool:
    if isinstance(value, bool):
        return value
    return str(value or "").strip().lower() in {"true", "1", "t", "yes"}


def _parse_float(value: str | None) -> float | None:
    text = _empty_to_none(value)
    if text is None:
        return None
    return float(text)


def _parse_int(value: str | None, default: int = 0) -> int:
    text = _empty_to_none(value)
    if text is None:
        return default
    return int(text)


def row_to_db_tuple(row: dict[str, str], seen_at: datetime) -> tuple[Any, ...]:
    """Map a languoid CSV-style dict to the language upsert parameter tuple."""
    return (
        row["id"].strip(),
        _empty_to_none(row.get("family_id")),
        _empty_to_none(row.get("parent_id")),
        (row.get("name") or "").strip(),
        _parse_bool(row.get("bookkeeping")),
        (row.get("level") or "").strip().lower(),
        _parse_float(row.get("latitude")),
        _parse_float(row.get("longitude")),
        _empty_to_none(row.get("iso639_p3code")),
        _empty_to_none(row.get("description")),
        _empty_to_none(row.get("markup_description")),
        _parse_int(row.get("child_family_count")),
        _parse_int(row.get("child_language_count")),
        _parse_int(row.get("child_dialect_count")),
        _empty_to_none(row.get("country_ids")),
        True,
        seen_at,
    )


UPSERT_SQL = """
INSERT INTO language (
    id, family_id, parent_id, name, bookkeeping, level,
    latitude, longitude, iso639_p3code, description, markup_description,
    child_family_count, child_language_count, child_dialect_count, country_ids,
    present_in_latest_glottolog, last_seen_in_glottolog_at
) VALUES (
    %s, %s, %s, %s, %s, %s,
    %s, %s, %s, %s, %s,
    %s, %s, %s, %s,
    %s, %s
)
ON CONFLICT (id) DO UPDATE SET
    family_id = EXCLUDED.family_id,
    parent_id = EXCLUDED.parent_id,
    name = EXCLUDED.name,
    bookkeeping = EXCLUDED.bookkeeping,
    level = EXCLUDED.level,
    latitude = EXCLUDED.latitude,
    longitude = EXCLUDED.longitude,
    iso639_p3code = EXCLUDED.iso639_p3code,
    description = EXCLUDED.description,
    markup_description = EXCLUDED.markup_description,
    child_family_count = EXCLUDED.child_family_count,
    child_language_count = EXCLUDED.child_language_count,
    child_dialect_count = EXCLUDED.child_dialect_count,
    country_ids = EXCLUDED.country_ids,
    present_in_latest_glottolog = TRUE,
    last_seen_in_glottolog_at = EXCLUDED.last_seen_in_glottolog_at
"""


def upsert_languages(
    conn: psycopg.Connection,
    rows: Sequence[dict[str, str]],
    *,
    batch_size: int = 500,
) -> tuple[int, int]:
    """
    Upsert selected languoid rows in one transaction and soft-absent the rest.
    Returns (selected_count, database_count_after).
    Does not commit — caller owns the transaction boundary.
    """
    seen_at = utcnow()
    selected_ids = [r["id"].strip() for r in rows]
    with conn.cursor() as cur:
        for start in range(0, len(rows), batch_size):
            batch = rows[start : start + batch_size]
            params = [row_to_db_tuple(r, seen_at) for r in batch]
            cur.executemany(UPSERT_SQL, params)

        if selected_ids:
            cur.execute(
                """
                UPDATE language
                SET present_in_latest_glottolog = FALSE
                WHERE present_in_latest_glottolog IS DISTINCT FROM FALSE
                  AND NOT (id = ANY(%s))
                """,
                (selected_ids,),
            )
        else:
            cur.execute(
                """
                UPDATE language
                SET present_in_latest_glottolog = FALSE
                WHERE present_in_latest_glottolog IS DISTINCT FROM FALSE
                """
            )

        cur.execute("SELECT COUNT(*) AS n FROM language")
        database_count = int(cur.fetchone()["n"])
    return len(rows), database_count


def count_existing_ids(conn: psycopg.Connection, ids: Iterable[str]) -> int:
    id_list = list(ids)
    if not id_list:
        return 0
    with conn.cursor() as cur:
        cur.execute(
            "SELECT COUNT(*) AS n FROM language WHERE id = ANY(%s)",
            (id_list,),
        )
        return int(cur.fetchone()["n"])
