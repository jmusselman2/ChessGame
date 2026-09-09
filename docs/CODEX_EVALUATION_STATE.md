# Codex Evaluation State

- **Evaluated `main` baseline:** `3de5e28dbcb29e5d86e163c39b26278686385c4c`
- **Current milestone:** M12 — Realtime
- **Status:** `READY FOR EVALUATION`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11
- **Unresolved findings:** M8-01 lets a nameless caller create a friendship the
  other side cannot list. M8-02 lets two concurrent re-adds both report that
  they restored the same removed friendship. M8-03 lets series creation race
  friend removal and commit an unmarked active series after removal. M10-01
  lets a game refresh mix an old game row with newly committed move history.
- **Latest artifacts:** `evals/M11/critic-report.md` and
  `evals/M11/test-report.md` (no new M11 defect regressions required).

## Next action

Evaluate M12 from the beginning under `docs/INDEPENDENT-EVALUATION.md`. Retain
the evaluator regressions and carry M8-01 through M8-03 and M10-01 as unresolved
findings, but do not wait for remediation. Continue milestone by milestone
through the separately committed and pushed M14 checkpoint.
