# Vignette

<p align="center">
  <img src="https://img.shields.io/badge/status-active-2ea44f" alt="status">
  <img src="https://img.shields.io/badge/backend-Spring%20Boot-6DB33F" alt="Spring Boot">
  <img src="https://img.shields.io/badge/frontend-Vue%203-42b883" alt="Vue 3">
  <img src="https://img.shields.io/badge/database-PostgreSQL-4169E1" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/docs-OpenAPI-orange" alt="OpenAPI">
  <img src="https://img.shields.io/badge/license-GPLv3-blue" alt="GPLv3">
</p>

<p align="center">
  Storyboard-based platform for language documentation, learning, and audio-rich scenario exploration.
  <br>
  <a href="https://titiplex.github.io/vignette/">API docs</a> ·
  <a href="#quick-start">Quick start</a> ·
  <a href="https://protolabo.github.io/vignette/">Project report (FR)</a> ·
  <a href="docs/volet-creation.md">Creation guide (FR)</a>
</p>

---

* [About](#about)
* [Tech stack](#tech-stack)
* [Repository layout](#repository-layout)
* [Quick start](#quick-start)
* [Local setup in detail](#local-setup-in-detail)
* [Tests and build](#tests-and-build)
* [Configuration](#configuration)
* [Database](#database)
* [Deploying on Supabase](#deploying-on-supabase)
* [API documentation](#api-documentation)
* [Contributing](#contributing)

---

## About

**Vignette** lets people build **storyboards** (scenarios made of scene images, audio, transcriptions
and translations) to document and teach **low-resource languages**. It targets children learning a
language, communities sharing content, and linguists.

|                  |                                                                              |
|------------------|------------------------------------------------------------------------------|
| **Author**       | Titouan JOHANNY                                                              |
| **Contributors** | Ryan Sari, Léa Hemidj, Ariane Laverrière, Marina Traore ([all](CONTRIBUTORS.md)) |
| **Context**      | IFT-3150, Université de Montréal (2026), supervised by Louis-Edouard LAFONTANT |
| **Universities** | Université de Montréal · Avignon Université                                  |
| **License**      | GPL-3.0                                                                      |

## Tech stack

| Part | Technology |
|------|------------|
| Backend | Java 21, Spring Boot, Spring Security (JWT), Spring Data JPA, Flyway |
| Frontend | Vue 3, Vite, Vitest, Playwright |
| Database | PostgreSQL everywhere: local, tests (Testcontainers), production (Supabase) |
| Language data | [Glottolog](https://glottolog.org), imported by a Python worker |
| API docs | springdoc-openapi (Swagger UI), published on GitHub Pages |

## Repository layout

```text
.
├── src/main/java/org/titiplex/   Spring Boot backend ─ each folder has its own README
│   ├── api/                      REST controllers, DTOs, Swagger annotations
│   ├── service/                  business rules, access checks, file storage
│   ├── persistence/              JPA entities and repositories
│   ├── config/                   security, JWT, OpenAPI, properties
│   ├── bootstrap/                startup tasks
│   └── util/                     shared helpers
├── src/main/resources/
│   ├── db/migration/             Flyway migrations (the database schema)
│   └── application*.properties   configuration per profile
├── vite/                         Vue frontend
├── scripts/glottolog_worker/     Python worker: Glottolog import and updates
├── docs/                         static API docs (GitHub Pages) and guides
└── pom.xml                       Maven build
```

Start with [`src/main/java/org/titiplex/README.md`](src/main/java/org/titiplex/README.md) for the
backend architecture.

## Quick start

Needs Java 21+, Maven 3.9+, Node.js 22+, Python 3 and PostgreSQL 15+. From the project root:

```bash
# once
pip3 install -r scripts/glottolog_worker/requirements.txt
(cd vite && npm install)
psql postgres -c "CREATE ROLE vignette LOGIN SUPERUSER;" -c "CREATE DATABASE vignette OWNER vignette;"

# every time: two terminals
mvn spring-boot:run -Dspring-boot.run.profiles=dev     # terminal A: API on :8081
cd vite && npm run dev                                 # terminal B: site on :5173
```

Open http://localhost:5173 and log in with `admin` / `admin12345`.

On the first start, Flyway creates the tables and, if the language table is empty, the backend
runs the Glottolog import (a few minutes).

## Local setup in detail

### Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| Java | 21+ (23 is fine) | backend |
| Maven | 3.9+ | backend build |
| Node.js + npm | 22+ | frontend |
| Python | 3 | Glottolog import |
| PostgreSQL | 15+ (Homebrew `postgresql@16` or `@18` on macOS) | database |
| Docker | any | backend tests only |

### One-time setup

1. **Install dependencies**

   ```bash
   pip3 install -r scripts/glottolog_worker/requirements.txt
   cd vite && npm install && cd ..
   ```

2. **Start PostgreSQL**, for example `brew services start postgresql@18`.

3. **Create the user and the database**

   ```bash
   psql postgres
   ```

   ```sql
   CREATE ROLE vignette LOGIN SUPERUSER;
   CREATE DATABASE vignette OWNER vignette;
   \q
   ```

   `already exists` errors can be ignored. Check with `psql -U vignette -d vignette -c "SELECT 1;"`.

4. **Emails (optional).** Notification emails are sent by the Python worker:

   ```bash
   cp scripts/glottolog_worker/.env.example scripts/glottolog_worker/.env
   ```

   In `.env`, set `GLOTTOLOG_SMTP_USER` / `GLOTTOLOG_SMTP_FROM` (the Gmail account that **sends**) and
   `GLOTTOLOG_SMTP_PASSWORD` (a Gmail **App Password**, not the account password). Never commit `.env`.

### Running the app

| Terminal | Command | Result |
|----------|---------|--------|
| A — backend | `mvn spring-boot:run -Dspring-boot.run.profiles=dev` | API on http://localhost:8081 (wait for `Started Application`) |
| B — frontend | `cd vite && npm run dev` | site on http://localhost:5173 |
| C — worker (optional) | `cd scripts && python3 -m glottolog_worker` | scheduled Glottolog updates |

Useful pages: `/languages` (map), `/admin` (admin dashboard), and http://localhost:8081/api/docs (Swagger).

### Notification emails

| Setting | Decides |
|---------|---------|
| `scripts/glottolog_worker/.env` | the account that **sends** |
| Admin → Glottolog / Schedule → **Notification email** | the address that **receives** |

Test it with `cd scripts && python3 -m glottolog_worker.send_test_email --to you@example.com`.

### Troubleshooting

**The languages map is empty.** http://localhost:8081/api/languages/by-country should return a large
JSON. If not, run the import by hand (PostgreSQL must be up), then reload `/languages`:

```bash
cd scripts
PGHOST=localhost PGPORT=5432 PGDATABASE=vignette PGUSER=vignette PGPASSWORD= \
  python3 -m glottolog_worker --bootstrap
```

**The backend refuses to start with a Flyway or schema error.** See
[Existing databases](src/main/resources/db/migration/README.md#existing-databases).

## Tests and build

| What | Command |
|------|---------|
| Backend tests (Docker must run) | `mvn clean test` |
| Frontend tests | `cd vite && npm run test:run` (end-to-end: `npm run e2e`) |
| Backend jar | `mvn clean package` then `java -jar target/projet-info.jar --spring.profiles.active=dev` |
| Frontend build | `cd vite && npm run build` (preview: `npm run preview`) |

How the tests are organized, what each kind checks and how to write one:
[backend tests](src/test/README.md) · [frontend tests](vite/tests/README.md).

## Configuration

| Profile | Database | Notes |
|---------|----------|-------|
| `dev` | PostgreSQL on `localhost:5432`, database and user `vignette`, empty password (or `DB_PASSWORD`) | port `8081`, `admin` account created at startup, first Glottolog import when empty |
| `prod` | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | port from `PORT` (default `8081`), pool size `DB_POOL_SIZE` (default `5`) |
| tests | PostgreSQL in Docker (Testcontainers) | nothing to configure |

`pgdev` is an alias of `dev`; the former H2 mode no longer exists.

### Environment variables

```dotenv
# always
APP_JWT_SECRET=change-me
APP_STORAGE_ROOT=./data/storage          # uploaded images and audio

# prod: Supabase session pooler (see "Deploying on Supabase")
DB_URL=jdbc:postgresql://aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
DB_USERNAME=postgres.<project-ref>
DB_PASSWORD=your_password
PORT=8081
```

Any PostgreSQL works the same way, e.g. `DB_URL=jdbc:postgresql://localhost:5432/vignette`.

## Database

The schema is owned by **Flyway** only: the migrations in
[`src/main/resources/db/migration`](src/main/resources/db/migration/README.md). Hibernate only
validates (`ddl-auto=validate`) and refuses to start when an entity and its table disagree.

To change the schema: change the entity, add a new `V<next>__<description>.sql`, run `mvn test`.
Never edit a migration that already ran on a shared database.

Upgrading an older database (Hibernate-managed, or created with the first `V1`): see
[Existing databases](src/main/resources/db/migration/README.md#existing-databases).

## Deploying on Supabase

Supabase is hosted PostgreSQL: the `prod` profile only needs the connection settings.

1. In the Supabase project, open **Connect** and copy the **Session pooler** parameters (port `5432`).
   Do not use the transaction pooler (port `6543`): it breaks JDBC prepared statements and the
   worker's `LISTEN/NOTIFY`. The direct connection is IPv6-only unless you add the IPv4 option.
2. *(Optional)* Copy your local data into the still empty Supabase database. Start the local backend
   once with the current code first, so the dump carries the Flyway history:

   ```bash
   pg_dump -U vignette --no-owner --no-privileges vignette \
     | psql "postgresql://postgres.<project-ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require"
   ```

3. Start the backend with the `prod` profile and the [environment variables](#environment-variables).
   On an empty database, Flyway creates the schema.
4. Give the Python worker the same database in its `.env`:

   ```dotenv
   DATABASE_URL=postgresql://postgres.<project-ref>:<password>@aws-0-<region>.pooler.supabase.com:5432/postgres?sslmode=require
   ```

5. **Turn the Supabase Data API off** (or remove `public` from its exposed schemas). Spring is the
   only client; otherwise tables such as `user_` can be read with the project's `anon` key, as they
   have no row-level security.
6. Make every schema change through Flyway: no edits in the Supabase dashboard, no Supabase CLI migrations.

Uploaded files are not in the database: they stay under `APP_STORAGE_ROOT`, back them up separately.

## API documentation

| Where | URL |
|-------|-----|
| Local Swagger UI | http://localhost:8081/api/docs |
| Local OpenAPI JSON | http://localhost:8081/api/docs/openapi |
| Published | https://titiplex.github.io/vignette/ |

A GitHub Actions workflow builds the backend, exports the OpenAPI spec and publishes `docs/` to
GitHub Pages. See [API_DOCUMENTATION.md](API_DOCUMENTATION.md).

## Contributing

1. Fork the repository and create a branch.
2. Make focused changes, with tests.
3. Open a pull request.

Ideas for later: richer admin tooling, Docker-based local setup, seeded demo content, better
contributor onboarding.

- Contributors: [CONTRIBUTORS.md](CONTRIBUTORS.md)
- Security issues: [SECURITY.md](SECURITY.md)
- API documentation: [API_DOCUMENTATION.md](API_DOCUMENTATION.md)
- Code of conduct: [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md)
- License: [GPL-3.0](LICENSE)
