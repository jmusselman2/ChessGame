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

`8080` is only the fallback. The server listens on `PORT` whenever the
environment sets it, which is how a host routes to it (`M15.2`, `D036`); a value
that is not a port number fails the start rather than quietly using `8080`.

The server starts in one of two modes:

- with `DATABASE_URL` and `SUPABASE_URL` set, it migrates the database and serves
  the authenticated users, friends, series, game-command, realtime, dashboard,
  and history APIs;
- without them it logs a warning and serves `/health` alone, so the command still
  works on a machine with no database configured.

`/health` answers `200` in **both** modes, so read the body and not only the
status. In health-only mode it says so, and names what is missing:

    ChessGame server is healthy (health-only: DATABASE_URL and SUPABASE_URL are not set)

That matters most on a host: a deploy whose environment was never filled in
passes its health check and is reported live while serving nothing.

`ChessServerConfig` points development Android clients at
`http://10.0.2.2:8080`, which is this machine as an emulator sees it. Since
`M14.5` the manifest grants `INTERNET`, and the `debug` source set's
`res/xml/network_security_config.xml` permits cleartext to `10.0.2.2` and
`localhost` only; the release configuration forbids cleartext entirely, so beta
and release traffic remains HTTPS-only (`D033`). The two-client emulator
play-through that proves this end to end was run for `M14.18` (2026-08-31); see
**Reaching the development server from an emulator** below for the one setup
step it needs.

### Server Image (Docker)

The image Render runs. Built and run locally exactly as the host builds and runs
it, which is the only way to check a deploy without deploying (`M15.2`, `D036`).

Build:

    docker build -t chessgame-server:local .

Run it the way a host does — the port from the environment, a free instance's
memory, secrets passed in rather than baked in:

    docker run --rm -p 10000:10000 --memory 512m       -e PORT=10000       -e DATABASE_URL="postgresql://chessgame:chessgame@postgres:5432/chessgame_dev"       -e SUPABASE_URL="https://rkwymrtqayyyfahfgmbm.supabase.co"       --network chessgame_default       chessgame-server:local

Status: VERIFIED (2026-09-01)

The whole of that, plus what a deploy actually depends on, runs as one command:

```bash
bash scripts/verify-server-image.sh
```

Status: VERIFIED (2026-09-01) — 8/8. It builds the image, runs it on the compose
network against the disposable local PostgreSQL, and checks that the server runs
as a non-root user, binds the port `PORT` names, answers `/health` without
claiming health-only, gets `MaxRAMPercentage`/`UseSerialGC` through to the JVM,
carries `db/migration/V1__initial_schema.sql` inside `server.jar`, verifies an
anonymous Supabase token and serves `/me` from the database, upgrades an
authenticated WebSocket and delivers the `connected` greeting, and refuses an
unauthenticated one with `401`. It never touches the beta database — that is
`scripts/verify-beta-database.sh`.

Two things about the build worth knowing before changing it:

- **`-PserverOnly=true` leaves `:android-app` out.** `settings.gradle.kts` reads
  it (and `CHESSGAME_SERVER_ONLY`). Without it the build image would need an
  Android SDK and would fail at configuration time. Nothing else sets either, so
  `./gradlew build` and CI are unaffected — which also means CI does **not**
  exercise this mode. Build the image after changing module wiring.
- **`database/migrations/` must stay out of `.dockerignore`.**
  `server/build.gradle.kts` copies it onto the server's classpath, and a server
  that shipped without it starts cleanly and finds no schema to apply.

Local startup inside a 512 MB container was 2.6 seconds (2026-09-01). That is a
floor for a Render cold start, not an estimate of one: the host also has to
schedule an instance and pull the image.

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
PORT
```

`.env.example` at the repository root is the committed template. `DATABASE_URL`
and `TEST_DATABASE_URL` point at the local disposable database (see **Local
PostgreSQL**); `SUPABASE_URL` and `SUPABASE_JWKS_URL` are filled in (see
**Supabase Development Project**), and `SUPABASE_ANON_KEY` is left blank for you
to fill in locally.

Copy it to `.env`, which is git-ignored, for local values.

`PORT` is not in `.env.example` and should not be set locally: it is supplied by
whatever runs the process. Render sets it, the server binds it, and `8080` is the
fallback for a machine where nothing does (`D036`).

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

- The schema in `database/migrations/` has been applied to the Supabase database
  since `M15.3` (2026-08-31). Under `D035` that is this same project rather than
  a separate beta one, so it now holds beta game data and the warnings under
  **Beta Deployment** apply to it.

### Building the Android beta (`M15.4`)

Status: VERIFIED (2026-09-02), including the play-through against the deployed
service on the `ChessPlayer1` emulator.

A beta build is an ordinary release build pointed at the deployed HTTPS endpoint
through the same `chessServerUrl` input a development build uses (`D034`) — the
address is build configuration, never a literal in application source:

    .\gradlew.bat :android-app:assembleRelease `
      "-PchessServerUrl=https://chessgame-hit7.onrender.com" `
      "-PsupabaseAnonKey=<publishable key>"

What makes that a beta rather than a development build:

- **HTTPS and WSS only.** The release network security configuration forbids
  cleartext outright, and only the `debug` source set permits it, to `10.0.2.2`
  and `localhost` (`D033`). `ChessServerConfig.webSocketUrl` turns `https` into
  `wss`, so the socket is exactly as protected as the rest of the traffic.
- **Forgetting the address is a loud failure, not a silent one.**
  `ChessAppDependencies` passes `BuildConfig.DEBUG` as the cleartext allowance,
  so a release build left on the emulator-loopback default refuses at startup and
  says how to fix it. Without that it would install, launch, and fail every
  request against the network security configuration, explaining nothing.
- **No secret is committed.** The publishable key is supplied at build time
  exactly as it is for a development build, and the beta `DATABASE_URL` never
  reaches the app at all — it is the server's, and lives only in Render's
  environment.

The check is *not* a Gradle-configuration failure on purpose: `.\gradlew.bat
build` assembles the release APK with the ordinary defaults, so failing there
would break the normal build and CI with it.

**The service sleeps, and the app has to say so.** Startup and canonical reloads
retry with capped exponential backoff under a configurable deadline
(`ServerWakePolicy`, default 150 s against the 59.0 s / 64.5 s cold starts
measured in `M15.2`), and the startup screen shows *"Waking the server…"* rather
than an error while that is happening. Mutating commands are deliberately **not**
retried: a move carries the version it was decided against, and re-sending it is
settled by the server's version guard, not by a client loop (`D021`).

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

**TLS is the client's job here.** The pooler accepts `sslmode=disable` — verified
2026-08-31, it connected in the clear when asked — so nothing but `sslmode=require`
in the URL keeps beta traffic encrypted. Check the client's own view rather than
`pg_stat_ssl`: behind Supavisor that view describes the pooler-to-PostgreSQL hop
inside Supabase and reads `false` even over a TLS client link.

    docker compose exec -T postgres psql "$BETA_DATABASE_URL" -c '\conninfo'

Status: VERIFIED (2026-08-31) — `SSL Connection | true`, TLSv1.3,
`TLS_AES_256_GCM_SHA384`.

### Exporting the beta database

Status: VERIFIED (2026-08-31)

Supabase Free has no automatic backups and no point-in-time recovery, and since
`D035` there is no second project holding an untouched copy. This is the whole of
the recovery story, so run it before anything risky and on whatever cadence the
beta deserves:

    docker compose exec -T postgres pg_dump "$BETA_DATABASE_URL"       --schema=public --no-owner --no-acl > beta-backup.sql

`--schema=public` keeps it to the application's own seven tables — roughly 14 KB
empty. Without it the dump also carries Supabase's internal `auth` and `storage`
schemas, which are not yours to restore. `--no-owner --no-acl` keeps it
restorable into a database with different role names.

Write the dump somewhere outside the repository: it contains beta players' data
and `.gitignore` does not know about it.

The beta, as distinct from local development:

```text
Ktor beta host:      Render Free Web Service "ChessGame" (Oregon), Docker runtime
Beta API base URL:   https://chessgame-hit7.onrender.com  (live since 2026-09-02)
How beta deployment is performed:
                     Render builds ./Dockerfile from the tracked branch and runs
                     the image; auto-deploy fires on each commit to that branch.
                     DATABASE_URL and SUPABASE_URL come from the service's
                     environment. Nothing is deployed from a developer machine.
How Android selects beta endpoint:
                     Not yet — M15.4. Build configuration, not a literal in
                     source; development builds keep the emulator-loopback
                     default (D033, D034).
How beta Supabase differs from local/test (`D035`: same project as development
for auth and the beta database; local/test remain the Docker PostgreSQL):
                     Auth is the one ChessGame Dev project everywhere. The beta's
                     game data is that project's PostgreSQL, reached through the
                     Supavisor session pooler; local and CI game data stay in the
                     disposable Docker PostgreSQL. DATABASE_URL is the whole of
                     that separation.
```

### The Render service, and what is configured on it

A Render Web Service exists. It was created by hand on 2026-08-28 as an early
test of the hosting resource, **not** as M15 deployment work. **Do not create a
second service** — `M15.2` configures and deploys to this one.

Read back from the Render API on 2026-09-01, so this is the service's actual
state rather than a plan:

```text
Render service name: ChessGame
Service id:          srv-da8qq8afngtc7388b690
Workspace:           ChessGame (tea-da8qj0e7bikc73d17ov0)
Type / runtime:      Web Service, Docker
Plan:                free
Region:              oregon
Repository / branch: jmusselman2/ChessGame, main
Dockerfile / context: ./Dockerfile, .
Health check path:   /health
Auto-deploy:         yes, on commit
Instances:           1
Suspended:           no
Public URL:          https://chessgame-hit7.onrender.com
```

`render.yaml` at the repository root is that configuration written down, so a
change to it is reviewable in a diff. The service stays dashboard-managed;
adopting it into a Blueprint is a deliberate human step and applying the file as
a *new* Blueprint would create the second service `D032` warns against (`D036`).

Note what is **not** in that list: `DATABASE_URL` and `SUPABASE_URL`. They are
supplied in Render's environment and are what `render.yaml` marks `sync: false`.
Without them the service starts in health-only mode, passes its health check, and
reports as a successful deploy while serving nothing — so check the `/health`
body after the first deploy, not just the status.

Where the beta actually stands, so no step is assumed from the existence of the
service:

| State | Status |
|---|---|
| 1. Hosting resource exists | **Done**, manually, outside the backlog |
| 2. Ktor is deployment-ready for Render | **Done** — `M15.2`, verified 2026-09-01 |
| 3. Beta Supabase/database/auth configured | **Done** — `M15.3`, verified 2026-08-31 |
| 4. Deployment succeeds and `/health` is reachable | **Done** — building since 2026-09-01; `dep-dabqj2eq1p3s73fs2sog` (`2be1f06`) is `live`, serving out of health-only mode, confirmed 2026-09-02 |
| 5. Android beta points at the deployed service | **Done** — `M15.4`, play-through on the `ChessPlayer1` emulator 2026-09-02 |

Three deploys failed, the last of `b54a40e` on 2026-08-31, all during the Docker
build (`build_failed`). That was expected and is now addressed: the repository
had no `Dockerfile`, which was `M15.2`'s first acceptance criterion. Do not treat
those failures as a regression to chase. Every deploy since the `Dockerfile`
landed has built successfully, starting with `036a1a18` at `2026-09-01T04:44:10Z`
— note that Render's `deactivated` status means a deploy that went live and was
later superseded, **not** one that failed.

**The service is live as of 2026-09-02.** `claude-autopilot` is integrated into
`main` (both are `2be1f06`), auto-deploy fires on each commit to it, and deploy
`dep-dabqj2eq1p3s73fs2sog` finished `live` at `2026-09-02T04:47:28Z`.
`curl https://chessgame-hit7.onrender.com/health` returns `200`
`ChessGame server is healthy` with **no** `(health-only: ...)` suffix, so both
`DATABASE_URL` and `SUPABASE_URL` are set on the service and it is serving the
real API rather than the health-only fallback.

Free-plan behaviour, now measured rather than expected: the service spins down
after about 15 idle minutes and the next request pays a significant cold start.
Measured 2026-09-02, `GET /health` over HTTPS:

| Idle before the request | First request | Warm requests after |
|---|---|---|
| ~21 min | **59.0 s** | 0.43 s, 0.20 s |
| ~20 min | **64.5 s** | 0.28 s |

A request only 7 minutes after the previous one returned in 0.25 s — inside the
15-minute window, so the instance was still up. That is the shape to expect: a
sleeping service costs about a minute, a woken one costs nothing.

Two samples are not a guaranteed upper bound (`D032` says there is none), but
they agree with Render's documented "roughly a minute" and are consistent enough
to design against: `M15.4`'s startup deadline needs comfortable headroom above
60 s, and the UI must read that first minute as *waking*, not as failure.

Do not put production or beta secrets directly in Git.

### What the live deployment still needs

Steps 1–3 below were done by a human — the integration and the environment
variables between 2026-09-01 and 2026-09-02, the deploy automatically from the
commit — and step 4 was measured from here. They are kept for the record; what
remains is under **Still outstanding**. The autonomous loop prepares and verifies
artifacts and may read service state, but does not deploy, set environment
variables, or handle credentials (`D032` acceptance, `M15` milestone note).

1. ~~**Give the service a branch that contains the `Dockerfile`.**~~ **Done** —
   `claude-autopilot` was integrated into `main`; both are `2be1f06`.
2. ~~**Put the beta environment variables on the service.**~~ **Done** — proven
   by the `/health` body, which no longer reports health-only. For reference, the
   two are `DATABASE_URL` (the session-pooler URL under **Beta database
   connection** above, with the password and `?sslmode=require`, held in the
   owner's git-ignored `.env` as `BETA_DATABASE_URL`) and `SUPABASE_URL`
   (`https://rkwymrtqayyyfahfgmbm.supabase.co`).
3. ~~**Deploy, and read the `/health` body.**~~ **Done** — builds have succeeded
   since `036a1a18` on 2026-09-01; auto-deploy then fired on `2be1f06` and
   `dep-dabqj2eq1p3s73fs2sog` went `live` at `2026-09-02T04:47:28Z`.
   `curl https://chessgame-hit7.onrender.com/health` returns
   `ChessGame server is healthy` without `(health-only: ...)`.
4. ~~**Measure cold starts.**~~ **Done** — two samples, 59.0 s and 64.5 s after
   ~20 idle minutes, against 0.20–0.43 s warm. See the table above.

5. ~~**Confirm no payment method is attached to the `ChessGame` workspace.**~~
   **Done** — confirmed by the project owner on 2026-09-02. It cannot be read
   from the repository or the service API, so this rests on that report; the Free
   plan and the single instance were confirmed from the API above.

6. ~~**Check Render usage after the play-through.**~~ **Done** — about **0.1 MB**
   of bandwidth over the whole session, including the deploy, the cold starts,
   the play-through, and the traffic to Supabase, against the Hobby workspace's
   5 GB monthly allowance. `plan: free`, `numInstances: 1`, `suspended:
   not_suspended`, no automatic upgrade.

Nothing is outstanding: `M15.2` and `M15.4` are both `DONE` as of 2026-09-02.

**Installing a beta build to test it.** The release APK is unsigned, so it cannot
be installed as built. For testing, sign it with the local debug keystore — this
is a test convenience, not a distribution mechanism, which is `M17.1`:

    apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android `
      --key-pass pass:android --ks-key-alias androiddebugkey <apk>

**What the 2026-09-02 play-through showed.** Against a service that had been idle
about three hours, the app showed "Waking the server…" rather than an error,
reached username onboarding once awake, loaded the dashboard, and played `e2e4`,
which came back as version 1 with the move listed. A WSS connection to
`wss://chessgame-hit7.onrender.com/ws` delivered `{"type":"connected"}`. Two
throwaway accounts, `BetaProbe1` and `BetaProbe2`, and one game now exist in the
beta database — `D035`'s accepted tradeoff.
