# M8 Critic Report

## Fresh assessment

**Provisional fresh verdict:** PASS

This section was written before consulting any retained M8 evaluation report.

### Scope and evidence

- Baseline: `f9411358d319d8501bfc58aa05470523a67bd6a8`
- Evaluator checkpoint entering M8: rewritten M7 at
  `2e3afb6e01f7a692d9b45822a65afaa24e2d2607`
- Requirements: `docs/BACKLOG.md` M8.1 through M8.4 and the applicable
  friendship decisions, including D045, D046, D047, and D053
- Production implementation: authenticated user lookup and friendship routes,
  `FriendshipRepository`, Exposed schema mappings, and database migrations
- Current callers: Android `ChessApiClient` lookup, add, list, and remove calls
- Verification: focused server and Android tests, complete server build, direct
  database-catalog inspection, current source/schema/caller audit, and device
  discovery

### Requirement assessment

| Requirement | Fresh result | Evidence summary |
| --- | --- | --- |
| M8.1 find user by exact username | PASS | The authenticated lookup normalizes a syntactically valid username, performs an exact normalized lookup, returns one public `UserSummary`, and distinguishes invalid input (`400`) from no match (`404`). Response models do not expose private authentication or activity fields. |
| M8.2 add friend | PASS | The authenticated add route requires both users to have usernames, forbids self-add and active duplicates in either direction, stores the pair in canonical order, and makes the friendship immediately mutual. Transactional insert/reactivation plus database ordering and primary-key constraints make initial, reversed, and reactivation races single-winner. A nameless caller receives `403` and cannot create an invisible one-sided friendship. |
| M8.3 list current friends | PASS | The repository selects only active, non-removed rows containing the caller and resolves the opposite participant. The result is mutual, removed friendships disappear for both users, and only public user summaries are returned. |
| M8.4 remove friend | PASS | Removal atomically marks the one canonical friendship row removed, keeps its history, and makes it absent from both users' lists. Current D053 authority supersedes the older close-series wording: removal must not update a series, game, table, or group, and automatic rematches continue. Failure-injection and commit-order tests confirm that separation. |

The schema directly enforces non-null participants, foreign keys to users, a
canonical `user_a_id < user_b_id` ordering check, and a composite primary key.
It retains `created_at`, status, and nullable `removed_at`; the MVP writes the
active state and retains the row for later reactivation rather than duplicating
the relationship.

The current source and tests also cover the highest-risk concurrency boundaries:
simultaneous same-direction adds, reversed adds, and simultaneous reactivations
all yield exactly one success and one duplicate response. Friendship removal
does not write series state, even if a series update is configured to fail, and
a series creation committing after removal remains open as required by D046 and
D053.

### Fresh findings

No confirmed production defect was found.

The initial `docker compose ps -q postgres` evaluator command returned no
container because the running Compose project was not discoverable from that
selector. The active `chessgame-postgres` container and its test database were
then identified from the live Docker inventory, and the catalog query completed
successfully. This was an evaluator discovery mismatch, not a production
failure.

### Live branch reconciliation

Before the M8 checkpoint, the published M1-M7 evaluator chain was rebased onto
`6d168190929c008caa5249b38909031109389ec2` (Android launch splash) and
`3e50441f6846de1213eb4fb38b6816bc4b720e34` (future-planning documentation).
The evaluator paused rather than silently mixing evidence. After the work was
unstashed, the user directed the run to resume on that published history while
retaining `f9411358d319d8501bfc58aa05470523a67bd6a8` as the recorded baseline.

The intervening diff was inspected in full. It does not change `server` or
`game-core`; the M8 production implementation is byte-identical. The 49 focused
server cases were rerun on the live rewritten tip and passed. Because the new
production code is Android-only, `:android-app:build --rerun-tasks` was also run
on that tip: all 521 unit tests, lint, debug/release compilation, and both APK
assemblies passed. The splash therefore introduces no known conflict with the
evaluation workflow or current M8 result.

## Historical comparison

After the fresh verdict was recorded, all retained M8 evaluation, remediation,
re-evaluation, and cleanup reports under
`evals/runs/2026-09-17-e2b3287/M8/` were reviewed. The original evaluation
found three production defects: a nameless caller could create a friendship
that the named side could not list (`M8-01`), simultaneous reactivations could
both report success (`M8-02`), and friend removal could race the then-existing
friendship check at series creation (`M8-03`).

The first two corrections remain present. `POST /friends` refuses a nameless
caller with `403` before writing a row, and reactivation conditionally updates
only a still-removed row, making a concurrent loser report the duplicate. The
post-comparison adversarial run passed all 6 deterministic cases, including the
unchanged proving cases for those defects and the already-correct same- and
reverse-direction initial-add races.

`M8-03` was legitimately found under the old series contract, then closed by
D046 removing friendship authorization from series creation. Current D053 goes
further and supersedes the remaining D013 removal coupling: unfriending now
affects the friends list only and must not mark, close, or otherwise mutate a
series. The retained adversarial scenarios have been transparently re-pointed
to that current authority. They prove that removal succeeds even when any
series update is rejected and that a series committing after removal remains
open. Both pass, and current M19.5 implementation and documentation agree.

The historical observation that transport-level surrounding whitespace is
trimmed was reconsidered and remains a non-defect: it does not create or look
up a stored username containing spaces. The reserved `PENDING` and `DECLINED`
schema states remain unwritable in the MVP, while all active reads/removals
filter explicitly and unexpected future states fail loudly rather than acting
as accepted friendships.

**Final M8 verdict: PASS.**
