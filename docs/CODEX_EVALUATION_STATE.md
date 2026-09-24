# Codex Evaluation State

- **Historical pre-M19 baseline:** `38be421dfd64c269687c893300f11e661bfa9c90`.
  `evals/M19/critic-report.md` and `evals/M19/test-report.md` evaluated that
  baseline before M19 was implemented; they remain unchanged as historical
  records.
- **Current evaluated `main`:** `83de79ceed51577b9b8cffa38ca1b937e029537a`
  for the M19-01 post-remediation re-evaluation only. Later M17 work on this
  baseline remains under evaluation; see *Not yet evaluated*.
- **Current `main`:** `83de79ceed51577b9b8cffa38ca1b937e029537a`.
- **Current milestone:** M19 post-remediation re-evaluation complete; current
  M17 re-evaluation next.
- **Status:** `M19 PASS — M19-01 INDEPENDENTLY CLOSED`. M17.3 and M17.5–M17.10
  landed after the original M17 evaluation and are not yet independently
  evaluated.
- **Scope boundary:** M19.1 moved to M20.1. The N >= 3 resignation and
  continuation flow formerly in M19.8 moved to M20.2. Neither is an M19 defect.
- **Current reports:** `evals/M19/post-remediation-re-evaluation-critic-report.md`
  and `evals/M19/post-remediation-re-evaluation-test-report.md`. The earlier
  `re-evaluation-*` reports remain the finding record.

## Verdict

M19.2–M19.12 satisfy their current acceptance criteria. The prior defect shared
by M19.3 and M19.7 is closed:

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
  The independent post-remediation re-evaluation ran the original regression
  unchanged on `83de79c`; it passed together with table, participant, and
  migration coverage. Duplicate detection uses the complete participant, the
  canonical order is ref then kind, and V5's all-user keys remain unchanged.

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

Current-baseline work not yet covered by an independent M17 report:

- `48adebb` — `M17.3`: the home screen names its player.
- `11bc23c` — `M17.5`: an "All users" page to add friends from, a testing aid
  that is part of the MVP (`D071`).
- `c7a6fe1` — `M17.6`: the "All users" page lists friends too.
- `9407a2d` — `M17.7`: responsive game layouts and rotation-safe local play.
- `b69b705` — `M17.8`: an overall HTTP request deadline; its backlog device
  acceptance remains incomplete.
- `ca5468d` — `M17.9`: readable promotion choices.
- `8538db3` — Android 5.1/API 22 compatibility.
- `83de79c` — `M17.10`: foreground resume refresh.

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

Evaluate M17.3 and M17.5–M17.10 on the pinned `83de79c` baseline, including the
remaining M17.8 device evidence and Android 5.1/API 22 compatibility.

After that, **M20.1 remains the next human-sign-off boundary**: choose
the multi-game rules/module architecture with the project owner before starting
M20.2. Do not choose that architecture automatically.
