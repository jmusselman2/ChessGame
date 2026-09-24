# Android 5.1/API 22 Compatibility — Test Report

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

## Build and package verification

| Check | Result |
| --- | --- |
| `:android-app:build --rerun-tasks --continue` | PASS, 110 tasks executed |
| Android host-side unit tests | PASS, 484/484; 0 failed, errored, or skipped |
| ktlint for Android sources/scripts | PASS |
| Android lint | PASS; 0 errors and no `NewApi` issue |
| Debug assembly/package | PASS |
| Release assembly/package | PASS |
| `apkanalyzer manifest min-sdk` | `22` |
| `aapt dump badging` | minSdk 22, targetSdk 37, launchable `MainActivity` |
| APK resource inspection | v26 adaptive icons plus mdpi–xxxhdpi raster fallbacks |

The lint report contains 43 warnings and two hints: available dependency/plugin
updates, unused resources/attributes, similar dependencies, and one redundant
label. None is an API compatibility error.

## Kindle Fire KFDOWI, Android 5.1.1/API 22

| Runtime check | Result |
| --- | --- |
| Install isolated evaluator APK | PASS |
| Cold launch and beta authentication | PASS |
| Onboarding/dashboard reached | PASS |
| Dashboard → Local game navigation | PASS |
| Local moves and activity recreation | PASS, `LocalGameRotationTest` 1/1 |
| Back ends local game; reopen starts fresh | PASS |
| Rendered board/layout/promotion instrumentation | PASS, `GameLayoutUiTest` 9/9 |
| Refusing-server failure presentation | PASS |
| Original installed ChessGame package preserved | PASS |

The refusing-server cold launch displayed:

- `Cannot sign in`;
- `The server would not say who you are (404). Try again.`;
- an enabled `Try again` action.

This confirms that API 22 does not crash in startup or error rendering and that
the player receives a basic recoverable network/server failure presentation.

## Newer device

Pixel 7, Android 16, on the same integrated source baseline:

- `GameLayoutUiTest`: 9/9 PASS;
- live beta `LocalGameRotationTest`: 1/1 PASS.

Initial device-only failures caused by version-code refusal, sleeping/locked
screens, and the unsupported API-22 keyguard command were isolated as
infrastructure/setup failures. Only clean reruns are counted as product
evidence.
