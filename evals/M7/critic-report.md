# M7 — Independent Evaluation: Critic Report

Baseline: `0da5f42b7deacde1aaa698f09d32b19a72e6df89`

## Verdict

**DEFECT FOUND.** M7 does not yet satisfy its documented identity-continuity
and activity-tracking requirements. No production code was changed. M8 was not
started.

## Finding batch

### M7-01 — A transient refresh failure can replace the anonymous account

`AnonymousAuthenticator.refreshOrCreate` treats every non-successful refresh
represented by `SupabaseAuthException` as proof that a new anonymous account is
needed. `SupabaseAuthClient` uses that exception for transient HTTP failures as
well as rejected credentials. Consequently, a rate-limited (429) or unavailable
(503) refresh is immediately followed by anonymous sign-up, and the successful
replacement session overwrites the stored user ID and refresh token.

This loses continuity with the server-side user, username, and future games even
though the existing refresh credential was never shown to be invalid. The same
refresh-or-create path is also shared by session renewal. Independent mock-HTTP
regressions prove both realistic transient status variants and assert that no
sign-up request may occur.

### M7-02 — A failed last-seen write suppresses the next valid activity

`LastSeenTracker.record` advances its in-memory throttle timestamp before
`UserRepository.touchLastSeen` succeeds. If PostgreSQL rejects the write, the
advanced timestamp remains cached and a retry one second later is incorrectly
throttled for the remainder of the five-minute window.

Real-PostgreSQL regressions prove this for both the user's first last-seen write
and a later write after an earlier timestamp was successfully persisted. In
both cases the database correctly retains the old value after the injected
failure, but the immediate retry returns false and does not persist the new
activity.

## Cleared suspicion

Five simultaneous first requests for one verified authentication subject all
resolved successfully to the same database user. The widened insert race did
not produce duplicate identities or caller failures, so it is not classified
as an M7 defect.

## Scope conclusion

The retained token-verification, authenticated-route, username, last-seen, and
Android anonymous-authentication suites remain green, including live Supabase
anonymous sign-in and refresh coverage. Those passing paths do not exercise
transient refresh responses or failed database writes, which are the two
legitimate unresolved defects above.
