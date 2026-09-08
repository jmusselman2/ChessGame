# M7 — Independent Evaluation: Test Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Evaluator coverage

- Android `M7AdversarialTest` covers HTTP 429/503 refresh failures, network
  failure, malformed success payloads, failed replacement sign-up, simultaneous
  first callers, and recovery after rotated-session persistence failure.
- Server user `M7AdversarialTest` covers same-subject insert contention,
  initial/later/route-integrated last-seen write failure, the exact throttle
  boundary, and simultaneous activity.
- Server `M7TokenAdversarialTest` covers required expiry and missing
  issuer/audience/subject/key ID, plus a valid multi-audience token.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Retained Android M7 authentication | 13 | 0 | 0 | 0 | PASS |
| Shared Android startup/restart paths | 27 | 0 | 0 | 0 | PASS |
| Android M7 adversarial | 6 | 2 | 0 | 0 | DEFECT PROVED |
| Retained server M7 focused | 48 | 0 | 0 | 0 | PASS |
| Adjacent server identity route | 6 | 0 | 0 | 0 | PASS |
| Server M7 adversarial | 11 | 4 | 0 | 0 | DEFECT PROVED |
| `game-core` | 394 | 0 | 0 | 0 | PASS |

The two Android failures are the 429 and 503 identity-replacement variants.
The server failures are three last-seen rollback variants and the signed token
without `exp`. The remaining four Android and seven server adversarial tests
pass, which closes the surrounding network, persistence-retry, concurrency,
claim-boundary, and throttle-boundary suspicions described in the critic report.

Five live Supabase tests executed because both required environment values were
present: three direct authentication tests and two shared startup tests. All
passed with no skips. A separate sanitized configuration check reported
`external.anonymous_users=true`, `disable_signup=false`, and one EC/ES256 JWKS
key. A deliberately invalid refresh token returned HTTP 400 with a structured
non-secret error response; no credential value was printed.

Additional verification passed:

- `:android-app:ktlintTestSourceSetCheck`;
- `:server:ktlintTestSourceSetCheck`;
- `:android-app:lintDebug`;
- `:android-app:assembleDebug`;
- `:server:assemble`;
- `git diff --check`.

The non-test verification invocation finished successfully with 58 tasks (2
executed, 56 up to date) after a prior invocation reached all requested tasks
but did not exit and was interrupted. The successful rerun is the controlling
result.

The last clean aggregate baseline build before deliberate M7 regressions had
1,202 JVM tests and zero failures, errors, or skips: 394 game-core, 398 Android,
and 410 server. The current aggregate test task is expected to fail on the six
deliberately failing M7 regression assertions, so retained suites and proving
regressions are reported separately.
