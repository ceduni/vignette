# `persistence/` — database access

- [`model/`](model/) — JPA entities (one per table) and the enums stored in them.
- [`repo/`](repo/) — Spring Data repositories.

Hibernate runs with `ddl-auto=validate`: it never creates or changes tables. The schema is owned by
the Flyway migrations in [`src/main/resources/db/migration`](../../../../resources/db/migration/),
and an entity that does not match it stops the application at startup.
