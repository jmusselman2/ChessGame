# Chess MVP

A native Android multiplayer chess game built as the first implementation of a reusable turn-based game platform.

The long-term goal is not to remain a chess application. Chess is being used to learn and validate the architecture needed for a future custom deck-building strategy game.

## Planned Stack

- **Android:** Kotlin + Jetpack Compose
- **Shared game logic:** pure Kotlin/JVM `game-core`
- **Backend:** Kotlin + Ktor
- **Database:** PostgreSQL hosted by Supabase
- **Authentication:** Supabase anonymous authentication for MVP
- **Realtime:** HTTPS commands + WebSocket updates
- **Build:** Gradle Kotlin DSL
- **Repository:** Monorepo

## Planned Repository Structure

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

**Milestone 1 (Repository and Build Bootstrap) is nearly complete.** `M1.1`–`M1.6`
are `DONE` (local verification 2026-08-25); `M1.7` (CI) is `IN PROGRESS` until
the updated GitHub Actions workflow has a verified green run. See
`docs/BACKLOG.md`.

- monorepo structure (`game-core/`, `android-app/`, `server/`,
  `database/migrations/`, `docs/`),
- pure Kotlin/JVM `game-core` with a passing unit test,
- Android app (Kotlin + Compose) that depends on and references `game-core`,
- Ktor server with a verified `/health` endpoint that references `game-core`,
- ktlint formatting + Android lint wired into `check`,
- verified developer commands recorded in `docs/DEVELOPMENT.md`,
- GitHub Actions CI running the single aggregate `./gradlew build`
  (workflow written; first green run still pending — `M1.7`).

Milestone-by-milestone progress is tracked in `docs/BACKLOG.md`. Milestone 2
(Chess Domain Model) has not been started.

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

## Continuous Autonomous Development

This repository is set up for continuous autonomous implementation of the
backlog. See `docs/AUTONOMOUS-DEVELOPMENT.md`. In short: work happens on the
`claude-autopilot` branch, one verified backlog task per commit, continuing
across milestone boundaries and stopping only for genuine blockers.
