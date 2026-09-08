# M7 — Independent Remediation Re-evaluation: Test Report

Baseline: `d03a0fd9e6067a7702debf8ad56a6e7a62ae4920`

## Retained coverage

The re-evaluation retained every original proving regression unchanged. It also
used the remediation's generalized tests for refresh-status classification,
last-seen failure rollback and per-user isolation, and missing/unreadable JWT
expiry claims. No test was deleted, skipped, weakened, or rewritten.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Focused Android auth/startup | 39 | 0 | 0 | 0 | PASS |
| Android M7 adversarial subset | 6 | 0 | 0 | 0 | PASS |
| Focused server M7 | 69 | 0 | 0 | 0 | PASS |
| Server M7 adversarial subset | 11 | 0 | 0 | 0 | PASS |
| Full `game-core` tests | 394 | 0 | 0 | 0 | PASS |
| Full Android host-side tests | 413 | 0 | 0 | 0 | PASS |
| Full server tests | 429 | 0 | 0 | 0 | PASS |
| Total JVM tests in aggregate build | 1,236 | 0 | 0 | 0 | PASS |

The focused Android total comprises 12 `AnonymousAuthTest`, 6
`M7AdversarialTest`, 3 live `SupabaseLiveAuthTest`, 16 `AppStartupTest`, and 2
live `AppStartupLiveTest` cases. The focused server total comprises 12 verifier,
6 token-adversarial, 9 authenticated-route, 6 identity-route, 10 last-seen, 5
user-adversarial, 9 username-validation, and 12 username-claim cases.

Additional checks passed inside `build --no-parallel --rerun-tasks`:

- repository `ktlintCheck` tasks;
- Android debug lint;
- Android debug and release assembly;
- server assembly and distribution;
- 134 executed Gradle tasks;
- `git diff --check` after the report update.

Five live Supabase tests ran because the required environment was configured;
none skipped. All database-backed tests ran against the disposable PostgreSQL
service on host port 54999.

The first focused server attempt was infrastructure-only: the disposable
`chessgame_test` catalog entry referred to a missing database directory, so all
41 PostgreSQL-backed cases failed to initialize while 28 non-database cases
passed. Only that disposable test database was dropped and recreated. The
complete 69-test focused rerun and the later 429-test full server run then
passed with zero failures, errors, or skips.
