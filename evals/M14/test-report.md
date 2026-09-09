# M14 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Full Android JVM suite, including M14 evaluator regressions | 416 | 3 | 0 | 0 | EXPECTED FAIL |
| Retained Android JVM tests | 413 | 0 | 0 | 0 | PASS |
| M14 server history, game-view, and identity slice | 26 | 0 | 0 | 0 | PASS |
| Full `game-core` suite | 394 | 0 | 0 | 0 | PASS |
| Android test-source ktlint | — | — | — | — | PASS |
| Debug APK assembly and Android lint | — | — | — | — | PASS |
| `git diff --check` | — | — | — | — | PASS |

The full Android suite was forced with `--rerun-tasks`. Its only failures were
the three new deterministic tests in `NetworkInterruptionTest`:

- `anUpdateForAGameStillOpeningIsNotDropped`: expected version 2, received 1
  (M14-01).
- `aDelayedCommandResponseCannotOverwriteANewerReload`: the reload reached
  version 3, then the delayed response regressed the screen to version 2
  (M14-02).
- `aCompletionRefreshCannotBeOverwrittenByAnOlderDashboardRead`: the
  completion refresh found one series, then the older response erased it to
  zero entries (M14-03).

The focused interruption harness ran 15 tests with exactly those three
failures, leaving its 12 retained cases passing. Across the full Android run,
the other 413 tests passed with no skips.

The server selection comprised `HistoryTest`, `GameViewTest`, and
`IdentityRouteTest`. It ran against the disposable PostgreSQL 18.6 instance on
port 54999. The complete pure `game-core` suite, debug APK assembly, Android
lint, and test-source formatting check passed. The aggregate repository build
is intentionally red while the evaluator regressions remain unresolved; its
non-server production artifacts and lint were verified independently here.

The Android SDK still lists `ChessPlayer1`, `ChessPlayer2`, and
`ChessPlayerM5Api36`, but `adb devices -l` found no running device. The detailed
M14.18 device play-through retained in `docs/BACKLOG.md` was therefore audited
as historical acceptance evidence rather than repeated in this checkpoint.

