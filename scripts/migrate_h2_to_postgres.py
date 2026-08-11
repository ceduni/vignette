#!/usr/bin/env python3
"""Migrate users/scenarios from local H2 (./data/bootapp) into PostgreSQL.

  python3 scripts/migrate_h2_to_postgres.py
  python3 scripts/migrate_h2_to_postgres.py --dry-run
"""

from __future__ import annotations

import argparse
import csv
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

try:
    import psycopg
    from psycopg import sql
except ImportError:
    print("Missing dependency: pip install 'psycopg[binary]'", file=sys.stderr)
    sys.exit(1)

# H2 table -> PostgreSQL table (same logical columns, lowercased on load)
TABLES_IN_ORDER = (
    "AUTHOR",
    "USER_",
    "USER_ROLES",
    "USER_ACADEMY_AFFILIATIONS",
    "SCENARIO",
    "SCENARIO_TAG",
    "SCENARIO_TAG_LINK",
    "THUMBNAIL",
    "AUDIO",
    "SCENARIO_LIKE",
    "SCENARIO_BOOKMARK",
)

PG_NAME = {
    "AUTHOR": "author",
    "USER_": "user_",
    "USER_ROLES": "user_roles",
    "USER_ACADEMY_AFFILIATIONS": "user_academy_affiliations",
    "SCENARIO": "scenario",
    "SCENARIO_TAG": "scenario_tag",
    "SCENARIO_TAG_LINK": "scenario_tag_link",
    "THUMBNAIL": "thumbnail",
    "AUDIO": "audio",
    "SCENARIO_LIKE": "scenario_like",
    "SCENARIO_BOOKMARK": "scenario_bookmark",
}

# Primary / conflict targets for upsert
CONFLICT = {
    "author": ("id",),
    "user_": ("id",),
    "user_roles": ("user_id", "role_name"),
    "user_academy_affiliations": ("user_id", "academy_name"),
    "scenario": ("id",),
    "scenario_tag": ("id",),
    "scenario_tag_link": ("scenario_id", "tag_id"),
    "thumbnail": ("id",),
    "audio": ("id",),
    "scenario_like": ("id",),
    "scenario_bookmark": ("id",),
}

SEQUENCE_BUMPS = (
    ("author", "author_seq"),
    ("scenario", "scenario_seq"),
    ("scenario_tag", "scenario_tag_seq"),
    ("thumbnail", "thumbnail_seq"),
    ("audio", "audio_seq"),
    ("scenario_like", "scenario_like_id_seq"),
    ("scenario_bookmark", "scenario_bookmark_id_seq"),
)


def repo_root() -> Path:
    return Path(__file__).resolve().parent.parent


def find_h2_jar(explicit: str | None) -> Path:
    if explicit:
        path = Path(explicit).expanduser().resolve()
        if not path.is_file():
            raise SystemExit(f"H2 jar not found: {path}")
        return path
    m2 = Path.home() / ".m2" / "repository" / "com" / "h2database" / "h2"
    jars = sorted(m2.glob("*/h2-*.jar"))
    if not jars:
        raise SystemExit(
            "No H2 jar under ~/.m2/repository/com/h2database/h2 — "
            "run `mvn dependency:resolve` or pass --h2-jar"
        )
    return jars[-1]


def find_java(explicit: str | None) -> str:
    if explicit:
        return explicit
    home = os.environ.get("JAVA_HOME")
    if home:
        candidate = Path(home) / "bin" / "java"
        if candidate.is_file():
            return str(candidate)
    found = shutil.which("java")
    if not found:
        raise SystemExit("java not found; set JAVA_HOME or PATH")
    return found


def h2_sql(java: str, h2_jar: Path, h2_url: str, statement: str) -> None:
    cmd = [
        java,
        "-cp",
        str(h2_jar),
        "org.h2.tools.Shell",
        "-url",
        h2_url,
        "-user",
        "sa",
        "-password",
        "",
        "-sql",
        statement,
    ]
    proc = subprocess.run(cmd, capture_output=True, text=True)
    if proc.returncode != 0:
        raise SystemExit(
            f"H2 command failed ({proc.returncode}):\n"
            f"{proc.stdout}\n{proc.stderr}"
        )


def export_table_csv(
    java: str, h2_jar: Path, h2_url: str, table: str, dest: Path
) -> Path:
    # Escape single quotes for H2 SQL string literals
    path = str(dest).replace("'", "''")
    sql = (
        f"CALL CSVWRITE('{path}', "
        f"'SELECT * FROM {table}', "
        f"'charset=UTF-8 fieldSeparator=,');"
    )
    h2_sql(java, h2_jar, h2_url, sql)
    return dest


def read_csv_rows(path: Path) -> tuple[list[str], list[dict[str, str | None]]]:
    if not path.is_file() or path.stat().st_size == 0:
        return [], []
    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        if not reader.fieldnames:
            return [], []
        columns = [c.lower() for c in reader.fieldnames]
        rows: list[dict[str, str | None]] = []
        for raw in reader:
            row: dict[str, str | None] = {}
            for key, value in raw.items():
                col = key.lower()
                if value is None or value == "":
                    row[col] = None
                else:
                    row[col] = value
            rows.append(row)
        return columns, rows


def coerce_value(column: str, value: str | None):
    if value is None:
        return None
    bool_cols = {"profile_public"}
    if column in bool_cols:
        return value.strip().upper() in {"TRUE", "T", "1", "YES"}
    int_cols = {
        "id",
        "author_id",
        "user_id",
        "scenario_id",
        "thumbnail_id",
        "parent_scenario_id",
        "tag_id",
        "idx",
        "storyboard_columns",
        "grid_column",
        "grid_column_span",
        "grid_row",
        "grid_row_span",
        "image_height",
        "image_width",
        "size_bytes",
    }
    if column in int_cols:
        return int(value)
    float_cols = {"marker_x", "marker_y"}
    if column in float_cols:
        return float(value)
    return value


def upsert_rows(
    conn: psycopg.Connection,
    table: str,
    columns: list[str],
    rows: list[dict[str, str | None]],
    dry_run: bool,
) -> int:
    if not rows:
        print(f"  {table}: 0 rows")
        return 0

    conflict = CONFLICT[table]
    update_cols = [c for c in columns if c not in conflict]
    col_idents = sql.SQL(", ").join(sql.Identifier(c) for c in columns)
    placeholders = sql.SQL(", ").join(sql.Placeholder() * len(columns))

    if update_cols:
        set_clause = sql.SQL(", ").join(
            sql.SQL("{} = EXCLUDED.{}").format(sql.Identifier(c), sql.Identifier(c))
            for c in update_cols
        )
        query = sql.SQL(
            "INSERT INTO {} ({}) VALUES ({}) ON CONFLICT ({}) DO UPDATE SET {}"
        ).format(
            sql.Identifier(table),
            col_idents,
            placeholders,
            sql.SQL(", ").join(sql.Identifier(c) for c in conflict),
            set_clause,
        )
    else:
        query = sql.SQL(
            "INSERT INTO {} ({}) VALUES ({}) ON CONFLICT ({}) DO NOTHING"
        ).format(
            sql.Identifier(table),
            col_idents,
            placeholders,
            sql.SQL(", ").join(sql.Identifier(c) for c in conflict),
        )

    values = [
        tuple(coerce_value(c, row.get(c)) for c in columns) for row in rows
    ]
    if dry_run:
        print(f"  {table}: would upsert {len(values)} rows")
        return len(values)

    with conn.cursor() as cur:
        cur.executemany(query, values)
    print(f"  {table}: upserted {len(values)} rows")
    return len(values)


def resolve_user_unique_conflicts(
    conn: psycopg.Connection,
    users: list[dict[str, str | None]],
    dry_run: bool,
) -> None:
    """Rename conflicting PG users when H2 ids differ."""
    if not users or dry_run:
        return
    with conn.cursor() as cur:
        for row in users:
            user_id = int(row["id"])  # type: ignore[arg-type]
            username = row["username"]
            email = row["email"]
            cur.execute(
                """
                UPDATE user_
                SET username = username || '_pre_migrate_' || id::text,
                    email = email || '.pre_migrate.' || id::text
                WHERE id <> %s
                  AND (username = %s OR email = %s)
                """,
                (user_id, username, email),
            )


def bump_sequences(conn: psycopg.Connection, dry_run: bool) -> None:
    if dry_run:
        print("  sequences: skipped (dry-run)")
        return
    with conn.cursor() as cur:
        for table, seq in SEQUENCE_BUMPS:
            cur.execute(
                sql.SQL(
                    "SELECT setval(%s, GREATEST("
                    "(SELECT COALESCE(MAX(id), 1) FROM {}), 1))"
                ).format(sql.Identifier(table)),
                (seq,),
            )
            cur.execute("SELECT last_value FROM " + seq)
            last = cur.fetchone()[0]
            print(f"  {seq} -> {last}")


def parse_args() -> argparse.Namespace:
    root = repo_root()
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--h2-file",
        default=str(root / "data" / "bootapp"),
        help="H2 file path without .mv.db (default: ./data/bootapp)",
    )
    parser.add_argument(
        "--h2-jar",
        default=None,
        help="Path to h2-*.jar (default: latest in local Maven cache)",
    )
    parser.add_argument(
        "--java",
        default=None,
        help="java executable (default: $JAVA_HOME/bin/java or PATH)",
    )
    parser.add_argument(
        "--pg-dsn",
        default=os.environ.get(
            "DATABASE_URL", "postgresql://vignette@localhost:5432/vignette"
        ),
        help="PostgreSQL DSN (default: DATABASE_URL or local vignette)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Export + report only; do not write to PostgreSQL",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    java = find_java(args.java)
    h2_jar = find_h2_jar(args.h2_jar)
    h2_file = Path(args.h2_file).expanduser().resolve()
    if not Path(str(h2_file) + ".mv.db").is_file() and not h2_file.with_suffix(
        ".mv.db"
    ).is_file():
        # allow either bootapp or bootapp.mv.db style
        mv = Path(str(h2_file) + ".mv.db")
        if not mv.is_file():
            raise SystemExit(f"H2 database not found: {h2_file}.mv.db")

    h2_url = f"jdbc:h2:file:{h2_file};IFEXISTS=TRUE;AUTO_SERVER=TRUE"

    print(f"Java:  {java}")
    print(f"H2:    {h2_url}")
    print(f"H2 jar:{h2_jar}")
    print(f"PG:    {args.pg_dsn}")
    print(f"Mode:  {'dry-run' if args.dry_run else 'migrate'}")

    with tempfile.TemporaryDirectory(prefix="h2_to_pg_") as tmp:
        tmp_path = Path(tmp)
        exports: dict[str, tuple[list[str], list[dict[str, str | None]]]] = {}
        print("\nExporting from H2…")
        for table in TABLES_IN_ORDER:
            csv_path = tmp_path / f"{table.lower()}.csv"
            export_table_csv(java, h2_jar, h2_url, table, csv_path)
            columns, rows = read_csv_rows(csv_path)
            exports[table] = (columns, rows)
            print(f"  {table}: {len(rows)} rows")

        print("\nWriting to PostgreSQL…")
        with psycopg.connect(args.pg_dsn) as conn:
            conn.execute("BEGIN")
            try:
                resolve_user_unique_conflicts(
                    conn, exports["USER_"][1], args.dry_run
                )
                for table in TABLES_IN_ORDER:
                    columns, rows = exports[table]
                    if not columns:
                        print(f"  {PG_NAME[table]}: empty export")
                        continue
                    upsert_rows(
                        conn, PG_NAME[table], columns, rows, args.dry_run
                    )
                print("\nUpdating sequences…")
                bump_sequences(conn, args.dry_run)
                if args.dry_run:
                    conn.execute("ROLLBACK")
                    print("\nDry-run complete (rolled back).")
                else:
                    conn.execute("COMMIT")
                    print("\nMigration committed.")
            except Exception:
                conn.execute("ROLLBACK")
                raise

    print(
        "\nNote: LANGUAGE / Glottolog tables were not copied "
        "(use the Glottolog worker). Storage files stay under APP_STORAGE_ROOT."
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
