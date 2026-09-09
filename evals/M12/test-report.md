# M12 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Evaluator coverage

Server `M12AdversarialTest` gives two distinct users one connection each. The
first connection signals that delivery reached it and then suspends forever;
the second completes a delivery marker. The test awaits the first marker before
checking the second, making the sequential fan-out blockage explicit.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Retained M12 and later new-game realtime suites | 31 | 0 | 0 | 0 | PASS |
| M12 adversarial stalled-recipient isolation | 1 | 1 | 0 | 0 | DEFECT PROVED |
| Test-source style check | — | — | — | — | PASS |
| `git diff --check` | — | — | — | — | PASS |

The combined forced run contained `RealtimeConnectionTest`,
`GameUpdateBroadcastTest`, `ReconnectRecoveryTest`, `WebSocketKeepAliveTest`,
the later `NewGameBroadcastTest`, and `M12AdversarialTest`. Its only failure was
`aStalledConnectionCannotPreventAnotherUserReceivingTheUpdate`; all 31 retained
tests passed against the disposable PostgreSQL 18.6 database with no skips.
