# Codex Evaluation State

- **Current baseline:** `2be1f0635291a239f0181d76f079311ccfb02024`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND`
- **Evaluation artifacts:** `evals/M3/critic-report.md`,
  `evals/M3/test-report.md`, `evals/M3/re-evaluation-critic-report.md`,
  `evals/M3/re-evaluation-test-report.md`,
  `evals/M3/terminal-remediation-re-evaluation-critic-report.md`,
  `evals/M3/terminal-remediation-re-evaluation-test-report.md`, and
  `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M3AdversarialTest.kt`

## Findings and disposition

- **Resolved and independently verified:** the complete six-scenario
  en-passant defect batch. All six regressions pass unchanged.
- **Resolved and independently verified:** the terminal move-query defect.
  Every terminal reason now closes `legalMoves` and `isLegal` through both
  public state overloads, consistent with `applyMove`; live checkmate,
  stalemate, and `terminalResult` behavior is unchanged. The corrected
  `ResignTest` still enforces D017.
- **Confirmed:** prospective draw claims are unavailable. A player whose
  declared legal move would create the third occurrence or reach 100 halfmoves
  has no game-core claim path because the public claim APIs accept only the
  current state and no prospective move. The two new regressions fail.

The standard-position legal-move oracle passes through depth four (20, 400,
8,902, and 197,281 nodes). No production code was changed by this evaluation.

## Exact next action

Have the prospective threefold/fifty-move claim gap independently reviewed and
fix every legitimate path on `claude-autopilot`. The remediation must bind and
validate the declared legal move; it must not make pre-threshold claims
unconditional. Preserve current-position claims, automatic draw behavior,
terminal guards, all evaluation artifacts, and all regressions. Merge the
reviewed remediation into `main`, realign `codex-autopilot` to the updated
`origin/main`, and independently re-evaluate M3 from the beginning.

Do not proceed to M4, and do not mark M3 `PASS`, until that independent
re-evaluation passes.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
