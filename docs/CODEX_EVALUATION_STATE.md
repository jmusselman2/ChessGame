# Codex Evaluation State

- **Evaluated `main` baseline:** `3de5e28dbcb29e5d86e163c39b26278686385c4c`
- **Current milestone:** M13 — Game End and Automatic Rematch
- **Status:** `READY FOR EVALUATION`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12
- **Unresolved findings:** M8-01 lets a nameless caller create a friendship the
  other side cannot list. M8-02 lets two concurrent re-adds both report that
  they restored the same removed friendship. M8-03 lets series creation race
  friend removal and commit an unmarked active series after removal. M10-01
  lets a game refresh mix an old game row with newly committed move history.
  M12-01 lets one indefinitely stalled socket block every later realtime
  recipient and the originating command response.
- **Latest artifacts:** `evals/M12/critic-report.md`,
  `evals/M12/test-report.md`, and server `M12AdversarialTest` regression.

## Next action

Evaluate M13 from the beginning under `docs/INDEPENDENT-EVALUATION.md`. Retain
the evaluator regressions and carry M8-01 through M8-03 and M10-01 as unresolved
findings plus M12-01, but do not wait for remediation. Continue milestone by
milestone through the separately committed and pushed M14 checkpoint.
