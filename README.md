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

The first milestone is repository/bootstrap setup and verification.

After bootstrap, update `docs/DEVELOPMENT.md` with commands that have actually been executed successfully.

## Getting Started

The exact build, test, server, and database commands will be finalized during Milestone 1 and recorded in:

```text
docs/DEVELOPMENT.md
```

Do not copy unverified command examples into automation or CI until they have been confirmed against the generated project.
