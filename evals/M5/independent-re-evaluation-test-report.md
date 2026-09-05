# M5 — Independent Remediation Re-evaluation: Test Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Evaluator coverage added

- `M5IndependentReevaluationTest` compares Android interaction results with
  `game-core` for every legal destination in seven representative positions,
  including promotion choices and prospective claims. It also covers an
  independent live-check sequence and checkmate at the fifty-move boundary.
- `M5IndependentUiReevaluationTest` plays a complete auto-oriented local game
  through Compose semantics, verifies move history and terminal presentation,
  and proves that post-terminal board input is ignored.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Android host-side unit tests | 398 | 0 | 0 | 0 | PASS |
| `game-core` tests | 394 | 0 | 0 | 0 | PASS |
| Server tests in full build | 408 | 0 | 0 | 0 | PASS |
| API 36 Compose instrumentation | 7 | 0 | 0 | 0 | PASS |
| Total JVM tests | 1,200 | 0 | 0 | 0 | PASS |

The following checks also passed:

- `:android-app:ktlintCheck`
- `:android-app:lintDebug`
- `:android-app:assembleDebug`
- `build --rerun-tasks` (134 tasks executed)
- `git diff --check`

The API 36 instrumentation device was `ChessPlayerM5Api36`, Android 16/API 36,
using the disposable `aosp_atd` image. The API 37 Espresso incompatibility was
not encountered because the stable required environment was available.

## Environment notes

The machine reserves Windows TCP ports 55348–55447, which includes the
repository's default development port 55432. The same disposable PostgreSQL
Compose service was therefore published locally on port 54999 for the full
build. The first full-build attempt reached the database-backed tests while the
configured database was stopped and failed on connection refusal before test
assertions; after the disposable service was healthy, the complete rerun passed.

An Android lint invocation overlapped an evaluator source edit and hit an
internal UAST service error. The stable serialized lint rerun passed. Neither
event is product evidence.
