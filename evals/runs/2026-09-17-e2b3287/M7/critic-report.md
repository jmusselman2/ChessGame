# M7 — Independent Evaluation: Critic Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Verdict

**DEFECT FOUND.** The complete M7 sweep found three legitimate unresolved
defect classes. No production code was changed, and M8 was not started.

## Complete finding batch

### M7-01 — Transient refresh failures can replace the anonymous account

`AnonymousAuthenticator.refreshOrCreate` treats every non-successful refresh
represented by `SupabaseAuthException` as proof that a new account is needed.
`SupabaseAuthClient` uses that same exception for transient service failures and
dead credentials. A rate-limited (429) or unavailable (503) refresh is therefore
followed by anonymous sign-up, and a successful sign-up overwrites the stored
user ID and tokens.

This can sever access to the server-side user, username, and games even though
the refresh credential was never shown to be dead. The behavior affects both
`currentSession` and the shared forced-renewal path. The remediation must use
Supabase's actual error signal for an irrecoverable refresh token rather than a
broad status family or every auth error.

### M7-02 — Failed last-seen persistence consumes the throttle window

`LastSeenTracker.record` publishes `now` in its process-local map before
`UserRepository.touchLastSeen` succeeds. If PostgreSQL rejects that write, the
timestamp remains cached and a request one second later is incorrectly skipped
for the rest of the five-minute window.

Real-PostgreSQL regressions prove this for an initial write, for a later write
after an older value was persisted, and through the authenticated `/me` path.
In the route case the failed update produces 500; the immediate retry reaches
the route and returns 200 but still leaves `last_seen_at` unset. The exact
five-minute boundary and eight simultaneous callers behave correctly when
persistence succeeds.

### M7-03 — A signed token without an expiration is accepted

`SupabaseTokenVerifier` asks java-jwt to validate expiration if the `exp` claim
exists but never requires the claim itself. A token signed by the configured EC
key, with the required issuer, audience, key ID, and nonblank subject, verifies
successfully when `exp` is absent.

M7.3 explicitly requires expiry verification. A bearer credential without a
lifetime must therefore be rejected rather than treated as indefinitely valid.
The adjacent signed-token probes pass: the expected audience may appear among
multiple audiences, while missing issuer, audience, subject, or key ID is
rejected.

## Cleared and non-defect observations

- Five concurrent first requests for one authentication subject resolve to one
  internal user row. No duplicate or caller failure was reproduced.
- Username length, ASCII allowed characters, normalized lookup, database-backed
  case-insensitive uniqueness, one-time claiming, same-name reclaim, concurrent
  claims, and lost-name reservation satisfy M7.4. Trimming transport-level
  surrounding whitespace still stores only a valid canonical username and is
  not classified as a username containing spaces.
- Network exceptions and malformed successful refresh payloads leave the stored
  account intact. If replacement sign-up itself fails, the old durable session
  also remains intact. One authenticator serializes simultaneous first callers
  into one sign-up.
- A transient failure while persisting a rotated refresh response is recoverable
  by retrying the parent token. Supabase deliberately returns the active child
  for that unreliable-client case, and the realistic evaluator probe passes.
  The earlier hypothetical fixture that revoked the parent immediately was not
  retained as product evidence. See the official
  [Supabase session contract](https://supabase.com/docs/guides/auth/sessions).
- A cross-user refresh response would violate the authenticated Supabase API
  contract; defensive comparison could be added, but that hypothetical upstream
  breach is not a separate M7 defect.
- Process restart can cause one extra last-seen write, and wall-clock rollback
  could lengthen a process-local throttle window. Neither becomes continuous
  heartbeat behavior in the documented single-process topology, so these are
  robustness considerations rather than additional M7 failures.
- Accepting an RSA key supplied by the trusted JWKS is forward-compatible key
  handling, not algorithm confusion. The verifier still binds the JWT header
  algorithm to the actual public-key type; unsigned and mismatched-key tokens
  remain rejected.

## Scope conclusion

The development Supabase project remained available with anonymous users
enabled, sign-up enabled, and one EC/ES256 JWKS key. No key or service-role
secret is committed or recorded in evaluator output. The retained Android,
server, username, database, identity-route, and shared startup coverage remains
green, but it did not cover the three failures above.
