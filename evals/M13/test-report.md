# M13 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| M13 server plus later idempotency suites | 69 | 0 | 0 | 0 | PASS |
| `game-core` resignation suite | 8 | 0 | 0 | 0 | PASS |
| `git diff --check` | — | — | — | — | PASS |

The server selection comprised `FinalizeGameTest`, `AutomaticRematchTest`,
`SeriesClosesAfterLastGameTest`, `ResignationTest`, `ResignRouteTest`, and the
later `DuplicateCommandTest` and `SeriesIdempotencyTest`. It was forced with
`--rerun-tasks` against the disposable PostgreSQL 18.6 database; all 69 tests
passed with no skips. The eight pure `game-core` resignation tests also passed.

No new M13 regression file was added. The earlier expected-failing evaluator
regressions remain retained for their carried findings.
