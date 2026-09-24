# M3 — Terminal Remediation Re-evaluation: Test Report

## Retained and expanded adversarial coverage

`M3AdversarialTest` now has 17 tests.

- Standard-position perft through depths 1–4: 20, 400, 8,902, 197,281 —
  **PASS**.
- All six retained en-passant marker/geometry regressions — **PASS**.
- The original finished-game `legalMoves` and `isLegal` regressions —
  **PASS**.
- `ChessGame` terminal query overloads and a genuine checkmate — **PASS**.
- Exhaustive terminal matrix: all eight terminal reasons, both public state
  types, and all 20,160 representable moves per reason — **PASS**.
- Live checkmate/stalemate/terminal-result classification after the
  `hasNoLegalMoves` decoupling — **PASS**.
- Representative move, claim, and undo query-to-transition consistency —
  **PASS**.

## New failing regressions

- `aThirdOccurrenceCreatedByTheDeclaredNextMoveIsClaimable` — **FAIL**. The
  current position has occurred twice; legal `f6g8` produces occurrence three,
  but the player about to make it has no threefold claim path.
- `theQuietMoveThatWouldReachOneHundredHalfmovesIsClaimable` — **FAIL**. Legal
  quiet `d1d2` advances the halfmove clock from 99 to 100, but the player about
  to make it has no fifty-move claim path.
- `aCaptureLeavingOnlySameColourBishopsEndsTheGame` — **FAIL**. Legal `e3g5`
  captures the last non-king defender and leaves two promoted-capable bishops
  on the same colour complex against a bare king, but the engine does not
  recognize the dead position or end the game.

The current API does not accept a declared move. The assertions exercise the
only public claim-query boundary to demonstrate the missing behavior; a correct
fix needs a move-aware claim contract rather than unconditional early
availability.

## Temporary independent perft expansion

A temporary FEN parser and multi-position perft test exercised published
Kiwipete and perft-suite positions spanning castling, en passant, promotion,
checks, pins, and tactical captures. All selected node counts matched their
independent oracle, including Kiwipete depth 3 = 97,862 and position 3 depth 4
= 43,238. The passing exploratory test was removed rather than retained as a
defect regression. Oracle source:
<https://github.com/freeeve/pgn/blob/v3/perft_test.go>.

## Verification

- Before the two prospective-claim regressions were added:
  `:game-core:ktlintCheck` and focused `M3AdversarialTest` — **PASS**, 14/14.
- Complete `:game-core:test` before those regressions — **PASS**, 360/360.
- Focused terminal/en-passant suites after the regressions were added:
  `EnPassantTest`, `CheckmateStalemateTest`, `ClaimDrawTest`, `ResignTest`, and
  `TerminalUndoLockTest` — **PASS**, 60/60.
- Full `M3AdversarialTest` after continued expansion: 17 run, 14 passed, 3
  failed. Perft, all en-passant tests, and all terminal-state tests passed; only
  the two prospective-claim regressions and the same-colour-bishop regression
  failed.
- Complete `:game-core:test` after continued expansion: 363 run, 360 passed, 3
  failed. There were no errors or skipped tests; the only failures were the
  three retained regressions.
- `:game-core:ktlintCheck` — **PASS**.

The failing regressions are retained as handoff evidence. No aggregate build
was claimed after introducing them because `:game-core:test` is intentionally
red on the confirmed production defect.
