# Chess MVP — Implementation Backlog

## Status Values

Use exactly one:

```text
TODO
IN PROGRESS
BLOCKED
DONE
```

Do not use Markdown checkboxes as a second status system.

Work in milestone order unless a dependency explicitly permits otherwise.

Each task must satisfy its acceptance criteria and verification requirements before being marked `DONE`.

## How the Backlog Is Organised

This file holds the backlog's rules, its index, and every milestone still being
worked, in full. Finished milestones are archived under `docs/backlog/`, grouped by
phase, one file per group, named by its range with two-digit milestone numbers so
the files sort in order (`M01-M05.md`, `M21.md`). Task `M19.9` is in
`docs/backlog/M18-M20.md` (`D093`). Archived milestones keep their own
`# M<n> — Title` headings. This file and the archives share `BACKLOG`'s rank in
`CLAUDE.md`'s precedence list.

- **Completing a task:** mark it `DONE` and add its completion note here.
- **Adding a milestone:** add it to this file, after the last active one, and its
  row to the index as `Active`.
- **Finishing a milestone:** in the commit that marks its last task `DONE`, move it
  unchanged into a new archive file in `docs/backlog/`, named by its range
  (`M22.md`, or `M22-M24.md` when several finished milestones go together), and
  point its index row there as `Done`. Archive files are never added to or renamed,
  so links into them keep working. Fix relative links in the moved text: the
  archive sits one directory deeper.
- The backlog holds only scheduled work. Work that is not scheduled lives in
  `docs/FUTURE.md` (`D072`).

## Milestone Index

| Milestone | Title                                                       | Where                                       | State  |
| --------- | ----------------------------------------------------------- | ------------------------------------------- | ------ |
| `M1`      | Repository and Build Bootstrap                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M2`      | Chess Domain Model                                          | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M3`      | Chess Legal Move Engine                                     | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M4`      | Undo Semantics                                              | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M5`      | Local Android Chess                                         | [`backlog/M01-M05.md`](backlog/M01-M05.md)  | Done   |
| `M6`      | Server + PostgreSQL Foundation                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M7`      | Identity and Username                                       | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M8`      | Friends                                                     | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M9`      | Game Series                                                 | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M10`     | Authoritative Game Command API                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M11`     | Authoritative Undo                                          | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M12`     | Realtime                                                    | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M13`     | Game End and Automatic Rematch                              | [`backlog/M06-M13.md`](backlog/M06-M13.md)  | Done   |
| `M14`     | Android Multiplayer Client, Dashboard, and History          | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M15`     | Beta Deployment Environment                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M16`     | Hardening                                                   | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M17`     | Friend Beta                                                 | [`backlog/M14-M17.md`](backlog/M14-M17.md)  | Done   |
| `M18`     | Post-Chess Architecture Review                              | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M19`     | Deck-Builder Platform Generalization                        | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M20`     | Multi-Game Rules Architecture                               | [`backlog/M18-M20.md`](backlog/M18-M20.md)  | Done   |
| `M21`     | AI Opponent, Local Game Persistence, and Game-Screen Polish | [`backlog/M21.md`](backlog/M21.md)          | Done   |
| `M22`     | Play and Social Improvements                                | [below](#m22--play-and-social-improvements) | Active |

The archives, by phase: `M01-M05.md` local chess; `M06-M13.md` the server and online
play; `M14-M17.md` the multiplayer client through the friend beta; `M18-M20.md` the
platform review and generalization; `M21.md` the AI opponent and local games.

## Task Selection Order (Autonomous Mode)

When running the continuous autonomous workflow (`docs/AUTONOMOUS-DEVELOPMENT.md`),
select the next task deterministically:

1. Consider only the milestones in this file (the index marks them `Active`),
   and in them only tasks with `Status: TODO`. Archived milestones are finished.
2. Exclude any task whose `Depends on` tasks are not all `DONE`.
3. Exclude any task marked `BLOCKED` and any task that would require a
   documented stop condition.
4. Of the remainder, pick the one with the lowest milestone number, then the
   lowest task number.
5. Mark it `IN PROGRESS` before editing code.
6. Mark it `DONE` only after its required **local** verification passes
   (implementation + acceptance criteria + `./gradlew build`).

### `DONE` is not the same as being allowed to start the next task

- `DONE` = the task's implementation and local acceptance criteria passed.
- The autonomous workflow may **not** select or begin the next task until the
  pushed `claude-autopilot` commit has passed its **required GitHub Actions
  run**. A green remote CI run is the branch-level progression gate; `DONE` is
  the task-level implementation gate. A task can legitimately be `DONE` while
  the branch is still waiting on CI. A commit that changes Markdown files only
  does not wait for its run (`D094`).
- For a task whose acceptance criteria require GitHub Actions itself to run
  successfully (for example `M1.7`), do **not** mark it `DONE` until that CI run
  has actually succeeded — the successful run is part of that task's acceptance
  criteria, not only the progression gate.
- If remote CI cannot be verified (GitHub CLI unavailable, unauthenticated, or
  no repo access), stop per the Stop Conditions in
  `docs/AUTONOMOUS-DEVELOPMENT.md`. Do not silently skip remote CI.
- Claude pushes `claude-autopilot` only. The owner brings `main` and
  `codex-autopilot` up to date by hand (`D094`).

Milestone boundaries are **not** stopping points. After a task is `DONE` and its
pushed commit's required CI is green, immediately select the next one,
continuing into the next milestone.

A single milestone-level task with no sub-tasks (for example `M2.1` inside a
milestone that only has `M2.1`, `M2.2`) is selected the same way.

---

# M22 — Play and Social Improvements

Formerly tier 1 of [`FUTURE.md`](FUTURE.md) ("Low cost, high value"): `F38`, `F1`,
`F2`, `F39`, `F3`, `F4`, `F6` and `F41`. The project owner scheduled them on
2026-10-14 and later chose the implementation sequence below; it is no longer merely
the order in which the future-work tier ranked them. Their `F` IDs are retired
(`D072`).
Username changes were scheduled at the same time as `M22.9`, then returned to `F5`
under `D098` because they depend on real authentication (`F13`).
`F39` became three tasks, so each has a single testable outcome. It is post-MVP work
and does not change the MVP's definition of done.

The owner resolved the original M22 product decisions on 2026-10-14
(`D095`–`D100`) and the retained-continuation correction on 2026-10-16 (`D102`).
No remaining task is decision-blocked. Larger tasks may still be split during
implementation, as `M21` was.

| Task     | Outcome                                             | Depends on | Formerly | Status    |
| -------- | --------------------------------------------------- | ---------- | -------- | --------- |
| `M22.1`  | At most one active series per party                 | None       | `F38`    | `DONE`    |
| `M22.2`  | Matching play advances the retained Redo continuation | M21.21   | —        | `DONE`    |
| `M22.3`  | Draw offers by agreement                            | M22.2      | `F2`     | `TODO`    |
| `M22.4`  | Win/loss/draw record per friend                     | None       | `F1`     | `TODO`    |
| `M22.5`  | Detailed profiles                                   | M22.4      | `F6`     | `TODO`    |
| `M22.6`  | Standard algebraic notation in `chess-core`         | None       | `F39`    | `TODO`    |
| `M22.7`  | Every move list and move wording in SAN             | M22.6      | `F39`    | `TODO`    |
| `M22.8`  | Turning automatic rematches off by hand             | None       | `F4`     | `DONE`    |
| `M22.10` | Online ply-by-ply review                            | M22.7      | `F41`    | `TODO`    |
| `M22.11` | Board coordinates                                   | None       | `F39`    | `TODO`    |
| `M22.12` | Drag-and-drop moves                                 | M22.2      | `F3`     | `TODO`    |

Every task's verification includes, besides its own targeted tests:

- the affected modules' tests (`.\gradlew.bat :chess-core:test`,
  `:chess-app:testDebugUnitTest`, or `:server:test` with `TEST_DATABASE_URL` and
  `--rerun-tasks` for server work);
- when a task adds or changes a Compose/device test, compile it with
  `.\gradlew.bat :chess-app:assembleDebugAndroidTest` and run the relevant test
  class on one provisioned device or emulator with
  `:chess-app:connectedDebugAndroidTest`, exactly as `DEVELOPMENT.md` describes;
  `build` alone does not compile or run `androidTest`;
- `.\gradlew.bat build`;
- `git diff --check`.

---

## M22.1 — At most one active series per party

**Status:** DONE

**Depends on:** None

**Formerly:** `F38`

### Objective

The project owner decided the rule in `D095`: a party, meaning the exact set of
a series' participants, has at most one active series. For chess, two people never
have two games with each other at once. Another series may start only once the
party's series has ended.

It removes an ambiguity players can already reach. Parallel series against one
opponent cannot be told apart: the dashboard shows each as the opponent's name and
the same detail ("White • Move 2"), History heads each with the same name, and the
Friends row links to only one of them (`DashboardSections.friends`).

### Decisions

- A forward-only migration keeps the newest active series for each chess pair,
  ordered deterministically by `created_at DESC, id DESC`, and deletes every older
  conflicting series and all of its dependent data. Existing data is not preserved
  merely for migration compatibility.
- Play opens the one active series or creates it when none exists. The series offer
  and `another=true` behavior are removed. An obsolete client that sends
  `another=true` is refused with an update-required conflict; no compatibility path
  may create a parallel series.
- Leave series ends both the series and its current game atomically. If any move has
  ever been accepted, the game ends as a resignation by the leaver. A game in which
  no move has ever been accepted is deleted without a result. No closed series has
  an unfinished game and no rematch follows.
- `games.has_started` is the durable fact behind “ever accepted”: it becomes true in
  the same transaction as the first accepted ordinary move and never becomes false,
  including after Undo or Redo. The migration backfills it from durable evidence of
  an accepted move (audit events, active history or redo history); absent evidence,
  it remains false.
- Every transaction that may end a game and settle its series locks the series row
  before the game row. Leave uses that same order. The shared lock helper and order,
  not timing assumptions, prevent a Leave/game-ending-command deadlock.

### Acceptance Criteria

- `D095` supersedes `D053` rule 1 (parallel series) and the game-preservation
  behavior in `D068`. `D053` rule 2, that
  unfriending affects the friends list only, stays. `CLAUDE.md`'s *Product Rules*
  and `PRODUCT.md` *Game Series* say the same; `AGENTS.md` is updated with the code
  because it describes what exists rather than planned behavior.
- A forward-only migration deletes older conflicting active series and their
  dependent rows, then enforces the invariant in the database for chess only. The
  enforcement is a trigger (or equivalently strong database mechanism) that locks
  the table row and rejects a second `ACTIVE` chess series for it; it does not impose
  a cardinality rule on future game types.
- Play always opens the pair's series: the existing one if there is one, otherwise a
  new one. `PlayOffer`, `startAnotherSeries` and the series offer are removed. An
  obsolete request for another series is refused and never creates one.
- Leave is idempotent and participant-only. It closes the series and either records
  the leaver's resignation in a started game or deletes a never-started game, in the
  same locked transaction. A leave racing a game-ending command produces one final
  result, a closed series and no rematch.
- A game with all moves undone still has `has_started = true`; leaving it records a
  resignation rather than deleting it.
- The dashboard, Friends row and History each show at most one active series per
  opponent.
- The Deck Builder's uniqueness over a table's participant set is not built here. It
  is designed with `F26`, not before (`D044`).
- **Tests:** server tests for the chess-scoped constraint, deterministic destructive
  migration of parallel series, Play with and without an existing series, the
  obsolete request, leaving a started game, move → Undo to empty → Leave, leaving a
  zero-move game, repeat Leave and a bounded leave/game-end race that completes
  without deadlock; client tests for Play, the removed offer and the final result
  after Leave.

### Completion

Completed 2026-10-17. `V14__one_active_chess_series.sql` adds `games.has_started` (set by
`GameRepository.save` for a `MOVE`, never cleared; backfilled from active moves, a Redo path
or a move audit event), deletes every older active chess series per table by
`created_at DESC, id DESC` with its games, moves, seats and events, and adds the chess-only
`game_series_one_active_chess_series` trigger, which locks the table row. It also ends the
unfinished games `D068` had left in closed series, as a Leave now would: a started game
becomes the `SeriesLeft` actor's resignation (`PlayerResigned` and `GameEnded` recorded),
and an unstarted one, or a started one with no recorded leaver, is deleted. `POST /series`
answers `201` (started) or `200` (opened); `?another=true` is a `409` asking for an update.
`SeriesService.leave` resigns or deletes the current game in the series-locked
transaction, and `SeriesService.lockGameWithSeries` makes moves, claims and resignations
lock the series row before the game row. The dashboard lists active series only;
`GameView` carries `hasStarted` for the leave warning. Android lost `PlayOffer`,
`SeriesOpening` and `startAnotherSeries`; leaving a started game shows it resigned with no
next game, leaving an unstarted one closes its screen, and the other player's screen says a
deleted game was removed. Verified with `:server:test --rerun-tasks` against PostgreSQL
(671 tests, including the new `OneActiveChessSeriesMigrationTest`, `SeriesExitTest`'s
leave/game-end race and the route tests), `:chess-app:testDebugUnitTest`,
`:chess-app:assembleDebugAndroidTest`, `.\gradlew.bat build` and `git diff --check`. No
Compose or device test changed, so none was run on a device.

---

## M22.2 — Matching play advances the retained Redo continuation

**Status:** DONE

**Depends on:** M21.21

### Objective

Correct `M21.21`/`D092`'s branching rule. Manually playing the next recorded future
action currently clears the entire Redo path even when it exactly matches that
action. Under `D102`, matching play advances through the retained continuation;
only divergent play creates a new continuation and clears the remaining future.

The retained continuation is an ordered suffix of structured actions and recorded
states. It is not merely a stack of Undo operations: explicit Undo and Redo keep
their one- or two-ply operation boundaries, while matching manual play advances one
structured action at a time.

### Acceptance Criteria

- **Exact matching.** A submitted action matches only when its canonical structured
  identity and complete recorded predecessor state match the next retained action.
  For chess the identity is `from`, `to` and promotion. SAN, player-facing wording,
  or visual board equality is never canonical identity. Castling rights, en passant,
  halfmove state, repetition state, side to move and every other canonical field are
  part of the predecessor comparison.
- **Matching advances.** An accepted matching action appends/reactivates that recorded
  action, consumes only it, preserves every action still ahead, and restores its
  recorded resulting snapshot without executing the old action through current rules.
  The consumed action is not still redoable. Consuming the final retained action leaves
  no Redo available.
- **Divergence branches.** An accepted legal action that does not match the next
  retained action starts a genuine new continuation and clears the entire remaining
  suffix atomically.
- **Operations and actions stay distinct.** Explicit Redo continues to restore the
  complete accepted Undo/takeback operation from `D092`. If matching manual play
  consumes the first action of a retained two-ply operation, its recorded reply remains
  as the next one-action continuation. No action is duplicated or silently discarded.
- **Pass-and-play.** Either side may advance its matching retained action on its turn,
  and the suffix remains available to the appropriate side.
- **Computer games.** A repeated matching human move consumes that recorded action
  without clearing the suffix. When its recorded computer reply becomes next, the
  ordinary computer-turn driver restores that exact reply and snapshot with zero
  `ChessEngine` calls. It invalidates any in-flight search first. With no matching
  retained reply, the existing one-search flow applies.
- **Online authority.** The server validates identity, predecessor, participant, turn,
  live game and `expectedVersion` in the locked move transaction. It atomically writes
  canonical state, active history, retained continuation, version and request metadata,
  and increments the version once. A manual matching move is audited as `MoveMade`;
  `MoveRedone` remains exclusive to the explicit Redo command. Stale and concurrent
  submissions keep their existing behavior.
- **No silent opponent action.** Matching the first action of a two-ply online
  operation does not play the opponent's reply. That reply remains available to the
  opponent, who may explicitly Redo it, manually repeat it to advance again, or make a
  different legal move that clears the suffix.
- **Requests.** A matching action still changes the board and active history, so it
  invalidates pending undo-request metadata like any accepted move. `M22.3` applies the
  same rule to a pending draw offer; request-only changes remain non-invalidating.
- **Finality.** Resignation and a successful draw claim still clear the suffix. A
  matching game-ending move finishes normally, clears any remaining future, and stays
  final under `D017`–`D019`. Finished games never offer Undo or Redo past finality.
- **Persistence and projection.** Partial consumption survives Back, refresh,
  rotation, process death, restart, and another online installation. SQLDelight and
  PostgreSQL preserve the remainder of a grouped operation atomically. Audit rows and
  client rule replay are never used to reconstruct it, and no retained snapshot is
  exposed to a client.
- **Common architecture.** Matching versus divergence joins `D101`'s common history
  invariants. Chess uses chess-specific move records and snapshots. The future Deck
  Builder uses its own structured action identities and snapshots. Neither follows the
  other's schema, and this task adds no generic action model.
- **Tests:**
  - Undo twice, manually repeat the next move, and prove the later move remains
    redoable; then manually repeat the complete suffix and prove the original
    continuation is reached with no Redo left.
  - A different move clears the entire suffix; promotions distinguish otherwise
    matching moves.
  - Positions with the same visible board but different castling, en-passant,
    repetition or move-count state do not falsely match.
  - A partially consumed two-ply operation retains its reply; explicit Redo still
    restores a complete accepted operation atomically.
  - Pass-and-play covers both sides. Computer coverage proves zero new engine calls
    for a retained reply and rejects a stale search result.
  - Online coverage proves actor, version, `MoveMade` versus `MoveRedone`, persistence,
    stale submission and concurrency behavior, and pending undo-request invalidation.
  - `M22.3` explicitly covers draw-offer invalidation by a matching continuation move.
  - Local and online recovery tests reopen a partially consumed continuation after
    Back, process death/repository reconstruction and restart.
  - Existing game-finality tests remain green.

### Verification

- `.\gradlew.bat :chess-core:test`.
- `.\gradlew.bat :chess-app:testDebugUnitTest`, including real-file SQLDelight
  persistence and controlled computer-engine tests.
- `.\gradlew.bat :server:test --rerun-tasks` with `TEST_DATABASE_URL` against real
  PostgreSQL; no database-backed test may be skipped.
- Compile and run any affected Compose/device test as the M22 milestone gate above
  requires.
- `.\gradlew.bat build`.
- `git diff --check`.

### Completion

Completed 2026-10-17. `ChessRules.applyMove(ChessGame, Move)` now asks
`ChessRules.retainedMove`: when the move's `from`, `to` and promotion equal the first record
of the next Redo operation and the whole current `GameState` equals that record's
`positionBefore`, it appends that record, takes the recorded next position as the state
(the next record's `positionBefore`, or the operation's `stateAfter`) and keeps the rest of
the operation, and every deeper one, on the path; a resulting finished state would clear it.
Any other move branches as before. Because the server, the local store and the computer all
play moves through it, the server needed no code change (a manual match is audited
`MoveMade`, saves state, history and path in the one guarded write, and clears undo-request
metadata). `LocalGameStore.recordMove` accepts a move only if the game equals what
`applyMove` gives from the stored game and writes the remaining path with the state;
`LocalGameChange` classifies such a move as `Move`; `ComputerGame` restores a retained
computer reply from its record with no engine search, after stopping any search. The
`D092` tests that required a hand-played repeat to branch were replaced by `D102` ones.
Verified with `:chess-core:test` (new `RetainedContinuationTest`),
`:chess-app:testDebugUnitTest` (real-file SQLDelight `LocalRedoStoreTest`, scripted-engine
`ComputerRedoTest`), `:server:test --rerun-tasks` against PostgreSQL (`RedoTest`'s actor,
version, audit, persistence, stale, concurrency, request and no-silent-reply cases),
`.\gradlew.bat build` and `git diff --check`. No Compose or device test changed.

---

## M22.3 — Draw offers by agreement

**Status:** TODO

**Depends on:** M22.2

**Formerly:** `F2`. Source: `MVP.md`, `PRODUCT.md`, `D019`.

### Objective

A player may offer a draw, and the opponent may accept or decline it. A new command
and a pending-offer state. Value: high.

### Decisions

`D097` decides the request lifecycle:

- either player may offer during an unfinished online game, regardless of turn;
- at most one draw offer is pending per game and it has no timer or separate Cancel;
- after a decline, no new draw offer may be made until an accepted move;
- one draw offer and one undo request may coexist;
- creating, declining or otherwise changing request metadata does not invalidate the
  other request;
- any board, active-history or result change invalidates both requests.

A draw offer and an undo request (`M21.19`) may be pending in one game at the same
time. This task replaces the undo-only dashboard field with the plural contract in
one beta cutover. Old APK compatibility is not required. "Draw offers by agreement
are outside MVP" stays true of the MVP; this is post-MVP work.

### Acceptance Criteria

- `D097` records the answers above and amends `D019`, whose "Draw offers by
  agreement remain outside MVP" stays true of the MVP but no longer means they do
  not exist. `CLAUDE.md`'s *Product Rules* ("Draw offers by agreement are outside
  MVP"), `PRODUCT.md` (*Draw Semantics* and the *Deferred Features* list) and
  `MVP.md` (its "Draw offers by agreement are not implemented" line) are reworded
  to say draw offers are post-MVP work built in `M22.3`.
- **Dashboard** (carried over from `F2` unchanged):
  - The server and client delete singular `pendingRequest` and use required, non-null
    `pendingRequests: List<PendingRequest>`. The server always emits it, including an
    empty list, and neither side gives it a wire default that could conceal a missing
    field.
  - There is no legacy fallback, dual-field period or later cleanup task. A new APK
    against an old server is incompatible, and an old APK may lose request status
    against the new server; both are acceptable during beta.
  - Simultaneous requests have one stable, tested display order: undo request before
    draw offer, independent of which direction each request is travelling.
  - `yourTurn` retains its chess-specific meaning: it is `true` only when it is the
    viewer's turn to move, never merely because a request needs their response.
  - The dashboard entry's derived `waitingOnYou` is `true` when `yourTurn` is true or
    any entry in `pendingRequests` requires the viewer's response.
  - Each entry accurately distinguishes an incoming request that the viewer must
    answer from an outgoing request that the viewer sent and is waiting on.
  - Server and client tests cover an undo request and draw offer pending together for
    both viewers, including DTO projection, stable ordering, YOUR TURN / THEIR TURN
    grouping, and row rendering of both requests.
  - Serialization tests prove the required plural field is emitted for both empty
    and populated lists and that a missing field is rejected.
- An accepted offer ends the game as a draw, immediately final, and settles the
  series (`SeriesService.settleAfter`) like any other ending.
- Request answers remain valid through intervening request-only versions, but not
  through any intervening board, active-history or result change. Persistence tracks
  that distinction explicitly rather than treating every request-only version as a
  gameplay-state change.
- A declined offer records that no new draw offer is eligible until the next accepted
  move. Undo or Redo alone does not lift that restriction.
- **Tests:** cover either player offering off-turn, one pending offer, no timer, the
  post-decline move gate, both request types in either direction, request-only
  interleavings, and invalidation by move, Undo, Redo, resignation, draw claim and
  either accepted request. A move that matches and advances `M22.2`'s retained
  continuation is still a board/history change and invalidates both requests.

---

## M22.4 — Win/loss/draw record per friend

**Status:** TODO

**Depends on:** None

**Formerly:** `F1`. Split from `F30` (detailed statistics) on 2026-09-23.

### Objective

Show a player their win, loss and draw record against each friend. Results are
already stored; this is a query and a screen. Value: high.

### Decisions

`D096` puts a compact `W-L-D` record on each friend's row in the dashboard's
FRIENDS section. It counts every finished chess game with that friend across every
series, from the viewer's perspective.

### Acceptance Criteria

- The record is computed by the server from stored results and reaches the client in
  a DTO. The client does not count games itself.
- The wire record is required and non-null. The server always supplies explicit win,
  loss and draw counts, including a real `0-0-0`; a missing field is an incompatible
  server response, not a value the client defaults or hides.
- Draws by every reason count as draws; a game still being played does not count.
- The compact display is `<wins>-<losses>-<draws> W-L-D`; its accessibility
  description speaks the three counts as wins, losses and draws.
- The statistics model can be shared later with `F30` (detailed statistics) and
  `F31` (series revival).
- **Tests:** server tests for the counts across wins, losses, draws, several series
  and games still being played; client tests for the display and a real `0-0-0`.

---

## M22.5 — Detailed profiles

**Status:** TODO

**Depends on:** M22.4

**Formerly:** `F6`. Source: `PRODUCT.md`.

### Objective

A profile page for a player. Effort: medium.

### Decisions

`D099` makes this a relationship profile. It shows the username, join month/year,
the lifetime record against the viewer (`M22.4`) and only groups the two users
share. A viewer may open it only when they are friends with the subject or currently
share a group.

### Acceptance Criteria

- A friend row and another user's shared-group member row can open the profile. The
  viewer's own row is not a profile-navigation entry.
- The route identifies the subject by immutable user ID, never by mutable username.
- The server authorizes each read from current friendship/group membership and
  returns only username, join month/year, the viewer-relative `W-L-D` record and
  shared groups. It never returns the subject's complete group membership.
- Join month/year is derived in UTC, so it does not change with server or device
  timezone.
- The record is the server-computed lifetime chess record from `M22.4`/`D096`.
- A caller with neither relationship receives `404`, so the endpoint does not reveal
  whether an arbitrary username exists.
- If a relationship disappears after a row was loaded, the app treats the resulting
  `404` as an ordinary unavailable-profile state with Back; it does not retry as an
  authentication or server-wake failure.
- **Tests:** server tests for immutable-ID lookup, UTC month boundaries, friend,
  shared-group, unrelated, removed-friend and left-group viewers; Android tests for
  both entry points, the non-clickable self row, every displayed field and stale-row
  `404` handling.

---

## M22.6 — Standard algebraic notation in `chess-core`

**Status:** TODO

**Depends on:** None

**Formerly:** `F39`, part 1

### Objective

Move lists print `Move.toString()` (`GameControls.format`): `e2e4`, castling as
`e1g1`, promotion as `e7e8q`. Players read standard algebraic notation (SAN). This
task adds a formatter that turns a move into SAN. `M22.7` shows it.

### Acceptance Criteria

- `ChessRules`, which stays `chess-core`'s only public entry point, offers SAN for a
  legal move in a position: a pure function of the position before the move and the
  move. Its helper may live in its own file, as the other helpers do.
- It writes:
  - piece letters `K`, `Q`, `R`, `B`, `N`, and none for a pawn;
  - `x` for a capture, with the pawn's file for a pawn capture (`exd5`);
  - disambiguation by file, by rank, or by both, only as much as is needed (`Nbd2`,
    `R1e2`, `Qh4e1`);
  - `O-O` and `O-O-O`;
  - `=Q`, `=R`, `=B`, `=N` for promotion;
  - en passant as an ordinary pawn capture (`exd6`);
  - `+` for check and `#` for checkmate.
- An illegal move is refused like every other transition: the query/transition rule
  (`AGENTS.md`) holds.
- Notation is for display only, never stored or sent as canonical data
  (`ARCHITECTURE.md` §19). The server and the wire format are unchanged.
- **Tests** in `chess-core` cover every item above, including three queens that can
  all reach one square, a promotion that captures and gives check, castling that
  gives check, a checkmating move, and an en passant capture.

---

## M22.7 — Every move list and move wording in SAN

**Status:** TODO

**Depends on:** M22.6

**Formerly:** `F39`, part 2

### Objective

Show `M22.6`'s notation wherever the app shows a move to a player.

### Acceptance Criteria

- Pass-and-play, the computer game, the online game and the local review show their
  move lists in SAN. The existing numbered pairs stay (`1. e4 e5`).
- The declared-move draw-claim wording (`GameControls.declaredClaimExplanation`), and
  anything else that prints a move to a player, uses SAN. Stored and wire forms keep
  a coordinate representation as canonical move identity, but never as display
  fallback.
- Locally, the position before each move comes from its `MoveRecord`. Online, the
  server adds required `san` to the existing chess `MoveView` (`from`, `to`,
  `promotion`) and replaces `GameView.moves: List<String>` with
  `List<MoveView>`. It derives each entry from the stored `MoveRecord` and its
  `positionBefore`. `lastMove` uses the same type. Android renders `san` and does not
  replay coordinates merely to produce notation.
- Undo request and undo-note DTOs use those same required chess move views for the
  requester move and reply. Pending and declined requests derive them from active
  move records; accepted and crossed notes derive them from the retained Redo
  operation. SAN remains derived display data, never canonical persistence.
- There is no coordinate UI fallback, optional SAN field, legacy singular form or
  mixed-version compatibility path. Missing SAN or its recorded context is an
  invariant/protocol failure. The beta cutover deletes all pre-cutover pending undo
  requests and last-answer notes and clears `last_change_kind`, `last_change_actor`,
  `last_change_version` and `last_change_move` before the new DTOs are served.
- Compose holds no rule. A move list is not recomputed in full on every
  recomposition.
- **Tests:** host-side tests for each screen's move list, claim wording, required SAN
  serialization, pending/declined/accepted/crossed undo wording and rejection of
  missing display data; cutover tests prove all obsolete undo-request, answer and
  last-change metadata is cleared; Compose tests prove at least one board renders
  SAN and never coordinates.

---

## M22.8 — Turning automatic rematches off by hand

**Status:** DONE

**Depends on:** None

**Formerly:** `F4`. Source: `PRODUCT.md`.

### Objective

Let a player stop the automatic rematch in a series. Effort: small.

### Completion

Closed as covered on 2026-10-14. `D095` makes Leave series end the current game,
close the series and guarantee that no rematch follows. A second switch would offer
no additional behavior. No implementation change belongs to this task; `M22.1`
implements the decided Leave behavior.

---

`M22.9` is intentionally unused. Username changes formerly held that number, but
`D098` removed them from M22 and returned them to `F5` until `F13` supplies real
authentication. The number is not reused.

---

## M22.10 — Online ply-by-ply review

**Status:** TODO

**Depends on:** M22.7

**Formerly:** `F41`

### Objective

`M21.16` left online review as an open product decision: ply-by-ply review exists
only for saved local games (`M21.3`). Bring it to online games. It is most useful
from History, where a finished online game opens read-only.

### Decisions

`D100` offers Review on a finished online game's end screen and when that game is
opened from History. Only finished games may enter review; an active online game
never offers it. `D101` requires the server to project recorded move contexts and
forbids client reconstruction by replaying a historical move chain.

### Acceptance Criteria

- `LocalGameReview`'s Start, Previous, Next and End controls are reused.
- The finished-game screen and History both offer Review. Active games do not.
- `GET /games/{gameId}/review` accepts only a participant and only a finished game.
  A missing game is `404`, a nonparticipant is `403`, and an active game is `409`.
  It returns `CompletedGameReviewView`: `gameId`, `version`,
  `plies: List<ReviewPlyView>`, `finalPosition: ReviewPositionView`, `result`, and
  `terminationReason`. Each ply has `move: MoveView` and
  `positionBefore: ReviewPositionView`. `ReviewPositionView` contains only `board`,
  `sideToMove`, and `inCheck`; it is a safe chess projection, not a serialized
  persistence snapshot. `inCheck` describes the displayed position even when that
  position is the final checkmate.
- The server derives each SAN independently from that move's own recorded pre-move
  position, projects each position from that same snapshot, and projects
  `finalPosition` and the result fields from the game's final canonical state. It
  never constructs a historical chain by executing moves through current rules.
  Missing or invalid move context is an invariant failure: Review remains
  unavailable, the ordinary finished-game view remains usable, and coordinates are
  not shown as a fallback.
- The move list it steps through is in SAN (`M22.7`).
- Local and online review map their recorded histories into one read-only Android
  review model, without sharing persistence DTOs or replaying rules.
- **Tests:** server tests for response identity, `404`/`403`/`409`, required SAN,
  exact recorded pre-move positions, checked positions including final checkmate,
  zero-move finished games, invalid move context, and exact final canonical
  state/result; host-side tests for stepping and where Review is offered; a Compose
  test for the controls.

---

## M22.11 — Board coordinates

**Status:** TODO

**Depends on:** None

**Formerly:** `F39`, part 3

### Objective

`BoardRendering.fileLabels` and `rankLabels` exist, but no board draws them. Show
file letters and rank numbers on every board.

### Acceptance Criteria

- File letters run along the bottom edge and rank numbers along the left edge of the
  board as drawn, inside the edge squares, so the board keeps its size.
- **The labels name the true squares.** Pass-and-play always has White at the bottom
  (`D087`), so it reads them from White's side, and its labels are never rotated. An
  online or computer board turned for Black shows the files from h to a and the ranks
  from 1 to 8, top to bottom.
- The labels are readable on both square shades and never hide a highlight, a
  destination dot or a capture ring (`M21.17`'s drawing order).
- The labels are left out of accessibility semantics. Board accessibility is `F40`.
- **Tests:** rendering tests for both orientations and pass-and-play; Compose tests
  in portrait and wide layouts.

---

## M22.12 — Drag-and-drop moves

**Status:** TODO

**Depends on:** M22.2

**Formerly:** `F3`. Source: `PRODUCT.md`.

### Objective

Let a player drag a piece to move it, as well as tapping.

### Acceptance Criteria

- Tap-to-move keeps working unchanged.
- Dragging the player's own piece onto a legal destination makes the same move a
  tap would, through `BoardInteraction`. Compose holds no rule; legality comes from
  `chess-core`.
- An illegal or cancelled drop returns the piece and changes nothing.
- Drag recognition does not steal ordinary vertical scrolling in a short one-pane
  layout. A gesture that becomes scrolling or is cancelled changes no selection or
  game state.
- The promotion choice and the draw-entitling prompt (`D041`) still stop before the
  move is made.
- It works on every board, including pass-and-play's upside-down pieces (`D087`),
  the computer game (only on the human's turn), and online, where legality is
  pre-validation only (`D004`).
- **Tests:** host-side tests for the drop path; Compose tests for a legal drop, an
  illegal drop, a promotion by drag, cancellation, and vertical scrolling that
  starts over the board without making a move. A drag that repeats the next retained
  action advances `M22.2`'s continuation exactly as the equivalent tap does.
