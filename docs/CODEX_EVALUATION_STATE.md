# Codex Evaluation State

- **Evaluated `main` baseline:** `38be421dfd64c269687c893300f11e661bfa9c90`
- **Current milestone:** M17 READY — M16 independently evaluated
- **Status:** `M16 PASS WITH CARRIED FINDINGS`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12,
  M13, M14, M15, M16
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
- **Latest artifacts:** `evals/M16/critic-report.md` and
  `evals/M16/test-report.md`. The earlier milestone reports and all retained
  evaluator regressions remain part of the evaluation record.

## Next action

Evaluate M17 next under the user-authorized M15-and-later continuation. Do not
perform an earlier remediation re-evaluation first. Carry M10-01, M12-01, and
M14-01 through M14-03 forward unchanged unless a later milestone naturally
requires them, and keep their evaluator regressions intact and expected-red.
