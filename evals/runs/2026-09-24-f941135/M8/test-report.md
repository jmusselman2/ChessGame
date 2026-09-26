# M8 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M8 reports.

| Verification | Result |
| --- | --- |
| `UserLookupTest`, `AddFriendTest`, `FriendsListTest`, `RemoveFriendTest`, and `M8AdversarialTest` | PASS - 49 tests, 0 failures, 0 errors, 0 skipped |
| `ChessApiClientTest` | PASS - 42 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :server:build --rerun-tasks` | PASS - 17 tasks executed; 590 tests, 0 failures, 0 errors, 0 skipped |
| Rebased live-tip M8 server selection | PASS - 49 tests, 0 failures, 0 errors, 0 skipped |
| Rebased live-tip `.\gradlew.bat :android-app:build --rerun-tasks` | PASS - 110 tasks executed; 521 tests, 0 failures, 0 errors, 0 skipped; lint and debug/release APK assembly passed |
| Live PostgreSQL catalog inspection | PASS - friendship columns, defaults, nullability, participant foreign keys, composite primary key, canonical-order check, and allowed-status check are present |
| Current routes, repository, migrations, and Android caller inspection | PASS - lookup/add/list/remove behavior agrees with current M8 authority and the later D053 supersession |

The 49 focused server tests comprised 9 lookup, 15 add, 7 list, 12 remove, and
6 adversarial cases. They cover authentication, normalization, public response
shape, self/duplicate/nameless rejection, mutual visibility, removal, retained
history/reactivation, and database races. The Android selection verifies the
current HTTP caller contract for the same endpoints.

The full server build ran compilation, formatting/lint checks, packaging, and
all 590 retained server tests. Expected logs from deliberately injected failure
and timeout cases were followed by passing assertions and a successful Gradle
exit.

Before checkpointing, the live evaluator history was found rebased onto an
Android splash implementation and planning-document updates. A tree comparison
confirmed no `server` or `game-core` change. The focused server selection was
rerun on the rewritten tip, and the complete Android build was added to cover
the intervening Android production code. The earlier Android result-path
assumption (`android-app/build/...`) was corrected to the module's actual
`android-app/app/build/...` path before the complete total was aggregated.

### Evaluator/environment correction

The first database inspection used `docker compose ps -q postgres`, which did
not resolve the already-running container under its active Compose project.
The evaluator corrected discovery using the live Docker inventory, verified the
`chessgame-postgres` container and mapped test database, and ran the catalog
query there. No production behavior or data was changed.

The final `adb devices -l` discovery returned no connected devices. This does
not prevent M8 evidence because device interaction is not applicable to this
server/client-contract milestone; no installation or device mutation was
attempted.

## Historical follow-up

The retained reports were read only after the fresh results above were
recorded. Their six deterministic adversarial scenarios were then rerun
explicitly:

| Verification | Result |
| --- | --- |
| `.\gradlew.bat :server:test --tests "*M8AdversarialTest" --rerun-tasks` | PASS - 6 tests, 0 failures, 0 errors, 0 skipped |

This selection directly covers historical `M8-01`, `M8-02`, and the original
initial-add controls. Its two former series-coupling scenarios now pin current
D046/D053 behavior: removal performs no series write, and series creation is
independent of friendship state. The selection completed successfully against
the disposable PostgreSQL test database.

The earlier evaluation proved three defects and its remediation re-evaluation
closed them against an intermediate contract. This run independently verifies
the first two fixes on the current pinned baseline and evaluates `M8-03` against
the later authoritative D053 supersession rather than incorrectly reviving the
obsolete close-on-unfriend rule.

**Final result: PASS.**
