# Codex Evaluation State

- **Main baseline evaluated:** `036a1a18c0a50a864d6c7356e9e40d860c2d82ac`
- **Current milestone:** M2 — Chess Domain Model
- **Status:** `DEFECTS FOUND — FIXED ON THE IMPLEMENTATION BRANCH, NOT YET
  RE-EVALUATED`
- **Evaluation artifacts:** `evals/M2/critic-report.md`,
  `evals/M2/test-report.md`, and the retained adversarial regressions in
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M2DomainInvariantTest.kt`

This checkpoint deliberately names the baseline, the artifacts, and the next
action rather than an evaluation commit SHA: the evaluation commit is rewritten
whenever it is carried between branches, and a checkpoint that points at itself
goes stale the first time that happens.

## Findings and their disposition

Both findings were independently reviewed on the implementation branch and both
were accepted. Neither is confirmed fixed for evaluation purposes until Codex
re-evaluates M2 on `main`.

- **Confirmed → addressed:** `DrawRuleState` retained a caller-supplied mutable
  repetition map, allowing an existing game state and its fivefold-repetition
  decision to change without a state transition. `DrawRuleState` now snapshots
  the supplied counts, so a retained mutable map cannot reach an already-built
  state.
- **Likely → addressed:** `DrawRuleState` accepted zero and negative repetition
  counts. A recorded position has occurred at least once, no rules or
  persistence path can produce a non-positive count, and the type already
  validated the halfmove clock; the constructor now rejects non-positive counts,
  which also rejects corrupt stored counts at persistence reconstruction.

`M2DomainInvariantTest` is retained unchanged and now passes; both cases stay in
place as regressions.

## Exact next action

Wait for the M2 fixes to be merged into `main`. Then synchronize
`codex-autopilot` with the updated `main` baseline and **independently
re-evaluate M2 from the beginning**, including confirming that the two findings
above are genuinely resolved rather than trusting this note.

M2 has **not** passed. Do not proceed to M3 until Codex's own re-evaluation of
M2 on the updated `main` passes.

## Completed milestones

None. M2 has not passed; M1 was excluded as non-behavioral bootstrap work.
