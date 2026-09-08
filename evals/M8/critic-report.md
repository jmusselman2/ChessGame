# M8 — Independent Evaluation: Critic Report

Baseline: `99414028bfd3e8b89953dc549ada77130bb3ae62`

## Verdict

**DEFECT FOUND.** The complete M8 sweep found three legitimate unresolved
defect classes. No production code was changed, and M9 was not started.

## Baseline reconciliation

The prior M7 evaluator checkpoint (`17176ed`) is an ancestor of `origin/main`.
The two later commits on the evaluated line (`b032172` and `9941402`) concern
beta validation and game-command concurrency; neither changes the M8 friends
implementation. At evaluation start, `HEAD`, `origin/main`, and
`origin/codex-autopilot` all resolved to the baseline above.

## Complete finding batch

### M8-01 — A nameless caller can create a friendship the other side cannot see

Authentication creates an internal user on that subject's first request, before
the user has necessarily claimed a username. `POST /friends` validates and
resolves only the requested target; it never requires the caller to have a
username. A fresh authenticated subject can therefore add `Alex`, receive 200,
and commit an active friendship row.

The relationship is not mutually usable through the API. `GET /friends` maps
each stored friend through `toSummaryOrNull` and silently drops a user without a
name. The proving request leaves Alex's list empty even though PostgreSQL says
the two users are active friends. This contradicts M8.3's requirement that the
calling user's current friends are returned and its explicit claim that both
sides see each other; it also breaks D009's user-visible mutuality.

The current Android app normally routes a nameless user through onboarding, but
the authenticated server API is the authority and cannot trust that caller.
Later production readers deepen the impact: dashboard and history summaries
also discard a nameless opponent, while game summaries require an opponent name.
The remediation must prevent creation of an externally unusable friendship (or
otherwise make every required representation total) at the authoritative
boundary, not rely on the first-party screen flow.

### M8-02 — Concurrent re-adds both report that they restored the same friendship

Re-adding a removed friend first reads the row and then performs an unconditional
pair update setting `removed_at` to null. Two concurrent re-adds can both read
the removed row. PostgreSQL serializes their updates, but the second update's
predicate does not require the row still to be removed, so both repository calls
return `Added`. `POST /friends` maps both results to 200.

The final database contains one active row, so schema uniqueness is intact, but
the documented duplicate contract is not: M8.2 specifies a duplicate response
and the endpoint contract records 409 for a duplicate in either direction. The
two overlapping operations cannot be ordered into the advertised one-winner,
one-duplicate behavior.

The regression forces both requests past the stale removed-row pre-check before
letting either mutation finish. The adjacent initial-add races are correct under
the same deterministic PostgreSQL orchestration: same-direction and reversed
requests each produce one `Added`, one `AlreadyFriends`, and one row. The defect
is therefore limited to the removed-row reactivation path.

### M8-03 — Friend removal can race a stale friend check and leave rematches enabled

`POST /series` checks `areFriends` in one transaction and later creates the
series and first game through `SeriesService`. `FriendshipRepository.remove`
deactivates the friendship and marks only the pair's already-visible active
series in its own transaction. Nothing serializes those decisions.

The deterministic regression pauses a series insert after the route's friend
check. While it is paused, `DELETE /friends/Alex` returns 200 and commits the
removed friendship. The insert then commits an `ACTIVE` series with
`close_after_current_game = false`. PostgreSQL's final state is therefore an
unfriended pair with a live series still eligible for automatic rematches.

That directly violates M8.4, D013, PRODUCT.md, and ARCHITECTURE.md §17: removing
a friend must disable the next automatic rematch, and the friendship and active
series must not disagree. A check in either route alone is insufficient; the
friendship authorization decision, series creation/open, and removal need a
shared transaction/lock protocol that makes the committed invariant race-safe.

## Cleared and non-defect observations

- Exact normalized username lookup, case-insensitive hits, partial misses,
  invalid-name rejection, authentication, and the public-only response shape
  satisfy M8.1.
- Surrounding whitespace is deliberately trimmed by both the Android client and
  the add route before validating a username. This transport normalization does
  not create a username containing spaces and is not an M8 defect.
- Self-add, sequential duplicate, reversed duplicate, removed-row reuse, mutual
  repository state, list ownership, removal from either side, and authentication
  boundaries pass the retained PostgreSQL tests.
- Deterministic simultaneous first adds in the same and reverse directions
  resolve to one winner and one duplicate with exactly one committed row.
- If marking an existing active series fails, the friendship removal rolls back;
  after the injected database failure is removed, retry deactivates the
  friendship and marks the series. Transaction atomicity on that path is sound.
- Ordinary removal preserves the friendship row, current game, finished games,
  and unrelated pairs, and marks an already-existing active series. Later
  close/rematch behavior was inspected only as a caller needed to prove M8's
  invariant; M9–M13 were not evaluated.

## Scope conclusion

All M8 requirements and acceptance criteria were reconciled with the schema,
repositories, authenticated routes, retained tests, M8 implementation history,
and relevant later callers. The retained coverage remains green but did not
exercise nameless authenticated callers, concurrent removed-row reactivation,
or the removal-versus-series-creation window.
