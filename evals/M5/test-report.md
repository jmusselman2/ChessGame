# M5 — Local Android Chess: Test Report

## Added adversarial coverage

`M5AdversarialTest` adds three host-side tests through the production local
interaction path:

- en passant can be selected and executed, as a positive special-move control;
- a move that would create a third occurrence is recognized by the declared-move
  engine oracle while no prospective local claim action exists;
- a quiet move at halfmove 99 is recognized by the declared-move engine oracle
  while no prospective local claim action exists.

`M5LocalUiAdversarialTest` adds two rendered Compose tests:

- a non-terminal check must be shown to the local player;
- checkmate must be shown while terminal Undo/resignation controls stay hidden.

## Verification results

- Complete `:android-app:testDebugUnitTest --rerun-tasks`: **PASS**, 373 tests,
  0 failures, 0 errors, 0 skipped. The three new host-side M5 tests pass.
- Complete `:game-core:test --rerun-tasks`: **PASS**, 394 tests, 0 failures,
  0 errors, 0 skipped.
- `:android-app:lintDebug`: **PASS**.
- Repository-wide `ktlintCheck`: **PASS**.
- `:android-app:assembleDebug`: **PASS**.
- Final `git diff --check`: **PASS**.

## Instrumentation evidence

The final run used a clean Android 16/API 36 Automated Test Device on the
disposable `emulator-5556` serial and executed three instrumentation tests:

- `ExampleInstrumentedTest.useAppContext`: **PASS**;
- `M5LocalUiAdversarialTest.checkmateIsShownAndTerminalControlsStayHidden`:
  **PASS**;
- `M5LocalUiAdversarialTest.aNonTerminalCheckIsShownToTheLocalPlayer`:
  **FAIL — PRODUCT**, because no semantics node contains `Check`.

An earlier Android 17/API 37.1 attempt installed and started the tests but both
Compose cases failed before assertion because Espresso 3.5.1 reflected on the
removed `android.hardware.input.InputManager.getInstance()` method. The existing
non-Compose context test passed. That attempt is classified as infrastructure,
not product evidence. A stable API 36 ATD image was then installed and booted on
the same disposable serial; no installation or data on `emulator-5554` was
touched.

## Manual-only evidence remaining

This checkpoint did not repeat a human-operated full-game play-through. The
repository retains its prior emulator Fool's-mate play-through evidence for
M5.7. Subjective visual legibility and physical tap ergonomics remain
manual-only; they do not change the two deterministic defects above.
