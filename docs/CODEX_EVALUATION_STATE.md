# Codex Evaluation State

- **Evaluated `main` baseline:** `a4bb6999af4dea4665c832bebcaf6122a021e183`
- **Current milestone:** COMPLETE — M8 remediation re-evaluated after M14
- **Status:** `EVALUATION COMPLETE — M8 REMEDIATION PASSED`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12,
  M13, M14
- **Closed by re-evaluation:** all three M8 findings. M8-01 (a
  nameless caller could create a friendship the other side cannot list) and
  M8-02 (two concurrent re-adds both reported that they restored the same
  removed friendship) are fixed, and their evaluator regressions pass
  unmodified. `D045` records M8-01's route contract; M8-02 is closed by the
  guarded reactivation update. M8-03 is resolved by `D046`, which removes the
  friendship check at series creation rather than making it race-safe; its
  regression is kept and re-pointed at the outcome that decision accepts, and
  it passes. `evals/M8/remediation-report.md` records all three, and is
  explicit about the two assertions that changed and why. M8 is now marked
  `PASS`; `evals/M8/re-evaluation-critic-report.md` records the independent
  disposition.
- **Unresolved findings:** M10-01 lets a game refresh mix an old game row with
  newly committed move history. M12-01 lets one indefinitely stalled socket
  block every later realtime recipient and the originating command response.
  M14-01 loses a matching realtime update while its game is still opening.
  M14-02 lets a delayed command response regress a newer reloaded game view.
  M14-03 lets an older dashboard response erase the automatic-rematch state
  found by a completion refresh.
- **Latest artifacts:** `evals/M8/re-evaluation-critic-report.md` and
  `evals/M8/re-evaluation-test-report.md`. The M14 reports and all retained
  evaluator regressions remain part of the evaluation record.

## Next action

The continuous independent evaluation is complete through M14, and the M8
remediation has independently passed re-evaluation. The remaining finding set
is M10-01, M12-01, and M14-01 through M14-03. Re-evaluate the next finding only
after production remediation is present; until then, keep every corresponding
evaluator regression intact and expected-red.
