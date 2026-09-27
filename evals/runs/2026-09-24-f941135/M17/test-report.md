# M17 Test Report

## Fresh verification

The fresh assessment below was completed before any retained M17 report was
read.

| Verification | Result |
| --- | --- |
| Server focused aggregate | PASS - 42 tests, 0 failures, 0 errors, 0 skipped |
| `DeploymentTest` | PASS - 10 |
| `AllUsersTest` | PASS - 6 |
| `GroupRouteTest` | PASS - 12 |
| `IndexesTest` | PASS - 2 |
| `ConnectionTimeoutsTest` | PASS - 5 |
| `LockTimeoutTest` | PASS - 1 |
| `AuditActorTest` | PASS - 6 |
| Historical follow-up `NewGameBroadcastTest` | PASS - 6, 0 failures, 0 errors, 0 skipped |
| Android focused aggregate | PASS - 299 tests, 0 failures, 0 errors, 0 skipped |
| `ChessApiClientTest` / wake / startup / stalled / silent socket | PASS - 73 |
| All-users UI and flow | PASS - 14 |
| Groups UI and flow | PASS - 24 |
| Layout, local-game, dashboard, friends, and shell logic | PASS - 58 |
| `ChessAppTest` | PASS - 111 |
| `NetworkInterruptionTest` | PASS - 19 |
| `scripts/verify-beta-apk.sh` | PASS - all six signing/configuration checks; ordinary unsigned APK restored |
| Kindle `GameLayoutUiTest` | PASS - 9 after waking/unlocking the initially sleeping device |
| Kindle `OnlineGameLayoutUiTest` | PASS - 4 |
| Disposable API 36 `LocalGameRotationTest` | PASS - 1 against the live HTTPS server |
| Instrumentation aggregate | PASS - 14 reliable rerun tests |
| Fresh live group/game path | PASS - group open, non-friend Play, one move each, both dashboards, leave without ending game |
| Live V10/index/advisor query | PASS - V10 successful, intended index set, five remaining out-of-scope unindexed foreign keys |
| Live audit query | PASS - 20 rows, zero null actors, zero game events missing `series_id` |
| Public `/health` | PASS - HTTP 200 in 481 ms, `ChessGame server is healthy (build 2fd13fa)` |
| Tracked release artifacts and credential files | PASS - none |
| M17.4 absence check | PASS - no requirement; only three governing statements that it does not exist |
| Complete `build --continue --rerun-tasks` on the identical production tree | PASS earlier in this run - 134 tasks; game-core 394, Android 521, server 593; lint and both APK assemblies passed |
| `git diff --check` | PASS |

Both JVM selections used `--rerun-tasks`. The 299 Android tests were two
disjoint forced selections: 250 current flow/API/app/layout cases plus 49
wake/dashboard/friends/local-game cases. Every selected PostgreSQL-backed test
ran rather than skipping.

The first Kindle instrumentation attempt ran with the screen asleep and all
nine Compose cases reported no resumed hierarchy. Device power/activity state
confirmed the harness boundary. After waking and unlocking the Kindle, the
same class passed 9/9; this was an evaluator-environment failure, not a product
finding. The four online layout cases then passed. The stateful rotation case
was kept off the owner's install and passed on a disposable read-only emulator.

The live database checks were read-only. No schema, grants, rows, or server
configuration were modified by those checks. Fresh live group testing created
throwaway anonymous accounts and a two-move game, then removed the emulator
account from the group to prove the game persisted.

No production file changed after the run's complete forced build; M15-M17 add
only evaluator reports/state. M17 additionally rebuilt release, debug, and
instrumentation APKs while exercising its signing and device paths.

## Historical follow-up

The retained report's six `NewGameBroadcastTest` cases were forced after the
fresh phase and all passed. Its ten `DeploymentTest` cases were already in the
fresh 42-test server selection. The current dashboard selection is 26 cases,
up from 23 historically, and all passed in the fresh 299-test Android total.

The old carried interruption/fan-out/response-order findings are closed by
D057-D059; the current suites covering their boundaries passed earlier in this
same run and the 19-case interruption suite passed again for M17. No historical
expected-red result remains.
