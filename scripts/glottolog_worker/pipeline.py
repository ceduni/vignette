"""Zenodo → CLDF → language import pipeline (reuses download_glottolog_languoid)."""

from __future__ import annotations

import sys
import time
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

# Allow ``import download_glottolog_languoid`` from the parent scripts/ directory.
_SCRIPTS_DIR = Path(__file__).resolve().parent.parent
if str(_SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(_SCRIPTS_DIR))

import download_glottolog_languoid as glottolog_dl  # noqa: E402

from . import db
from .config import Settings

HeartbeatFn = Callable[[], None]

REQUIRED_ROW_COLUMNS = ("id", "name", "level")


class PipelineError(Exception):
    """Typed pipeline failure with a status code for pipeline_state."""

    def __init__(self, status: str, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


@dataclass
class PipelineResult:
    pipeline_status: str
    remote_checksum: str | None
    imported_checksum: str | None
    source_rows: int
    selected_rows: int
    inserted_count: int
    updated_count: int
    unchanged_count: int
    database_count: int
    download_duration_ms: int
    sync_duration_ms: int
    message: str


def _heartbeat(fn: HeartbeatFn | None) -> None:
    if fn is not None:
        fn()


def validate_rows(rows: list[dict[str, str]]) -> None:
    """Fail fast if required columns are missing or empty."""
    if not rows:
        raise PipelineError(
            db.STATUS_FAILED_VALIDATION,
            "Selected languoid set is empty after transform/filter.",
        )
    for index, row in enumerate(rows):
        for column in REQUIRED_ROW_COLUMNS:
            value = (row.get(column) or "").strip()
            if not value:
                raise PipelineError(
                    db.STATUS_FAILED_VALIDATION,
                    f"Row {index}: required column '{column}' is empty.",
                )


def download_and_transform(
    settings: Settings,
    *,
    record_id: int,
    zip_name: str,
    heartbeat: HeartbeatFn | None = None,
) -> tuple[list[dict[str, str]], list[dict[str, str]], int]:
    """
    Download CLDF zip (if needed), extract, transform, select for import.
    Returns (all_rows, selected_rows, download_duration_ms).
    """
    cache_dir = settings.cache_dir
    cache_dir.mkdir(parents=True, exist_ok=True)
    settings.data_dir.mkdir(parents=True, exist_ok=True)

    zip_path = cache_dir / f"glottolog-cldf-v{settings.glottolog_version}.zip"
    download_ms = 0

    if not zip_path.exists():
        t0 = time.monotonic()
        try:
            glottolog_dl.download_zip(record_id, zip_path, zip_name)
        except SystemExit as exc:
            raise PipelineError(db.STATUS_FAILED_FETCH, str(exc)) from exc
        except Exception as exc:  # noqa: BLE001 — surface as fetch failure
            raise PipelineError(db.STATUS_FAILED_FETCH, f"Download failed: {exc}") from exc
        download_ms = int((time.monotonic() - t0) * 1000)
        _heartbeat(heartbeat)
    else:
        print(f"Zip déjà en cache : {zip_path}")

    extract_dir = cache_dir / f"extracted-v{settings.glottolog_version}"
    try:
        if not extract_dir.exists():
            extract_dir.mkdir(parents=True, exist_ok=True)
            print(f"Décompression de {zip_path}...")
            with zipfile.ZipFile(zip_path) as archive:
                archive.extractall(extract_dir)
            _heartbeat(heartbeat)

        cldf_dir = glottolog_dl.find_cldf_dir(extract_dir)
        glottolog_dl.ensure_required_cldf_files(zip_path, extract_dir, cldf_dir)
        all_rows = glottolog_dl.build_languoid_rows(cldf_dir)
        selected = glottolog_dl.select_for_import(all_rows)
    except SystemExit as exc:
        raise PipelineError(db.STATUS_FAILED_TRANSFORM, str(exc)) from exc
    except PipelineError:
        raise
    except Exception as exc:  # noqa: BLE001
        raise PipelineError(db.STATUS_FAILED_TRANSFORM, f"Transform failed: {exc}") from exc

    return all_rows, selected, download_ms


def run_pipeline(
    conn,
    settings: Settings,
    *,
    history_id: int,
    heartbeat: HeartbeatFn | None = None,
) -> PipelineResult:
    """
    Full check → optional download/import cycle.
    Caller holds the pipeline lock and owns request lifecycle.
    """
    # --- CHECKING_REMOTE -------------------------------------------------
    db.set_pipeline_status(
        conn,
        db.STATUS_CHECKING_REMOTE,
        "Fetching Zenodo fingerprint",
    )
    db.update_history(conn, history_id, pipeline_status=db.STATUS_CHECKING_REMOTE)
    _heartbeat(heartbeat)

    try:
        record_id, remote_checksum, zip_name = glottolog_dl.fetch_zenodo_fingerprint(
            settings.glottolog_version, None
        )
    except SystemExit as exc:
        raise PipelineError(db.STATUS_FAILED_FETCH, str(exc)) from exc
    except Exception as exc:  # noqa: BLE001
        raise PipelineError(db.STATUS_FAILED_FETCH, f"Zenodo fingerprint failed: {exc}") from exc

    now = db.utcnow()
    db.set_pipeline_status(
        conn,
        db.STATUS_CHECKING_REMOTE,
        f"Remote checksum {remote_checksum}",
        last_detected_checksum=remote_checksum,
        last_check_at=now,
    )
    db.update_history(
        conn,
        history_id,
        remote_checksum=remote_checksum,
        pipeline_status=db.STATUS_CHECKING_REMOTE,
    )

    state = db.fetch_pipeline_state(conn)
    if state.last_imported_checksum and state.last_imported_checksum == remote_checksum:
        message = "Zenodo checksum unchanged — no download."
        with conn.cursor() as cur:
            cur.execute("SELECT COUNT(*) AS n FROM language")
            database_count = int(cur.fetchone()["n"])
        db.set_pipeline_status(
            conn,
            db.STATUS_NO_CHANGE,
            message,
            last_check_at=now,
            last_success_at=now,
            last_error=None,
            retry_count=0,
            next_retry_at=None,
        )
        db.update_history(
            conn,
            history_id,
            status=db.HISTORY_SUCCEEDED,
            finished_at=now,
            pipeline_status=db.STATUS_NO_CHANGE,
            remote_checksum=remote_checksum,
            imported_checksum=remote_checksum,
            source_rows=0,
            selected_rows=0,
            inserted_count=0,
            updated_count=0,
            unchanged_count=0,
            database_count=database_count,
            error_message=None,
        )
        return PipelineResult(
            pipeline_status=db.STATUS_NO_CHANGE,
            remote_checksum=remote_checksum,
            imported_checksum=remote_checksum,
            source_rows=0,
            selected_rows=0,
            inserted_count=0,
            updated_count=0,
            unchanged_count=0,
            database_count=database_count,
            download_duration_ms=0,
            sync_duration_ms=0,
            message=message,
        )

    # --- DOWNLOADING -----------------------------------------------------
    db.set_pipeline_status(conn, db.STATUS_DOWNLOADING, f"Downloading Zenodo {record_id}")
    db.update_history(conn, history_id, pipeline_status=db.STATUS_DOWNLOADING)
    _heartbeat(heartbeat)

    all_rows, selected, download_ms = download_and_transform(
        settings,
        record_id=record_id,
        zip_name=zip_name,
        heartbeat=heartbeat,
    )
    db.set_pipeline_status(
        conn,
        db.STATUS_DOWNLOADING,
        f"Downloaded / cached Zenodo {record_id}",
        last_downloaded_checksum=remote_checksum,
    )

    # --- VALIDATING ------------------------------------------------------
    db.set_pipeline_status(conn, db.STATUS_VALIDATING, "Validating selected rows")
    db.update_history(conn, history_id, pipeline_status=db.STATUS_VALIDATING)
    _heartbeat(heartbeat)
    validate_rows(selected)
    db.set_pipeline_status(
        conn,
        db.STATUS_VALIDATING,
        f"Validated {len(selected)} rows",
        last_validated_checksum=remote_checksum,
    )

    # --- TRANSFORMING (already done; record stage for operators) ---------
    db.set_pipeline_status(
        conn,
        db.STATUS_TRANSFORMING,
        f"{len(all_rows)} source → {len(selected)} selected",
    )
    db.update_history(
        conn,
        history_id,
        pipeline_status=db.STATUS_TRANSFORMING,
        source_rows=len(all_rows),
        selected_rows=len(selected),
        download_duration_ms=download_ms,
    )
    # Persist CSV for debugging / parity with Java path (best-effort).
    try:
        glottolog_dl.write_languoid_csv(selected, settings.languoid_csv)
        glottolog_dl.write_local_fingerprint(
            settings.languoid_csv,
            settings.glottolog_version,
            record_id,
            remote_checksum,
        )
    except Exception as exc:  # noqa: BLE001 — non-fatal for DB import
        print(f"Avertissement: écriture CSV locale échouée: {exc}", file=sys.stderr)

    # --- IMPORTING -------------------------------------------------------
    db.set_pipeline_status(conn, db.STATUS_IMPORTING, f"Upserting {len(selected)} languages")
    db.update_history(conn, history_id, pipeline_status=db.STATUS_IMPORTING)
    _heartbeat(heartbeat)

    sync_t0 = time.monotonic()
    try:
        # Count how many selected ids already exist (for rough insert/update stats).
        existing = db.count_existing_ids(conn, [r["id"] for r in selected])
        selected_count, database_count = db.upsert_languages(conn, selected)
        # Commit language changes before recording imported checksum.
        conn.commit()
    except Exception as exc:  # noqa: BLE001
        conn.rollback()
        raise PipelineError(db.STATUS_FAILED_IMPORT, f"Import failed: {exc}") from exc

    sync_ms = int((time.monotonic() - sync_t0) * 1000)
    inserted = max(0, selected_count - existing)
    updated = max(0, existing)
    finished = db.utcnow()

    db.set_pipeline_status(
        conn,
        db.STATUS_COMPLETED,
        f"Imported {selected_count} languages (checksum {remote_checksum})",
        last_check_at=finished,
        last_success_at=finished,
        last_imported_checksum=remote_checksum,
        last_downloaded_checksum=remote_checksum,
        last_validated_checksum=remote_checksum,
        last_detected_checksum=remote_checksum,
        last_error=None,
        retry_count=0,
        next_retry_at=None,
        active_history_id=None,
    )
    db.update_history(
        conn,
        history_id,
        status=db.HISTORY_SUCCEEDED,
        finished_at=finished,
        pipeline_status=db.STATUS_COMPLETED,
        remote_checksum=remote_checksum,
        imported_checksum=remote_checksum,
        source_rows=len(all_rows),
        selected_rows=selected_count,
        inserted_count=inserted,
        updated_count=updated,
        unchanged_count=0,
        database_count=database_count,
        download_duration_ms=download_ms,
        sync_duration_ms=sync_ms,
        error_message=None,
    )

    return PipelineResult(
        pipeline_status=db.STATUS_COMPLETED,
        remote_checksum=remote_checksum,
        imported_checksum=remote_checksum,
        source_rows=len(all_rows),
        selected_rows=selected_count,
        inserted_count=inserted,
        updated_count=updated,
        unchanged_count=0,
        database_count=database_count,
        download_duration_ms=download_ms,
        sync_duration_ms=sync_ms,
        message=f"Imported {selected_count} languages",
    )
