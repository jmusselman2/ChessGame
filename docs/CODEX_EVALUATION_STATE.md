# Codex Evaluation State

- **Current baseline:** `9d468b7ba718004c21cb8a8a20afd86b35fafd48`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND`
- **Evaluation artifacts:** `evals/M2/final-re-evaluation-report.md`,
  `evals/M3/critic-report.md`, `evals/M3/test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`

## Outstanding finding

- **Confirmed:** en-passant recognition and generation trust
  `GameState.enPassantTarget` without validating the complete board invariant.
  They accept missing, friendly, or non-pawn bypassed pieces, impossible target
  ranks, invalid capture geometry, and can misclassify an occupied-target
  ordinary capture as en passant.

## Exact next action

Have the complete M3 en-passant eligibility finding independently reviewed and
fixed, and merge the legitimate fix into `main`. Then realign
`codex-autopilot` to the updated `origin/main` and independently re-evaluate M3
from the beginning. Do not proceed to M4 until M3 passes.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
