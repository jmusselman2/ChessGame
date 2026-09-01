# Codex Evaluation State

- **Current baseline:** `ab3472628e5468831d0c2ec326a90dbe7dc581d2`
- **Current milestone:** M2 — Chess Domain Model
- **Status:** `DEFECT FOUND — ADDRESSED ON THE IMPLEMENTATION BRANCH, NOT YET
  RE-EVALUATED`
- **Evaluation artifacts:** `evals/M2/critic-report.md`,
  `evals/M2/test-report.md`, `evals/M2/re-evaluation-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M2DomainInvariantTest.kt`

## Finding and its disposition

- **Confirmed → addressed:** `DrawRuleState` snapshotted the constructor input,
  but exposed its multi-entry `positionCounts` backing map as a mutable JVM map.
  A caller could mutate an existing state and manufacture a fivefold-repetition
  decision without a game-state transition. Independently reviewed and accepted:
  `toMap()` returns an immutable map for none or one entry but a plain
  `LinkedHashMap` for two or more, which is the normal case for a game in
  progress. The snapshot is now published unmodifiable, so writes through the
  published map or its entries are rejected rather than applied.

The two defects from the first M2 evaluation remain resolved: their retained
regression tests pass. `M2DomainInvariantTest` is retained unchanged, all three
cases, and now passes.

## Exact next action

1. **Human integration:** merge `claude-autopilot` into `main`. Integration
   stays human-controlled; the autonomous loop does not do it.
2. **Codex realignment:** realign `codex-autopilot` to the updated
   `origin/main`.
3. **Codex re-evaluation:** independently re-evaluate M2 from the beginning,
   confirming this finding is genuinely resolved rather than trusting this note.

M2 has **not** passed. Only Codex's own re-evaluation of the updated `main` can
change that, and M3 does not begin until it does.

## Completed milestones

None. M2 has not passed; M1 was excluded as non-behavioral bootstrap work.
