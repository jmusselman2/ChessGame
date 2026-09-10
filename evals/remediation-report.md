# Remediation of the Six Carried Evaluation Findings

**Date:** 2026-09-10
**Branch:** `claude-autopilot`
**Findings closed:** `M10-01`, `M12-01`, `M14-01`, `M14-02`, `M14-03`, `M18-01`

Every evaluator regression named below passes **unmodified**. No assertion was
weakened, re-pointed, or deleted. Production code changed for five findings and
project documentation changed for the sixth, which was a documentation defect.

---

## M10-01 — a refresh could combine an old game row with new move history

**Where:** `server/.../db/GameRepository.kt`
**Regression:** `M10AdversarialTest.aRefreshCannotMixAnOldGameRowWithNewMoveHistory`
**Decision:** `D057`

A game lives in two tables and READ COMMITTED gives every statement its own
snapshot, so `load` could take the row from before a competing `save` and the
history from after it — version 0 and the initial position, carrying `e2-e4`.

`load` now reads the row, reads the history, and **re-reads the version**. Equal
means nothing committed in between and the pair belongs to one state; changed
means a save landed, and the read is retaken against what that save left. Three
attempts, then a fall back to the existing locked `loadForUpdate`, which cannot
be torn — reaching that needs a commit inside every attempt, so it bounds the
loop rather than being a path anyone expects to run.

The regression asserts the *newer* coherent state (version 1, the played
position and history), which the retry produces: attempt 1 sees 0 → 1 and is
discarded, attempt 2 reads the committed post-move state. `loadForUpdate` keeps
its row lock unchanged; a mutating command still must not decide against a
snapshot that can move under it (`M16.7`).

Considered and rejected: `REPEATABLE READ` for the read (correct, but answers
with the *older* state and changes the semantics of a shared `transaction`
helper), `FOR SHARE` on every display read (makes a board refresh block a move
trying to commit), and a `games`/`moves` join (atomic, but returns the game row
once per ply and costs the readable row/history mapping).

## M12-01 — one stalled socket blocked every later recipient and the response

**Where:** `server/.../realtime/RealtimeHub.kt`
**Regression:** `M12AdversarialTest.aStalledConnectionCannotPreventAnotherUserReceivingTheUpdate`
**New coverage:** `RealtimeFanOutTest` (4 cases)
**Decision:** `D058`

`publish` awaited each `send` in turn and dropped a connection only when it
*threw*. A socket that neither throws nor completes therefore starved every
recipient behind it, and — because the game routes await `announce` before
responding — withheld the HTTP response to a command that had already committed.

`publish` now sends to every connection concurrently, one child coroutine each,
each under `withTimeout(realtimeSendTimeout)` (5 seconds). A throw or a timeout
drops that one connection and nothing else. Genuine cancellation of the
publishing request is rethrown rather than filed as a dead socket, so a cancelled
publish unsubscribes nobody — the ordering of the `catch` clauses
(`TimeoutCancellationException` before `CancellationException`) is what keeps
those two apart. The deadline is a `RealtimeHub` constructor property so a test
can assert what it does without waiting five seconds for it.

`RealtimeFanOutTest` adds what the evaluator regression does not reach: that a
stalled connection is dropped *at* its deadline and publishing returns at all;
that every connection is sent to at the same time (three connections whose sends
only complete once all three have been reached — a sequential fan-out could never
get there); that a connection failing outright still leaves the others delivered
and kept; and that cancelling a publish leaves its connections subscribed.

Considered and rejected: publishing on an application-scoped coroutine outside
the request (removes the response coupling entirely and is probably where this
ends up under real load, but a larger lifecycle change than the defect calls
for), a bounded per-connection outbound queue (the general slow-consumer answer,
unjustified at two players per game), and aligning the deadline with
`webSocketPongTimeout` (that timeout answers "is the connection dead", a
different question).

## M14-01, M14-02, M14-03 — three client ordering defects

**Where:** `android-app/.../app/ChessAppViewModel.kt`
**Regressions:** `NetworkInterruptionTest.anUpdateForAGameStillOpeningIsNotDropped`,
`.aDelayedCommandResponseCannotOverwriteANewerReload`,
`.aCompletionRefreshCannotBeOverwrittenByAnOlderDashboardRead`
**New coverage:** `StaleResponseOrderingTest`
**Decision:** `D059`

Analysed together, as asked: the server's state is version-ordered (`D021`) but
the client had no ordering of its own, so three legal request orderings each
produced a screen the server never described. One rule per surface:

**Which game is on screen.** `shownGameId` reads the id from `Loading`, `Ready`
*or* `Failed`. `onRealtimeMessage` asks it instead of asking `Ready` alone, so a
`game-updated` for a game still opening reloads that game rather than looking
like news about a different one and being answered by a read decided before the
move (`M14-01`). `reloadGame` now shares the same accessor.

**Forwards only, by canonical version.** `show` refuses to install a same-game
view whose `version` is lower than the one on screen: that is a late answer to a
question the screen has moved past, and installing it takes the opponent's move
back off the board (`M14-02`). Its *message* is still shown and `submitting` is
still cleared — the player asked for something and is owed the outcome, which is
what `StaleResponseOrderingTest` locks: a command that fails after a newer reload
reports itself without rolling the board back.

**Dashboard reads ordered by issue number.** `fetchDashboard` numbers each read
as it is issued and discards an answer outright — `loading` included — if a
later-issued read has already landed. `followSeries` no longer replaces
`dashboardJob` with an uncoordinated fetch: it goes through the same
`beginDashboardLoad` loop as `loadDashboard`, so both callers share one
discipline (`M14-03`).

The completion refresh is deliberately **not** queued behind a dashboard read
already in flight. That read was decided before the game ended and cannot contain
the rematch, so queueing would leave the player on "finding out what happens
next" for the whole of someone else's request — a cold start, at worst. Freshness
ordering, not queueing, is what stops the older answer landing on top afterwards.
Request-issue order is the only ordering the dashboard has, since unlike a game it
carries no version; giving it a server-side version is the principled alternative
and was rejected as a server change to fix a client ordering bug.

## M18-01 — the review claimed a second physical device M17 never recorded

**Where:** `docs/PLATFORM-REVIEW.md`, `docs/BACKLOG.md` (`M18.1` completion note)
**Regression:** `evals/M18/M18DocumentationRegressionTest.ps1`

Resolved on the evidence, in the direction the evidence pointed. Searched for a
record of the project owner's device: the `M17.1` and `M17.2` sections,
`ARCHITECTURE.md` §29, `MVP.md`, and the `M17.1` commit `9f63065` (message and
diff). None identifies it. What `M17.1` records is one physical device — the beta
tester's, which they installed the signed APK on themselves and played a game
through on — and one detailed two-device play-through, 2026-09-03, which ran on
the `ChessPlayer1` and `ChessPlayer2` **emulators**. That is consistent with the
acceptance criteria, which ask for *one* real user on a physical device and say
explicitly that emulator testing alone would not close the task.

So both overstated sentences now say what the record supports — two real people
played a real game — and both carry a dated correction naming `M18-01`, what was
searched, and why the conclusions are unaffected: none of the concepts the review
calls proven rested on a second physical device. The narrower summaries in
`ARCHITECTURE.md` and `MVP.md` were already accurate and were left alone. No
testing evidence was invented and no device claim was preserved.

Two stale `GameRepository.kt` line links in `docs/PLATFORM-REVIEW.md` were
repointed while the file was open, along with one in `RealtimeHub.kt`, since the
remediation moved the lines they name.

---

## Verification

| Verification | Result |
| --- | --- |
| `M10AdversarialTest` | PASS (was the proof of M10-01) |
| `M12AdversarialTest` | PASS (was the proof of M12-01) |
| `NetworkInterruptionTest` — the three M14 cases | PASS (were the proof of M14-01/02/03) |
| `evals/M18/M18DocumentationRegressionTest.ps1` | PASS |
| `RealtimeFanOutTest`, `StaleResponseOrderingTest` (new) | PASS |
| Server suite | PASS |
| Android JVM suite | PASS |
| `game-core` suite | PASS |
| `.\gradlew.bat build` | BUILD SUCCESSFUL |
| `git diff --check` | clean |

Decisions recorded: `D057` (stable display read), `D058` (realtime fan-out),
`D059` (client result ordering).
