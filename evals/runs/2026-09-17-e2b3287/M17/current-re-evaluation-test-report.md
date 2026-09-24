# M17.3 and M17.5–M17.10 — Current Independent Re-evaluation: Test Report

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

## Focused automated results

| Suite | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `ShellChromeContentTest` | 6 | 0 | 0 | 0 | PASS |
| server `AllUsersTest` | 6 | 0 | 0 | 0 | PASS |
| server `UserLookupTest` | 9 | 0 | 0 | 0 | PASS |
| Android `AllUsersFlowTest` | 7 | 0 | 0 | 0 | PASS |
| Android UI-model `AllUsersTest` | 7 | 0 | 0 | 0 | PASS |
| `GameLayoutSpecTest` | 12 | 0 | 0 | 0 | PASS |
| `AppStartupTest` | 18 | 0 | 0 | 0 | PASS |
| `StalledRequestTest` | 2 | 0 | 0 | 0 | PASS |
| `NetworkInterruptionTest` | 19 | 0 | 0 | 0 | PASS |
| `ChessAppTest` | 111 | 0 | 0 | 0 | PASS |
| **Focused total** | **197** | **0** | **0** | **0** | **PASS** |

The same pinned product baseline also passed the aggregate suites already run
for the preceding checkpoint: 394 game-core, 570 server, and 484 Android
host-side tests, with no failures, errors, or skips. `build --continue` included
ktlint, Android lint, debug/release assembly and packaging.

## Physical-device results

Pixel 7, Android 16:

- `GameLayoutUiTest`: 9/9 PASS. It rendered 19 forced viewports at font scales
  1.0, 1.3 and 2.0; checked board geometry and square floors, both orientations'
  tap mapping, independent scrolling, short-window reachability, stable glyph
  sizing, the shared promotion row, and local game interaction.
- `LocalGameRotationTest`: 1/1 PASS against the configured beta endpoint. A
  fresh app startup reached the dashboard, a played local position survived
  activity recreation, Back ended it, and reopening Local game started fresh.

Two setup failures were excluded from product evidence. The first deployment
was refused because the device already held a higher version code. The next
attempt launched while the handset was dozing, so Compose had no foreground
hierarchy. The device's prior stay-awake setting was recorded, temporarily
enabled, and restored to `0`; the clean rerun then passed all nine tests.

The M17.10 lifecycle model tests passed, but the full independent two-device
opponent-move/return timing scenario was not repeated. Its retained Pixel/Kindle
completion evidence is corroborating rather than counted above.

## Commands

- focused server and Android host-side selection with `--rerun-tasks
  --continue`: PASS, 197 tests;
- `:android-app:connectedDebugAndroidTest` filtered to `GameLayoutUiTest` on the
  Pixel 7: PASS, 9 tests on the clean rerun;
- the same task filtered to `LocalGameRotationTest`, with the beta HTTPS URL
  supplied at build time: PASS, 1 test;
- no production or evaluator test source changed.
