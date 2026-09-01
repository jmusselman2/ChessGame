# M3 — Chess Legal Move Engine: Re-evaluation Test Report

## Retained and expanded tests

- The seven existing `M3AdversarialTest` cases are retained.
- All six en-passant regressions now **PASS** unchanged. Their former failures
  demonstrated production defects; those defects are resolved on this
  baseline with no remaining uncertainty from the tested scenarios.
- The standard-position perft test now verifies depth four as well as depths
  one through three: 20, 400, 8,902, and 197,281 nodes — **PASS**. This is broad
  move-engine coverage, not proof of every rule by itself.

## New adversarial tests

- `aFinishedGameHasNoLegalMoves` — **FAIL**. Verifies that a final result makes
  the public legal-move list empty. The standard starting position still
  publishes 20 moves, demonstrating a production defect.
- `noMoveIsLegalAfterTheGameIsFinished` — **FAIL**. Verifies that the public
  legality predicate rejects a move after finalization. `e2e4` is still
  reported legal even though `applyMove` rejects it, demonstrating the same
  production defect through a separate public API. There is no material
  uncertainty in this conclusion.

## Verification

- Focused `EnPassantTest` plus the original M3 adversarial suite before adding
  the terminal-state tests: **PASS**.
- Focused `M3AdversarialTest` after expansion: 9 run, 7 passed, 2 failed.
- `:game-core:ktlintCheck`: **PASS**.
- Complete `:game-core:test`: 355 run, 353 passed, 2 failed. The only failures
  are the two new terminal-state regressions.

No production code was changed. The failures are retained as evidence for an
independent implementation agent to review and remediate.
