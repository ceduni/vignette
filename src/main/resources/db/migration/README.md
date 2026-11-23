# `db/migration/` — database schema (Flyway)

Flyway is the **only** owner of the schema. Hibernate only validates (`ddl-auto=validate`).

| File | Content |
|------|---------|
| `V1__baseline.sql` | Every table, sequence, key and constraint. |
| `V2__indexes_and_glottolog_wake_trigger.sql` | Lookup indexes, Glottolog pipeline singleton row, `LISTEN/NOTIFY` trigger waking the Python worker. |

## Changing the schema

1. Change the JPA entity.
2. Add `V<next>__<description>.sql` with the matching SQL.
   **Never edit a migration that already ran on a shared database** (Flyway checks a checksum).
3. Run `mvn test` (needs Docker): it applies the migrations to a fresh PostgreSQL and validates the entities.

Enum columns have a `CHECK` constraint listing the values: a new enum constant needs a migration
that replaces it.

## Existing databases

**Database managed by Hibernate before Flyway** (tables but no `flyway_schema_history`):

- `dev`: nothing to do, it is marked as `V1` on the first start;
- `prod`: start once with `FLYWAY_BASELINE_ON_MIGRATE=true`;
- still has the history of the old `V1`–`V16` migrations: back up, then `DROP TABLE flyway_schema_history;`.

**Database created by the first version of `V1__baseline.sql`.** `V1` was rewritten in a compact form
(same tables and constraint names) and now also creates `thumbnail.vignette_scene_json`. Run once,
after a backup:

```sql
ALTER TABLE thumbnail ADD COLUMN IF NOT EXISTS vignette_scene_json text;
UPDATE flyway_schema_history SET checksum = 1589308254 WHERE version = '1' AND type = 'SQL';
```

A database marked `BASELINE` at `V1` only needs the `ALTER TABLE` line.
