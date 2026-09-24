# Database

PostgreSQL schema and migrations for the Chess MVP.

This directory contains the implemented PostgreSQL schema and migration source
of truth. The `M6` foundation:

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

Later migrations, each named for the decision and task behind it:

| Migration                            | What it does                                                                                                                                                |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `V2__friendship_status.sql`          | `friendships.status`, room for a later approval flow (`D047`)                                                                                               |
| `V3__groups.sql`                     | `groups` and `group_members`, standing invite-eligibility pools (`D049`, `M19.2`)                                                                           |
| `V4__engagement_timestamps.sql`      | `users.last_login_at` and `users.last_action_at` (`D060`, `M19.11`)                                                                                         |
| `V5__tables_and_participants.sql`    | `game_types`, `tables`, `table_participants` and `game_participants` replace the friend-pair columns of `game_series` and `games` (`D048`, `D063`, `M19.3`) |
| `V6__parallel_series.sql`            | drops the one-active-series-per-table index, so a pair may have several active series (`D053`, `M19.4`)                                                     |
| `V7__series_outlive_friendships.sql` | drops `game_series.close_after_current_game`; removing a friend no longer closes a series (`D053`, `M19.5`)                                                 |
| `V8__seat_rotation.sql`              | `game_series.seat_rotation` (`D050`, `D066`, `M19.6`)                                                                                                       |
| `V9__non_user_participants.sql`      | `non_user_participants`, and seats that can hold one: participants that are not people (`D051`, `D067`, `M19.7`)                                            |

`docs/ARCHITECTURE.md` §27 describes the resulting tables.

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
