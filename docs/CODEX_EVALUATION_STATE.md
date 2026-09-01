# Codex Evaluation State

- **Current baseline:** `d80ac61523ed323bd5e80800affda00265ac20b3`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND`
- **Evaluation artifacts:** `evals/M3/critic-report.md`,
  `evals/M3/test-report.md`, `evals/M3/re-evaluation-critic-report.md`,
  `evals/M3/re-evaluation-test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`

## Findings and disposition

- **Resolved and independently verified:** the complete six-scenario
  en-passant defect batch. All six regressions pass unchanged. Eligibility is
  derived consistently from target rank, target occupancy, capture geometry,
  and the opposing bypassed pawn.
- **Confirmed:** a finished game still advertises legal moves.
  `ChessRules.legalMoves` returns moves and `ChessRules.isLegal` returns `true`
  after `GameState.result` finalizes the game, while `ChessRules.applyMove`
  rejects that same transition. The two new regressions fail on the current
  baseline.

The standard-position legal-move oracle passes through depth four (20, 400,
8,902, and 197,281 nodes). No production code was changed by this evaluation.

## Exact next action

Have the M3 terminal-state query inconsistency independently reviewed and fix
every legitimate path on the implementation branch. Preserve all evaluation
artifacts and regressions, merge the reviewed remediation into `main`, then
realign `codex-autopilot` to the updated `origin/main` and independently
re-evaluate M3 from the beginning. Do not proceed to M4 until M3 passes.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
