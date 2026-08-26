# Development and Verification Guide

This file records the exact commands and environment details used to build, run, and verify the project.

Milestone 1 is complete: the commands in the **Verified Commands** section below
have all been executed successfully against this repository locally (last
confirmed 2026-08-25), and the CI workflow ran green on `claude-autopilot` HEAD
(`595c124`, GitHub Actions run 32922786058). Sections covering later milestones
(migrations, beta deployment) still contain placeholders and must be filled in
when that work is done. The local PostgreSQL section is verified as of
2026-08-26 (`M6.1`).

Do not treat an unverified example command as authoritative. When you add or
change a command, run it, then record it here with `Status: VERIFIED` and the
date.

## Environment Versions

Verified during bootstrap:

- JDK: 24 for `game-core`, `server`, and CI
- Android Studio local runtime: bundled JDK 25
- Gradle: 9.5.0
- Kotlin: 2.2.10
- Android Gradle Plugin: 9.3.1
- Android compileSdk: 37
- Android minSdk: 31
- Android targetSdk: 37
- Ktor: 3.5.2
- ktlint Gradle plugin: 14.2.0
- PostgreSQL: 18.6 (Docker `postgres:18-alpine`, host port 55432 — see **Local PostgreSQL**)

Use the Gradle wrapper committed to the repository.

## Prerequisites

Expected:

- JDK compatible with the selected Kotlin/Android/Ktor versions
- Android Studio
- Android SDK
- Git
- Docker (for the disposable local PostgreSQL — see **Local PostgreSQL**)
- Gradle wrapper committed to repository

Record exact required versions after bootstrap.

## Verified Commands

Run all Gradle commands from the repository root.

### Single Aggregate Verification Command

Windows:

    .\gradlew.bat build

Linux/macOS/CI:

    ./gradlew build

Status: VERIFIED (2026-08-25)

`build` is the one command that verifies the whole repository. Across every
module it runs:

- `ktlintCheck` (the ktlint plugin wires it into `check`, and `build` depends on
  `check`),
- `game-core` unit tests,
- `server` unit tests and the server distribution (`distZip`/`distTar`),
- Android debug + release unit tests,
- Android lint,
- the Android debug and release APKs.

This is what CI runs. Use it as the affected-work verification for any change
that can affect more than one module, and as the final gate before marking a
backlog task `DONE`. Prefer the narrower commands below for fast iteration while
implementing.

### Aggregate Quality Check (no packaging)

Windows:

    .\gradlew.bat check

Linux/macOS/CI:

    ./gradlew check

Status: VERIFIED (2026-08-25)

`check` runs the verification lifecycle only — ktlint, Android lint, and all
module tests — without assembling the APKs or the server distribution. It is a
faster subset of `build`.

### Kotlin Formatting Check

Windows:

    .\gradlew.bat ktlintCheck

Linux/macOS/CI:

    ./gradlew ktlintCheck

Status: VERIFIED

### Kotlin Auto-format

Windows:

    .\gradlew.bat ktlintFormat

Linux/macOS:

    ./gradlew ktlintFormat

Status: VERIFIED

Review `git diff` after auto-formatting.

### Game Core Tests

Windows:

    .\gradlew.bat :game-core:test

Linux/macOS/CI:

    ./gradlew :game-core:test

Status: VERIFIED

### Android Unit Tests

Windows:

    .\gradlew.bat :android-app:testDebugUnitTest

Linux/macOS/CI:

    ./gradlew :android-app:testDebugUnitTest

Status: VERIFIED (2026-08-25)

Host-side JVM unit tests for the Android module. Do not rely on Android manual
testing for chess-rule correctness — that belongs in `game-core` tests.

### Android Debug Build

Windows:

    .\gradlew.bat :android-app:assembleDebug

Linux/macOS/CI:

    ./gradlew :android-app:assembleDebug

Status: VERIFIED (2026-08-25)

Produces the debug APK without running lint or release tasks.

### Android Build (debug + release + lint + tests)

Windows:

    .\gradlew.bat :android-app:build

Linux/macOS/CI:

    ./gradlew :android-app:build

Status: VERIFIED (2026-08-25)

The Android application has also been manually verified to launch successfully.

### Android Lint / Static Checks

Windows:

    .\gradlew.bat check

Status: VERIFIED

ktlint is the current Kotlin formatting/style enforcement tool.

Detekt is not currently enabled because the stable 1.x release does not support the project's JVM 24/25 analysis targets.

### Server Tests

Windows:

    .\gradlew.bat :server:test

Linux/macOS/CI:

    ./gradlew :server:test

Status: VERIFIED

### Server Build

Windows:

    .\gradlew.bat :server:build

Linux/macOS/CI:

    ./gradlew :server:build

Status: VERIFIED

### Server Run

Windows:

    .\gradlew.bat :server:run

Linux/macOS:

    ./gradlew :server:run

Status: VERIFIED

Health endpoint:

    http://localhost:8080/health

Expected response:

    ChessGame server is healthy

The endpoint has been verified to return HTTP 200.

### Gradle Project Structure

Windows:

    .\gradlew.bat projects

Status: VERIFIED

Expected modules:

- `:android-app`
- `:game-core`
- `:server`

## Local PostgreSQL

Status: VERIFIED (2026-08-26)

The local database is a disposable Docker container defined in `compose.yaml` at
the repository root. It is development/test only — never a production or beta
environment — and its credentials are deliberately non-secret local values.

| | |
|---|---|
| Image | `postgres:18-alpine` (PostgreSQL 18.6) |
| Container | `chessgame-postgres` |
| Host port | `55432` (so an installed local PostgreSQL on `5432` is left alone) |
| User / password | `chessgame` / `chessgame` (local throwaway values) |
| Development database | `chessgame_dev` |
| Test database | `chessgame_test` |

### Start

    docker compose up -d

Status: VERIFIED (2026-08-26)

Wait until it reports healthy:

    docker compose ps

### Stop, keeping the data

    docker compose stop

### Reset — throw the database away and start clean

    docker compose down -v
    docker compose up -d

Status: VERIFIED (2026-08-26)

`down -v` removes the data volume. The next `up` re-runs
`database/init/01-create-test-database.sql`, which recreates `chessgame_test`
alongside `chessgame_dev`. This is how the test database is reset.

### Open a psql session

    docker compose exec postgres psql -U chessgame -d chessgame_dev
    docker compose exec postgres psql -U chessgame -d chessgame_test

Status: VERIFIED (2026-08-26)

`psql` is not needed on the host; it runs inside the container.

### Connection URLs

    postgresql://chessgame:chessgame@localhost:55432/chessgame_dev
    postgresql://chessgame:chessgame@localhost:55432/chessgame_test

These are also in `.env.example` as `DATABASE_URL` and `TEST_DATABASE_URL`.
Integration tests read them from the environment (`.env` is git-ignored); the
committed template holds only these local throwaway values.

How migrations are applied: documented after `M6.3`.

Do not put real secrets in this document.

## Database Migrations

Status: VERIFIED (2026-08-26)

Migrations are plain, forward-only SQL files in `database/migrations/`, applied by
Flyway. The SQL files are the source of truth for the schema — nothing generates
them from Kotlin.

### File naming

    database/migrations/V<version>__<description>.sql

for example `V1__initial_schema.sql`. Versions are applied in numeric order and a
file that has been applied is never edited; a correction is a new file with the
next version.

The build copies `database/migrations/*.sql` onto the server's classpath at
`db/migration` (Flyway's default), so the server and the tests migrate from
exactly the same files.

### Applying migrations

`com.jmussel.chessgame.server.db.Migrations`:

    Migrations.migrate(dataSource)        // apply everything outstanding
    Migrations.appliedVersions(dataSource) // what this database has already run
    Migrations.reset(dataSource)          // drop everything and re-apply

`migrate` is repeatable: Flyway records applied versions in `flyway_schema_history`,
so the same call is safe on a fresh database, a half-migrated one, and one that is
already current. `reset` is destructive and belongs only to the disposable
development and test databases.

`DatabaseConfig.fromEnvironment()` builds the pooled `DataSource` from
`DATABASE_URL` (or `TEST_DATABASE_URL`).

### Running against the local database

Start the disposable database (see **Local PostgreSQL**), then run the server
tests with the test database configured:

Windows (PowerShell):

    $env:TEST_DATABASE_URL = "postgresql://chessgame:chessgame@localhost:55432/chessgame_test"
    .\gradlew.bat :server:test

Linux/macOS:

    TEST_DATABASE_URL=postgresql://chessgame:chessgame@localhost:55432/chessgame_test ./gradlew :server:test

Status: VERIFIED (2026-08-26)

When `TEST_DATABASE_URL` is not set, the database-backed tests report themselves
as passing without touching a database, so `./gradlew build` works on a machine
with no PostgreSQL. CI sets the variable against its own PostgreSQL service
container, so they always run there.

How the test database is reset: `Migrations.reset`, or
`docker compose down -v && docker compose up -d` for a completely fresh container
(see **Local PostgreSQL**).

## Environment Variables

Expected categories may include:

```text
DATABASE_URL
TEST_DATABASE_URL
SUPABASE_URL
SUPABASE_ANON_KEY
SUPABASE_JWKS_URL
```

`.env.example` at the repository root is the committed template. `DATABASE_URL`
and `TEST_DATABASE_URL` point at the local disposable database (see **Local
PostgreSQL**); `SUPABASE_URL` and `SUPABASE_JWKS_URL` are filled in (see
**Supabase Development Project**), and `SUPABASE_ANON_KEY` is left blank for you
to fill in locally.

Copy it to `.env`, which is git-ignored, for local values.

Never commit:

- passwords,
- service-role secrets,
- private signing keys,
- production credentials.

## Supabase Development Project

Status: VERIFIED (2026-08-26)

The shared development environment. This is *not* the disposable local database —
that is the Docker container under **Local PostgreSQL**, which stays the target
for tests.

| | |
|---|---|
| Project | `ChessGame Dev` |
| Project ref | `rkwymrtqayyyfahfgmbm` |
| Region | `us-east-2` |
| PostgreSQL | 17.6 (`db.rkwymrtqayyyfahfgmbm.supabase.co`) |
| API URL | `https://rkwymrtqayyyfahfgmbm.supabase.co` |
| Auth issuer | `https://rkwymrtqayyyfahfgmbm.supabase.co/auth/v1` |
| JWKS | `https://rkwymrtqayyyfahfgmbm.supabase.co/auth/v1/.well-known/jwks.json` |
| Token signing | `ES256` (EC key, served from JWKS) |

None of the above is a secret — the project ref and API URL ship inside the
Android app. Keys are a different matter and are never committed.

### Anonymous authentication

Anonymous sign-ins are enabled (`D006`), verified on 2026-08-26 by posting to
`/auth/v1/signup` with the publishable key and receiving a session whose token
carries `"is_anonymous": true` and `amr` method `anonymous`. That check leaves a
throwaway anonymous user behind in the development project, which is harmless.

### Getting the keys

    supabase projects api-keys --project-ref rkwymrtqayyyfahfgmbm

Status: VERIFIED (2026-08-26)

Put the publishable (`anon`) key in your local `.env` as `SUPABASE_ANON_KEY`;
`.env` is git-ignored. The `service_role` key is a full-access secret: it never
belongs in the Android app, in this repository, or in a log.

The database password is not retrievable from the CLI — reset or copy it from the
project's dashboard when a direct database connection is needed.

### What is not configured yet

- The schema in `database/migrations/` has **not** been applied to the Supabase
  database. Only the local disposable database has it so far.
- The beta environment is separate again and is `M15.3`.

## Local Logs

Document:

- where Ktor logs appear,
- log level used for development,
- how to enable useful debugging without logging secrets.

## CI

CI provider: GitHub Actions

Workflow file:

    .github/workflows/ci.yml

Triggers:

- pushes to `main`
- pushes to `claude-autopilot`
- pull requests targeting `main`

Actions used (kept current to avoid GitHub runner deprecation warnings):

- `actions/checkout@v7`
- `actions/setup-java@v5` (Temurin, Java 24)
- `gradle/actions/setup-gradle@v6`

The job also starts a disposable `postgres:18-alpine` service container and sets
`TEST_DATABASE_URL` to point at it, so the server's database-backed tests run for
real in CI instead of skipping. Its credentials are throwaway CI values.

The workflow runs a single aggregate step:

    ./gradlew build

`build` covers ktlintCheck, `game-core` tests, `server` tests + distribution,
Android debug/release unit tests, Android lint, and the Android APKs. If a
future need arises to split CI into parallel jobs, keep `./gradlew build` as the
union of what those jobs run.

Required policy:

- CI must remain green for verified work.
- Do not ignore a failing required CI check.
- Fix failures caused by the current change before treating work as complete.
- After changing the workflow file or action versions, confirm the next run on
  `main` or `claude-autopilot` is green.

Status: the workflow (single `./gradlew build` step, `actions/checkout@v7`,
`actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`) ran green on
`claude-autopilot` commit `595c124` — GitHub Actions run
[32922786058](https://github.com/jmusselman2/ChessGame/actions/runs/32922786058),
job `Build and Test`, conclusion `success` (2026-08-25). `M1.7` is `DONE`.

### Remote CI Gate (autonomous workflow)

In the continuous autonomous workflow (`docs/AUTONOMOUS-DEVELOPMENT.md`), a task
may be marked `DONE` once local `./gradlew build` passes, but the workflow may
**not** start the next task until the pushed `claude-autopilot` commit has
passed its required GitHub Actions run.

Monitor the run with the GitHub CLI, and confirm it is the run for the commit
you just pushed:

    git rev-parse HEAD
    gh run list --branch claude-autopilot --limit 10
    gh run watch <run-id> --exit-status
    gh run view <run-id> --log-failed

- Match the run's head SHA to `git rev-parse HEAD`. Do not accept the latest
  unrelated run, a run for an older commit, or an in-progress run as passing the
  gate.
- If `gh` is not installed, `gh auth status` fails, or the repo is not
  reachable, that is a **missing external prerequisite**: stop per the Stop
  Conditions in `docs/AUTONOMOUS-DEVELOPMENT.md`. Do not silently skip the
  remote CI gate.
- A failing run is normally a config/implementation problem — diagnose, fix,
  re-run `./gradlew build` locally, commit, push, and watch CI again using the
  same failure-escalation ladder.

## Verification Policy

For every behavior change:

1. run relevant narrow tests,
2. run affected-module tests,
3. build/check the affected module,
4. run the single aggregate command `./gradlew build` when the change can touch
   more than one module (and always before marking a backlog task `DONE`),
5. run `git diff --check` and fix every reported whitespace error before
   committing,
6. inspect `git status`,
7. inspect `git diff`.

`git diff --check` must report nothing (exit 0) before any commit. If it flags
trailing whitespace or a stray conflict marker on a line the change touched, fix
that line — do not commit over it.

Do not rely on Android manual testing for chess-rule correctness.

Do not mark a test skipped or weaken an assertion merely to obtain a green build when the test represents a documented requirement.

## Beta Deployment

After M15, document separately from local development:

```text
Ktor beta host:
Beta API base URL:
How beta deployment is performed:
How Android selects beta endpoint:
How beta Supabase differs from local/test:
```

Do not put production or beta secrets directly in Git.
