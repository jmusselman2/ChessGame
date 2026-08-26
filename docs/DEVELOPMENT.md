# Development and Verification Guide

This file records the exact commands and environment details used to build, run, and verify the project.

Milestone 1 is complete: the commands in the **Verified Commands** section below
have all been executed successfully against this repository locally (last
confirmed 2026-08-25), and the CI workflow ran green on `claude-autopilot` HEAD
(`595c124`, GitHub Actions run 32922786058). Sections covering later milestones
(PostgreSQL, migrations, beta deployment) still contain placeholders and must be
filled in when that work is done.

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
- PostgreSQL: NOT YET CONFIGURED

Use the Gradle wrapper committed to the repository.

## Prerequisites

Expected:

- JDK compatible with the selected Kotlin/Android/Ktor versions
- Android Studio
- Android SDK
- Git
- PostgreSQL for local/test development
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

Document after M6.1:

```text
How PostgreSQL is started:
Database name:
Test database name:
Port:
How migrations are applied:
How test DB is reset:
How integration tests obtain credentials:
```

Do not put real secrets in this document.

## Database Migrations

Document the selected migration tool and verified commands after M6.2/M6.3.

Required operations:

- create/apply migrations,
- inspect migration status,
- reset disposable development/test DB safely.

## Environment Variables

Expected categories may include:

```text
SUPABASE_URL
SUPABASE_ANON_KEY
SUPABASE_JWKS_URL
DATABASE_URL
```

Actual names must be finalized during setup.

Commit an `.env.example` or equivalent template containing names/placeholders only.

Never commit:

- passwords,
- service-role secrets,
- private signing keys,
- production credentials.

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
