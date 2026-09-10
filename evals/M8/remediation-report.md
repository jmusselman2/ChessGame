# M8 — Friends: Remediation Report

Written by Claude on `claude-autopilot`, after reproducing the findings in
`evals/M8/critic-report.md` and `evals/M8/test-report.md`. Those two reports are
the evaluator's record of what Codex observed and are left as written.

**Part 1 of this report covers `M8-01` and `M8-02`, written when `M8-03` was
still deferred.** Part 2, appended in a later session, covers `M8-03` and a
forward-compatible friendship schema change. The *`M8-03` — deferred* section
below is left as written; Part 2 says what actually happened to it, and why the
answer was not the one that section anticipated.

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

---

# Part 2 — `M8-03`, resolved by deleting the check (`D046`), plus the friendship `status` column (`D047`)

Written in a later session, on `claude-autopilot`, building on `e94f477`.

## `M8-03` — the fix is to delete the check, not to lock it

**The finding was real. The remedy is not the one its framing implies.**

The critic report describes `M8-03` as a race: `POST /series` reads `areFriends`
in one transaction, `DELETE /friends` commits in another, and nothing serialises
them, so a series can commit moments after the friendship it just verified was
removed. It concludes that "the friendship authorization decision, series
creation/open, and removal need a shared transaction/lock protocol".

That conclusion follows only if the check has to exist. **It does not.** The
project owner's decision, recorded as `D046`: there is no server-side friendship
or acquaintance verification at series creation, now or going forward. The app's
invite UI only ever offers people the player already knows, and that selection is
the gate.

So the `areFriends` check in `POST /series` is **removed outright** — not locked,
not re-checked, not wrapped in a shared transaction. There is nothing left to
race once the check is gone.

Why this is the better answer, and not merely the cheaper one: the check was
never load-bearing. It re-decided on the server a question the app had already
answered, and the price of keeping it honest was a cross-aggregate invariant
between `friendships` and `game_series` needing a lock protocol across three call
sites. That is real concurrency machinery bought to defend a second opinion about
a UI affordance.

`D046` states the accepted cost in the decision itself rather than burying it: a
caller who bypasses the app and calls the API directly can open a series with
someone who is not their friend. It also names itself a **narrow, deliberate
exception** to `ARCHITECTURE` §7 ("the Android client is untrusted"), and says
why the exception is safe to make here — the concession is one relationship
assertion whose worst outcome is an unwanted series, while every canonical game
fact (position, version, result, turn) is still refused from the client exactly
as before.

### What happened to the `M8-03` regression test

**It did not pass trivially. It was re-pointed, and it passes now.**

This was checked empirically rather than assumed. With the `areFriends` check
deleted and nothing else changed,
`removingWhileAStaleFriendCheckCreatesASeriesCannotLeaveRematchesEnabled` **still
failed**, with its original message.

The reason is worth being precise about, because it is the whole question. The
test's *name* is about the stale check, but its *assertion* never was: it asserted
that an active series committing after a friend removal must come out marked
`close_after_current_game`. Deleting the check removes the mechanism the evaluator
used to build the scenario; it does not change the outcome the assertion demands.

That assertion is exactly the invariant `D046` abandons. Under the new decision an
unfriended pair holding an open series is an accepted state, not a violated one —
series creation is not tied to friendship at all. So the test was asserting a
requirement the product no longer has.

It is therefore **rewritten rather than retired**, keeping everything that was
still worth having:

- The deterministic orchestration is untouched — same advisory-lock trigger on the
  series insert, same interleaving, same proof that the removal commits inside the
  window and the series commits after it.
- Only the final assertion is inverted, from `assertTrue(closeAfterCurrentGame)` to
  `assertFalse(...)`, with the message naming `D046`.
- Renamed to
  `aSeriesCommittingAfterAFriendRemovalIsLeftOpenBecauseCreationNeverChecksTheFriendship`,
  and carrying a KDoc that says what it used to assert, what it asserts now, and
  why.

The point of keeping it is that it now **locks the decision**: anyone who
reintroduces a friendship gate or a locking protocol at series creation fails a
test that sends them to `D046` first.

One other retained test asserted the deleted behaviour directly —
`OpenSeriesTest.theEndpointRefusesSomeoneWhoIsNotAFriend`. It is likewise
re-pointed, to `theEndpointOpensASeriesWithSomeoneWhoIsNotAFriend`, asserting 201
and one series, so `D046`'s accepted cost is on the record as an assertion rather
than as an absence.

This is the only place in this remediation where an evaluator regression's
assertion was changed. It was changed because an authoritative product decision
removed the requirement behind it — not to make an implementation pass — and it
is called out here so the re-evaluation can judge that for itself.

## The friendship `status` column (`D047`)

`friendships` gains `status text not null default 'ACTIVE'`, constrained to
`ACTIVE`, `PENDING`, `DECLINED`, in `V2__friendship_status.sql`.

**Nothing about MVP behaviour changes.** Every row written today is `ACTIVE`;
there is still no accept step (`D009`). `PENDING` and `DECLINED` are reserved for
a later approval-toggle setting and are never written.

This is a deliberate, recorded exception to the project rule against building for
hypothetical needs. `D047` gives the reasoning: the cost of adding this later is
not one migration but a migration plus a retrofit of every read that currently
equates "row exists and is not removed" with "these two are friends", where a
missed reader is a `PENDING` friendship silently behaving as an accepted one. What
is bought now is a column and a predicate — no state machine, no endpoint, no
branch MVP code can reach.

Wired through as:

- `FriendshipsTable.status`, and `StoredFriendship.status`.
- `isActive` is now `removed_at is null && status == ACTIVE`, so a future
  `PENDING` row cannot leak into a friend list the day the value first appears.
- `friendsOf` and `remove` filter on `status = ACTIVE` as well as
  `removed_at is null`, for the same reason.
- `insert` writes `ACTIVE` explicitly rather than leaning on the column default;
  `reactivate` restores it alongside clearing `removed_at`.
- `add`'s dispatch is now explicit that only a *removed* row can be revived. A row
  that is neither active nor removed hits `error(...)` rather than a
  plausible-looking `AlreadyFriends` — unreachable today, and when `PENDING`
  becomes writable the omission announces itself instead of hiding. That choice is
  in `D047`.

**Deliberately not extended** to series, participants, or membership. Tables and
multi-participant series do not exist in this codebase — it is strictly
two-player, keyed by `white_user_id`/`black_user_id` — and that concept belongs to
the future platform work (`D044`).

`add column ... not null default` does not rewrite the table on PostgreSQL 11+,
and existing rows (including the beta's, `D035`) become `ACTIVE`, which is what
they already meant.

## Documents corrected

`D046` and `D047` are the decisions. Lower-precedence documents asserted things
that `D046` makes false, and were corrected rather than left to contradict it:

- `ARCHITECTURE` §7 — the untrusted-client rule now names its one exception.
- `ARCHITECTURE` §15 — the friendship model gains `status`.
- `ARCHITECTURE` §16 — series creation does not check friendship.
- `ARCHITECTURE` §17 — a series committing concurrently with a removal is not
  marked.
- `BACKLOG` `M9.1` — its completion note recorded "403 for someone who is not a
  friend", which the endpoint can no longer answer.
- `BACKLOG` `M8.4` — its "so the two can never disagree" claim is narrowed to the
  series that exists when the removal runs.

## Tests

Added: `InitialSchemaTest.aFriendshipIsActiveWithoutBeingAskedToBe` and
`aFriendshipCannotHaveAStatusThatIsNotOneOfTheThreeReserved` (the default and the
check constraint, at the schema);
`AddFriendTest.aFriendshipIsStoredActiveAndComesBackActiveAfterBeingRevived` (the
repository round-trip, including that reviving restores `ACTIVE`).

Changed: the two assertions described above, both because `D046` removed the
requirement they encoded.

Deleted or skipped: none.

### Results

| Run | Result |
| --- | --- |
| `friends.*` + `series.*` + `db.*` | **BUILD SUCCESSFUL** |
| `dashboard.*`, `history.*`, `user.*`, `auth.*`, `game.*` (less M10), `realtime.*` (less M12), `ApplicationTest`, `DeploymentTest`, `ServerLoggingTest` | **BUILD SUCCESSFUL** |
| `./gradlew build -x :server:test -x :android-app:testDebugUnitTest` | **BUILD SUCCESSFUL** |

All six `M8AdversarialTest` cases pass, including `M8-01` and `M8-02` from Part 1.
`M10AdversarialTest`, `M12AdversarialTest`, and `NetworkInterruptionTest` were not
run and not modified.

## Status after Part 2

M8's three findings are all addressed: `M8-01` and `M8-02` fixed in `e94f477`,
`M8-03` resolved by `D046`. M8 is still not marked `PASS` — only Codex may do
that, after re-evaluating.

`M10-01`, `M12-01`, and `M14-01`/`02`/`03` remain open, and CI stays red on them.
