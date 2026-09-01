# Codex Evaluation State

- **Current baseline:** `c2f7af9fb34981861ac086d7e5fb3842ff135a5f`
- **Current milestone:** M2 — Chess Domain Model
- **Status:** `DEFECT FOUND`
- **Evaluation artifacts:** `evals/M2/critic-report.md`,
  `evals/M2/test-report.md`, `evals/M2/re-evaluation-report.md`,
  `evals/M2/collection-immutability-audit.md`, and the M2 regression tests in
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/`

## Outstanding findings

- **Confirmed:** `ChessGame.history` neither snapshots a constructor-supplied
  list nor prevents mutation through its public JVM list.
- **Confirmed:** shared lists remain mutable through their JVM implementations:
  `Square.ALL`, `PieceType.PROMOTION_CHOICES`,
  `StandardPosition.BACK_RANK`, all three shared `Direction` lists, and
  `PseudoLegalMoves.KNIGHT_STEPS`.

The three earlier `DrawRuleState` findings are resolved on this baseline. Its
input is snapshotted and its published map, entries, keys, and values reject
mutation in the retained and expanded regressions.

## Exact next action

Have the complete M2 collection-immutability finding batch independently
reviewed and fixed, and merge the legitimate fixes into `main`. Then realign
`codex-autopilot` to the updated `origin/main` and independently re-evaluate M2
from the beginning, including this systematic collection audit. Do not proceed
to M3 until M2 passes.

## Completed milestones

None. M2 has not passed; M1 was excluded as non-behavioral bootstrap work.
