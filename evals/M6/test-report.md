# M6 — Independent Evaluation: Test Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Added evaluator coverage

`M6AdversarialTest` adds two real-PostgreSQL tests:

1. simultaneous saves against one expected version, with a database trigger
   widening the race window;
2. rollback after an injected failure on the second history-row insertion.

Both retain assertions over canonical state, version, active history, and audit
events so a partial write cannot pass.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| M6 database-focused server tests | 63 | 0 | 0 | 0 | PASS |
| Full server tests | 410 | 0 | 0 | 0 | PASS |
| `game-core` tests | 394 | 0 | 0 | 0 | PASS |
| Android host-side tests | 398 | 0 | 0 | 0 | PASS |
| Total JVM tests in aggregate build | 1,202 | 0 | 0 | 0 | PASS |

Additional checks passed:

- server runtime dependency resolution;
- `:server:ktlintTestSourceSetCheck`;
- aggregate `build --rerun-tasks` (134 tasks executed);
- Android lint and debug/release assembly within the aggregate build;
- `git diff --check`.

The PostgreSQL service was `postgres:18-alpine`, healthy, with separate
`chessgame_dev` and `chessgame_test` databases. The documented host port 55432
falls in a Windows excluded range on this machine, so only the host publication
changed to 54999; the container image, database names, credentials, and schema
were unchanged.
