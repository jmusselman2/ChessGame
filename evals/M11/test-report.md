# M11 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `UndoMoveTest` | 17 | 0 | 0 | 0 | PASS |
| `MoveVersusUndoTest` | 6 | 0 | 0 | 0 | PASS |
| `git diff --check` | — | — | — | — | PASS |

The selection was forced with `--rerun-tasks` against the disposable PostgreSQL
18.6 `chessgame_test` database. The concurrency class includes the later M16.7
probe that forces a mutating read to wait for an in-flight write rather than
straddling row and history snapshots. All 23 tests passed with no skips.

No new M11 regression file was added. The expected-failing M10 adversarial
refresh regression remains retained for M10-01.
