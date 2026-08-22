# Development and Verification Guide

This file records the exact commands and environment details used to build, run, and verify the project.

During Milestone 1, replace all placeholders/examples below with commands that have actually been executed successfully.

Do not treat an example command as authoritative until verified.

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

### Aggregate Quality Check

Windows:

    .\gradlew.bat check

Linux/macOS/CI:

    ./gradlew check

Status: VERIFIED

This runs the Gradle verification lifecycle, including Android lint and applicable module checks/tests.

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

### Android Build

Windows:

    .\gradlew.bat :android-app:build

Linux/macOS/CI:

    ./gradlew :android-app:build

Status: VERIFIED

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
- pull requests targeting `main`

The workflow runs:

    ./gradlew ktlintCheck
    ./gradlew check
    ./gradlew :game-core:test
    ./gradlew :android-app:build
    ./gradlew :server:test
    ./gradlew :server:build

Required policy:

- CI must remain green for verified work.
- Do not ignore a failing required CI check.
- Fix failures caused by the current change before treating work as complete.

## Verification Policy

For every behavior change:

1. run relevant narrow tests,
2. run affected-module tests,
3. build/check the affected module,
4. inspect `git status`,
5. inspect `git diff`.

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
