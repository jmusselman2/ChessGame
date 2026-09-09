# M8 — Friends: Remediation Report (`M8-01` and `M8-02`)

Written by Claude on `claude-autopilot`, after reproducing the findings in
`evals/M8/critic-report.md` and `evals/M8/test-report.md`. Those two reports are
the evaluator's record of what Codex observed and are left as written.

**This report covers `M8-01` and `M8-02` only.** `M8-03` is deliberately not
fixed here; the reason is in *`M8-03` — deferred* below, and its regression is
untouched and still failing on purpose.

## Baseline reconciliation

Codex evaluated M8 against `9941402`. At the start of this work
`claude-autopilot`, `origin/claude-autopilot`, `origin/codex-autopilot`, and
`origin/main` all resolved to `ba9cee1` — the evaluator checkpoints for M10
through M14, none of which touch the friends implementation. The M8 defects were
reproduced on that line before anything was changed.

The friends suites at baseline: **38 tests, 3 failing** — exactly the three
findings, and nothing else.

- `aNamelessCallerCannotCreateAOneSidedInvisibleFriendship` —
  `200 OK created an active friendship that Alex cannot list`
- `simultaneousReactivationsHaveOneWinnerAndOneDuplicate` —
  `expected: <1> but was: <2>`
- `removingWhileAStaleFriendCheckCreatesASeriesCannotLeaveRematchesEnabled` —
  `an active series that committed after removal must not remain eligible for
  rematches`

Run against the disposable local PostgreSQL from `docs/DEVELOPMENT.md` with
`TEST_DATABASE_URL` set, so no database-backed test was allowed to skip.

## `M8-01` — A nameless caller can create a one-sided invisible friendship

**Independently validated: legitimate.**

Authentication creates the internal user on that subject's first request
(`D006`), so an authenticated caller does not necessarily have a username yet.
`POST /friends` resolved and validated only the person being added — it never
asked whether the caller had a name of their own. A fresh authenticated subject
could therefore add `Alex`, receive 200, and commit an active friendship row.

The row is real in PostgreSQL and invisible from Alex's side: `GET /friends`
maps each friend through `toSummaryOrNull`, which drops a user with no name. So
Alex's list is empty while the database says the two are active friends. `D009`
makes a friendship mutual the moment it is made; a row only one side can see is
not that.

Fixed in `FriendRoutes`: `POST /friends` now refuses a caller with no claimed
username with **403 Forbidden** and `Claim a username before adding friends`,
before the request body is interpreted at all — this is about the caller's
standing, not about what they asked for. That completes the endpoint's answers:
400 malformed or yourself, 403 no name of your own, 404 no such user, 409
duplicate in either direction, 200 added.

**This is a user-facing behaviour change and is recorded as `D045`.** The
alternative — leaving the add alone and teaching friends lists, the dashboard,
history, and series summaries to render a nameless friend — was rejected there:
it invents a placeholder identity in four places for a user who has deliberately
not chosen one, and it would require `SeriesSummary` to stop demanding an
opponent name, which is a real invariant. Refusing at the one boundary that can
create such a row keeps the impossibility in a single place. 403 rather than 400
matches `POST /series`, which already answers 403 when the caller is not
entitled to what they asked for.

`DELETE /friends/{username}` is deliberately unchanged: a caller with no name
can no longer have a friendship to remove, so it already answers 404
`Not friends with ...`, and a second gate there would only restate that.

### Is the nameless-opponent handling now dead code?

Checked, because `D045` makes the case it defends unreachable through the API.
**It is kept, and it is not strictly dead.** The four sites are:

| Site | Shape | Verdict |
| --- | --- | --- |
| `FriendRoutes` `GET /friends` | `mapNotNull { ...toSummaryOrNull() }` | keep |
| `DashboardEntry.of` | `?: return null` | keep |
| `SeriesHistoryEntry.of` | `?: return null` | keep |
| `SeriesSummary.of`, `GameView.of` | `requireNotNull` | keep |

No *new* friendship row can have a nameless side after this fix: the target
always needed a username, the caller now does too, and a username is claimed once
and never released or changed (`PRODUCT.md`; `UsernameRoutes` refuses a second
claim). Every path into a series or a game runs through a friendship, so no new
series, dashboard row, history row, or game view can carry a nameless opponent
either.

What keeps it from being dead is that **the refusal is not retrospective**. Rows
written before this decision — including any in the beta's shared `ChessGame Dev`
database (`D035`) — keep whatever shape they have, and this handling is what
reads them without a crash or a 500. Deleting it would trade a defensive `null`
for a serialization failure on data nobody has audited. It stays as defence for
existing rows rather than as protection against a path that can still be walked;
`D045`'s consequences say exactly that, so a later reader knows it is deliberate
rather than forgotten.

## `M8-02` — Concurrent re-adds of a removed friendship both report success

**Independently validated: legitimate.**

`FriendshipRepository.add` read the row, saw `removed_at` set, and called
`reactivate`, whose UPDATE matched on the pair alone:

```
where user_a_id = ? and user_b_id = ?
```

Two concurrent re-adds can both read the row as removed. PostgreSQL serialises
their updates, but with no predicate about the row's state the loser's update
still matches the row the winner already revived, so `update` returns 1 and both
callers are told `Added`. `POST /friends` maps both to 200. One row survives, so
schema uniqueness is intact — it is the response contract that breaks. `M8.2`
specifies a duplicate response and the endpoint contract records 409 for a
duplicate in either direction, and these two overlapping operations cannot be
ordered into the advertised one-winner, one-duplicate behaviour.

Fixed in `FriendshipRepository.reactivate`: the UPDATE now also requires
`removed_at is not null`. Under READ COMMITTED the loser blocks on the winner's
row lock, then re-evaluates the predicate against the committed row, finds
`removed_at` null, and matches nothing. `reactivate` returns `null` for zero
rows affected, and `add` maps that to `AlreadyFriends`:

```kotlin
else -> reactivate(lower, higher)?.let(AddFriendResult::Added) ?: AddFriendResult.AlreadyFriends
```

The predicate is what makes the returned answer trustworthy rather than a report
of what the caller *intended*; a comment on `reactivate` says so, since the
predicate looks redundant to anyone reading it without the race in mind.

**The first-add races are untouched**, as the critic report directs: the
same-direction and reversed initial adds were already correct, both still pass,
and neither the insert path nor the `existing.isActive` short-circuit was
modified.

## `M8-03` — deferred, intentionally

`M8-03` (friend removal racing a stale friend check in `POST /series`, leaving an
`ACTIVE` series with `close_after_current_game = false` after the pair is
unfriended) is **not fixed here, and its regression is left present, unmodified,
and failing.** Verified explicitly: `git diff` on
`server/src/test/kotlin/com/jmussel/chessgame/server/friends/M8AdversarialTest.kt`
is empty, and
`removingWhileAStaleFriendCheckCreatesASeriesCannotLeaveRematchesEnabled` is the
one failure in the friends suites after this work.

It is deferred because it is not the same kind of bug as the two fixed here.
`M8-01` and `M8-02` are each contained in one function and change nothing about
how friendships and series relate to each other. `M8-03` is a missing
serialisation protocol spanning `POST /series`, `SeriesService`, and
`FriendshipRepository.remove`, and the critic report is explicit that a check in
either route alone is insufficient. Closing it means deciding **how the
friendship authorisation decision, series creation, and series closure agree
under concurrency** — which of `friendships` and `game_series` is the lock
holder, whether a series opens under a row lock on the friendship, or whether the
friend/series coupling is decoupled so that a series' eligibility for rematches
is derived from the friendship at rematch time rather than latched at creation.

That is a product decision about how friends and series are coupled, not a repair
of an oversight, and it belongs to a deliberate decision recorded in
`docs/DECISIONS.md` rather than to a fix folded into a friendship-table commit.
Fixing it here would also have put a `SeriesService` transaction change in the
same commit as two friendship-row fixes, which is precisely what makes a failed
re-evaluation hard to attribute.

## Findings rejected or reclassified

None. Both classes addressed here were reproduced and both are legitimate. The
evaluator's non-defect observations were re-read and left alone — the trimmed
surrounding whitespace, the initial-add races, the removal/series-mark rollback
atomicity, and the list-ownership and authentication boundaries all still pass
unchanged.

## Tests

**No evaluator test was deleted, skipped, weakened, or rewritten.** Both proving
regressions were correct statements of the requirement and are unchanged; both
now pass. The `M8-03` regression is unchanged and still fails.

Added: one `AddFriendTest` case,
`theEndpointRefusesACallerWhoHasNotClaimedAUsername`, which pins the specific
status (403) and the empty friendships table. The evaluator's regression asserts
only that the response is not a success, so the retained suite is what locks the
`D045` contract rather than merely the refusal.

The `M8-02` fix needed no new test beyond the evaluator's: the ordinary
reactivation path is already covered by `aRemovedFriendCanBeAddedBackOnTheSameRow`,
which proves the guarded UPDATE still revives the row when it really is removed.

### Results

| Run | Result |
| --- | --- |
| `friends.*` | 39 tests, 1 failed — the `M8-03` regression only |
| `friends.*` + `series.*` + `user.*` + `dashboard.*` + `history.*` | 190 tests, 1 failed — the `M8-03` regression only |
| Remaining server suites except `M10AdversarialTest`/`M12AdversarialTest` | BUILD SUCCESSFUL |
| `./gradlew build -x :server:test -x :android-app:testDebugUnitTest` | BUILD SUCCESSFUL |

The two excluded test tasks are the ones carrying out-of-scope evaluator
regressions (`M10-01`, `M12-01` in `:server:test`; `M14-01/02/03` in
`:android-app:testDebugUnitTest`). Everything else `./gradlew build` runs —
`ktlintCheck` across all modules, Android lint, both APKs, the server
distribution, and `game-core`'s tests — passed. `:server:test` was run in full by
enumeration apart from the two M10/M12 adversarial classes.

## Status

`docs/CODEX_EVALUATION_STATE.md` still carries `M8-03`, `M10-01`, `M12-01`, and
`M14-01` through `M14-03` as unresolved. CI is expected to remain red on those
regressions; this commit does not make the branch green and does not claim to.
M8 is not marked `PASS` — only Codex may do that, after re-evaluating `M8-01` and
`M8-02` from the beginning.
