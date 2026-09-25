# M3 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M3 reports.

| Verification | Result |
| --- | --- |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) and emulator `emulator-5554` discovered |
| 17 focused M3 suites | PASS — 258 tests, 0 failures, 0 errors, 0 skipped |
| `M3AdversarialTest` | PASS — 20 tests |
| `M3ReferencePerftTest` | PASS — 8 published reference positions |
| `.\gradlew.bat :game-core:build --rerun-tasks` | PASS — 394 tests, 0 failures, 0 errors, 0 skipped; compile, jar, ktlint, check, and build successful |

The focused command selected the dedicated M3.1–M3.14 test classes plus the
adversarial and reference-perft classes. It completed successfully in 5m47s.
The subsequent unfiltered core build completed successfully in 4m57s.

The JDK emitted deprecation warnings from the Kotlin compiler's use of
`sun.misc.Unsafe`; these did not affect compilation, tests, or product behavior
and are not a production defect in this repository.

## Historical follow-up

The retained M3 reports were read only after the fresh results above were
recorded. Their former en-passant, terminal-query, prospective-claim, and
same-colour-bishop failures are all represented in the retained adversarial
suite. That entire suite passed 20/20 in the focused fresh run. The historical
multi-position move-generation oracle is retained in `M3ReferencePerftTest` and
passed 8/8.

The full `game-core` result remained 394 passed, 0 failed, 0 errored, and 0
skipped. No evaluator repair or rerun was necessary.

**Final result: PASS.**
