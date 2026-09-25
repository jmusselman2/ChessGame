# M4 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M4 reports.

| Verification | Result |
| --- | --- |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) and emulator `emulator-5554` discovered |
| `MoveHistoryTest` | PASS — 15 tests |
| `UndoEligibilityTest` | PASS — 11 tests |
| `TerminalUndoLockTest` | PASS — 10 tests |
| `M4AdversarialTest` | PASS — 3 tests |
| Focused M4 total | PASS — 39 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :game-core:build --rerun-tasks` | PASS — 394 tests, 0 failures, 0 errors, 0 skipped; compile, jar, ktlint, check, and build successful |

The focused M4 command completed in 27 seconds. The subsequent unfiltered core
build completed in 5m07s. The JDK's Kotlin-compiler `sun.misc.Unsafe`
deprecation warnings did not affect compilation or behavior.

## Historical follow-up

The retained M4 reports were read only after the fresh results above were
recorded. They report the same 39-test focused suite and 394-test complete core
suite, including the three retained adversarial checks. All pass in this run.

An additional current caller search confirmed that Android `GameControls` and
server `GameCommandService` call the guarded APIs. No production code directly
calls the mechanical `undoLastMove` helper.

No evaluator repair or rerun was necessary. **Final result: PASS.**
