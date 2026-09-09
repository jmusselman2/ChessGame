# M9 — Independent Evaluation: Test Report

Baseline: `3de5e28dbcb29e5d86e163c39b26278686385c4c`

## Coverage

The retained M9 verification comprises `OpenSeriesTest`, `InitialGameTest`,
`SeriesLifecycleTest`, and `DashboardTest`. `SeriesIdempotencyTest` supplies the
later end-to-end concurrency regression for two simultaneous opens of a
game-less series and repeated opens across rematches.

## Results

| Verification | Tests | Result |
| --- | ---: | --- |
| M9 retained plus later series idempotency | 54 | PASS |
| Compilation and test-source checks included by Gradle | — | PASS |
| `git diff --check` | — | PASS |

The first invocation began before the disposable PostgreSQL container was
ready and failed all database fixtures with connection refusals. After starting
the repository's Compose PostgreSQL service, the same forced test selection was
rerun against `chessgame_test`; all 54 tests passed with no failures or skips.

No new M9 regression file was added. The already-retained M8 adversarial tests
continue to prove M8-01 through M8-03 and are intentionally expected to fail
until production remediation.
