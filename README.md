# Chess MVP

A native Android multiplayer chess game built as the first implementation of a reusable turn-based game platform.

The long-term goal is not to remain a chess application. Chess is being used to learn and validate the architecture needed for a future custom deck-building strategy game.

## Current Stack

- **Android:** Kotlin + Jetpack Compose
- **Shared game logic:** pure Kotlin/JVM `game-core`
- **Backend:** Kotlin + Ktor
- **Database:** PostgreSQL 18 locally/CI; beta hosting not selected
- **Persistence:** JetBrains Exposed + HikariCP; Flyway + SQL migrations
- **Authentication:** Supabase anonymous authentication for MVP
- **Realtime:** HTTPS commands + WebSocket updates
- **Build:** Gradle Kotlin DSL
- **Repository:** Monorepo

## Repository Structure

```text
.
├── CLAUDE.md
├── README.md
├── game-core/
├── android-app/
├── server/
├── database/
│   ├── README.md
│   └── migrations/
└── docs/
    ├── PRODUCT.md
    ├── MVP.md
    ├── ARCHITECTURE.md
    ├── DECISIONS.md
    ├── BACKLOG.md
    ├── DEVELOPMENT.md
    └── AUTONOMOUS-DEVELOPMENT.md
```

## Source of Truth

Read the project documents before making architectural or behavioral changes.

Start with:

1. `docs/DECISIONS.md`
2. `docs/PRODUCT.md`
3. `docs/ARCHITECTURE.md`
4. `docs/MVP.md`
5. `docs/BACKLOG.md`

The root `CLAUDE.md` defines document precedence and autonomous-development rules.

## Current Status

The domain engine and authoritative backend are substantially implemented and
tested. The project is **not yet an end-to-end playable multiplayer Android
MVP** because the completed Android auth/API/presentation components have not
been wired into the application entry point and online game flow.

Current backlog state after reconciling it with the implementation:

- 71 tasks `DONE`, 22 `TODO`, 1 `BLOCKED`, and none `IN PROGRESS`,
- M1–M4 and M6–M13 are complete,
- M5.1–M5.6 are complete; M5.7 awaits device/emulator verification,
- M14.1–M14.4 completed dashboard/history data and presentation components,
- M14.5–M14.18 track the missing Android application/network shell and
  multiplayer integration,
- M15 beta hosting is deliberately not started and depends on M14.18,
- M16.3–M16.5 server hardening is complete; client/network hardening remains,
- M17 beta distribution and M18 architecture review remain.

Implemented foundations include:

- a pure Kotlin/JVM chess engine covering legal moves, terminal results, draw
  rules, active history, undo, and resignation,
- a Ktor server with Supabase JWT verification, PostgreSQL persistence,
  friends, series, authoritative commands, WebSockets, automatic rematches,
  dashboard/history queries, idempotency safeguards, and safe logging,
- a Flyway-managed PostgreSQL schema exercised against disposable PostgreSQL in
  local development and CI,
- a Compose local pass-and-play screen plus separate Android anonymous-auth,
  API-client, dashboard, and history components,
- aggregate Gradle verification with ktlint, Android lint, JVM tests, Android
  unit tests, APK assembly, and server distributions.

The most recent completed CI-verified implementation baseline at this
documentation update was commit `0ef3228`, which passed GitHub Actions run
[33022371135](https://github.com/jmusselman2/ChessGame/actions/runs/33022371135).
Milestone-by-milestone scope and dependencies are tracked in
`docs/BACKLOG.md`; the next coding task is `M14.5`.

## Getting Started

Use the committed Gradle wrapper. Verified commands are in `docs/DEVELOPMENT.md`.

Single aggregate verification (also what CI runs):

```bash
./gradlew build
```

Common narrower commands:

```bash
./gradlew :game-core:test
./gradlew :server:test
./gradlew :server:run          # then GET http://localhost:8080/health
./gradlew :android-app:assembleDebug
./gradlew ktlintCheck
```

Database-backed server tests require `TEST_DATABASE_URL`; CI supplies it and
the local Docker setup is documented in `docs/DEVELOPMENT.md`.

## Continuous Autonomous Development

This repository is set up for continuous autonomous implementation of the
backlog. See `docs/AUTONOMOUS-DEVELOPMENT.md`. In short: work happens on the
`claude-autopilot` branch, one verified backlog task per commit, continuing
across milestone boundaries and stopping only for genuine blockers.
