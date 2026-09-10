# M19 — Independent Evaluation: Critic Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Verdict

**INCOMPLETE — 0 OF 12 SUBMILESTONES IMPLEMENTED.** M19 is fully specified at
the backlog level but every M19.1–M19.12 task remains `TODO`. Decisions
D048–D056 are design inputs only; the repository and migrated schema remain the
two-player, pair-keyed chess platform. This is not an external evaluation
blocker: each submilestone was assessed in sequence and recorded below. No
production code was changed and no defect IDs were created for work that the
plan already declares unimplemented.

## Sequential requirement evaluation

### M19.1 — Repository and module structure: INCOMPLETE

No accepted decision covers the repository, server process, module boundaries,
or shared-platform extraction layout, and no human sign-off for that
difficult-to-reverse choice is recorded. D044 still correctly defers extraction
until two implementations are in view. Consequently the scope effects on
M19.2–M19.11 are not settled.

### M19.2 — Groups: INCOMPLETE

There are no `groups` or `group_members` tables, repositories, routes, or tests.
Friend-or-shared-group invite eligibility, immediate membership, unilateral
leave, transitive eligibility, and non-friend refusal are absent.

### M19.3 — Tables and participants: INCOMPLETE

There is no table aggregate or participant relation. `game_series` remains
keyed by `user_a_id`/`user_b_id`; `games` remains keyed by
`white_user_id`/`black_user_id`. Per-game-type participant ranges, exact-set
series identity, non-user participant references, and a data-preserving
forward migration do not exist.

### M19.4 — Migration V3 and parallel series: INCOMPLETE

Only V1 and V2 migrations exist. The installed database still has
`game_series_one_active_per_pair` and `close_after_current_game`.
`SeriesService.openWithGame` still reuses the active pair series, and the
architecture still documents the pair constraint. No V3 migration or
supersession test update exists.

### M19.5 — Unfriending independent of series: INCOMPLETE

Friend removal still drives the close-after-current-game lifecycle through the
series service, the schema and DTOs still expose that flag, and the existing
tests encode D013. No independent series-exit action has replaced the coupling.

### M19.6 — Seat rotation by cycle: INCOMPLETE

There is no persisted cycle length or prior base order and no N=2/3/4 property
suite. Chess still alternates two colours directly. Enemy exclusion/final-turn
placement cannot exist without participant kinds.

### M19.7 — Non-user participant kind: INCOMPLETE

All game participants remain foreign keys to `users`; there is no discriminator
or persistent non-user instance. The required isolation from friendships,
dashboard, activity, and username uniqueness, plus kind-filtered rotation, is
not implemented.

### M19.8 — N-participant resignation and series exit: INCOMPLETE

The current command model has exactly two chess players. There is no
N-participant resignation state, continue-at-table decision, fresh table for
the remainder, or explicit series-exit action.

### M19.9 — Undo storage sizing spike: INCOMPLETE

The review identifies whole-history rewrite cost and D055 chooses an unbounded
last-shuffle horizon, but no measured sizing artifact compares state size,
snapshot retention, or per-action database cost. No analysis chooses among
whole snapshots, structural sharing, or incremental storage, and the proposed
append/truncate persistence shape has not been costed or adopted.

### M19.10 — Per-viewer projection and state identity: INCOMPLETE

The review correctly identifies the visibility boundary, but no accepted
decision defines a viewer-qualified state identity or its consequences for
caching and deduplication. `GameView` still projects chess perspective rather
than hidden information, and there is no deck/seed/card redaction layer.

### M19.11 — Login/action activity timestamps: INCOMPLETE

The `users` table and `UsersTable` mapping contain `last_seen_at` only. There is
no `last_login_at` or `last_action_at`, no write path for either timestamp, and
no decision defining their semantics. The current API therefore has no new
fields, but only because the feature is absent.

### M19.12 — Error and connection-failure logging: INCOMPLETE

M16 request/command logging remains in place, but realtime connect/drop paths
contain no error-level logging and no test forces a connection failure and
captures a log line. Caught errors are not comprehensively recorded under the
M16.5 no-secrets policy.

## Schema verification

The migrated disposable PostgreSQL schema contains only `users`,
`friendships`, `game_series`, `games`, `moves`, `game_events`, and Flyway's
history table. `users` has `last_seen_at` but neither new activity timestamp;
`game_series` retains pair columns and `close_after_current_game`; `games`
retains white/black user columns; and the pair uniqueness index exists. This
matches the source migrations and rules out an untracked partial M19 schema.

## Classification and next implementation boundary

These twelve outcomes are unmet planned work, not newly discovered defects in
implemented M19 behavior. Creating shallow evaluator tests for types and routes
that do not exist would duplicate the backlog and would not exercise a contract,
so no expected-red runtime regression was added. M19.1 remains the first
implementation gate and still requires the recorded human architecture choice
the task calls for. That missing sign-off did not block completing this
evaluation of M19.2–M19.12.

## Carried findings

M10-01, M12-01, M14-01 through M14-03, and M18-01 remain unresolved. The first
five are runtime defects in the existing chess platform; M18-01 is a review
evidence overclaim. None is subsumed by M19's unimplemented scope.
