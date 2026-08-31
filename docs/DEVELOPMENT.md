# Development and Verification Guide

This file records the exact commands and environment details used to build, run, and verify the project.

The engine, authoritative server, database integration, and the Android
application all build successfully. Database migrations and local PostgreSQL are
configured. The Android multiplayer flow has been verified end to end on two
emulators against a development server and the `ChessGame Dev` Supabase project
(`M14.18`, 2026-08-31) — see **Reaching the development server from an
emulator**. Beta deployment is still not configured; that is `M15`.

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

Keep these versions synchronized with `gradle/libs.versions.toml`, Android build
configuration, `compose.yaml`, and CI.

## Verified Commands

Run all Gradle commands from the repository root.

### Single Aggregate Verification Command

Windows:

    .\gradlew.bat build

Linux/macOS/CI:

    ./gradlew build

Status: VERIFIED (2026-08-28; 134 tasks, BUILD SUCCESSFUL)

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

The bootstrap-era Android application was manually verified to launch. The
current local chess screen builds and is covered by host-side tests, but its
representative emulator/device play-through is still the blocker on `M5.7`.
The authenticated dashboard and online game flow are not yet wired into the
application entry point; see `M14.5`–`M14.18`.

### Android Lint / Static Checks

Windows:

    .\gradlew.bat check

Status: VERIFIED

The current lint report completes with zero errors. At the 2026-08-28 audit it
contained 20 non-blocking warnings and 2 hints, mainly dependency-update notices
and generated-template cleanup.

ktlint is the current Kotlin formatting/style enforcement tool.

Detekt is not currently enabled because the stable 1.x release does not support the project's JVM 24/25 analysis targets.

### Server Tests

Windows:

    .\gradlew.bat :server:test

Linux/macOS/CI:

    ./gradlew :server:test

Status: VERIFIED

Most server tests exercise PostgreSQL transactions and constraints. To run them
for real locally, start the Compose database, set `TEST_DATABASE_URL`, and force
the task:

    $env:TEST_DATABASE_URL = "postgresql://chessgame:chessgame@localhost:55432/chessgame_test"
    .\gradlew.bat :server:test --rerun-tasks

Status: VERIFIED (2026-08-28; 366 tests, 0 failed, 0 skipped)

When `TEST_DATABASE_URL` is absent, `DatabaseTestSupport` returns without
running database assertions. Those methods are currently reported by Gradle as
passed rather than skipped, so a green local build without the variable is not
evidence that PostgreSQL integration ran. CI always supplies the variable.

`--rerun-tasks` is required here, not belt-and-braces. `tasks.test` reads the
variable with `System.getenv` at configuration time and passes it on with
`environment(...)`, and neither is a declared task input: setting or changing
the variable invalidates Gradle's *configuration cache* but leaves the test
task's own inputs untouched. When the task has usable outputs from a previous
run, it may therefore be reported `UP-TO-DATE` and the database tests do not
execute at all — verified empirically (2026-08-28), where the configuration
cache was invalidated and `:server:test` was still `UP-TO-DATE`. With no cached
outputs, or after a source change, the task runs normally; the hazard is a
*silently reused* result, not a guaranteed skip, which is exactly why a green
run without the flag proves nothing about PostgreSQL.

Keep the flag on any run whose purpose is to prove PostgreSQL integration works.
Declaring the variable as a task input in `server/build.gradle.kts` (for example
an `inputs.property` backed by `providers.environmentVariable(...)`) would fix
reuse *across* configured and unconfigured states, so an ordinary run could no
longer be satisfied by output produced without the database. It still would not
guarantee that a given invocation reached the database now: an unchanged input
set with the variable already present remains up to date whether or not
PostgreSQL is currently running. For verification meant to prove the database is
reachable and exercised at this moment, use `--rerun-tasks` regardless. The
plain `:server:test` above remains the everyday command for runs that do not
depend on the database.

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

The server starts in one of two modes:

- with `DATABASE_URL` and `SUPABASE_URL` set, it migrates the database and serves
  the authenticated users, friends, series, game-command, realtime, dashboard,
  and history APIs;
- without them it logs a warning and serves `/health` alone, so the command still
  works on a machine with no database configured.

`ChessServerConfig` points development Android clients at
`http://10.0.2.2:8080`, which is this machine as an emulator sees it. Since
`M14.5` the manifest grants `INTERNET`, and the `debug` source set's
`res/xml/network_security_config.xml` permits cleartext to `10.0.2.2` and
`localhost` only; the release configuration forbids cleartext entirely, so beta
and release traffic remains HTTPS-only (`D033`). The two-client emulator
play-through that proves this end to end was run for `M14.18` (2026-08-31); see
**Reaching the development server from an emulator** below for the one setup
step it needs.

### Reaching the development server from an emulator

Status: VERIFIED (2026-08-31, `M14.18`)

**On an Android 16/17 emulator an app cannot reach the host through
`10.0.2.2`.** Measured on both `ChessPlayer1` and `ChessPlayer2`
(`android-37.1`, `ro.build.version.release` 17, API 37): a plain
`HttpURLConnection` to `http://10.0.2.2:8080/health` returns 200 as the `shell`
user and times out after 10–15 s as the app's own uid, while the same app uid
reaches `https://example.com` and the host's LAN address on the same port
without trouble. The app therefore signs in to Supabase and then fails on its
first call to the Chess server, which the shell reports only as
`Could not reach the server`.

Use `adb reverse` and point the build at `localhost` instead:

    adb -s emulator-5554 reverse tcp:8080 tcp:8080
    adb -s emulator-5556 reverse tcp:8080 tcp:8080

Windows:

    $env:SUPABASE_ANON_KEY = "<publishable key>"
    .\gradlew.bat :android-app:assembleDebug "-PchessServerUrl=http://localhost:8080"

Linux/macOS:

    SUPABASE_ANON_KEY=<publishable key>       ./gradlew :android-app:assembleDebug -PchessServerUrl=http://localhost:8080

`chessServerUrl` follows the same pattern as `supabaseAnonKey`: a Gradle
property, a `gradle.properties` entry, or the `CHESS_SERVER_URL` environment
variable, defaulting to `http://10.0.2.2:8080` when none is given (`D034`).
`localhost` is already one of the two addresses the debug network security
configuration permits in the clear, and a `reverse` map does not survive the
emulator restarting, so re-run it after a cold boot. A release build is
unaffected: it forbids cleartext outright.

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

How migrations are applied is documented in the next section.

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
already current.

`reset` is destructive — it runs a Flyway `clean()`, which drops everything in the
schema — so since `M15.5` it refuses unless the database is disposable. The rule is
in `DisposableDatabase` and is deliberately narrow: only a `localhost`,
`127.0.0.1`, or `::1` host qualifies, which is the `compose.yaml` container and CI's
service container. Anything else, including a URL that cannot be parsed, is refused
with a `NotADisposableDatabaseException` naming the host, and the refusal happens
before anything is dropped. The address checked is the one the live JDBC connection
reports, not one a caller passes in.

This matters because `D035` makes the beta share the `ChessGame Dev` Supabase
project: without the guard, a `TEST_DATABASE_URL` pointing there would drop beta
data that the Supabase Free plan cannot restore. `DatabaseTestSupport` calls the
destructive path on every server test run.

To destroy a non-loopback database deliberately, set the override to exactly this
value — no other value is accepted, so nothing already exported can enable it by
accident:

    CHESSGAME_ALLOW_DESTRUCTIVE_RESET=i-know-this-destroys-data

`migrate` is not guarded. It is forward-only and idempotent, and the server calls it
on startup against whatever database it is deployed with.

`DatabaseConfig.fromEnvironment()` builds the pooled `DataSource` from
`DATABASE_URL` (or `TEST_DATABASE_URL`).

### Running against the local database

Start the disposable database (see **Local PostgreSQL**), then run the server
tests with the test database configured:

Windows (PowerShell):

    $env:TEST_DATABASE_URL = "postgresql://chessgame:chessgame@localhost:55432/chessgame_test"
    .\gradlew.bat :server:test --rerun-tasks

Linux/macOS:

    TEST_DATABASE_URL=postgresql://chessgame:chessgame@localhost:55432/chessgame_test ./gradlew :server:test --rerun-tasks

Status: VERIFIED (2026-08-26)

`--rerun-tasks` is part of the recipe: the variable is not a declared task
input, so without the flag Gradle may reuse prior output and report
`:server:test UP-TO-DATE`; when that happens, the database tests do not run. See
**Server Tests** for the detail.

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

### Building the Android app against it

The app reads the URL and publishable key as `BuildConfig` fields. Supply the key
with `-PsupabaseAnonKey=...`, a `supabaseAnonKey` entry in `gradle.properties`,
or the `SUPABASE_ANON_KEY` environment variable; the URL defaults to the project
above. Setting `SUPABASE_URL` and `SUPABASE_ANON_KEY` also switches on the live
tests — `SupabaseLiveAuthTest` (the two auth calls) and `AppStartupLiveTest`
(the app's startup path end to end) — which are otherwise no-ops:

    $env:SUPABASE_ANON_KEY = "<publishable key>"
    $env:SUPABASE_URL = "https://rkwymrtqayyyfahfgmbm.supabase.co"
    .\gradlew.bat :android-app:testDebugUnitTest --rerun-tasks

Status: VERIFIED (2026-08-28, `M14.6`)

`--rerun-tasks` matters for the same reason it does for the server tests: the
variables are not declared task inputs, so without it Gradle can report the test
task `UP-TO-DATE` and the live tests never run.

Each live run leaves one throwaway anonymous user in the development project.

### What is not configured yet

- The schema in `database/migrations/` has **not** been applied to the Supabase
  database. Only the local disposable database has it so far.
- Applying it is `M15.3`, which under `D035` targets this same project rather
  than a separate beta one. Once that happens this project holds beta game data,
  and the warnings under **Beta Deployment** apply to it.

## Local Logs

Status: VERIFIED (2026-08-26, `M16.5`)

Ktor logs to standard output through `server/src/main/resources/logback.xml`,
which is suitable locally and for a beta host that collects process output.
The root level is `INFO`; Netty, Exposed, and Hikari are reduced to `WARN`.

Request logging records only method, path, and response status. `/health` is
filtered out. It deliberately does not record headers or bodies, so bearer
tokens, refresh tokens, the Supabase key, and duplicate copies of game state do
not enter logs.

Accepted commands log their decision at `DEBUG` because the durable audit event
already records them. Refused commands log at `INFO` with action, user id, game
id, expected version, and outcome. Do not enable generic header/body logging to
debug a request; add narrowly scoped structured context and a test proving that
credentials and canonical state are absent.

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
`TEST_DATABASE_URL` to point at it, so the server's database-backed assertions
run for real in CI instead of returning without exercising PostgreSQL. Its
credentials are throwaway CI values.

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

Status: the current workflow (single `./gradlew build` step plus PostgreSQL,
`actions/checkout@v7`, `actions/setup-java@v5`, and
`gradle/actions/setup-gradle@v6`) ran green on `claude-autopilot` commit
`0ef3228d5e509faa2fb9be4df3efcd125d283042` — GitHub Actions run
[33022371135](https://github.com/jmusselman2/ChessGame/actions/runs/33022371135),
job `Build and Test`, conclusion `success` (2026-08-26). The CI configuration
is current through `M16.5`; later work must continue using the same per-commit
remote gate.

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

`M14.18` verified the Android multiplayer flow end to end on 2026-08-31, and
the project owner accepted `D032`'s free-tier provider decision and its
operational limits the same day, which completed `M15.1`. That acceptance
cleared the recurring-cost stop condition **only**. It did not authorize
creating or changing paid resources, attaching a payment method, deploying, or
handling beta credentials — `M15.2` and `M15.3` each still need their own
explicit human authorization.

Confirmed terms as of 2026-08-31 (numbers and references in `D032`):

| | |
|---|---|
| Render Free instance hours | 750 per workspace per calendar month; Free web services suspended until next month when gone |
| Render Hobby workspace (`$0`) | 5 GB outbound bandwidth, 500 build pipeline minutes, up to 25 services |
| Render Free sleep | spins down after 15 min without traffic; roughly a one-minute cold start |
| Render over-quota, no payment method | services spin down until the next month; no charge |
| Render scaling | Free instance type is single-instance; autoscaling needs Pro |
| Supabase Free | 2 active projects per org, 500 MB database, 1 GB storage, 5 GB egress + 5 GB cached, 50k MAU |
| Supabase Free pausing | project paused after one week of inactivity |
| Supabase Free over-quota | notified, grace period, then restricted under Fair Use (`402`); no charge |
| Supabase Free backups | none; no point-in-time recovery |

**The beta reuses `ChessGame Dev` (`D035`).** No second Supabase project is
created. Development and beta share that project's identities (`auth.users`),
its quotas, and its availability; a pause or a Fair Use restriction stops both.
Game data does **not** mix: local development and CI keep using the disposable
Docker PostgreSQL under **Local PostgreSQL**, and the application schema goes
into the Supabase database only for the beta. That separation is the
`DATABASE_URL` / `TEST_DATABASE_URL` value, so treat those variables as the
thing protecting beta data.

Two rules follow, and `M15.5` enforces the first in code:

- **Never point `TEST_DATABASE_URL` at the Supabase database.** The server tests
  call `Migrations.reset`, which runs a Flyway `clean()` and drops everything in
  the schema. Supabase Free has no backups or point-in-time recovery.
- **Keep the beta `DATABASE_URL` out of your `.env`.** It belongs only in the
  Render environment.

**The beta `DATABASE_URL` must go through the Shared Pooler.** Render is
IPv4-only and a Supabase project's direct database endpoint
(`db.<ref>.supabase.co`) is IPv6-only unless the paid IPv4 add-on is bought,
which the `$0` boundary forbids. Use Supavisor at
`aws-<region>.pooler.supabase.com` in **session mode on port 5432** — IPv4-only
on every tier including Free. Do not use transaction mode on port 6543: it does
not support prepared statements, which Exposed over HikariCP relies on.

### Beta database connection (`M15.3`)

Status: VERIFIED (2026-08-31) for everything that does not need the database
password; applying the migrations is blocked on that one value.

The beta database is this same project's PostgreSQL (`D035`), reached through the
Supavisor **session** pooler:

    postgresql://postgres.rkwymrtqayyyfahfgmbm:<password>@aws-0-us-east-2.pooler.supabase.com:5432/postgres?sslmode=require

Every part of that is settled:

| | |
|---|---|
| Cluster | `aws-0-us-east-2` — confirmed against the pooler on 2026-08-31: `aws-0` reaches password authentication, `aws-1` answers `(ENOTFOUND) tenant/user postgres.rkwymrtqayyyfahfgmbm not found` |
| Port | `5432`, session mode — transaction mode on `6543` has no prepared statements, which Exposed over HikariCP needs (`M15.1`) |
| Username | `postgres.<project-ref>`, the pooler's tenant form, not plain `postgres` |
| `sslmode` | `require`, carried in the URL |
| Why not direct | `db.rkwymrtqayyyfahfgmbm.supabase.co` is IPv6-only and does not resolve on an IPv4-only host such as Render, which was reconfirmed here |

`DatabaseConfig.fromUrl` now carries the query string through onto the JDBC URL
and percent-decodes the credentials, so `?sslmode=require` survives and a
generated password with escaped characters works. Before `M15.3` the query string
was dropped, which would have connected in the clear.

**The one value that is not here.** The database password cannot be read from the
CLI — `supabase projects api-keys` returns API keys, not it — so it has to come
from the project dashboard under *Settings → Database*. Put the whole URL above,
with the password filled in, into your git-ignored `.env` as `BETA_DATABASE_URL`.
It is never committed, and it must never be set as `DATABASE_URL` or
`TEST_DATABASE_URL`, which stay pointed at the disposable container (`M15.5`
enforces the second of those).

Then apply the migrations and verify the whole chain with one command — it starts
the server against the beta database, which migrates on startup, signs in
anonymously, and calls `/me` so Supabase issuing, JWKS verification, and a
database-backed write are all exercised:

```bash
bash scripts/verify-beta-database.sh
```

In normal operation `BETA_DATABASE_URL`'s value belongs in Render's environment
as `DATABASE_URL`, not in anyone's `.env`.

During M15, document the beta separately from local development:

```text
Ktor beta host:
Beta API base URL:
How beta deployment is performed:
How Android selects beta endpoint:
How beta Supabase differs from local/test (`D035`: same project as development
for auth and the beta database; local/test remain the Docker PostgreSQL):
```

### Hosting resource already created (2026-08-28)

A Render Web Service exists. It was created by hand as an early test of the
hosting resource, **not** as M15 deployment work, and it is not functional.
**Do not create a second service** — M15.2 configures and deploys to this one.

```text
Render service name: ChessGame
Type / runtime:      Web Service, Docker
Plan:                Free ($0/month)
Repository / branch: jmusselman2/ChessGame, main
Public URL:          https://chessgame-hit7.onrender.com
```

Where the beta actually stands, so no step is assumed from the existence of the
service:

| State | Status |
|---|---|
| 1. Hosting resource exists | **Done**, manually, outside the backlog |
| 2. Ktor is deployment-ready for Render | Not done — `M15.2` |
| 3. Deployment succeeds and `/health` is reachable | Not done — `M15.2` |
| 4. Beta Supabase/database/auth configured | Not done — `M15.3` |
| 5. Android beta points at the deployed service | Not done — `M15.4` |

The first deploy, of commit `0ef3228`, failed during the Docker build with exit
status 1. That is expected: the repository has no `Dockerfile` yet, which is
`M15.2`'s first acceptance criterion. Do not treat the failed build as a
regression to chase.

Free-plan behaviour to expect once it does run: the service spins down after
inactivity and the next request pays a significant cold start (`D032`).

Do not put production or beta secrets directly in Git.
