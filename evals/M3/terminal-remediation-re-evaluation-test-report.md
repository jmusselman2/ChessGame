# M3 — Terminal Remediation Re-evaluation: Test Report

## Retained and expanded adversarial coverage

`M3AdversarialTest` now has 16 tests.

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

The current API does not accept a declared move. The assertions exercise the
only public claim-query boundary to demonstrate the missing behavior; a correct
fix needs a move-aware claim contract rather than unconditional early
availability.

## Verification

- Before the two prospective-claim regressions were added:
  `:game-core:ktlintCheck` and focused `M3AdversarialTest` — **PASS**, 14/14.
- Complete `:game-core:test` before those regressions — **PASS**, 360/360.
- Focused terminal/en-passant suites after the regressions were added:
  `EnPassantTest`, `CheckmateStalemateTest`, `ClaimDrawTest`, `ResignTest`, and
  `TerminalUndoLockTest` — **PASS**, 60/60.
- Full `M3AdversarialTest` after expansion: 16 run, 14 passed, 2 failed. Perft,
  all en-passant tests, and all terminal-state tests passed; only the two new
  prospective-claim regressions failed.
- Complete `:game-core:test` after expansion: 362 run, 360 passed, 2 failed.
  There were no errors or skipped tests; the only failures were the two new
  regressions.
- `:game-core:ktlintCheck` — **PASS**.

The failing regressions are retained as handoff evidence. No aggregate build
was claimed after introducing them because `:game-core:test` is intentionally
red on the confirmed production defect.
