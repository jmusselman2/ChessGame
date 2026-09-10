# M8 — Independent Remediation Re-evaluation: Test Report

Re-evaluation baseline: `a4bb6999af4dea4665c832bebcaf6122a021e183`

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| M8 and adjacent server suites | 256 | 0 | 0 | 0 | PASS |
| `M8AdversarialTest` within that selection | 6 | 0 | 0 | 0 | PASS |
| Android `ChessAppTest` plus interruption harness | 116 | 3 | 0 | 0 | EXPECTED FAIL |
| Retained tests in that Android selection | 113 | 0 | 0 | 0 | PASS |
| Carried M10/M12 evaluator regressions | 2 | 2 | 0 | 0 | EXPECTED FAIL |
| Full `game-core` result XML | 394 | 0 | 0 | 0 | PASS |
| Build excluding known-red evaluator test tasks | — | — | — | — | PASS |
| `git diff --check` | — | — | — | — | PASS |

The 256-test database-backed server selection included the `friends`, `series`,
`user`, `dashboard`, `history`, and `db` packages. It was forced with
`--rerun-tasks` against the healthy disposable PostgreSQL 18.6 instance on port
54999. All tests passed, including the six M8 adversarial cases, schema upgrade
coverage, username lookup, both friendship directions, removal and revival,
series lifecycle, dashboard, and history boundaries.

The selected Android run covered the modified `ChessAppTest` refusal fixture
and the retained interruption harness. Its only failures were the unchanged
M14-01, M14-02, and M14-03 regressions; the other 113 tests passed. The separate
server carry check failed only M10-01 and M12-01, confirming that no later
remediation was present to re-evaluate.

The full `game-core` execution produced 394 passing test results with no skips.
After all results and downstream build tasks were written, its Gradle test
worker did not terminate and the wrapper was interrupted; this is recorded as
a runner teardown issue rather than a test failure. A second aggregate build
excluding `:server:test`, `:android-app:testDebugUnitTest`, and
`:game-core:test` returned `BUILD SUCCESSFUL`, covering ktlint, Android lint,
debug and release APKs, and server distributions.

