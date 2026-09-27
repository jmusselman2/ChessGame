# M19 Critic Report

## Fresh assessment

**Fresh verdict: PASS.**

This assessment was completed from the current requirements, decisions,
implementation, migrations, callers, and retained tests before any historical
M19 report was read.

### Scope and boundaries

- Run baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M19:
  `13009135359874ec95b1d0feffc12b454f51543f`
- Evaluated scope: M19.2 through M19.12 in `docs/BACKLOG.md`.
- Former M19.1 is M20.1 and was not started.
- N >= 3 resignation/continuation is M20.2 and was not evaluated as M19 work.
- No production code or historical report was changed.

### Sequential requirement evaluation

| Requirement | Verdict | Fresh evidence |
| --- | --- | --- |
| M19.2 - groups and standing invite eligibility | PASS | V3, `GroupRepository`, group routes, `InviteEligibility`, and current route/repository tests preserve creator membership, friend-of-adder membership, shared-group eligibility, unilateral leave, and separation from game state. |
| M19.3 - tables and participant relations | PASS | V5 registers chess at 2-2 participants, creates tables and kind-qualified participants, enforces the registered range and exact set, and preserves existing data. `M19MigrationEvaluationTest` and the full server suite pass. |
| M19.4 - parallel series | PASS | V6 removes the active-series-per-table constraint. The 409 offer is newest-first, `another=true` creates a distinct series, and table-row locking preserves double-tap convergence. Server and Android callers remain covered. |
| M19.5 - unfriending is independent of series | PASS | V7 removes `close_after_current_game`; friend deletion affects the friendship only and does not close an existing series or game. |
| M19.6 - cycle-based seat rotation | PASS | V8 persists per-series cycles. Current tests cover N = 2, 3, and 4, fairness and rotation-family rules; chess continues to alternate seats. |
| M19.7 - non-user participant kinds | PASS | V9 uses `(kind, ref)` identity, kind-qualified references, and opaque state. Rotation and person-shaped callers filter by kind. The retained equal-UUID `USER`/`SCRIPTED` regression passes in both input orders and converges on one table. |
| M19.8 - explicit series exit | PASS | Explicit leave closes the series, records the actor, leaves the current game reachable, and prevents a later rematch. Chess resignation remains a game action. M20.2 is not part of this result. |
| M19.9 - undo storage sizing | PASS | `docs/UNDO-STORAGE.md` records measurements, alternatives, write amplification, barriers, and the D061 recommendation. This is correctly a policy/spike deliverable, not runtime work. |
| M19.10 - per-viewer state projection | PASS | `docs/GAME-STATE-VISIBILITY.md`, D069, and D070 define allowlisted projection, hidden-state exclusions, history/spectator treatment, and `(gameId, version, viewer)` payload identity while retaining canonical version semantics. Reciprocal documentation pointers are present. |
| M19.11 - engagement timestamps | PASS | V4 adds nullable, unbackfilled timestamps. `/me` records login, accepted game commands record action within their transaction, refused commands and reads do not, and the values are not exposed through the API. |
| M19.12 - failure logging | PASS | Authentication, route, realtime send/timeout, and socket lifecycle failure paths are logged at the documented levels. Forced-failure tests prove useful records are emitted without credentials or hidden state. |

The complete current implementation satisfies every in-scope acceptance
criterion. No fresh finding was opened.

## Historical comparison

Historical M19 material was consulted only after the fresh verdict was fixed.
The earliest retained report evaluated a preimplementation revision and is not
evidence for the current tree. The later re-evaluation found `M19-01`, where
table identity incorrectly dropped participant kind when a user and scripted
participant shared UUID text. The post-remediation reports closed that finding
at `83de79ceed51577b9b8cffa38ca1b937e029537a`.

The retained `M19ParticipantIdentityRegressionTest` passes unchanged on the
current tree. It creates `USER:<uuid>` and `SCRIPTED:<uuid>` in both caller
orders, verifies both remain distinct, and verifies one canonical table. The
retained V2-to-current migration evaluation also passes. Changes after the
historical closure were reviewed for M19 impact; current route, schema,
migration, repository, Android, and logging suites cover the relevant drift.
Nothing reopens `M19-01` or conflicts with M19.2-M19.12.

## Final verdict

**Final M19 verdict: PASS.**

There is no confirmed production defect and no presently known prerequisite
preventing completion. The fresh M1-M19 run is complete. M20.1 was not started.
