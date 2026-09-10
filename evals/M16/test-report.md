# M16 — Independent Evaluation: Test Report

Production baseline: `38be421dfd64c269687c893300f11e661bfa9c90`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Duplicate commands, series idempotency, logging, and locked command reads | 33 | 0 | 0 | 0 | PASS |
| Network interruption, app restart, and silent-socket resilience | 26 | 3 | 0 | 0 | EXPECTED FAIL |
| Retained cases within the Android resilience selection | 23 | 0 | 0 | 0 | PASS |
| `git diff --check` | — | — | — | — | PASS |

The server selection comprised `DuplicateCommandTest` (11),
`SeriesIdempotencyTest` (10), `ServerLoggingTest` (6), and
`MoveVersusUndoTest` (6). It was forced with `--rerun-tasks` against the
disposable PostgreSQL instance on port 54999 and passed completely.

The Android selection comprised `NetworkInterruptionTest` (15),
`AppRestartTest` (9), and `SilentSocketTest` (2), forced with
`--rerun-tasks`. `AppRestartTest` and `SilentSocketTest` passed completely.
Exactly three `NetworkInterruptionTest` cases failed:

- `anUpdateForAGameStillOpeningIsNotDropped` — M14-01;
- `aDelayedCommandResponseCannotOverwriteANewerReload` — M14-02; and
- `aCompletionRefreshCannotBeOverwrittenByAnOlderDashboardRead` — M14-03.

The other 12 network-interruption cases passed. The result therefore
reproduces the carried M14 findings without revealing a new M16 failure. The
known M10-01 and M12-01 adversarial classes were not part of this focused run;
their expected-red regressions remain intact from their own checkpoints.
