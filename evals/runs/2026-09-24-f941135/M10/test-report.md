# M10 Test Report

## Fresh verification

The focused verification and source assessment were completed before any
retained M10 report was read.

| Verification | Result |
| --- | --- |
| `MakeMoveTest`, `StaleVersionTest`, `ClaimDrawCommandTest`, `TwoClientGameTest`, `GameViewTest`, and `M10AdversarialTest` | PASS - 63 tests, 0 failures, 0 errors, 0 skipped |
| Retained torn-read regression | PASS - the refresh returned one coherent committed game row/history pair |
| New finished-game capability probe | PASS - a resigned threefold-capable position exposed zero draw claims |
| Current requirements, implementation, schema, response mapping, and caller inspection | PASS - no production defect found |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; device interaction not applicable to this server milestone |
| Complete `build --continue --rerun-tasks` | PASS - 134 tasks executed in 16m28s |
| Complete game-core suite | PASS - 394 tests, 0 failures, 0 errors, 0 skipped |
| Complete Android unit suite | PASS - 521 tests, 0 failures, 0 errors, 0 skipped; lint and debug/release APK assembly passed |
| Complete server suite | PASS - 593 tests, 0 failures, 0 errors, 0 skipped |
| `git diff --check` | PASS |

The 63 focused tests comprise 21 move-command cases, 10 stale-version cases, 15
draw-claim cases, 7 two-client cases, 8 view-mapping cases, and 2 independent
adversarial/capability probes. The disposable PostgreSQL-backed cases ran rather
than skipping.

### Evaluator probe

`M10AdversarialTest.aFinishedGameDoesNotAdvertiseDrawClaims` creates two named
participants, plays the eight real knight moves required to make the initial
position occur three times through `GameCommandService`, verifies that a claim
is then available, and resigns Black through the same command service. The
canonical stored game is over and the White `GameView` has an empty
`availableDrawClaims` list. This guards the API capability contract without
changing production code.

The complete build ran all formatting, compilation, packaging, lint, and test
gates from a clean task graph. Deliberate failure-logging, lock-timeout, and
best-effort activity-write scenarios emitted their expected diagnostic logs;
their assertions passed and they did not become build failures.

## Historical follow-up

The retained M10 evaluation's sole defect proof,
`aRefreshCannotMixAnOldGameRowWithNewMoveHistory`, now passes unmodified. The
current D057 implementation re-reads the version after history materialization,
retries a changed snapshot, and has a bounded locked fallback. This is the exact
repair described in the retained remediation report; the current run found no
regression in ordinary reads, mutating command locks, guarded writes, or refusal
payloads.
