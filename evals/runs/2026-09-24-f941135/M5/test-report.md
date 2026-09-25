# M5 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M5 reports.

| Verification | Result |
| --- | --- |
| Focused M5 Android unit suites | PASS — 113 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :android-app:testDebugUnitTest :android-app:build --rerun-tasks` | PASS — 515 tests; debug/release APKs, lint, ktlint, check, and build successful |
| `.\gradlew.bat :game-core:test` | PASS — complete 394-test result verified up to date on the unchanged baseline |
| Kindle M5 rendered-UI set | PASS — 6 tests after wake/unlock repair |
| Kindle `GameLayoutUiTest` | PASS — 9 tests |
| Unique Kindle instrumentation total | PASS — 15 tests |

The focused unit selection covered `BoardRenderingTest`,
`BoardInteractionTest`, `LegalMoveHighlightTest`, `ApplyLocalMoveTest`,
`BoardOrientationTest`, `GameControlsTest`, `LocalGameTest`,
`DeclaredDrawClaimTest`, `M5AdversarialTest`, and
`M5IndependentReevaluationTest`.

The Kindle run covered live check and checkmate presentation, a complete game
through rendered taps, post-terminal input lockout, prospective draw claim/play/
cancel paths, board fit, both orientation tap maps, short and long scrolling,
large font scale, two-pane controls, piece sizing, and promotion layout.

The initial sleeping-device Compose failures were evaluator/environment
failures. Waking/unlocking the device made the exact failed complete-game test
pass; the complete affected set then passed. The initial missing-APK-path
install attempt changed no device state and succeeded after correcting the
selector.

## Historical follow-up

The retained M5 reports were read only after the fresh results above were
recorded. Their former live-check and prospective-claim failures are directly
covered by the passing fresh unit and Kindle instrumentation selections. The
retained complete-game Compose regression also passes on the Kindle.

The historical final re-evaluation reported 398 Android unit tests and seven
API 36 instrumentation tests. The current pinned baseline passed 515 complete
Android unit tests and 15 unique M5/layout instrumentation tests on Android
5.1/API 22. No further evaluator repair or rerun was necessary after the
sleep/wake correction.

**Final result: PASS.**
