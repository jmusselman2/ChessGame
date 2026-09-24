# M19 — Current Implementation Re-evaluation: Test Report

Evaluated `main`: `1f17312848a6d6c9499c0accbd7eb6401f44e657`

## Results

| Verification                                                  | Result                                               |
| ------------------------------------------------------------- | ---------------------------------------------------- |
| M19 focused server/schema/API suites on disposable PostgreSQL | PASS                                                 |
| Empty database migration through V9                           | PASS                                                 |
| Evaluator V2 -> V9 preservation test                          | PASS                                                 |
| Retained V4 -> current `TablesMigrationTest`                  | PASS                                                 |
| Full server suite before expected-red evaluator regression    | PASS — 560 tests, none skipped                       |
| Full Android debug unit suite                                 | PASS — 442 tests, none skipped                       |
| Formatting/static checks                                      | PASS                                                 |
| Aggregate build with evaluator regression                     | EXPECTED FAIL — 562 server tests, only M19-01 failed |
| `M19ParticipantIdentityRegressionTest`                        | EXPECTED FAIL                                        |
| Live beta cold wake / build identity                          | PASS — build `1f17312`, 54.329 s                     |
| Live HTTPS/WSS beta smoke                                     | PASS                                                 |
| Endpoint-configured debug APK build/install/launch            | PASS                                                 |
| Throwaway-signed release APK verification                     | PASS — all 6 checks                                  |
| Newly established Android session restoration                 | PASS — dashboard after cold relaunch                 |
| Direct beta Flyway-history query                              | BLOCKED — no beta database credential                |
| Signed distributable beta APK                                 | BLOCKED — no project signing key                     |
| Previously existing Android session/data smoke                | BLOCKED — available AVDs began without the app       |
| Deployed log readback                                         | BLOCKED — no Render API credential                   |

All database-backed local tests used the disposable PostgreSQL URL documented in
`docs/DEVELOPMENT.md`, with the required database flag enabled. No beta reset or
manual beta database edit was performed.

## Migration proof

`M19MigrationEvaluationTest` explicitly targets Flyway V2, inserts two users,
their friendship, a series, a game, a move, and an audit event, then runs V3–V9.
It checks Flyway history is exactly 1 through 9; all old ids and rows remain;
table/game participant rows were created; group and non-user tables exist; and
V4 did not invent login or action timestamps.

The existing `TablesMigrationTest` provides an independent denser V4 fixture:
multiple pairs, active and closed series, running and finished games, version and
result fields, dashboard/history reads, moves, and events. Clean migration and
repeatability remain covered by `MigrationsTest` and `InitialSchemaTest`.
The retained engagement tests separately prove accepted login/action writes,
that refused commands and ordinary reads do not count as actions, and that a
timestamp write failure cannot make login unavailable. Direct beta timestamp
inspection is part of the unavailable database access; the beta `/me` check
proves those private values do not leak to the client.

## Evaluator regression

`M19ParticipantIdentityRegressionTest` fails before any insert with:

> A table seats each participant once

The two values are distinct `Participant`s — `USER:<uuid>` and
`SCRIPTED:<uuid>` — and V9 permits exactly those separate references. The
failure is therefore expected evidence for M19-01, not an infrastructure error.

## Beta smoke

`evals/M19/current-beta-smoke.ps1` creates three throwaway Supabase sessions and
prints no token or key. It passed:

- same-token account/session reuse and private timestamp projection;
- username claims, lookup, and friendship listing;
- group creation, eligible additions, transitive membership, and unilateral
  leave;
- opening the existing-series offer and starting a parallel series;
- legal move plus realtime opponent update;
- stale-version refusal carrying canonical recovery state, then undo;
- resignation, automatic rematch, and side rotation;
- explicit series exit with byte-equivalent board/version afterward;
- unfriending without closing or hiding the active series; and
- dashboard and history preservation for the games created by the smoke.

The current build's successful database-backed startup is indirect deployment
evidence for V9: `Application.main` calls `connectAndMigrate` before installing
the authenticated API, and the health response was not the health-only variant.
A direct `flyway_schema_history` query and inspection of genuinely pre-M19 beta
rows still require the unavailable beta database credential.

The Android debug APK was built with the deployed HTTPS endpoint and available
publishable Supabase key, installed cleanly on `ChessPlayer1`, and launched with
`LaunchState: COLD`. The first attempt displayed the intended `Waking the
server...` recovery UI; retry reached username onboarding. After claiming a
throwaway beta username, force-stop plus a second cold launch restored the same
session directly to the dashboard in 11.736 seconds. No AVD contained a
pre-existing installation, so restoration of data predating this evaluation
could not be tested on-device. The API session-reuse check and migration fixture
cover that boundary independently.

`scripts/verify-beta-apk.sh` passed all six checks with its disposable key:
ordinary release remained unsigned, the supplied key produced a verifiable APK,
version metadata and HTTPS endpoint were present, cleartext remained forbidden,
and both incomplete signing configurations failed loudly. The script returned
the workspace to the ordinary unsigned-release state. A real distributable
signature remains unavailable because the project keystore was not supplied.
