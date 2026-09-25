# M6 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M6 reports.

| Verification | Result |
| --- | --- |
| Disposable PostgreSQL container and database inventory | PASS — healthy PostgreSQL 18 container; separate development and test databases present |
| Test-database create/insert/select/drop round trip | PASS — selected value `42`, then removed the temporary table |
| Focused `MigrationsTest`, `InitialSchemaTest`, `GameRepositoryTest`, and `M6AdversarialTest` run | PASS — 45 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :server:build --rerun-tasks` | PASS — 590 tests, 0 failures, 0 errors, 0 skipped; ktlint, packaging, check, and build successful |
| Server runtime dependency resolution | PASS — Exposed 1.5.0 modules, HikariCP 7.1.0, PostgreSQL JDBC 42.7.13, and Flyway 13.4.0 resolved |
| Direct current-schema constraint inspection | PASS — 78 primary, foreign, unique, and check constraints listed from PostgreSQL catalogs |
| Real `:server:run` against `chessgame_dev` | PASS — Flyway validated 10 migrations and reported schema version 10 current |
| `GET http://127.0.0.1:8080/health` | PASS — HTTP 200, `ChessGame server is healthy` |

The focused selection exercises empty/current/reset migration behavior, the
current evolved schema constraints, representative state serialization and
round trips, optimistic concurrency, history replacement, audit persistence,
and transactional rollback. The complete server build extends that result
across all retained server regressions and integration suites.

The server process used for the live migration and health check was terminated
after the successful request. No credentials were printed or recorded.

## Historical follow-up

The retained M6 reports were read only after the fresh results above were
recorded. Their two added adversarial database tests are part of the passing
45-test focused selection and the 590-test server build.

After comparison, the two suites most directly related to the retained
robustness notes and the evolved V10 index migration were rerun:

| Verification | Result |
| --- | --- |
| `.\gradlew.bat :server:test --tests "*DatabaseConfigTest" --tests "*IndexesTest" --rerun-tasks` | PASS — 13 tests, 0 failures, 0 errors, 0 skipped |

The earlier evaluation reported 63 database-focused and 410 complete server
tests against V1. This run used a more narrowly milestone-specific fresh set of
45 tests, then covered all current database and non-database server tests in the
590-test complete build against V10. No failed evaluator command or environment
repair was required.

**Final result: PASS.**
