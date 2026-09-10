# Codex Evaluation State

- **Evaluated `main` baseline:** `3de5e28dbcb29e5d86e163c39b26278686385c4c`
- **Current milestone:** COMPLETE — M14 evaluated
- **Status:** `EVALUATION COMPLETE`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12,
  M13, M14
- **Remediated, awaiting re-evaluation:** all three M8 findings. M8-01 (a
  nameless caller could create a friendship the other side cannot list) and
  M8-02 (two concurrent re-adds both reported that they restored the same
  removed friendship) are fixed, and their evaluator regressions pass
  unmodified (`D045`). M8-03 is resolved by `D046`, which removes the
  friendship check at series creation rather than making it race-safe; its
  regression is kept and re-pointed at the outcome that decision accepts, and
  it passes. `evals/M8/remediation-report.md` records all three, and is
  explicit about the two assertions that changed and why. Only Codex may mark
  M8 `PASS`, after re-evaluating it.
- **Unresolved findings:** M10-01 lets a game refresh mix an old game row with
  newly committed move history. M12-01 lets one indefinitely stalled socket
  block every later realtime recipient and the originating command response.
  M14-01 loses a matching realtime update while its game is still opening.
  M14-02 lets a delayed command response regress a newer reloaded game view.
  M14-03 lets an older dashboard response erase the automatic-rematch state
  found by a completion refresh.
- **Latest artifacts:** `evals/M14/critic-report.md`,
  `evals/M14/test-report.md`, the three deterministic M14 regressions in
  `android-app/app/src/test/java/com/jmussel/chessgame/app/NetworkInterruptionTest.kt`,
  and `evals/M8/remediation-report.md`.

## Next action

The continuous independent evaluation is complete through M14. Remediation has
begun: all three M8 findings are addressed and await Codex's re-evaluation. The
carried finding set is now M10-01, M12-01, and M14-01 through M14-03, and CI
stays red until those are fixed. Keep the evaluator regressions intact until
their corresponding production fixes make them pass; where a product decision
removes the requirement a regression encoded, say so in the remediation report
rather than deleting the test.
