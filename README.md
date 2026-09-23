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
│   ├── init/
│   └── migrations/
├── evals/                  -- independent milestone evaluation reports
└── docs/
    ├── PRODUCT.md
    ├── MVP.md
    ├── FUTURE.md               -- non-MVP work, not scheduled (D072)
    ├── ARCHITECTURE.md
    ├── PLATFORM-REVIEW.md
    ├── UNDO-STORAGE.md
    ├── GAME-STATE-VISIBILITY.md
    ├── DECISIONS.md
    ├── BACKLOG.md
    ├── DEVELOPMENT.md
    ├── AUTONOMOUS-DEVELOPMENT.md
    ├── INDEPENDENT-EVALUATION.md
    └── CODEX_EVALUATION_STATE.md
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
  game to the end without developer intervention. Since then the home screen
  names its player, and an "All users" page lets testers add friends without
  typing exact names (`M17.3`, `M17.5`, `M17.6`). User settings (`M17.4`) wait on
  the project owner deciding what they hold.
- `M18` — a review of what the chess implementation proved about the platform
  underneath it, in `docs/PLATFORM-REVIEW.md`.
- `M19` — the platform generalized to tables and typed participants, parallel
  series, explicit series exit, persisted seat rotation, engagement timestamps,
  and failure logging, plus two analyses: undo storage
  (`docs/UNDO-STORAGE.md`) and game-state visibility
  (`docs/GAME-STATE-VISIBILITY.md`). The independent re-evaluation's one
  finding, `M19-01`, has been remediated; see `docs/CODEX_EVALUATION_STATE.md`.

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

Independent evaluation (`evals/`) runs on its own track. It has evaluated M19;
the `M19-01` remediation and `M17.3`, `M17.5` and `M17.6` are still to be
evaluated.
`docs/CODEX_EVALUATION_STATE.md` is the source of truth for its evidence and
findings.

What happens next is the human-sign-off architecture decision in `M20.1`. The
N >= 3 continuation flow is separately deferred to `M20.2`; it is not part of
M19's chess-only series-exit work. Work outside the MVP is listed, unscheduled,
in `docs/FUTURE.md`; the backlog holds only scheduled work (`D072`).

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
evaluation continues through the milestone requested and recorded in
`docs/CODEX_EVALUATION_STATE.md`.
