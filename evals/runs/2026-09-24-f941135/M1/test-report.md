# M1 Test Report

- **Run:** `2026-09-24-f941135`
- **Pinned baseline:** `f9411358d319d8501bfc58aa05470523a67bd6a8`
- **Result:** PASS
- **Historical comparison:** completed after the fresh provisional record

## Static Verification

- Required directories `game-core/`, `android-app/`, `server/`,
  `database/migrations/`, and `docs/` exist.
- `settings.gradle.kts` includes all three code modules, with the Android module
  mapped to `android-app/app`.
- `game-core/build.gradle.kts` applies only Kotlin/JVM and declares no
  production dependency. A source import scan found no Android, AndroidX,
  Ktor, PostgreSQL, Exposed, Hikari, JDBC, or SQL imports.
- Android and server build files both depend on `project(":game-core")`, and
  current production callers import game-core types.
- `.github/workflows/ci.yml` supplies PostgreSQL 18, JDK 24, current action
  majors, and runs `./gradlew build --continue` on `main`,
  `claude-autopilot`, and pull requests targeting `main` as documented.

## Executed Verification

All commands ran from the repository root unless noted.

| Command/check | Result |
| --- | --- |
| `gradlew.bat projects` | PASS; three expected modules recognized |
| `gradlew.bat :game-core:test` | PASS; 394 tests, 0 failures/errors/skips |
| `gradlew.bat :game-core:build` | PASS |
| `gradlew.bat :android-app:testDebugUnitTest` | PASS; 515 tests, 0 failures/errors/skips |
| `gradlew.bat :android-app:assembleDebug` | PASS |
| `gradlew.bat :android-app:build` | PASS; unit tests, lint, debug/release packaging |
| `gradlew.bat :server:test` | PASS after environment repair; 590 tests, 0 failures/errors/skips |
| `gradlew.bat :server:build` | PASS |
| `gradlew.bat ktlintCheck` | PASS |
| `gradlew.bat check` | PASS |
| `gradlew.bat build --continue` | PASS; 134 tasks reported |
| `gradlew.bat ktlintFormat` | PASS in a disposable detached worktree; non-interactive |
| `gradlew.bat :server:run` plus `GET /health` | PASS; HTTP 200, `ChessGame server is healthy` |
| API 37 emulator install/launch | PASS; isolated evaluator package cold-launched and was removed |
| GitHub Actions for pinned SHA | PASS; runs `36087527703` (`main`) and `36087399047` (`claude-autopilot`), both `Build and Test` success |

## Evaluator/Environment Correction

The first `:server:test` attempt found that `TEST_DATABASE_URL` was configured
but its documented disposable PostgreSQL container, `chessgame-postgres`, was
stopped. Connection failures were therefore evaluator-environment failures,
not production failures. The repetitive run was interrupted, the same existing
container was started and allowed to become healthy, and `:server:test` was
rerun from scratch. The rerun passed all 590 tests, and the subsequent server,
quality, and aggregate builds passed.

## Historical Comparison

No historical M1 report exists under
`evals/runs/2026-09-17-e2b3287/`. The retained M2 critic report records that M1
was skipped in that earlier evaluation. The comparison supplied no omitted M1
criterion, prior finding, or additional adversarial case, so no further command
was warranted.

## Final Result

All independently selected fresh checks pass. No current M1 finding was
identified.
