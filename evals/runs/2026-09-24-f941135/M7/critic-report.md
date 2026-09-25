# M7 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M7 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M7: `a6c7f49c8ab3d31a7326130b97fb5cf59916b347`
- Requirements: `docs/BACKLOG.md` M7.1 through M7.5 and the applicable identity,
  authentication, username, and activity decisions
- Production implementation: Android anonymous-auth client, session persistence,
  and orchestration; Ktor bearer authentication and JWKS verification; user,
  username, and last-seen persistence
- Current callers: app startup/authentication and authenticated HTTP/WebSocket
  server requests
- Verification: live public Supabase Auth/JWKS probes, focused Android and server
  suites, complete Android/server builds, schema inspection, and current caller
  audit

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M7.1 Supabase development project | PASS | The configured development project exposes a healthy Auth endpoint when called with the configured publishable key, and its live JWKS endpoint returned one usable EC/ES256 signing key. Current Supabase documentation still supports anonymous users and asymmetric signing keys/JWKS. No privileged key was requested, used, printed, or recorded. |
| M7.2 Android anonymous auth primitives | PASS | `SupabaseAuthClient`, `AnonymousAuthenticator`, and the app-private DataStore session store implement create, restore, refresh-near-expiry, dead-refresh replacement, and serialized access. Only a refused refresh (`400`/`401`) replaces the anonymous account; rate limits, server errors, network failures, and unreadable replies preserve the stored identity and propagate for retry. Fifteen focused tests passed, including three unskipped live create/refresh/restore cases. |
| M7.3 Ktor token verification | PASS | `SupabaseTokenVerifier` accepts only supported key/algorithm pairings and verifies signature, issuer, `authenticated` audience, readable expiry, nonblank subject, and key id. The authentication provider resolves one stable internal user, retains failures in token/user resolution, and does not log bearer contents. Focused forged, malformed, expired, wrong-issuer/audience/key/algorithm, concurrency, and route cases passed. |
| M7.4 username claim | PASS | Usernames enforce 3-24 ASCII letters, numbers, underscore, or hyphen, with lowercase normalized lookup and database uniqueness. Claims are transactionally race-safe, renames are refused, and lost-account names are not recycled. The simultaneous-claim regression proves exactly one winner. |
| M7.5 last seen | PASS | Successfully authenticated meaningful HTTP and WebSocket requests invoke activity recording; unauthenticated/rejected requests do not. `LastSeenTracker` preserves the required five-minute per-user throttle with no heartbeat. Concurrent callers do not multiply writes, a failed write gives the window back for retry, and the provider treats only telemetry persistence as best-effort so a valid request continues. |

The current implementation distinguishes identity-critical failures from
best-effort activity telemetry. A database refusal while creating/resolving the
user remains fatal, while a refused `last_seen_at` update is logged, leaves the
five-minute window available, and does not turn an otherwise valid request into
an authentication failure. Failure-injection stack traces in the passing suite
are therefore expected assertions, not production failures.

The JWKS client retains known keys for its configured cache interval and fetches
again for an unknown key id. Supabase's current guidance describes shorter edge
and client cache durations as part of urgent-key-revocation planning, but M7 has
no immediate-revocation criterion and no current verification failed. This is a
deployment tradeoff, not a confirmed M7 defect.

### Fresh findings

No confirmed production defect was found.

The live Auth health probe initially omitted the required `apikey` header; the
probe was corrected and returned HTTP 200. A supplemental probe of the Data API
root was also abandoned after that endpoint correctly required a privileged
role. Neither evaluator endpoint mismatch implicates production, and no
`service_role` key was requested or used.

## Historical comparison

After the fresh verdict was recorded, all retained M7 reports under
`evals/runs/2026-09-17-e2b3287/M7/` were reviewed. The original evaluation found
three production defects: transient refresh failures could replace an anonymous
identity (`M7-01`), a failed last-seen write could consume the throttle window
(`M7-02`), and a correctly signed token without `exp` could verify (`M7-03`).

All three corrections remain present and covered. The post-comparison Android
adversarial run passed all 6 cases, including the 429 and 503 identity-retention
proofs. The post-comparison server selection passed all 38 token and activity
cases: 6 token-adversarial, 12 verifier, 15 last-seen, and 5 broader M7
adversarial cases. Those runs directly cover missing/unreadable expiry, the
failed-write hand-back, exact-boundary and concurrency behavior, and the
same-subject first-request race.

The historical remediation intentionally still returned a server error when
`last_seen_at` persistence failed. The current baseline contains the later D080
correction: activity telemetry alone is best-effort, so the authenticated
request continues and the next request retries the write. Current retained
tests deliberately inject the database refusal and pass, confirming the newer
behavior without weakening failures in token verification or user resolution.

Historical robustness observations were also reconsidered. Parent-token retry
after a rotated-session persistence failure remains supported by the provider;
a process restart may cause one additional activity write; wall-clock rollback
may lengthen an in-memory throttle window; and a supported algorithm bound to
the matching key type from trusted JWKS is not algorithm confusion. None
reproduced a requirement failure or justified a new finding.

**Final M7 verdict: PASS.**
