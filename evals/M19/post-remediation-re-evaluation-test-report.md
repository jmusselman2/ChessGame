# M19-01 — Independent Post-Remediation Re-evaluation: Test Report

Evaluated `main`: `83de79ceed51577b9b8cffa38ca1b937e029537a`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| `M19ParticipantIdentityRegressionTest` | 1 | 0 | 0 | 0 | PASS |
| `TableRepositoryTest` | 13 | 0 | 0 | 0 | PASS |
| `TablesMigrationTest` | 2 | 0 | 0 | 0 | PASS |
| `NonUserParticipantTest` | 6 | 0 | 0 | 0 | PASS |
| `M19MigrationEvaluationTest` | 1 | 0 | 0 | 0 | PASS |
| Full server suite | 570 | 0 | 0 | 0 | PASS |
| Full `game-core` suite in aggregate build | 394 | 0 | 0 | 0 | PASS |
| Full Android host-side suite in aggregate build | 484 | 0 | 0 | 0 | PASS |

The original evaluator regression ran unchanged and now creates one table for
`USER:<uuid>` plus `SCRIPTED:<uuid>` in either input order. The retained table
tests independently prove that an actual duplicate remains rejected and that
an all-user key remains byte-identical to the form V5 wrote.

All database-backed tests used the configured disposable PostgreSQL test
database. The focused 23-test selection and all 570 server tests ran with no
skips.

Additional verification:

- `./gradlew.bat :server:test --rerun-tasks --continue`: PASS;
- `./gradlew.bat build --continue`: PASS, including ktlint, Android lint,
  debug/release assembly, server distributions, and all JVM tests;
- no production or test source was changed by the re-evaluation.
