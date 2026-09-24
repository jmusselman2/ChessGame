# Codex Evaluation State

- **Historical pre-M19 baseline:** `38be421dfd64c269687c893300f11e661bfa9c90`.
  `evals/M19/critic-report.md` and `evals/M19/test-report.md` evaluated that
  baseline before M19 was implemented; they remain unchanged as historical
  records.
- **Current evaluated `main`:** `83de79ceed51577b9b8cffa38ca1b937e029537a`
  for M19-01, M17.3/M17.5–M17.10, and Android 5.1/API 22 compatibility.
- **Current `main`:** `83de79ceed51577b9b8cffa38ca1b937e029537a`.
- **Current milestone:** current M17 behavior and Android 5.1/API 22
  compatibility re-evaluations complete; consolidated state refresh next.
- **Status:** `M19 PASS — M19-01 INDEPENDENTLY CLOSED; M17.3 AND
  M17.5–M17.10 PASS`. M17.4 remains intentionally blocked on an owner decision.
- **Scope boundary:** M19.1 moved to M20.1. The N >= 3 resignation and
  continuation flow formerly in M19.8 moved to M20.2. Neither is an M19 defect.
- **Current reports:** `evals/M19/post-remediation-re-evaluation-critic-report.md`
  and `evals/M19/post-remediation-re-evaluation-test-report.md`. The earlier
  `re-evaluation-*` reports remain the finding record. Current M17 reports are
  `evals/M17/current-re-evaluation-critic-report.md` and
  `evals/M17/current-re-evaluation-test-report.md`; compatibility reports are
  `evals/M17/android-5.1-compatibility-critic-report.md` and
  `evals/M17/android-5.1-compatibility-test-report.md`.

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

## Current M17 dispositions

- M17.3: PASS — dashboard-only, bounded, non-tappable username.
- M17.4: BLOCKED by design — settings content awaits the project owner; no code.
- M17.5: PASS — authenticated All Users flow, failure/empty/retry/Add/navigation.
- M17.6: PASS — non-friends then friends, recency order and shared 200-row cap.
- M17.7: PASS — responsive layouts and rotation-safe local-game lifetime.
- M17.8: PASS — 30 s real-client request limit, WebSocket exemption, deterministic
  stalled-peer/startup regressions, aggregate build and fresh Pixel 7 live start.
- M17.9: PASS — shared 52 dp promotion target and 32 dp font-independent glyph.
- M17.10: PASS — immediate foreground reload and socket replacement. Its model
  regressions passed; the implementation track's two-device timing was reviewed
  but the complete scenario was not independently repeated.

No new M17 finding was opened. Android 5.1/API 22 compatibility in `8538db3`
passes static, build, package, Pixel regression, and physical Kindle API 22
runtime verification.

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

Refresh the consolidated verification record, run final aggregate checks, and
then keep M20.1 as the next human-sign-off boundary.

After that, **M20.1 remains the next human-sign-off boundary**: choose
the multi-game rules/module architecture with the project owner before starting
M20.2. Do not choose that architecture automatically.
