# M3 — Chess Legal Move Engine: Test Report

## Added tests

Added `M3AdversarialTest` with seven tests. No production code was changed.

- `standardPositionMatchesKnownPerftThroughDepthThree` — **PASS**. Verifies
  standard-position legal-move totals of 20, 400, and 8,902.
- `enPassantTargetWithoutBypassedPawnOffersNoCapture` — **FAIL**. Demonstrates
  a ghost en-passant capture with no bypassed pawn; production defect.
- `enPassantTargetBesideFriendlyPawnOffersNoCapture` — **FAIL**. Demonstrates
  removal of a friendly pawn through an invalid marker; production defect.
- `enPassantTargetBesideNonPawnOffersNoCapture` — **FAIL**. Demonstrates that a
  non-pawn can be treated as the bypassed pawn; production defect.
- `enPassantTargetOnImpossibleRankOffersNoCapture` — **FAIL**. Demonstrates
  acceptance of an impossible target rank; production defect.
- `occupiedEnPassantTargetIsOnlyAnOrdinaryCapture` — **FAIL**. Demonstrates an
  ordinary capture being misclassified so the piece behind the target would
  also be removed; production defect.
- `enPassantCaptureRecognitionRequiresPawnCaptureGeometry` — **FAIL**.
  Demonstrates `isCapture` accepting a pawn move with impossible capture
  geometry; production defect at the public rule helper boundary.

## Verification

- Baseline before new tests:
  `./gradlew.bat :game-core:test --rerun-tasks` — **PASS**.
- Focused adversarial class: 7 run, 1 passed, 6 failed.
- `./gradlew.bat :game-core:ktlintCheck :game-core:test --rerun-tasks`:
  formatting **PASS**; 351 tests run, 345 passed, 6 failed.

All six failures are intentionally retained evidence. Existing tests and the
new perft coverage pass.
