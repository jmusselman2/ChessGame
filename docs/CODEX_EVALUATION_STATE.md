# Codex Evaluation State

- **Current baseline:** `ab3472628e5468831d0c2ec326a90dbe7dc581d2`
- **Current milestone:** M2 — Chess Domain Model
- **Status:** `DEFECT FOUND`
- **Evaluation artifacts:** `evals/M2/critic-report.md`,
  `evals/M2/test-report.md`, `evals/M2/re-evaluation-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M2DomainInvariantTest.kt`

## Outstanding finding

- **Confirmed:** `DrawRuleState` snapshots the constructor input, but exposes
  its multi-entry `positionCounts` backing map as a mutable JVM map. A caller can
  mutate an existing state and manufacture a fivefold-repetition decision
  without a game-state transition.

The two defects from the first M2 evaluation are otherwise resolved: their
retained regression tests pass on this baseline.

## Exact next action

Have the remaining M2 immutability defect independently reviewed and fixed, and
merge the fix into `main`. Then realign `codex-autopilot` to the updated
`origin/main` and independently re-evaluate M2 from the beginning. Do not
proceed to M3 until M2 passes.

## Completed milestones

None. M2 has not passed; M1 was excluded as non-behavioral bootstrap work.
