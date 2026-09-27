# M19 Test Report

## Fresh verification

The fresh requirement and implementation assessment was completed before any
retained M19 report was read.

| Verification | Result |
| --- | --- |
| M19.2-M19.12 source/schema/caller matrix | PASS - every current acceptance criterion is implemented or, for M19.9/M19.10, documented at its required boundary |
| Focused forced server rerun | PASS - 71 suites, 593 tests, 0 failures, 0 errors, 0 skipped |
| Focused forced Android rerun | PASS - 6 suites, 209 tests, 0 failures, 0 errors, 0 skipped |
| `M19MigrationEvaluationTest` | PASS unchanged - V2 data survives the current migration chain, with new engagement timestamps null |
| `M19ParticipantIdentityRegressionTest` | PASS unchanged - equal UUID text across `USER` and `SCRIPTED` remains two identities and one canonical table in either input order |
| Historical comparison | PASS - historical `M19-01` remains closed; no new check was justified after current coverage |
| Device discovery | PASS - Kindle `G090MJ0574130HMR` (`KFDOWI`) was the only connected device |
| `git diff --check` before report authoring | PASS |

The focused command used `--rerun-tasks` and selected the current group,
table, migration, series, rotation, non-user, leave, timestamp, logging, and
Android caller coverage. The server selection resolved to the complete current
server suite. The 209 Android tests were the directly relevant focused suites.

## Final retained-regression and build gate

The final command reran the M18 documentation regression and then executed:

`./gradlew.bat build --continue --rerun-tasks`

| Module / gate | Suites | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| M18 documentation regression | - | - | 0 | 0 | 0 | PASS |
| `game-core` | 34 | 394 | 0 | 0 | 0 | PASS |
| `server` | 71 | 593 | 0 | 0 | 0 | PASS |
| Android host-side | 39 | 521 | 0 | 0 | 0 | PASS |
| Aggregate Gradle build | - | 1,508 | 0 | 0 | 0 | PASS |

Gradle reported `BUILD SUCCESSFUL` in 16m 50s with all 134 actionable tasks
executed. The build also completed formatting/static checks, Android lint,
debug/release assembly, and server distributions.

Intentional forced-failure tests emitted expected exception and warning logs,
including lock-timeout, refused-user-creation, and transient `last_seen_at`
paths. Their assertions passed and Gradle reported no failed task; these are
evaluator fixtures, not production defects.

`git diff --check` is rerun on the complete M19 deliverable immediately before
the checkpoint is created.

**Final result: PASS. No skipped test and no open M19 finding.**
