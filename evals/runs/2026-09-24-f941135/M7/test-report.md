# M7 Test Report

## Fresh verification

This fresh verification was completed and recorded before consulting retained
M7 reports.

| Verification | Result |
| --- | --- |
| Live Supabase JWKS request | PASS - HTTP 200; one EC/ES256 signature key with a key id |
| Live Supabase Auth health request with publishable `apikey` | PASS - HTTP 200 |
| `SupabaseLiveAuthTest` within focused Android auth run | PASS - 3 live tests, 0 skipped; create, refresh, and restore exercised against the development project |
| `AnonymousAuthTest` within focused Android auth run | PASS - 12 tests, 0 failures, 0 errors, 0 skipped |
| `SupabaseTokenVerifierTest`, `M7TokenAdversarialTest`, `AuthenticatedRouteTest`, `UsernameTest`, `UsernameClaimTest`, `M7AdversarialTest`, and `LastSeenTest` | PASS - 68 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :android-app:build :server:build --rerun-tasks` | PASS - 125 tasks executed; Android 515 tests and server 590 tests, 0 failures, 0 errors, 0 skipped |
| Current source, schema, and caller inspection | PASS - auth/session, JWT/JWKS, user resolution, username race/uniqueness, and five-minute last-seen paths agree with the requirements |

The focused Android selection contained 15 tests total. Because the configured
publishable key was present, all three live tests ran rather than skipping and
created only documented throwaway anonymous identities in the development Auth
project. The publishable key value was never printed or recorded.

The 68 focused server tests comprised 12 token-verifier, 6 token-adversarial, 9
authenticated-route, 9 username validation, 12 username-claim, 5 broader M7
adversarial, and 15 last-seen cases. They cover the known high-risk boundaries:
unsupported or forged tokens, missing/unreadable expiry, first-request races,
same-name claim races, exact throttle boundaries, concurrent activity, failed
telemetry writes, and valid-request continuation after such a failure.

The complete builds extended the focused checks across every retained Android
and server regression, lint/check tasks, packaging, and both debug and release
Android assemblies. Expected log output from deliberately refused user creation
and `last_seen_at` writes was followed by passing assertions and a successful
build.

### Evaluator/environment corrections

- The first Auth health request omitted the endpoint's required `apikey` header.
  Re-running with the already configured publishable key returned HTTP 200.
- A supplemental Data API root request rejected the publishable key because that
  endpoint requires a privileged role. The irrelevant probe was abandoned; no
  privileged key was sought and no production defect was reported.
- An initial Android test-result path assumption was corrected to the actual
  module path before totals were aggregated.

## Historical follow-up

The retained reports were read only after the fresh results above were
recorded. Their regressions for all three original findings were then rerun
explicitly:

| Verification | Result |
| --- | --- |
| `.\gradlew.bat :android-app:testDebugUnitTest --tests "*M7AdversarialTest" --rerun-tasks` | PASS - 6 tests, 0 failures, 0 errors, 0 skipped |
| `.\gradlew.bat :server:test --tests "*M7AdversarialTest" --tests "*M7TokenAdversarialTest" --tests "*LastSeenTest" --tests "*SupabaseTokenVerifierTest" --rerun-tasks` | PASS - 38 tests, 0 failures, 0 errors, 0 skipped |

The server total comprised 6 token-adversarial, 12 verifier, 15 last-seen, and
5 broader M7 adversarial tests. Expected stack traces from deliberately refused
user creation and activity writes are evidence for the asserted failure paths;
both Gradle invocations exited successfully.

The earlier evaluation ran against a smaller codebase and initially proved
`M7-01`, `M7-02`, and `M7-03`; its remediation re-evaluation subsequently
closed them. This run independently reproduced their passing regressions on the
current pinned baseline and extended the aggregate check to 515 Android and 590
server tests.

**Final result: PASS.**
