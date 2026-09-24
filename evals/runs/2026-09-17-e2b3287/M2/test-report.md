# M2 — Chess Domain Model: Test Report

## Test changes

Added `game-core/src/test/kotlin/com/jmussel/chessgame/core/chess/M2DomainInvariantTest.kt`.
No production code was changed.

## Adversarial test results

### `externalMapMutationCannotManufactureAnAutomaticDraw`

- **What it verifies:** a `DrawRuleState` snapshots its supplied repetition
  counts, so later mutation by the caller cannot change an existing game state
  or manufacture the fivefold-repetition threshold.
- **Result:** **FAIL**.
- **Production defect:** **Yes.** The failure directly demonstrates that a
  documented immutable domain value changes without a game-state transition;
  the changed value is consumed by current automatic-draw logic.
- **Uncertainty:** low. The practical trigger requires a mutable map alias, but
  the public constructor accepts one and the immutable-value requirement is
  explicit.

### `rejectsNonPositiveRepetitionCounts`

- **What it verifies:** repetition-count entries must be positive; zero and
  negative occurrences are rejected at construction.
- **Result:** **FAIL**.
- **Production defect:** **Likely.** The failure proves current domain and
  persistence reconstruction accept semantically impossible draw state.
- **Uncertainty:** moderate. The requirement describes occurrence tracking but
  does not explicitly mandate eager constructor validation, and normal moves do
  not create invalid counts.

## Commands and aggregate results

1. Baseline before adding adversarial tests:
   `./gradlew.bat :game-core:test` — **PASS**.
2. New tests only:
   `./gradlew.bat :game-core:test --tests '*M2DomainInvariantTest'` —
   **FAIL**, 2 tests run, 2 failed.
3. Formatting and full relevant suite:
   `./gradlew.bat :game-core:ktlintCheck :game-core:test --rerun-tasks` —
   `ktlintCheck` **PASS**; tests **FAIL**, 325 run, 323 passed, 2 failed.

Both failures are the intentionally retained adversarial regressions above.
All 323 existing game-core tests passed in the combined run. Production code
was not changed to make either new test pass.
