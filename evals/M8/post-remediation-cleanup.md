# Post-remediation cleanup

Three independent, low-risk fixes on `claude-autopilot`, building on `a13cde5`.
Kept out of `evals/M8/remediation-report.md` deliberately: none of these change
what M8's findings were or how they were fixed, and folding them in would make
that report harder to re-evaluate against.

No M8 finding is re-opened. `D046` and `D047` stand. `M10-01`, `M12-01`, and
`M14-01`/`02`/`03` are untouched.

## 1. A fictional refusal in `ChessAppTest`

`aSeriesThatWillNotOpenLeavesThePlayerOnTheDashboardWithSomethingToRead` stubbed
`403 "Not friends with Alex"` from `POST /series`. `D046` deleted the friendship
check that produced it, so the test asked the app to render a response the server
can no longer make.

The test's subject is still worth having, and is unchanged: the app shows
`ChessApiException.explanation` verbatim for any non-2xx (`DashboardMessages`) and
has no opinion about the reason, so what is being proved is that a refused
`/series` reaches the player and leaves them on the dashboard.

Fixed by restubbing it as `404 "No such user"` — a refusal `POST /series` still
answers today, taken verbatim from `SeriesRoutes`. A KDoc on the test says the
refusal is a stand-in for any of them, and why it changed, so the next reader does
not have to work out why a real refusal is being faked.

The name was left alone: "a series that will not open" was already generic about
the reason and is still accurate.

**Choice of status, briefly.** Nothing between the mock and the assertion branches
on status — `isProbablyAsleep` treats every `ChessApiException` as "the server
answered", so `404`, `500`, and `503` all travel the same path. The choice is about
truthfulness, not behaviour, and `404 "No such user"` is a literal current response
string.

## 2. The removal response and what it can honestly claim

**The user-facing sentences needed no change. The documented guarantees did.**
This was checked rather than assumed, and the reasoning is the useful part.

`DELETE /friends/{username}` has two success sentences:

- `Removed X; your current game finishes first`, when a series was marked.
- `Removed X`, when none was.

The **marked** branch is fully accurate, and the reason is the partial unique index
`game_series_one_active_per_pair`: a pair can hold at most one `ACTIVE` series at a
time. If this removal marked one, no second one can have committed alongside it, so
there is nothing the sentence is leaving out.

The **unmarked** branch says nothing about series at all, so it promises nothing
that the `D046` race can falsify. Rewording it to hedge — "unless one was just
starting" — would corrupt the copy for every ordinary removal to describe a
concurrent-request window, which is worse than saying nothing.

What *was* wrong was the code's own documentation, which claimed a guarantee
`D046` had removed:

- `FriendshipRepository.remove`'s KDoc said the two writes in one transaction mean
  "the pair can never end up un-friended with a series that will keep making
  rematches". That is now false, and it is false by decision. Reworded to say what
  the transaction actually guarantees — the removal and the mark land together or
  not at all, for the series it *read* — plus the `D046` consequence stated plainly
  (a concurrently committing series is not seen, not marked, and deliberately left
  open), and the unique-index reasoning above.
- `RemoveFriendResult.Removed`'s KDoc said `seriesMarkedToClose` "says whether a
  series will close with it" — a claim about the future. Reworded to report what
  the removal *did*.
- A comment at the response site in `FriendRoutes` records why the unmarked branch
  deliberately says nothing.

**Other callers of the old guarantee:** the only other reader of
`seriesMarkedToClose` is `RemoveFriendTest`, which asserts it is `true` when a
series existed — still correct. Two places repeat the D013 promise in prose and
were left as they are: `ChessApiClient.removeFriend`'s KDoc, and the app's
confirmation dialog ("A game you are playing now will finish as normal — there just
will not be another one"). Both describe what the removal does in the ordinary
case, which is what a player needs to read before confirming; and under `D046`
"there will not be another one" was never an absolute anyway, since either side can
open a new series afterwards.

### Did `D046` need a Consequences update?

**No — it already says this.** `D046`'s consequences already carry:

> Because of the above, `RemoveFriendResult.seriesMarkedToClose` — and the sentence
> the endpoint builds from it — describes the series that existed when the removal
> ran, not any that commit alongside it.

The defect was that the code's own comments contradicted a consequence the decision
had already recorded. Adding a second bullet saying the same thing would make the
decision longer without making it truer, so the fix is to bring the code in line
with it. Noted here instead, which is what this report is for.

## 3. `--continue` in CI

`.github/workflows/ci.yml` now runs `./gradlew build --continue`.

**Workflow-only. No production or test code was touched for this item.**

The problem it solves was found across the two prior sessions: without the flag
Gradle stops at the first failing task, so on a red commit whether `:server:test`
or `:android-app:testDebugUnitTest` actually executes depends on scheduling. The
same broken branch reported five failures on one run, three on the next, and two
plus three on a third — so the outstanding count could not be read off any single
run, and a real fix could look like no progress.

Semantics for the green case are unchanged, and this was verified rather than
assumed: `./gradlew build --continue` on the currently-passing task set is
`BUILD SUCCESSFUL`. `--continue` does not run tasks that depend on a failed one and
does not change the build's verdict; it only stops Gradle abandoning independent
tasks it could still have run.

`docs/DEVELOPMENT.md` is updated to say what CI actually runs, since it claimed
`./gradlew build` verbatim.

## Verification

| Run | Result |
| --- | --- |
| `:android-app:testDebugUnitTest --tests ChessAppTest` | **BUILD SUCCESSFUL** |
| `:server:test` — every suite except `M10AdversarialTest` and `M12AdversarialTest` | **BUILD SUCCESSFUL** |
| `./gradlew build --continue -x :server:test -x :android-app:testDebugUnitTest` | **BUILD SUCCESSFUL** |

M8-01, M8-02, and M8-03 all still pass. `M10AdversarialTest`,
`M12AdversarialTest`, and `NetworkInterruptionTest` were not run and not modified —
`ChessAppTest` was selected by name so the Android task ran without
`NetworkInterruptionTest`.

## Still open

`M10-01`, `M12-01`, `M14-01`, `M14-02`, `M14-03`. CI stays red on those.
