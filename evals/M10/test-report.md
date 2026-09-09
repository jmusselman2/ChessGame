# M10 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Evaluator coverage

Server `M10AdversarialTest` wraps only the evaluator reader's JDBC connection
and pauses its first `moves` query. The game-row query therefore completes
before a real `GameRepository.save` commits `e2-e4`; the history query runs
afterward. Latches make the interleaving deterministic and the writer uses an
independent ordinary database connection.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Retained M10 command/API suites | 53 | 0 | 0 | 0 | PASS |
| M10 adversarial canonical refresh | 1 | 1 | 0 | 0 | DEFECT PROVED |
| `git diff --check` | — | — | — | — | PASS |

The adversarial failure is
`aRefreshCannotMixAnOldGameRowWithNewMoveHistory`: after the writer committed
version 1, the paused refresh returned version 0. The later assertions also pin
the matching post-move position and history expected from a canonical refresh.

The retained selection comprised `MakeMoveTest`, `StaleVersionTest`,
`ClaimDrawCommandTest`, and `TwoClientGameTest`, forced with `--rerun-tasks`
against the disposable PostgreSQL 18.6 `chessgame_test` database. All 53 passed
with no skips.
