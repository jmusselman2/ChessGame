# Database

PostgreSQL schema and migrations for the Chess MVP.

This directory contains the implemented PostgreSQL schema and migration source
of truth for the MVP:

- `M6.1` — development/test PostgreSQL — DONE; the disposable database is the
  Docker container in `compose.yaml`, with `database/init/` seeding
  `chessgame_test`. Commands are in `docs/DEVELOPMENT.md`.
- `M6.2` — PostgreSQL access library selection — DONE; JetBrains Exposed's SQL
  DSL over HikariCP and the PostgreSQL JDBC driver, recorded as `D030` in
  `docs/DECISIONS.md`.
- `M6.3` — repeatable migration process — DONE; plain SQL files here, applied by
  Flyway through `com.jmussel.chessgame.server.db.Migrations`. See
  `docs/DEVELOPMENT.md`.
- `M6.4` — initial schema — DONE; `V1__initial_schema.sql` creates `users`,
  `friendships`, `game_series`, `games`, `moves`, and `game_events` with the
  required constraints.
- `M6.5` — persistence integration — DONE; Ktor repositories load and update
  canonical state transactionally.

Migration files live in `database/migrations/` and are named
`V<version>__<description>.sql`. Applied migrations are immutable; corrections
use the next version. The former `.gitkeep` placeholder was removed when
`V1__initial_schema.sql` was added.

The shared Supabase development project provides authentication, and `M15.3`
applied these migrations to that same project's PostgreSQL to serve the beta
(`D035` reuses `ChessGame Dev` rather than creating a second project). Local and
CI tests continue to use disposable PostgreSQL, so beta data and test data never
share a database.

Do not commit real secrets or production credentials here. See `.env.example`
for the environment-variable template.
