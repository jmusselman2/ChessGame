# Codex Evaluation State

- **Historical pre-M19 baseline:** `38be421dfd64c269687c893300f11e661bfa9c90`.
  `evals/M19/critic-report.md` and `evals/M19/test-report.md` evaluated that
  baseline before M19 was implemented; they remain unchanged as historical
  records.
- **Current evaluated `main`:** `1f17312848a6d6c9499c0accbd7eb6401f44e657`.
- **Current `main`:** `c7a6fe1`, which is not yet evaluated. See *Not yet
  evaluated* below.
- **Current milestone:** M19 current-implementation re-evaluation complete.
- **Status:** `DEFECT FOUND — M19.2–M19.12 EVALUATED; 1 FINDING, REMEDIATED
  2026-09-17 ON claude-autopilot, AWAITING INDEPENDENT RE-EVALUATION`. M17.3,
  M17.5 and M17.6 landed after this evaluation and are not yet evaluated.
- **Scope boundary:** M19.1 moved to M20.1. The N >= 3 resignation and
  continuation flow formerly in M19.8 moved to M20.2. Neither is an M19 defect.
- **Current reports:** `evals/M19/re-evaluation-critic-report.md` and
  `evals/M19/re-evaluation-test-report.md`.

## Verdict

M19.2, M19.4–M19.6, and M19.8–M19.12 satisfy their current acceptance criteria.
M19.3 and M19.7 share one defect:

- **M19-01 — participant exact-set identity is incorrectly collapsed by bare
  UUID.** `Participant` and the V9 schema identify a participant by
  `(kind, ref)`, and `tables.participant_set` serializes `KIND:ref`. However,
  `TableRepository.findOrCreate` rejects duplicates using `ref` alone and sorts
  its canonical seats using `ref` alone. A `USER` and `SCRIPTED` participant
  with the same UUID are valid, distinct identities in the schema but cannot be
  seated together through the repository. The evaluator-only
  `M19ParticipantIdentityRegressionTest` records the failure. Production code
  was not changed by the evaluation. **Remediated 2026-09-17** by the
  implementation track (`evals/remediation-report.md`, *M19-01*): duplicate
  detection uses the whole participant and seats are ordered by ref then kind.
  The regression passes unmodified. The implementation track re-ran it on
  2026-09-23 against disposable PostgreSQL, on the same code as `main`
  `c7a6fe1`: `M19ParticipantIdentityRegressionTest` 1/1 and
  `TableRepositoryTest` 13/13, none skipped. That is the implementation track's
  evidence, not an independent re-evaluation.

All six earlier evaluation findings remain closed by the 2026-09-10
remediation: M10-01, M12-01, M14-01, M14-02, M14-03, and M18-01. Their retained
regressions still pass on the current baseline.

## Migration evidence

- V1–V9 migrate cleanly from an empty disposable PostgreSQL database.
- The evaluator-only `M19MigrationEvaluationTest` starts at V2, writes existing
  users, a friendship, series, game, move, and audit event, applies V3–V9, and
  verifies every identity and row is preserved. It also verifies the new
  participant rows, nullable engagement timestamps, and Flyway history through
  version 9.
- The retained `TablesMigrationTest` independently exercises the V4 pair schema
  through the current participant schema, including closed and active series,
  dashboard, history, moves, and events.

## Not yet evaluated

Commits on `main` after the evaluated `1f17312`:

- `7f0846e` — the `M19-01` remediation (`TableRepository` identity).
- `48adebb` — `M17.3`: the home screen names its player.
- `11bc23c` — `M17.5`: an "All users" page to add friends from, a testing aid
  that is part of the MVP (`D071`).
- `c7a6fe1` — `M17.6`: the "All users" page lists friends too.

`M17.4` (user settings) is `BLOCKED` on the project owner and has no code to
evaluate. Documentation-only commits after `c7a6fe1` (`D072`, `docs/FUTURE.md`)
change no behaviour.

## Beta result

At the time of the M19 evaluation, the existing Render beta was serving the
evaluated `main`: a cold `GET /health`
returned HTTP 200 after 54.329 seconds with `ChessGame server is healthy (build
1f17312)`. The non-health-only response proves the database-backed application
started; startup calls `connectAndMigrate` before installing authenticated
routes, so the deployed build successfully applied its migration set through
V9.

`evals/M19/current-beta-smoke.ps1` passed against the deployed HTTPS/WSS
endpoint with three throwaway accounts. It covered session reuse, username
lookup, friendships, group creation and eligible additions, transitive group
membership and leave, the existing-series offer and a parallel series, a legal
move and realtime opponent notification, stale-version recovery, undo,
resignation and automatic rematch, seat rotation, explicit series exit without
changing the current game, unfriending without closing a series, dashboard, and
history. D035 means those throwaway beta rows are retained.

The following beta-only evidence remains externally blocked:

- no `BETA_DATABASE_URL` or Supabase database credential is available, so
  Flyway version 9 and pre-migration beta rows cannot be queried directly;
- no Render API credential is available, so deployed log lines cannot be read
  back; safe failure logging is covered by the local forced-failure tests;
- no project beta signing keystore is available, so a distributable signed APK
  cannot be produced. The endpoint-configured debug APK and the repository's
  throwaway-signing verification are the locally available substitutes.

The endpoint-configured debug APK installed and launched on `ChessPlayer1`.
After its cold-start wake/retry path reached onboarding, a newly claimed beta
session survived force-stop and a second cold launch directly to the dashboard
(11.736 seconds). A session that predated this evaluation could not be exercised
because the available AVDs started without the app installed.

## Verification commands

All database commands used
`TEST_DATABASE_URL=postgresql://chessgame:chessgame@localhost:55432/chessgame_test`
against disposable PostgreSQL.

- focused M19 server and migration tests: PASS;
- `./gradlew :server:test --rerun-tasks --continue`: PASS, 560 tests and none
  skipped, before adding the expected-red evaluator regression;
- `./gradlew :android-app:testDebugUnitTest --rerun-tasks`: PASS, 442 tests and
  none skipped;
- `./gradlew ktlintCheck`: PASS;
- `./gradlew build --continue`: expected failure only at
  `M19ParticipantIdentityRegressionTest` (562 server tests, 1 failure; Android
  build, lint, and 442 tests completed successfully under `--continue`);
- `./gradlew :server:test --tests M19MigrationEvaluationTest --rerun-tasks`:
  PASS;
- `./gradlew :server:test --tests M19ParticipantIdentityRegressionTest
  --rerun-tasks`: EXPECTED FAIL;
- `evals/M19/current-beta-smoke.ps1`: PASS;
- `./gradlew :android-app:assembleDebug -PchessServerUrl=<beta HTTPS endpoint>
  -PsupabaseAnonKey=<publishable key>`: PASS; clean emulator install and cold
  relaunch/session restoration: PASS;
- `scripts/verify-beta-apk.sh`: PASS, 6/6 with a disposable signing key;
- `git diff --check`: PASS.

## Exact next action

1. Re-evaluate the `M19-01` remediation (`7f0846e`, `evals/remediation-report.md`).
2. Evaluate `M17.3`, `M17.5` and `M17.6` against their acceptance criteria in
   `docs/BACKLOG.md` and `D071`.

After that, **M20.1 remains the next human-sign-off boundary**: choose
the multi-game rules/module architecture with the project owner before starting
M20.2. Do not choose that architecture automatically.
