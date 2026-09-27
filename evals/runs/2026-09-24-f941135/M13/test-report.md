# M13 Test Report

## Fresh verification

The initial focused verification and source assessment were completed before
any retained M13 report was read.

| Verification | Result |
| --- | --- |
| `FinalizeGameTest`, `AutomaticRematchTest`, `ResignationTest`, `ResignRouteTest`, `SeriesLifecycleTest`, `SeriesExitTest`, `SeriesExitRouteTest`, `SeriesIdempotencyTest`, `AuditActorTest`, `SeriesOutlivesFriendshipTest`, and `NewGameBroadcastTest` | PASS - 95 tests, 0 failures, 0 errors, 0 skipped |
| `ResignTest` and `TerminalUndoLockTest` | PASS - 18 tests, 0 failures, 0 errors, 0 skipped |
| Historical-comparison follow-up: `DuplicateCommandTest` | PASS - 11 tests, 0 failures, 0 errors, 0 skipped |
| Focused aggregate | PASS - 124 tests, 0 failures, 0 errors, 0 skipped |
| Current requirements, transaction boundaries, persistence/schema, commands/routes, audit, realtime, and callers | PASS - no production defect found |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; device interaction not applicable to this server lifecycle milestone |
| `git diff --check` | PASS |

All PostgreSQL-backed server cases ran rather than skipping. The fresh server
selection deliberately extends the narrow original M13 suites with current
D053/D068 exit, route, concurrency, idempotency, audit-actor, friendship-
independence, and new-game broadcast coverage. Its race tests accept only the
two serializable leave-versus-finish outcomes and verify that audit/rematch
counts agree with the games committed.

The complete build was run on the identical production/evaluator tree before
only M11-M13 report and state documents changed. M13-specific suites were then
forced with `--rerun-tasks` after the M12 checkpoint.

## Historical follow-up

The retained report's current duplicate-command suite was rerun separately
after comparison: all 11 tests passed. Its older marked-to-close conclusions
were not reused as current evidence; D068 behavior was assessed fresh through
`SeriesExitTest` and `SeriesExitRouteTest`, including the finishing race.
