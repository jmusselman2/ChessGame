# M16 Test Report

## Fresh verification

The initial focused and source assessment was completed before any retained M16
report was read.

| Verification | Result |
| --- | --- |
| `NetworkInterruptionTest` | PASS - 19 tests, 0 failures, 0 errors, 0 skipped |
| `AppRestartTest` | PASS - 9 tests, 0 failures, 0 errors, 0 skipped |
| `SilentSocketTest` | PASS - 2 tests, 0 failures, 0 errors, 0 skipped |
| Android resilience aggregate | PASS - 30 tests, 0 failures, 0 errors, 0 skipped |
| `DuplicateCommandTest` | PASS - 11 tests, 0 failures, 0 errors, 0 skipped |
| `SeriesIdempotencyTest` | PASS - 13 tests, 0 failures, 0 errors, 0 skipped |
| `ServerLoggingTest` | PASS - 6 tests, 0 failures, 0 errors, 0 skipped |
| `MoveVersusUndoTest` | PASS - 6 tests, 0 failures, 0 errors, 0 skipped |
| Server hardening aggregate | PASS - 36 tests, 0 failures, 0 errors, 0 skipped |
| Focused aggregate | PASS - 66 tests, 0 failures, 0 errors, 0 skipped |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) connected; owner app protected |
| `git diff --check` | PASS |

Both selections were forced with `--rerun-tasks`. Every PostgreSQL-backed
server case ran rather than skipping. The Android raw silent-peer control is
important: it shows the dead socket remains open without ping, so the passing
detection case does not merely observe an ordinary close.

The current `NetworkInterruptionTest` is four cases larger than the retained
report's selection. It includes forward-only response installation and
foreground recovery as well as the original failed-load, queued reload, lost
reply, cold-start, reconnect, backoff, and error-state boundaries. All three
formerly expected-red M14 cases now pass.

The current `SeriesIdempotencyTest` is three cases larger than the historical
selection because the governing model now supports offer-first existing-series
handling and explicit parallel creation. Its concurrency case continues to
exercise the locked first-game path.

The complete build was run on the identical production/evaluator tree before
only M11-M16 report and evaluation-state documents changed. M16's focused
selections were forced after the M15 checkpoint.

## Historical follow-up

The retained report's 33 server cases are contained in the current 36-case
selection, and its 26 Android cases are contained in the current 30-case
selection. The old three Android failures now pass under D059; no separate
post-comparison rerun was needed because the fresh forced run already executed
their exact retained methods.
