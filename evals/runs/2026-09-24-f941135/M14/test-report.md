# M14 Test Report

## Fresh verification

The fresh source, automated, and device assessment was completed before any
retained M14 report was read.

| Verification | Result |
| --- | --- |
| Full `:android-app:testDebugUnitTest --rerun-tasks` | PASS - 521 tests, 0 failures, 0 errors, 0 skipped |
| Live Android authentication tests within the full suite | PASS - `AppStartupLiveTest` 2 and `SupabaseLiveAuthTest` 3, none skipped |
| Android instrumentation compilation | PASS - debug test APK assembled against the live HTTPS endpoint |
| Protected Kindle instrumentation | PASS - 20 tests, 0 failures; six layout/game classes, excluding the stateful rotation test |
| Focused server/API verification | PASS - 96 tests across 9 suites, 0 failures, 0 errors, 0 skipped |
| Fresh two-client online path | PASS - onboarding through game 3, history, review, and session restore |
| Fresh local terminal-game path | PASS - Fool's mate, terminal result, and post-terminal input lockout |
| Historical-comparison follow-up: `NetworkInterruptionTest` and `StaleResponseOrderingTest` | PASS - 20 tests, 0 failures, 0 errors, 0 skipped |
| Complete `build --continue --rerun-tasks` on the same production/evaluator tree | PASS - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `git diff --check` | PASS |

The focused server selection comprised `HistoryTest`, `IdentityRouteTest`,
`DashboardTest`, `FriendsListTest`, `AddFriendTest`, `RemoveFriendTest`,
`OpenSeriesTest`, `GameViewTest`, and `RealtimeConnectionTest`. All
PostgreSQL-backed cases ran rather than skipping.

The full Android suite and the focused D059 rerun were forced with
`--rerun-tasks`. The post-history selection comprised 19
`NetworkInterruptionTest` cases and the one `StaleResponseOrderingTest` case.
It preserves all three old M14 failure orderings and the wider retry,
foreground, websocket, refusal, and cold-start recovery boundaries.

The complete repository build was run on the identical production/evaluator
tree before only M11-M14 reports and evaluation-state documents changed. The
M14-specific Android, server, instrumentation, and D059 selections were then
forced after the M13 checkpoint.

## Fresh live path

The beta health endpoint returned 200 with build `35da49a`. Two throwaway API
36 clients (`M14A270847` and `M14B270847`) proved friendship, series creation,
realtime turn changes, move, accepted undo, threefold draw claim, resignation
cancel/confirm, reversed-colour games 2 and 3, dashboard state, completed-game
history/review, and process-death session restore. A local game independently
proved checkmate presentation and terminal input lockout.

The emulator package-manager/launcher/offline failures encountered before the
successful run were repaired by switching to two read-only instances of the
same API 36 AVD. Those failures produced no product finding. Both overlays were
discarded after verification.

## Historical follow-up

The retained M14 report's `M14-01`, `M14-02`, and `M14-03` regression cases all
pass under D059. No new regression was added because current retained coverage
already reproduces the exact formerly failing orderings deterministically.
