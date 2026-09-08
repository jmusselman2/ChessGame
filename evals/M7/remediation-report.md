# M7 — Identity and Username: Remediation Report

Written by Claude on `claude-autopilot`, after independently validating the
findings in `evals/M7/critic-report.md` and `evals/M7/test-report.md`. Those two
reports are the evaluator's record of what Codex observed and are left as
written.

## Baseline reconciliation

Codex evaluated M7 against production baseline
`0da5f42b7deacde1aaa698f09d32b19a72e6df89`. `claude-autopilot` and `main` had
since moved on by four commits (`ebf9ebb`, `3548733`, `a8acce7`, `81fbab9` — the
M16.6 socket keepalive, the M5 and M6 evaluator checkpoints, and M17.2's build
naming). None of them touch the Android auth layer, the server auth package, or
`LastSeenTracker`.

The two evaluator commits carrying the M7 artifacts (`762f2ab`, `9a0b725`) were
cherry-picked onto `claude-autopilot` before anything was changed, and all three
defect classes were then reproduced on that line:

- `:android-app:testDebugUnitTest --tests com.jmussel.chessgame.auth.*` — 19
  tests, 2 failing (the `429` and `503` identity-replacement variants).
- `:server:test` for both M7 adversarial classes — 11 tests, 4 failing (the three
  last-seen rollback variants and the signed token with no `exp`).

The first server attempt failed for an unrelated reason: the local disposable
PostgreSQL container's `chessgame_test` database had corrupt heap pages
(`could not read blocks 43..43`). It was dropped and recreated, after which the
failures were exactly the six the evaluator reported. No database-backed test was
allowed to skip: `DATABASE_URL`, `TEST_DATABASE_URL`, `SUPABASE_URL`, and
`SUPABASE_ANON_KEY` were all set, so the five live Supabase tests ran too.

## M7-01 — A transient refresh failure replaces the anonymous account

**Independently validated: legitimate.**

`AnonymousAuthenticator.refreshOrCreate` caught `SupabaseAuthException` with no
discrimination and called `createSession()`. `SupabaseAuthClient` raises that one
exception for every non-2xx status, so a rate limit or an outage was
indistinguishable from a dead refresh token, and the successful sign-up that
followed overwrote the stored user id and both tokens through `store()`.

This is a product defect, not a robustness nicety. For an anonymous player the
session *is* the account (`D006`, `D008`): the replacement abandons the username
— permanently, because a name is never released — together with the friends and
the games behind it. `M7.2`'s own completion note scopes replacement to "if the
refresh token is dead", which a `429` does not establish. `D039` already draws
the same line for the Chess server's `401`.

Fixed in `AnonymousAuthenticator`: a refusal starts a new account only when it is
Supabase refusing the credential itself — the `400` (`invalid_grant`) or `401` it
answers an unknown, revoked, or already-consumed refresh token with. Everything
else is rethrown with the stored session untouched. `AppStartup` already renders
a propagated `SupabaseAuthException` as `Failed(canRetry = true)`, so the player
gets a retry that can actually work. Both entry points are covered by the one
change, because `currentSession()` and `renewedSession()` share
`refreshOrCreate`.

The rule is keyed on the status rather than the response body deliberately: the
status is what `SupabaseAuthClient` already exposes, `invalid_grant` arrives as
`400` regardless, and the body is not part of any contract this repository
states. `D043` records that choice and the alternatives.

Codex's two proving regressions are kept exactly as written; both now pass.

## M7-02 — A failed `lastSeenAt` write spends the throttle window

**Independently validated: legitimate.**

`LastSeenTracker.record` published `now` into its `ConcurrentHashMap` and only
then called `UserRepository.touchLastSeen`. When the write threw, the map kept
the new timestamp, so every request for the rest of the throttle was dropped
behind a value that was never stored. All three evaluator variants reproduced:
the initial write, a later write over an older stored value, and the
authenticated `/me` route.

`M7.5` requires meaningful activity to update `lastSeenAt`, accurate to within
the throttle. The throttle is a statement that the activity is *already
recorded*; when the write failed nothing was recorded, so the stored value could
drift arbitrarily far from the truth.

Fixed in `LastSeenTracker`: the claim is still what serialises concurrent callers
into one write, but it is now handed back when `touchLastSeen` throws, and the
exception is rethrown unchanged. The hand-back is conditional
(`remove(key, claimed)` / `replace(key, claimed, previous)`), so a later caller
that has already taken the window keeps it — that caller's claim is not the
failed one's to reverse. Nothing else in the class moved, so the exact-boundary
and eight-caller semantics Codex had already cleared are untouched, and the
route's existing 500 on a failed write is deliberately unchanged: that was not
what this finding was about, and the immediate retry now succeeds.

Codex's three proving regressions are kept exactly as written; all three now
pass.

## M7-03 — A signed token with no expiry is accepted

**Independently validated: legitimate.**

`SupabaseTokenVerifier` built its java-jwt verifier with issuer and audience but
never required `exp`. java-jwt validates expiration only when it can read one and
says nothing when it cannot, so a token signed by the configured key with the
right issuer, audience, key id, and a non-blank subject verified successfully
with no `exp` at all — a bearer credential with no end, which is the one property
a stolen token must not have.

`M7.3`'s acceptance criteria and its completion note both put expiry inside what
is verified, so this is the documented contract and not a new requirement. Every
token Supabase issues carries `exp`, so requiring it regresses nothing real; the
five live Supabase tests and the nine `AuthenticatedRouteTest` cases confirm it.

Fixed in `SupabaseTokenVerifier.verified`: after the signature, issuer, and
audience verify, a token whose parsed expiry is absent is rejected with
`InvalidTokenException`. `withClaimPresence("exp")` was considered and rejected —
it accepts an `exp` that is present but is not a number, which java-jwt then
declines to validate. Reading the parsed instant covers the missing claim and the
malformed one with one check.

Codex's proving regression is kept exactly as written; it now passes.

## Findings rejected or reclassified

None. All three classes were reproduced and all three are legitimate. The
evaluator's own non-defect observations were re-read and left alone:

- **The rotated-token recovery fixture Codex rejected stays rejected.** Its
  realistic replacement — a failed persistence of a rotated session, followed by
  a retry of the parent refresh token — passes both before and after this work,
  and nothing here assumes the parent cannot be retried. The remediation never
  needed that assumption: the persistence failure propagates from `store()`
  before anything is overwritten, exactly as it did.
- **Process restart costing one extra last-seen write, and a wall-clock rollback
  lengthening a process-local window,** remain robustness considerations in the
  documented single-process topology, not M7 failures. The window hand-back does
  not change either.
- **A cross-user refresh response** would breach the authenticated Supabase API
  contract; no defensive comparison was added, matching the evaluator's own
  conclusion.

## Related variants inspected and covered

Each class was searched for reasonably discoverable siblings rather than fixed
one case at a time:

- **M7-01.** `currentSession()` and `renewedSession()` both route through the one
  corrected method. `createSession()` and `store()` were already correct — a
  failed sign-up and a failed `SessionStore.write` both propagate with the stored
  session intact, which Codex's passing probes confirm. The generalised rule is
  locked by a new `AnonymousAuthTest` case sweeping `408`, `403`, `429`, `500`,
  `502`, `503`, and `504`, and by a second case proving `401` still starts a new
  account alongside the existing `400`.
- **M7-02.** `record` is the only mutation point; `reset()` is unconditional by
  design. Two new `LastSeenTest` cases lock the generalised rule: a window is
  spent by the write that succeeds and not by the one that failed (and the
  following burst is still throttled), and a failed write for one user leaves
  another user's window alone.
- **M7-03.** The adjacent claim checks — issuer, audience, subject, key id,
  algorithm binding, unknown key, unsigned token — were re-read and already
  reject. Two new `SupabaseTokenVerifierTest` cases cover the missing `exp` and
  the unreadable `exp` in the retained suite, and `TestTokens` gained a small
  `signed { }` escape hatch for token shapes `tokenFor` deliberately cannot
  produce.

## Tests

No evaluator test was deleted, skipped, weakened, or rewritten. The six proving
regressions were all correct statements of the requirement and are unchanged.

Added: two `AnonymousAuthTest` cases, two `LastSeenTest` cases, two
`SupabaseTokenVerifierTest` cases, and `TestTokens.signed`.

## Status

`docs/CODEX_EVALUATION_STATE.md` reads
`DEFECT FOUND — REMEDIATED, AWAITING RE-EVALUATION`. M7 is not marked `PASS`;
only Codex may do that, after this reaches `main` and it reevaluates M7 from the
beginning. M8 has not begun.
