# Vignette Project

<p align="center">
  <img src="https://img.shields.io/badge/status-active-2ea44f" alt="status">
  <img src="https://img.shields.io/badge/backend-Spring%20Boot-6DB33F" alt="Spring Boot">
  <img src="https://img.shields.io/badge/frontend-Vue%203-42b883" alt="Vue 3">
  <img src="https://img.shields.io/badge/database-PostgreSQL-4169E1" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/docs-OpenAPI-orange" alt="OpenAPI">
  <img src="https://img.shields.io/badge/license-GPLv3-blue" alt="GPLv3">
  <img src="https://img.shields.io/badge/media-audio%20%26%20storyboards-purple" alt="Media">
</p>

<p align="center">
  Storyboard-based platform for language documentation, learning, and audio-rich scenario exploration.
</p>

<p align="center">
  <a href="https://titiplex.github.io/vignette/">API Docs</a>
  ·
  <a href="#getting-started">Getting Started</a>
</p>

**Vignette** is a web platform for **storyboard-based language documentation and learning**.

It lets users create, explore, and manage linguistic content built around scenarios, thumbnails, audio, and
community-oriented features.

<!-- TOC -->
* [Vignette Project](#vignette-project)
  * [Project Information](#project-information)
  * [Overview](#overview)
  * [Goals](#goals)
  * [Technologies](#technologies)
  * [Dependencies](#dependencies)
  * [Structure](#structure)
  * [Links](#links)
  * [Getting started](#getting-started)
    * [Prerequisites](#prerequisites)
    * [Local setup with PostgreSQL and the languages map](#local-setup-with-postgresql-and-the-languages-map)
    * [Back-End](#back-end)
      * [Building and launching the application](#building-and-launching-the-application)
      * [Test backend code](#test-backend-code)
      * [Package the backend as a jar file](#package-the-backend-as-a-jar-file)
    * [Front-End](#front-end)
      * [Install and launch](#install-and-launch)
      * [Build](#build)
      * [Test frontend code](#test-frontend-code)
  * [Configuration](#configuration)
    * [Development profile](#development-profile)
    * [Production profile](#production-profile)
  * [Environment variables](#environment-variables)
    * [Required or recommended](#required-or-recommended)
    * [Production database variables](#production-database-variables)
  * [API documentation](#api-documentation)
    * [Local API docs](#local-api-docs)
    * [Published API docs](#published-api-docs)
  * [Roadmap ideas](#roadmap-ideas)
  * [Contributing](#contributing)
  * [Security](#security)
  * [License](#license)
  * [Code of Conduct](#code-of-conduct)
<!-- TOC -->

## Project Information

|                  |                                                    |
|------------------|----------------------------------------------------|
| **Title**        | Vignette                                           |
| **Author**       | Titouan JOHANNY                                    |
| **Published**    | false                                              |
| **Universities** | - Université de Montréal</br> - Avignon Université |

## Overview

This project aims at creating storyboards for low resource languages, thus supporting **language documentation,
exploration, and learning through storyboards**.
It is aimed at teaching the language to children, as well as providing a centralized platform for community sharing and
providing tools for linguists.

It was initiated at **_Université de Montréal_** in 2026 by **_Titouan JOHANNY_**, as a part of the class IFT-3150 under
the supervision of **_Mr. Louis-Edouard LAFONTANT_**.

## Goals

- Provide a clean platform for **documenting languages through structured scenarios**
- Support **storyboard-like navigation** through thumbnails and related media
- Enable **audio-backed learning and documentation**
- Offer a backend API that can serve both the web app and external tooling
- Keep the project maintainable through a split frontend/backend architecture

## Technologies

The backend is built in Java with Spring Boot and follows a API REST architecture.
The frontend is built with Vite and Vue.js.
The API docs is made with Swagger and OpenAPI, and published on Github Pages.

## Dependencies

Languages data and metadata are directly imported from [Glottolog](https://glottolog.org)

## Structure

```text
.
├── .github/workflows/       # CI and documentation publication workflows
├── docs/                    # Static API documentation published on GitHub Pages
├── scripts/                 # Glottolog Python worker (import / updates)
├── src/                     # Spring Boot backend
│   ├── main/java/org/titiplex/
│   │   ├── api/
│   │   ├── bootstrap/
│   │   ├── config/
│   │   ├── persistence/
│   │   └── service/
│   └── main/resources/
├── vite/                    # Vue frontend
├── pom.xml                  # Maven backend build
└── API_DOCUMENTATION.md     # Notes about API doc generation/publication
```

## Links

- [API docs](https://titiplex.github.io/vignette/)
- [Repository](https://github.com/titiplex/vignette/)
- [Project report](https://protolabo.github.io/vignette/) (in french)

## Getting started

### Prerequisites

You need:

- Java 21+ (23 is fine)
- Maven 3.9+
- Node.js 22+
- npm
- Python 3 (Glottolog language import)
- PostgreSQL (for the full setup with the world map)
- on macOS, Homebrew `postgresql@16` works well

Two local modes:

| Mode | Profile | Database | Use when |
|------|---------|----------|----------|
| Full stack | `pgdev` | PostgreSQL | languages map, Glottolog import, admin updates |
| Light | `dev` | H2 file (`./data/bootapp`) | quick Java work without the Python pipeline |

For H2 only, see [Back-End](#back-end) with profile `dev`.  
For the map and languages, use the setup below.

### Local setup with PostgreSQL and the languages map

Open a terminal at the **project root** (folder with `pom.xml`, `vite/`, and `scripts/`).

#### One-time setup

**1. Install dependencies**

```bash
# Python (Glottolog worker → Postgres)
pip3 install -r scripts/glottolog_worker/requirements.txt

# Frontend (Vue, map, UI)
cd vite
npm install
cd ..
```

**2. Start PostgreSQL**

```bash
brew services start postgresql@16
```

(or start Postgres with your usual method)

**3. Create the database user and database (once)**

```bash
psql postgres
```

In `psql`:

```sql
CREATE ROLE vignette LOGIN SUPERUSER;
CREATE DATABASE vignette OWNER vignette;
\q
```

If Postgres says `already exists`, ignore it and type `\q`.

Check:

```bash
psql -U vignette -d vignette -c "SELECT 1;"
```

**4. Emails (optional) — who sends notifications**

```bash
cp scripts/glottolog_worker/.env.example scripts/glottolog_worker/.env
```

Edit `scripts/glottolog_worker/.env`:

- `GLOTTOLOG_SMTP_USER` / `GLOTTOLOG_SMTP_FROM` → Gmail account that **sends** mail  
- `GLOTTOLOG_SMTP_PASSWORD` → Gmail **App Password** (16 characters), not the normal account password  

Do not commit `.env`.

Who **receives** mail is set in the admin UI (see below).

#### Every time you run the app

Use **at least two terminals**. A third terminal is only needed for continuous Glottolog auto-updates.

**Terminal A — Java (PostgreSQL)**

```bash
# project root
mvn spring-boot:run -Dspring-boot.run.profiles=pgdev
```

Wait for `Started Application`.

- API: `http://localhost:8081`
- If there are **no languages** in the database yet, Java starts the Python import  
  (`python3 -m glottolog_worker --bootstrap`). The first import can take a few minutes.  
- Related log lines: `Startup Python bootstrap`, `[glottolog_worker]`.

Local admin account:

- username: `admin`
- password: `admin12345`

**Terminal B — Frontend**

```bash
cd vite
npm run dev
```

Open:

- site: http://localhost:5173/
- languages map: http://localhost:5173/languages
- admin: http://localhost:5173/admin

**Terminal C — Python worker (optional)**

Leave this open for scheduled Glottolog updates:

```bash
cd scripts
python3 -m glottolog_worker
```

#### Admin: who receives notification emails

1. Log in as `admin` / `admin12345`.
2. Open `/admin`.
3. In Glottolog / Schedule, set **Notification email** (recipient).
4. Save.

| Config | Role |
|--------|------|
| `scripts/glottolog_worker/.env` | account that **sends** mail |
| Admin → Notification email | address that **receives** mail |

Test email:

```bash
cd scripts
python3 -m glottolog_worker.send_test_email --to you@example.com
```

#### Checks

Languages API (expect a large JSON, not `{}`):

http://localhost:8081/api/languages/by-country

If this is empty or fails, clicking a country on the map will not show useful data.

Also:

- Swagger UI: http://localhost:8081/api/docs
- OpenAPI JSON: http://localhost:8081/api/docs/openapi

#### If languages are still missing after startup

Run one import by hand (Postgres must be up):

```bash
cd scripts
export PGHOST=localhost
export PGPORT=5432
export PGDATABASE=vignette
export PGUSER=vignette
export PGPASSWORD=

python3 -m glottolog_worker --bootstrap
```

Reload http://localhost:5173/languages.

### Back-End

The backend is the API part of the project that powers the web app.

#### Building and launching the application

Light local mode with **H2** (no PostgreSQL / no Python pipeline required):

````shell
# in project root
mvn clean build

# launching the application (H2)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
````

> For the map and languages, use profile `pgdev` in [Local setup with PostgreSQL and the languages map](#local-setup-with-postgresql-and-the-languages-map).

Backend should start on `http://localhost:8081`

Dev endpoints (`dev` profile):

- Swagger UI: `http://localhost:8081/api/docs`
- OpenAPI JSON: `http://localhost:8081/api/docs/openapi`
- H2 console: `http://localhost:8081/h2-console`

#### Test backend code

```shell
# in project root
mvn clean test
```

#### Package the backend as a jar file

```shell
mvn clean package
java -jar target/projet-info.jar --spring.profiles.active=dev
```

### Front-End

#### Install and launch

````shell
cd vite # go to the frontend directory
npm install # install the dependencies, skip if already done
npm run dev
````

#### Build

```shell
npm run build
```

To preview the production build:

```shell
npm run preview
```

#### Test frontend code

```bash
cd vite
npm install # install the dependencies, skip if already done
npm run test:run # for single run
npm run test # for watch mode
npm run test:coverage # for coverage report
```

Frontend end to end tests :

````shell
cd vite
npm run e2e

# With playwright UI
npm run e2e:ui
````

## Configuration

### Development profile (`dev`)

The `dev` profile is a light local mode with:

- port `8081`,
- an H2 file database (`./data/bootapp`),
- H2 console enabled,
- multipart upload limits,
- Swagger / OpenAPI enabled.

It does **not** share data with PostgreSQL. The Python Glottolog worker is meant for Postgres.

### Local PostgreSQL profile (`pgdev`)

The `pgdev` profile shares a local PostgreSQL database with the Python worker:

- port `8081`,
- Postgres on `localhost` (default port `5432`, database `vignette`, user `vignette`),
- password empty by default (or set `DB_PASSWORD` if you use one),
- language catalogue can be filled at first start via the Python worker when the table is empty.

### Production profile

The prod profile is configured for deployment with:

- port from `PORT` (default `8081`),
- PostgreSQL datasource,
- configurable credentials through environment variables.

## Environment variables

### Required or recommended

````dotenv
APP_JWT_SECRET=change-me
APP_STORAGE_ROOT=./data/storage
````

### Production database variables

````dotenv
DB_URL=jdbc:postgresql://localhost:5432/vignette
DB_USERNAME=your_user
DB_PASSWORD=your_password
PORT=8081
````

## API documentation

The backend uses **springdoc-openapi** to generate API documentation automatically.

### Local API docs

Once the backend is running:

- Swagger UI: ``http://localhost:8081/api/docs``
- OpenAPI JSON: ``http://localhost:8081/api/docs/openapi``

### Published API docs

The repository includes a GitHub Actions workflow that:

- builds the backend,
- starts the API,
- exports the **OpenAPI** spec,
- publishes the ``docs/`` folder to GitHub Pages.

If GitHub Pages is enabled for the repository, the published API documentation is available from the project’s Pages
site.

## Roadmap ideas

This project can be extended further with:

- richer admin tooling,
- better media management and storage organization,
- stronger access-control modeling,
- improved contributor onboarding,
- Docker-based local setup,
- seeded demo content,

## Contributing

Contributions are welcome.

A good contribution flow is:

1. Fork the repository
2. Create a dedicated branch
3. Make focused changes
4. Add or update tests
5. Open a pull request

## Security

If you find a vulnerability, please follow the process described in <a href="SECURITY.md">SECURITY.md</a>.

## License

This project is distributed under the **GPL-3.0** license. See <a href="LICENSE">LICENSE</a> for details.

## Code of Conduct

A contributor code of conduct can be found in <a href="CODE_OF_CONDUCT.md">CODE_OF_CONDUCT.md</a>.