# Chess MVP

A native Android multiplayer chess game built as the first implementation of a reusable turn-based game platform.

The long-term goal is not to remain a chess application. Chess is being used to learn and validate the architecture needed for a future custom deck-building strategy game.

## Current Stack

- **Android:** Kotlin + Jetpack Compose
- **Shared game logic:** pure Kotlin/JVM `game-core`
- **Backend:** Kotlin + Ktor
- **Database:** PostgreSQL 18 locally/CI; beta on `ChessGame Dev`'s PostgreSQL
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
    ├── PLATFORM-REVIEW.md
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

**`docs/BACKLOG.md` is the source of truth for task-level status.** This section
summarises where the project has got to and does not restate task counts, which
is what made the previous version of it wrong.

The chess MVP is complete, deployed, and has been played by someone other than
the developer. The arc:

- `M1`–`M13` — the chess engine, the authoritative Ktor server, and the Android
  components.
- `M14` — Android wired into an integrated multiplayer application, proved with a
  two-client play-through.
- `M15` — the Ktor server deployed to Render Free against `ChessGame Dev`'s
  PostgreSQL.
- `M16` — client and network hardening.
- `M17` — a signed beta APK handed to a real tester, who installed it on their own
  physical Android device, got through onboarding unaided, and played an online
  game to the end without developer intervention.
- `M18` — a review of what the chess implementation proved about the platform
  underneath it, in `docs/PLATFORM-REVIEW.md`.

Implemented foundations include:

- a pure Kotlin/JVM chess engine covering legal moves, terminal results, draw
  rules, active history, undo, and resignation,
- a Ktor server with Supabase JWT verification, PostgreSQL persistence,
  friends, series, authoritative commands, WebSockets, automatic rematches,
  dashboard/history queries, idempotency safeguards, and safe logging,
- a Flyway-managed PostgreSQL schema exercised against disposable PostgreSQL in
  local development and CI,
- a Compose Android app covering anonymous auth, onboarding, friends,
  dashboard, online play, history, and local pass-and-play,
- aggregate Gradle verification with ktlint, Android lint, JVM tests, Android
  unit tests, APK assembly, and server distributions.

Independent evaluation (`evals/`) runs on its own track and lags implementation.
`docs/CODEX_EVALUATION_STATE.md` is the source of truth for how far it has got
and what it currently has open.

What happens next — integrating `claude-autopilot` into `main`, and designing the
deck-building game the platform was built toward — is human-directed.
`docs/PLATFORM-REVIEW.md` and `D044` are where that design work starts.

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

Independent milestone evaluation on `codex-autopilot` follows
`docs/INDEPENDENT-EVALUATION.md`: each milestone receives its own committed and
pushed checkpoint, defects are carried forward without stopping the run, and
evaluation continues through M14.
