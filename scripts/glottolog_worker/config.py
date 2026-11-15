"""Configuration from environment variables (no hardcoded user home paths)."""

from __future__ import annotations

import os
import socket
from dataclasses import dataclass
from pathlib import Path

# scripts/glottolog_worker/config.py → repository root (parent of scripts/)
_REPO_ROOT = Path(__file__).resolve().parents[2]
_WORKER_DIR = Path(__file__).resolve().parent


def _load_dotenv_files() -> None:
    """Load optional .env files without overriding already-exported env vars."""
    candidates = (
        _WORKER_DIR / ".env",
        _REPO_ROOT / ".env",
        _REPO_ROOT / "scripts" / ".env",
    )
    for path in candidates:
        if not path.is_file():
            continue
        for raw_line in path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, _, value = line.partition("=")
            key = key.strip()
            value = value.strip().strip("'").strip('"')
            if not key:
                continue
            # Do not clobber variables already set in the shell.
            if os.environ.get(key) in (None, ""):
                os.environ[key] = value


def _int_env(name: str, default: int) -> int:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        return default
    return int(raw)


def _bool_env(name: str, default: bool) -> bool:
    raw = os.environ.get(name)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _path_env(name: str, default_under_repo: str) -> Path:
    """
    Resolve a path from env, or fall back to a path under the repo root.
    Relative env values are resolved against the current working directory.
    """
    raw = (os.environ.get(name) or "").strip()
    if not raw:
        return (_REPO_ROOT / default_under_repo).resolve()
    path = Path(raw).expanduser()
    if not path.is_absolute():
        path = Path.cwd() / path
    return path.resolve()


@dataclass(frozen=True)
class SmtpSettings:
    """Outbound email settings (Gmail App Password recommended for local/dev)."""

    host: str
    port: int
    username: str
    password: str
    from_addr: str
    starttls: bool
    provider: str

    @property
    def configured(self) -> bool:
        """True when credentials look ready for authenticated SMTP (e.g. Gmail)."""
        return bool(self.username and self.password)


@dataclass(frozen=True)
class Settings:
    """Runtime settings for the Glottolog worker."""

    database_url: str | None
    pghost: str
    pgport: int
    pgdatabase: str
    pguser: str
    pgpassword: str

    # Backup wake interval (lost NOTIFY / network blip). Not used to decide schedule due-ness.
    safety_check_seconds: int
    lock_ttl_seconds: int
    request_stale_seconds: int
    heartbeat_interval_seconds: int
    retry_base_seconds: int
    retry_max_seconds: int

    glottolog_version: str
    worker_id: str
    cache_dir: Path
    data_dir: Path
    smtp: SmtpSettings

    @property
    def languoid_csv(self) -> Path:
        return self.data_dir / "languoid.csv"

    def connect_kwargs(self) -> dict:
        """Arguments for ``psycopg.connect``."""
        if self.database_url:
            return {"conninfo": self.database_url}
        return {
            "host": self.pghost,
            "port": self.pgport,
            "dbname": self.pgdatabase,
            "user": self.pguser,
            "password": self.pgpassword,
        }


def _resolve_smtp() -> SmtpSettings:
    """
    Resolve SMTP settings.

    Priority:
      1. Explicit GLOTTOLOG_SMTP_* env
      2. If GLOTTOLOG_SMTP_PROVIDER=gmail (default when user/password set) → Gmail host/port/TLS
      3. Otherwise local catcher 127.0.0.1:1025 (no auth)
    """
    username = (os.environ.get("GLOTTOLOG_SMTP_USER") or "").strip()
    password = os.environ.get("GLOTTOLOG_SMTP_PASSWORD") or ""
    provider = (os.environ.get("GLOTTOLOG_SMTP_PROVIDER") or "").strip().lower()

    if not provider:
        if username or password:
            provider = "gmail"
        else:
            provider = "local"

    if provider in {"gmail", "google"}:
        host = (os.environ.get("GLOTTOLOG_SMTP_HOST") or "smtp.gmail.com").strip()
        port = _int_env("GLOTTOLOG_SMTP_PORT", 587)
        starttls = _bool_env("GLOTTOLOG_SMTP_STARTTLS", True)
        from_addr = (
            os.environ.get("GLOTTOLOG_SMTP_FROM") or username or "vignette@gmail.com"
        ).strip()
    elif provider in {"local", "catcher", "mailpit"}:
        host = (os.environ.get("GLOTTOLOG_SMTP_HOST") or "127.0.0.1").strip()
        port = _int_env("GLOTTOLOG_SMTP_PORT", 1025)
        starttls = _bool_env("GLOTTOLOG_SMTP_STARTTLS", False)
        from_addr = (
            os.environ.get("GLOTTOLOG_SMTP_FROM") or "vignette@localhost"
        ).strip()
    else:
        host = (os.environ.get("GLOTTOLOG_SMTP_HOST") or "127.0.0.1").strip()
        port = _int_env("GLOTTOLOG_SMTP_PORT", 587)
        starttls = _bool_env("GLOTTOLOG_SMTP_STARTTLS", True)
        from_addr = (
            os.environ.get("GLOTTOLOG_SMTP_FROM") or username or "vignette@localhost"
        ).strip()

    return SmtpSettings(
        host=host,
        port=port,
        username=username,
        password=password,
        from_addr=from_addr,
        starttls=starttls,
        provider=provider,
    )


def load_settings() -> Settings:
    _load_dotenv_files()
    host = socket.gethostname() or "worker"
    default_worker_id = f"{host}-{os.getpid()}"
    return Settings(
        database_url=(os.environ.get("DATABASE_URL") or "").strip() or None,
        pghost=os.environ.get("PGHOST", "localhost"),
        pgport=_int_env("PGPORT", 5432),
        pgdatabase=os.environ.get("PGDATABASE", "vignette"),
        pguser=os.environ.get("PGUSER", "vignette"),
        pgpassword=os.environ.get("PGPASSWORD", ""),
        safety_check_seconds=_int_env("GLOTTOLOG_SAFETY_CHECK_SECONDS", 6 * 3600),
        lock_ttl_seconds=_int_env("GLOTTOLOG_LOCK_TTL_SECONDS", 600),
        request_stale_seconds=_int_env("GLOTTOLOG_REQUEST_STALE_SECONDS", 300),
        heartbeat_interval_seconds=_int_env("GLOTTOLOG_HEARTBEAT_INTERVAL_SECONDS", 30),
        retry_base_seconds=_int_env("GLOTTOLOG_RETRY_BASE_SECONDS", 60),
        retry_max_seconds=_int_env("GLOTTOLOG_RETRY_MAX_SECONDS", 3600),
        glottolog_version=os.environ.get("GLOTTOLOG_VERSION", "5.3").strip() or "5.3",
        worker_id=(os.environ.get("GLOTTOLOG_WORKER_ID") or default_worker_id).strip(),
        cache_dir=_path_env("GLOTTOLOG_CACHE_DIR", "data/glottolog-cache"),
        data_dir=_path_env("GLOTTOLOG_DATA_DIR", "data/glottolog"),
        smtp=_resolve_smtp(),
    )
