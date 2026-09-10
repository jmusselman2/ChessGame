# Codex Evaluation State

- **Evaluated `main` baseline:** `38be421dfd64c269687c893300f11e661bfa9c90`
- **Current milestone:** COMPLETE — M19 independently evaluated
- **Status:** `EVALUATION COMPLETE — M19 INCOMPLETE (0/12 IMPLEMENTED)`
- **Evaluated milestones:** M2, M3, M4, M5, M6, M7, M8, M9, M10, M11, M12,
  M13, M14, M15, M16, M17, M18, M19
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
- **Unresolved findings:** none.
- **Closed by remediation (2026-09-10):** all six carried findings. **M10-01** —
  `GameRepository.load` now re-reads the version after the history and retakes
  the read when it moved, so a refresh returns one coherent state and, under a
  concurrent commit, the newer one (`D057`). **M12-01** — `RealtimeHub.publish`
  now sends per connection concurrently under a bounded deadline, rethrowing
  genuine cancellation (`D058`). **M14-01/02/03** — the client now tracks the
  game on screen through `Loading`/`Failed`, installs a same-game view forwards
  only by canonical version, and orders overlapping dashboard reads by issue
  number, with `followSeries` on the same loop as `loadDashboard` (`D059`).
  **M18-01** — the two-physical-device claim was removed from
  `docs/PLATFORM-REVIEW.md` and the `M18.1` completion note, because `M17.1`
  records one physical device and no evidence of a second was found; the
  correction and what was searched are recorded in both places. Every evaluator
  regression passes unmodified: `M10AdversarialTest`, `M12AdversarialTest`, the
  three `NetworkInterruptionTest` cases, and
  `evals/M18/M18DocumentationRegressionTest.ps1`. New coverage was added beside
  them, not in place of them: `RealtimeFanOutTest` and
  `StaleResponseOrderingTest`.
- **M19 disposition:** all twelve M19.1–M19.12 tasks remain explicitly `TODO`.
  The repository has design decisions D048–D056 but no M19 implementation; the
  pair-keyed schema and behavior remain intact. This is incomplete planned work,
  not an external evaluation blocker or twelve new defect IDs.
- **Latest artifacts:** `evals/M19/critic-report.md`,
  `evals/M19/test-report.md`, and `evals/remediation-report.md` (the 2026-09-10
  remediation of all six carried findings). The earlier milestone reports and all
  retained evaluator regressions remain part of the evaluation record.

## Next action

No unevaluated milestone remains after M19 in `docs/BACKLOG.md`. The independent
evaluation is complete through every currently defined milestone, and no finding
it raised is open. M19.1 is the next implementation boundary and requires the
human architecture sign-off its acceptance criteria name.

A remediation re-evaluation should now expect all six regressions **green**, and
should check the fixes rather than the symptoms: that `load`'s verification is
against the same counter the guarded write moves and that its retry bound
terminates (`D057`); that a publish drops only the connection that failed and
that request cancellation is not mistaken for a dead socket (`D058`); that
"forwards only" is keyed on the canonical version and that a discarded dashboard
answer changes nothing at all, `loading` included (`D059`).
