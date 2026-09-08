# M8 — Independent Evaluation: Test Report

Baseline: `99414028bfd3e8b89953dc549ada77130bb3ae62`

## Evaluator coverage

Server `M8AdversarialTest` uses the disposable PostgreSQL evaluator service for
six probes:

- a nameless authenticated caller attempting to add a named user;
- simultaneous first adds in the same direction;
- simultaneous first adds in reversed directions;
- simultaneous reactivation of one removed friendship row;
- rollback and retry when active-series marking rejects a removal; and
- removal racing a series insert that already passed its friend check.

The concurrency tests use PostgreSQL advisory locks and observed database lock
waiters to force the intended interleavings. They do not infer a race from
timing or sleeps.

## Results

| Verification | Tests | Failed | Errors | Skipped | Result |
| --- | ---: | ---: | ---: | ---: | --- |
| Retained M8 lookup/add/list/remove | 41 | 0 | 0 | 0 | PASS |
| Adjacent series lifecycle/open/idempotency | 35 | 0 | 0 | 0 | PASS |
| Final M8 adversarial | 6 | 3 | 0 | 0 | DEFECT PROVED |
| Aggregate server run before final reactivation probe | 435 | 2 | 0 | 0 | EXPECTED FAIL |
| Relevant Android client/app/navigation | 156 | 0 | 0 | 0 | PASS |
| `game-core` | 394 | 0 | 0 | 0 | PASS |

The final adversarial failures are:

- `aNamelessCallerCannotCreateAOneSidedInvisibleFriendship` — POST returned 200,
  PostgreSQL stored an active row, and the named user's list omitted the caller;
- `simultaneousReactivationsHaveOneWinnerAndOneDuplicate` — both forced
  contenders returned `Added` instead of one returning `AlreadyFriends`; and
- `removingWhileAStaleFriendCheckCreatesASeriesCannotLeaveRematchesEnabled` —
  the active series committed after removal with its close marker false.

The other three adversarial probes pass. Both forced first-add variants return
one winner and one duplicate with one row, and an injected active-series mark
failure rolls the friendship mutation back before a successful retry.

The aggregate server run contained the then-current five evaluator probes plus
all 430 retained server tests. Its only failures were the two defect regressions
present at that point; all retained tests passed with no errors or skips. The
sixth, reactivation regression was then added and run with the complete final
adversarial class, where it failed deterministically as reported above.

Every database verification invocation set both evaluator URLs and used
`--rerun-tasks`. Flyway repeatedly cleaned and migrated the disposable
`chessgame_test` database on PostgreSQL 18.6; no database test skipped.

The combined Android/game-core invocation completed the test tasks and wrote
zero-failure XML results (156 Android and 394 game-core tests) but Gradle did not
exit afterward. It was interrupted after a bounded idle wait. The completed
test results are reported separately from that runner-exit issue.

Additional verification passed:

- `:server:assemble`;
- `:server:ktlintTestSourceSetCheck`; and
- `git diff --check`.
