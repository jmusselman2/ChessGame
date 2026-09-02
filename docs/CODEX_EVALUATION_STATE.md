# Codex Evaluation State

- **Evaluated baseline:** `2be1f0635291a239f0181d76f079311ccfb02024`
- **Current milestone:** M3 — Chess Legal Move Engine
- **Status:** `DEFECT FOUND — REMEDIATED, AWAITING RE-EVALUATION`
- **Remediation:** implemented on `claude-autopilot`, not yet independently
  re-evaluated. Only Codex may move M3 to `PASS`, and only from a fresh
  evaluation of the fixed `main`.
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
- **Confirmed, and remediated on `claude-autopilot`:** prospective draw claims
  were unavailable. A player whose declared legal move would create the third
  occurrence or reach 100 halfmoves had no game-core claim path, because the
  public claim APIs accepted only the current state and no prospective move.
  `ChessRules.availableDrawClaims`, `canClaimDraw`, and `claimDraw` now have
  declared-move overloads on `GameState` and `ChessGame`. The declaration binds
  — the move must be legal, and only that exact move counts — the claim ends the
  game from the position it was made in without playing the declared move, and a
  declared checkmating move offers no draw claim. The no-move overloads keep
  their existing meaning, so no claim is granted without the declaration it
  depends on (`D038`).
- **Confirmed, and remediated on `claude-autopilot`:** a legal capture leaving
  two same-colour bishops for one side against a bare king was not recognized as
  insufficient material. `InsufficientMaterial.isDraw` had enumerated the named
  textbook endings and rejected any position with more than two pieces left. It
  now states the rule by colour complex: once no pawn, rook, or queen remains,
  the position is dead when at most one piece besides the kings remains, or when
  every remaining piece is a bishop and all of them stand on one square colour —
  whatever the count, and whoever owns them. This covers promotion-created
  bishop sets. Opposite-coloured bishops and any position still holding a knight
  alongside a second piece stay live (`D038`).

### Disposition of the three retained regressions

- `aCaptureLeavingOnlySameColourBishopsEndsTheGame` — **kept unchanged**, and
  now passes.
- `aThirdOccurrenceCreatedByTheDeclaredNextMoveIsClaimable` and
  `theQuietMoveThatWouldReachOneHundredHalfmovesIsClaimable` — the scenarios are
  kept and their final assertions were rebound to the declared move. As written
  they asserted the entitlement on the **no-move** query, which the same report
  identified as the wrong contract ("a correct fix needs a move-aware claim
  contract rather than unconditional early availability"); the two cannot both
  hold. Each test now asserts the claim through the declared-move overload,
  proves the claim ends the game with the right reason, and additionally asserts
  that the entitlement does **not** exist undeclared, under a different quiet
  move, under a pawn move, under an illegal move, or one halfmove early.

The standard-position legal-move oracle passes through depth four (20, 400,
8,902, and 197,281 nodes). Additional published perft positions covering
castling, en passant, promotion, checks, and pins also matched their oracle.

## Exact next action

`claude-autopilot` carries the remediation for both confirmed defect batches and
must be merged into `main` by a human. After it reaches `main`:

1. Realign `codex-autopilot` to the updated `origin/main`.
2. Independently re-evaluate M3 from the beginning against that baseline — a
   fresh evaluation, not a spot check of these two fixes. Cover in particular
   claim entitlement (current and prospective), automatic versus claimable draw
   separation, dead-position detection including promotion-created material,
   terminal guards, and legal move generation.
3. Only Codex may then mark M3 `PASS`.

Do not proceed to M4, and do not mark M3 `PASS`, until that independent
re-evaluation passes.

## Completed milestones

- **M2 — Chess Domain Model:** `PASS` on
  `9d468b7ba718004c21cb8a8a20afd86b35fafd48`.
