# Codex Evaluation State

- **Current baseline:** `9d468b7ba718004c21cb8a8a20afd86b35fafd48`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND` — addressed on `claude-autopilot`, not yet
  independently re-evaluated
- **Evaluation artifacts:** `evals/M2/final-re-evaluation-report.md`,
  `evals/M3/critic-report.md`, `evals/M3/test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`

## Finding and its disposition

- **Confirmed → addressed:** en-passant recognition and generation trusted
  `GameState.enPassantTarget` without validating the board it describes. All six
  adversarial scenarios were independently reviewed and all six were accepted: a
  missing, friendly, or non-pawn bypassed piece, an impossible target rank,
  invalid capture geometry, and an occupied-target ordinary capture misclassified
  as en passant — the last of which made generation and application disagree, so
  one move removed two pieces.

  Eligibility is now decided from the board rather than from the marker: the
  target must be empty, on the rank the side to move captures onto, with the
  opposing pawn that skipped it directly behind, and the move must be that side's
  pawn making a one-square diagonal capture. Generation and recognition apply the
  same test, so a move counts as en passant exactly when it is one of the
  generated ones. All six regressions are retained unchanged and pass; none was
  weakened or removed.

M2's immutability guarantees are untouched and its regression coverage still
passes.

## Exact next action

1. **Human integration:** merge `claude-autopilot` into `main`. Integration
   stays human-controlled; the autonomous loop does not do it.
2. **Codex realignment:** realign `codex-autopilot` to the updated
   `origin/main`.
3. **Codex re-evaluation:** independently re-evaluate M3 from the beginning,
   confirming these findings are genuinely resolved rather than trusting this
   note.

M3 is **not** independently passed. Only Codex's own re-evaluation of the
updated `main` can change that, and M4 does not begin until it does.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
