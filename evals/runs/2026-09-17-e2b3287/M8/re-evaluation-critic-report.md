# M8 — Independent Remediation Re-evaluation: Critic Report

Re-evaluation baseline: `a4bb6999af4dea4665c832bebcaf6122a021e183`

## Verdict

**PASS.** M8-01 and M8-02 are fixed, and M8-03 is closed by the accepted
product decision `D046`, which removes the friendship-at-series-creation
requirement whose stale check created the reported race. No new M8 defect was
found. No production code was changed by this re-evaluation.

## Finding disposition

### M8-01 — closed

`POST /friends` now refuses an authenticated caller who has not claimed a
username with 403 before interpreting the requested friend. Both sides of every
new API-created friendship can therefore be rendered in the mutual friends
list. The original evaluator regression passes unchanged, and the retained
route test additionally pins the response and verifies that no row is written.

This boundary is complete: the requested friend must already be named because
lookup is by username, the caller is now required to be named, and usernames
cannot later be released or changed. Defensive null handling remains appropriate
for rows written before `D045`.

### M8-02 — closed

Reactivation now updates only a row whose `removed_at` is still non-null.
PostgreSQL re-evaluates that predicate after a competing updater releases the
row lock, so exactly one concurrent re-add changes the row and reports `Added`;
the loser matches zero rows and reports `AlreadyFriends`. The original
same-direction and reversed-direction concurrency regressions pass unchanged,
as do ordinary first-add, duplicate, remove, and revive paths.

### M8-03 — closed by superseding decision

The original finding was legitimate under the former contract: series creation
checked friendship in a separate transaction and could commit after a removal
that had no series to mark. `D046` deliberately removes that authorization
check and accepts that a direct API caller can open a series with any named
other user. With no friendship assertion at series creation, there is no stale
assertion to make atomic and no requirement that a concurrently committing
series be marked by removal.

Changing the regression's final assertion is justified by that explicit
requirement change, not by the implementation merely failing its old test. The
test retains the same deterministic interleaving and now proves the outcome
`D046` requires: removal commits, the later series commits, and that series
remains open. `OpenSeriesTest` independently pins the accepted direct-API
consequence with a 201 response for a non-friend.

## Requirement evaluation

- **M8.1 — username lookup:** exact normalized lookup, invalid-name handling,
  not-found handling, authentication, and response privacy remain intact.
- **M8.2 — add friend:** ordered-pair storage prevents self, duplicate, and
  reversed-duplicate rows; additions are mutual immediately; removed rows are
  revived once; and both participants in every new API-created row are named.
- **M8.3 — friends list:** only active, non-removed friendships are listed for
  the caller, from either side and in creation order. Removed, pending, and
  declined rows do not appear.
- **M8.4 — remove friend:** removal preserves the row and current game and marks
  the active series visible to its transaction to close after the current
  game. `D046` narrows the former concurrency promise: a series committing
  alongside removal is deliberately independent of friendship and remains
  open.

## D047 compatibility review

The new `friendships.status` column defaults existing and new rows to `ACTIVE`,
is non-null, and is constrained to `ACTIVE`, `PENDING`, or `DECLINED`.
Current friendship reads and removal require `ACTIVE` as well as a null
`removed_at`; reactivation restores `ACTIVE`. The reserved values have no
current writer or user-visible state machine. Migration and repository tests
cover the default, constraint, round trip, removal, and revival behavior.

## Carried findings

M10-01, M12-01, and M14-01 through M14-03 are unchanged by the M8 remediation.
Their evaluator regressions were rerun and still fail exactly as recorded; they
remain open and are not duplicate M8 findings.
