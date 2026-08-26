# Database

PostgreSQL schema and migrations for the Chess MVP.

This directory is part of the monorepo structure established in Milestone 1
(`M1.1`). The migration tooling, connection details, and initial schema are
defined later:

- `M6.1` — development/test PostgreSQL
- `M6.2` — PostgreSQL access library selection (recorded in `docs/DECISIONS.md`)
- `M6.3` — repeatable migration process (documented in `docs/DEVELOPMENT.md`)
- `M6.4` — initial schema (`users`, `friendships`, `game_series`, `games`,
  `moves`, `game_events`)

Migration files live in `database/migrations/`. The `.gitkeep` file there is only
a placeholder to keep the empty directory tracked — delete it in the same change
that adds the first real migration file (`M6.3`).

Do not commit real secrets or production credentials here. See `.env.example`
for the environment-variable template.
