# M2 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M2 reports.

| Verification | Result |
| --- | --- |
| `adb devices -l` | Kindle `G090MJ0574130HMR` (`KFDOWI`) discovered |
| Focused M2 tests (`M2DomainInvariantTest`, `M2CollectionImmutabilityTest`, `StandardPositionTest`) | PASS — 30 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :game-core:test :game-core:build --rerun-tasks` | PASS — 394 tests, 0 failures, 0 errors, 0 skipped; build and game-core ktlint checks successful |
| Production import/dependency audit | PASS — no Android/server/database dependency in `game-core` production code |
| Current caller audit | PASS — Android rendering/state and server persistence conversions consume the domain model without moving those concerns into it |

The full core regression run completed in 8m54s while another local Gradle
workload was using the host. The test worker remained responsive and CPU-active;
no timeout, environment failure, or product failure occurred.

## Historical follow-up

The retained M2 reports were read only after the fresh results above were
recorded. They justified explicit confirmation of the historical constructor,
published-map, game-history, and shared-list mutation paths. Those checks were
already present in the focused fresh selection:

- `M2DomainInvariantTest`: 3 passed;
- `M2CollectionImmutabilityTest`: 14 passed;
- `StandardPositionTest`: 13 passed.

The full `game-core` result remained 394 passed, 0 failed, 0 errored, and 0
skipped. No evaluator repair or rerun was necessary.

**Final result: PASS.**
