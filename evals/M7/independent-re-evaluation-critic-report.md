# M7 — Independent Remediation Re-evaluation: Critic Report

## Scope and verdict

M7.1–M7.5 were re-evaluated from the beginning against the merged production
baseline `d03a0fd9e6067a7702debf8ad56a6e7a62ae4920`. The review reconciled the
backlog, product and architecture requirements, decisions D004, D006, D007,
D008, D010, D031, D039, and D043, the original evaluator reports and tests,
the remediation report, production callers, and retained Android/server tests.

**Verdict: PASS.** The three prior findings are closed, no legitimate
unresolved M7 defect was found, and no production code was changed.

## Prior finding closure

### M7-01 — Transient refresh failures replacing the account: CLOSED

`AnonymousAuthenticator` now creates a replacement account only after the
refresh endpoint refuses the credential with HTTP 400 or 401. Rate limits,
timeouts, forbidden responses, server failures, connection failures, malformed
successful responses, and persistence failures propagate while the prior
durable identity remains intact. The one corrected path is shared by normal
session restoration and forced renewal, and the authenticator mutex continues
to prevent duplicate first-run sign-ups.

All six retained Android adversarial tests pass, including the original 429 and
503 proofs. The generalized retained suite also passes for 408, 403, 500, 502,
and 504, for 400/401 replacement, failed replacement sign-up, rotated-session
persistence recovery, and concurrent callers.

### M7-02 — Failed last-seen persistence spending the window: CLOSED

`LastSeenTracker` still uses the in-memory timestamp as the per-user concurrency
claim, but conditionally restores the preceding state if persistence throws.
The conditional remove/replace cannot erase a newer claim taken by another
caller. Initial, later, and authenticated-route write failures now leave an
immediate retry eligible; a successful retry starts the next throttle window,
and different users remain isolated.

All five retained server user adversarial tests and all ten retained last-seen
tests pass against PostgreSQL, including exact-boundary and simultaneous-call
coverage. The route deliberately continues to report the failed database write
as a server error; the corrected retry persists activity.

### M7-03 — Signed token without usable expiry: CLOSED

`SupabaseTokenVerifier` performs signature, issuer, and audience verification
and then requires a parsed expiry instant. Tokens with a missing or non-time
`exp` are rejected, as are expired, forged, unsigned, wrong-issuer,
wrong-audience, unknown-key, missing-key-id, and blank-subject tokens. A valid
multi-audience token remains accepted.

All six retained token adversarial tests and all twelve verifier tests pass.

## Remaining M7 scope

- The live development project was reachable during the Android run: three
  direct anonymous-auth tests and two startup tests passed without exposing the
  configured key.
- Stored-session restoration, refresh rotation, persistence, failure recovery,
  process-style store reuse, expiration margins, request shape, and mutex
  serialization remain covered.
- Authenticated subject resolution remains database-unique under concurrent
  first requests and stable on later requests.
- Username validation, normalization, one-time claiming, idempotent reclaim,
  case-insensitive database uniqueness, concurrent claims, lost-name
  reservation, route authentication, and error mapping remain green.
- Last-seen activity is recorded only after verified authentication and remains
  throttled per user to the documented five-minute window.

The earlier non-defect distinctions remain unchanged: retrying a parent refresh
token after a rotated-session persistence failure is supported by the live
provider behavior; a process restart may cause one extra last-seen write; wall
clock rollback may lengthen a process-local window; and accepting a trusted
JWKS key of its matching supported algorithm is not algorithm confusion.
