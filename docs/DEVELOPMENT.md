# Development and Verification Guide

This file records the exact commands and environment details used to build, run, and verify the project.

During Milestone 1, replace all placeholders/examples below with commands that have actually been executed successfully.

Do not treat an example command as authoritative until verified.

## Environment Versions

Fill in during bootstrap:

```text
JDK:
Gradle:
Kotlin:
Android Gradle Plugin:
Android compileSdk:
Android minSdk:
Ktor:
PostgreSQL:
```

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

### Aggregate Build / Check

**Status:** UNVERIFIED PLACEHOLDER

Example shape:

```bash
./gradlew build
```

Replace with the actual verified aggregate command if different.

### Game Core Tests

**Status:** UNVERIFIED PLACEHOLDER

Likely shape for pure Kotlin/JVM:

```bash
./gradlew :game-core:test
```

### Game Core Build

**Status:** UNVERIFIED PLACEHOLDER

```bash
./gradlew :game-core:build
```

### Android Unit Tests

**Status:** UNVERIFIED PLACEHOLDER

```bash
./gradlew :android-app:testDebugUnitTest
```

### Android Debug Build

**Status:** UNVERIFIED PLACEHOLDER

```bash
./gradlew :android-app:assembleDebug
```

### Android Lint / Static Checks

**Status:** UNVERIFIED PLACEHOLDER

Record exact command after tooling is selected.

### Server Tests

**Status:** UNVERIFIED PLACEHOLDER

```bash
./gradlew :server:test
```

### Server Build

**Status:** UNVERIFIED PLACEHOLDER

```bash
./gradlew :server:build
```

### Server Run

**Status:** UNVERIFIED PLACEHOLDER

Example shape:

```bash
./gradlew :server:run
```

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

After M1.7, document:

```text
CI provider:
Workflow file:
Triggers:
Commands run:
Required status checks:
```

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
