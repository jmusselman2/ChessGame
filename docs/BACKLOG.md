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
`F2`, `F39`, `F3`, `F4`, `F5`, `F6` and `F41`, in the order that tier ranked them. The
project owner scheduled them on 2026-10-14, and their `F` IDs are retired (`D072`).
`F39` became three tasks, so each has a single testable outcome. It is post-MVP work
and does not change the MVP's definition of done.

Seven tasks wait on decisions only the owner can make. They are `BLOCKED`, and each
lists exactly what would unblock it, with a recommendation that is **not** a
decision. A `BLOCKED` task that touches a product rule `CLAUDE.md` marks "must not be
changed silently", or a security decision, also needs a numbered decision before work
starts. Larger tasks may be split further once they are unblocked, as `M21` was.

| Task     | Outcome                                             | Depends on | Formerly | Status    |
| -------- | --------------------------------------------------- | ---------- | -------- | --------- |
| `M22.1`  | At most one active series per party                 | None       | `F38`    | `BLOCKED` |
| `M22.2`  | Win/loss/draw record per friend                     | None       | `F1`     | `BLOCKED` |
| `M22.3`  | Draw offers by agreement                            | None       | `F2`     | `BLOCKED` |
| `M22.4`  | Standard algebraic notation in `chess-core`         | None       | `F39`    | `TODO`    |
| `M22.5`  | Every move list and move wording in SAN             | M22.4      | `F39`    | `TODO`    |
| `M22.6`  | Board coordinates                                   | None       | `F39`    | `TODO`    |
| `M22.7`  | Drag-and-drop moves                                 | None       | `F3`     | `TODO`    |
| `M22.8`  | Turning automatic rematches off by hand             | None       | `F4`     | `BLOCKED` |
| `M22.9`  | Username changes                                    | None       | `F5`     | `BLOCKED` |
| `M22.10` | Detailed profiles                                   | M22.2      | `F6`     | `BLOCKED` |
| `M22.11` | Online ply-by-ply review                            | M22.5      | `F41`    | `BLOCKED` |

Every task's verification includes, besides its own targeted tests:

- the affected modules' tests (`.\gradlew.bat :chess-core:test`,
  `:chess-app:testDebugUnitTest`, or `:server:test` with `TEST_DATABASE_URL` and
  `--rerun-tasks` for server work);
- `.\gradlew.bat build`;
- `git diff --check`.

---

## M22.1 — At most one active series per party

**Status:** BLOCKED

**Depends on:** None

**Formerly:** `F38`

### Objective

The project owner decided the rule on 2026-10-14: a party, meaning the exact set of
a series' participants, has at most one active series. For chess, two people never
have two games with each other at once. Another series may start only once the
party's series has ended.

It removes an ambiguity players can already reach. Parallel series against one
opponent cannot be told apart: the dashboard shows each as the opponent's name and
the same detail ("White • Move 2"), History heads each with the same name, and the
Friends row links to only one of them (`DashboardSections.friends`).

### Blocked on

1. **Pairs that already have several active series when the migration runs.**
   *Recommendation (not decided):* keep the newest active series. Mark the others
   left (`D068`), so their current games can still be finished, start no rematch,
   and delete nothing.
2. **Whether a left series whose last game is still being played counts as the
   party's active series.** *Recommendation (not decided):* yes, until that game
   ends. Otherwise two games between the same people could coexist.
3. **What an older APK gets when it asks to start another series**
   (`POST /series?another=true`). *Recommendation (not decided):* answer it as a
   plain Play and return the existing series, so the old client opens it.

### Acceptance Criteria

- A decision supersedes `D053` rule 1 (parallel series). `D053` rule 2, that
  unfriending affects the friends list only, stays. `CLAUDE.md`'s *Product Rules*,
  `PRODUCT.md` *Game Series* and `AGENTS.md` say the same.
- A forward-only migration, safe on live data, restores the pair's uniqueness in the
  database and resolves existing parallel series as decided above.
- Play always opens the pair's series: the existing one if there is one, otherwise a
  new one. `PlayOffer`, `startAnotherSeries` and the series offer are removed. An
  older APK's request for another series gets the decided answer.
- The dashboard, Friends row and History each show at most one active series per
  opponent.
- The Deck Builder's uniqueness over a table's participant set is not built here. It
  is designed with `F26`, not before (`D044`).
- **Tests:** server tests for the constraint, the migration on a database holding
  parallel series, Play with and without an existing series, the old request, and
  the left-series case; client tests for Play and the removed offer.

---

## M22.2 — Win/loss/draw record per friend

**Status:** BLOCKED

**Depends on:** None

**Formerly:** `F1`. Split from `F30` (detailed statistics) on 2026-09-23.

### Objective

Show a player their win, loss and draw record against each friend. Results are
already stored; this is a query and a screen. Value: high.

### Blocked on

1. **Where the record shows** (dashboard, Friends list, series). *Recommendation
   (not decided):* on the friend's row in the dashboard's FRIENDS section.
2. **What it counts: one series, or every game with that friend.**
   *Recommendation (not decided):* every finished game with that friend, across all
   series. With `M22.1` there is one active series at a time, so the two mostly
   coincide.

### Acceptance Criteria

- The record is computed by the server from stored results and reaches the client in
  a DTO. The client does not count games itself.
- Draws by every reason count as draws; a game still being played does not count.
- The statistics model can be shared later with `F30` (detailed statistics) and
  `F31` (series revival).
- **Tests:** server tests for the counts across wins, losses, draws, several series
  and games still being played; client tests for the display.

---

## M22.3 — Draw offers by agreement

**Status:** BLOCKED

**Depends on:** None

**Formerly:** `F2`. Source: `MVP.md`, `PRODUCT.md`, `D019`.

### Objective

A player may offer a draw, and the opponent may accept or decline it. A new command
and a pending-offer state. Value: high.

### Blocked on

1. **When an offer expires, and how it interacts with undo and the game version.**
   *Recommendation (not decided):* follow `D091`, as the undo request does: no
   cancel and no timer; any other accepted change to the game clears it; an answer
   counts only at the version it was asked at.
2. **When an offer can be made.** *Recommendation (not decided):* either player, at
   any time, with at most one pending offer per game.

A draw offer and an undo request (`M21.19`) may be pending in one game at the same
time. `M21.19` implements only undo requests, so its dashboard contract remains the
singular `pendingRequest` until this task introduces the second request type. This
task owns the compatible replacement rather than changing that API speculatively
beforehand. "Draw offers by agreement are outside MVP" stays true of the MVP; this is
post-MVP work.

### Acceptance Criteria

- A decision records the answers above and amends `D019`, whose "Draw offers by
  agreement remain outside MVP" stays true of the MVP but no longer means they do
  not exist. `CLAUDE.md`'s *Product Rules* ("Draw offers by agreement are outside
  MVP"), `PRODUCT.md` (*Draw Semantics* and the *Deferred Features* list) and
  `MVP.md` (its "Draw offers by agreement are not implemented" line) are reworded
  to say draw offers are post-MVP work built in `M22.3`.
- **Dashboard** (carried over from `F2` unchanged):
  - The server and client dashboard DTOs replace the singular field with
    `pendingRequests: List<PendingRequest> = emptyList()`, containing every request
    pending in that game for the viewer.
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
- An accepted offer ends the game as a draw, immediately final, and settles the
  series (`SeriesService.settleAfter`) like any other ending.

---

## M22.4 — Standard algebraic notation in `chess-core`

**Status:** TODO

**Depends on:** None

**Formerly:** `F39`, part 1

### Objective

Move lists print `Move.toString()` (`GameControls.format`): `e2e4`, castling as
`e1g1`, promotion as `e7e8q`. Players read standard algebraic notation (SAN). This
task adds a formatter that turns a move into SAN. `M22.5` shows it.

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

## M22.5 — Every move list and move wording in SAN

**Status:** TODO

**Depends on:** M22.4

**Formerly:** `F39`, part 2

### Objective

Show `M22.4`'s notation wherever the app shows a move to a player.

### Acceptance Criteria

- Pass-and-play, the computer game, the online game and the local review show their
  move lists in SAN. The existing numbered pairs stay (`1. e4 e5`).
- The declared-move draw-claim wording (`GameControls.declaredClaimExplanation`), and
  anything else that prints a move to a player, uses SAN. Only the stored and wire
  forms keep `Move.toString()`.
- Locally, the position before each move comes from its `MoveRecord`. Online, it
  comes from `OnlineGame.replayOf`. When that replay fails, as it can on an older
  client, the screen keeps coordinate notation rather than showing nothing.
- Compose holds no rule. A move list is not recomputed in full on every
  recomposition.
- **Tests:** host-side tests for each screen's move list, the replay-failure
  fallback and the claim wording; Compose tests for at least one board.

---

## M22.6 — Board coordinates

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

## M22.7 — Drag-and-drop moves

**Status:** TODO

**Depends on:** None

**Formerly:** `F3`. Source: `PRODUCT.md`.

### Objective

Let a player drag a piece to move it, as well as tapping.

### Acceptance Criteria

- Tap-to-move keeps working unchanged.
- Dragging the player's own piece onto a legal destination makes the same move a
  tap would, through `BoardInteraction`. Compose holds no rule; legality comes from
  `chess-core`.
- An illegal or cancelled drop returns the piece and changes nothing.
- The promotion choice and the draw-entitling prompt (`D041`) still stop before the
  move is made.
- It works on every board, including pass-and-play's upside-down pieces (`D087`),
  the computer game (only on the human's turn), and online, where legality is
  pre-validation only (`D004`).
- **Tests:** host-side tests for the drop path; Compose tests for a legal drop, an
  illegal drop and a promotion by drag.

---

## M22.8 — Turning automatic rematches off by hand

**Status:** BLOCKED

**Depends on:** None

**Formerly:** `F4`. Source: `PRODUCT.md`.

### Objective

Let a player stop the automatic rematch in a series. Effort: small.

### Blocked on

1. **Whether this is already covered.** Explicitly leaving a series (`M19.8`,
   `D068`) already ends the series at once and leaves its current game to be
   finished, so no rematch follows. *Recommendation (not decided):* the owner
   confirms whether that covers this item. If it does, close this task as covered.
   If not, the switch goes on the online game screen rather than waiting for
   `F35` (user settings).

### Acceptance Criteria

To be written once the owner has answered: either closing this task as covered by
`M19.8`, or a switch whose behaviour and position are decided.

---

## M22.9 — Username changes

**Status:** BLOCKED

**Depends on:** None

**Formerly:** `F5`. Source: `MVP.md`, `PRODUCT.md`.

### Objective

Let a player change their username. Effort: medium.

### Blocked on

This is a security decision, and a stop condition until the owner makes it.

1. **What happens to the old name.** `D008` keeps lost names reserved.
2. **How to prevent impersonation,** given that identity is claimed by username with
   no verification (`D082`).

*Recommendation (not decided):* decide this alongside `F13`, real authentication.
Until then, a changed name is as easy to claim as any other.

### Acceptance Criteria

To be written once the owner has decided the above.

---

## M22.10 — Detailed profiles

**Status:** BLOCKED

**Depends on:** M22.2

**Formerly:** `F6`. Source: `PRODUCT.md`.

### Objective

A profile page for a player. Effort: medium.

### Blocked on

1. **What a profile shows, and to whom.** *Recommendation (not decided):* the
   username, when they joined, the record with the viewer (`M22.2`) and the groups
   they share. Visible to friends and to members of a shared group.

### Acceptance Criteria

To be written once the owner has decided the above.

---

## M22.11 — Online ply-by-ply review

**Status:** BLOCKED

**Depends on:** M22.5

**Formerly:** `F41`

### Objective

`M21.16` left online review as an open product decision: ply-by-ply review exists
only for saved local games (`M21.3`). Bring it to online games. It is most useful
from History, where a finished online game opens read-only.

### Blocked on

1. **Where review is offered.** *Recommendation (not decided):* both at the end of a
   game and from History.
2. **Which games can be reviewed.** *Recommendation (not decided):* finished games
   only.

### Acceptance Criteria

- `LocalGameReview`'s Start, Previous, Next and End controls are reused.
- Its positions come from replaying the server's move list through `chess-core`.
  That is display only and does not restore canonical state, so it is consistent
  with `D061`. When the replay fails on an older client, review is not offered.
- The move list it steps through is in SAN (`M22.5`).
- **Tests:** host-side tests for stepping, the replay failure and where it is
  offered; a Compose test for the controls.
