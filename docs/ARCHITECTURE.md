# Chess MVP — Architecture

## 1. Architectural Objective

Build a correct multiplayer chess application while establishing durable boundaries that can later support a more complex turn-based deck-building game.

Optimize for:

- pure, testable game rules,
- authoritative server-side state changes,
- deterministic state transitions,
- asynchronous multiplayer,
- concurrency safety,
- replaceable persistence and transport details,
- minimal premature abstraction.

Do not build a universal board-game engine during the chess MVP.

## 2. Technology Stack

| Area               | Choice                                                                  |
| ------------------ | ----------------------------------------------------------------------- |
| Android language   | Kotlin                                                                  |
| Android UI         | Jetpack Compose                                                         |
| Shared game logic  | Pure Kotlin/JVM module                                                  |
| Backend            | Kotlin + Ktor                                                           |
| Database           | PostgreSQL 18 locally/CI; beta on `ChessGame Dev`'s PostgreSQL (`D035`) |
| SQL access         | JetBrains Exposed DSL over HikariCP                                     |
| On-device storage  | SQLDelight for local games (`D084`, `D085`; built by `M21.1`)           |
| Migrations         | Flyway applying forward-only SQL files                                  |
| Authentication     | Supabase anonymous auth                                                 |
| Serialization      | Kotlin serialization + JSON                                             |
| Commands / queries | HTTPS                                                                   |
| Realtime updates   | WebSockets                                                              |
| Build              | Gradle Kotlin DSL                                                       |
| Repository         | Monorepo                                                                |

`chess-core` starts as Kotlin/JVM because both current consumers are JVM-based: Android and the Ktor server.

The shared Supabase development project currently provides anonymous
authentication only. The application schema is applied to disposable local/CI
PostgreSQL, not to that Supabase database. `M15.3` additionally applied it to
that same project's PostgreSQL to serve the beta: `D035` reuses `ChessGame Dev`
rather than creating a separate beta project, so development and beta share
identities and quotas while local/CI game data stays in the disposable
PostgreSQL. Beta hosting for the Ktor server is Render Free (`D032`).

Do not introduce Kotlin Multiplatform until a concrete non-JVM consumer exists.

## 3. System Boundaries

```text
┌──────────────────────── ANDROID APP ────────────────────────┐
│ Kotlin + Jetpack Compose                                    │
│                                                             │
│ Presentation                                                │
│ ViewModels / screen state                                   │
│ Repositories / API client                                   │
│        │                                                    │
│        ├──── uses chess-core locally for rules/UX           │
│        └──── local games: chess-core, chess-ai, SQLDelight  │
│              (device-authoritative, D084; never the server) │
└──────────────────────┬──────────────────────────────────────┘
                       │
                 HTTPS + WebSocket
                       │
                       ▼
┌──────────────────── KTOR SERVER ────────────────────────────┐
│ Authentication                                              │
│ Users / Friendships                                         │
│ Game Series                                                 │
│ Command handlers                                            │
│ Concurrency / transactions                                  │
│ Realtime publication                                        │
│        │                                                    │
│        └──── uses the same chess-core                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌──────────────────── POSTGRESQL ─────────────────────────────┐
│ users, friendships                                          │
│ groups, group_members                                       │
│ game_types, tables, table_participants                      │
│ game_series, games, game_participants                       │
│ non_user_participants                                       │
│ moves, game_events                                          │
└─────────────────────────────────────────────────────────────┘
```

## 4. Repository Modules

Recommended high-level layout:

```text
.
├── chess-core/
├── chess-ai/
├── chess-app/
├── server/
├── database/
│   └── migrations/
└── docs/
```

Allowed dependencies:

```text
chess-app ──→ chess-core, chess-ai
chess-ai  ──→ chess-core
server    ──→ chess-core
```

Forbidden dependencies:

```text
chess-core ──→ chess-app
chess-core ──→ server
chess-core ──→ database
chess-core ──→ Supabase
chess-core ──→ chess-ai
server     ──→ chess-ai
deck-*     ──→ chess-ai
```

`chess-ai` (`D086`) is a pure Kotlin/JVM module holding the chess-specific
`ChessEngine` interface and the project's engine, which chooses the computer's move
in a local game. It is not a rules engine and not a cross-game abstraction: the
prohibition on a generic `GameEngine` stands (`D044`, which `D086` supersedes only
for this one chess-specific seam). An engine that needs native code, such as
Stockfish, would live in `chess-app` and implement the same interface.

As built by `M21.5`: `chess-ai` holds `ChessEngine` (`chooseMove(state, difficulty,
isCancelled)`, returning `null` once cancelled), `Difficulty` (levels 1–3) and
`AlphaBetaEngine`, an iterative-deepening alpha-beta search over `ChessRules` with
material and simple positional evaluation. `D088` records its depths, budgets and
margins. The engine blocks while it thinks; the caller puts it on a background
thread. It uses Gradle's Kotlin/JVM plugin and `chess-core` and nothing else, and
`chess-app` depends on it from `M21.6`.

As built by `M21.11` (`D089`): `Difficulty` has four levels, `VERY_EASY` to `HARD`,
stored as 1–4. Very Easy and Easy are `D088`'s Easy and Medium unchanged, scored by
`Evaluation`. Medium and Hard score positions with `SensibleEvaluation`, which adds
opening play (castling, development, the queen and edge pawns kept back), pawn
structure, king shelter and static exchanges built from `chess-core`'s `Attacks`. At
the end of a line where the engine's opponent is to move, the search plays out the
opponent's winning captures through `ChessRules` instead of guessing. Every move the
engine plays or searches still goes through `ChessRules` (`D088`).

### Multi-game layout (`D082`)

ChessGame and the Deck Builder are separate Android apps in this one repository.
They share one Ktor server, one database and one deployment. `M20.3` renamed the
chess modules: `game-core` is `chess-core` and `android-app` is `chess-app`. The
repository moves toward this layout:

```text
chess-core      pure JVM: chess rules      — exists (formerly game-core, M20.3)
chess-ai        pure JVM: chess engine     — exists (M21.5, D086), chess-app only
chess-app       the ChessGame app          — exists (formerly android-app, M20.3)
deck-core       pure JVM: Deck Builder rules — created with real Deck Builder rules
deck-app        the Deck Builder app       — created with real Deck Builder rules
client-common   code both clients share    — extracted once deck-app exists
server          the one Ktor server        — exists
```

```text
chess-app ──→ chess-core, client-common
deck-app  ──→ deck-core,  client-common
server    ──→ chess-core, deck-core
```

Forbidden, in addition to the rules above for every rules module:

```text
chess-app  ──→ deck-core
deck-app   ──→ chess-core
chess-core ──→ deck-core
deck-core  ──→ chess-core
```

A shared pure-JVM rules/support module (something like `shared-game-core`) that
both rules modules depend on is allowed later. It is created only when real chess
and Deck Builder implementations show genuinely identical semantics, never from
chess alone (`D044`). Until then nothing is extracted, and the server reaches each
ruleset through an explicit `when (gameType)` (§30).

### Implementation status

This document describes the intended structure, not how much of it is built.
**`docs/BACKLOG.md`, with its archives under `docs/backlog/`, is the source of truth
for implementation status**, task by task (`D093`), and this section deliberately
does not restate it: the snapshot that used to live here was pinned to a single
commit and went stale as the work passed it.

What is durable is the shape. Every module the first diagram above names exists and is
exercised by `./gradlew build` — `chess-core`, the Ktor command/query surface with
PostgreSQL persistence and realtime publication, and an Android application wired
end to end from `MainActivity` through `ChessApp`. `docs/PLATFORM-REVIEW.md`
reviews what building all of it proved about the boundaries described here, and
`M18`'s completion note in `docs/backlog/M18-M20.md` says where that leaves the
project.

## 5. `chess-core`

`chess-core` contains pure chess rules and state. It is the chess rules module, not
a generic engine, which is why `M20.3` renamed it from `game-core` (`D082`). The
Deck Builder's rules will live beside it in `deck-core`, under the same
restrictions, and neither module depends on the other.

Implemented concrete chess concepts include:

```text
ChessGame
GameState
Board
Piece
Square
Move
ChessRules
GameResult
CastlingRights
DrawRuleState
```

`chess-core` must not depend on:

- Android,
- Compose,
- Ktor,
- PostgreSQL,
- Supabase,
- HTTP,
- WebSockets,
- database DTOs,
- screen state.

The module may expose deterministic operations such as:

```text
legalMoves(...)
applyMove(...)
canUndo(...)
undo(...)
drawClaimAvailability(...)
gameResult(...)
```

Do not create generic `UniversalGame`, `CardGame`, or speculative deck-builder abstractions.

## 6. Application and Platform Domains

The following are not chess-engine concepts:

```text
User
Friendship
GameSeries
Authentication
Dashboard
Realtime connection
Database transaction
```

Keep them in the Android/server application layers rather than `chess-core`.

They are also the **shared platform** for both products (`D082`): users and
identity, usernames, friends, groups, tables, participants, series, and the
realtime and audit infrastructure where it really is shared. One user record
serves both apps, so friends, groups and profile data are the same in each. Each
app shows them simply as the user's own, never as another product's. Tables,
games, series and history stay tied to their game type and product.

## 7. Server Authority

Server authority covers games that need coordination across devices (`D004`, as
amended by `D084`). A game whose players are all on one device, pass-and-play or
against the computer, is device-authoritative: it never goes through Ktor, and
nothing in this section applies to it (§11.4).

For every remotely coordinated game, the Android client is untrusted.

Android may use `chess-core` to pre-validate a move and provide immediate UX feedback, but it does not decide the canonical result.

One named exception exists, and only one: the server does not verify that two
users are friends when a series is created (`D046`). The invite UI is the gate
there. It is scoped to that single relationship assertion — no canonical game
state, version, result, or turn is ever taken from the client.

The client must never submit:

```text
"replace the game with this board state"
```

Instead it sends intent:

```text
MakeMove(
    gameId,
    expectedVersion,
    from,
    to,
    promotion
)
```

Typical server flow:

```text
authenticate
→ load canonical game
→ validate participant
→ validate expected version
→ validate command
→ execute through chess-core
→ persist atomically
→ increment version
→ publish update
```

Android must not directly read or write canonical game tables through Supabase database APIs.

Normal canonical access to a remotely coordinated game goes through Ktor.

## 8. Command Model

Commands represent requested state changes.

Chess MVP commands include:

```text
MakeMove
UndoMove
ClaimDraw
Resign
```

Application/social commands include:

```text
CreateUserProfile
AddFriend
RemoveFriend
StartSeries
LeaveSeries
CreateGroup
AddGroupMember
LeaveGroup
```

The implementation does not need one universal generic command hierarchy if concrete command types are simpler.

## 9. Audit Events

Persist an append-only audit history for meaningful changes during normal operation.
While the product remains in beta, intentional hard deletion may remove any beta
data, including audit rows (`D101`); append-only is not a beta-data preservation
guarantee.

Recorded in `game_events`:

```text
MoveMade
MoveUndone
MoveRedone
UndoRequested
UndoAccepted
UndoDeclined
DrawClaimed
PlayerResigned
GameEnded
RematchCreated
SeriesLeft
```

Each event names who caused it and what it belongs to (`D078`): `actor_id` is the
player whose request caused it, which for `GameEnded` and `RematchCreated` is the
player whose command ended the game; `game_id` is its game, if any; and `series_id`
is its series, so a series' log holds its games' events as well as its own.

`actor_id` references `users`, so it cannot name a `COMPUTER` or `SCRIPTED`
participant (§18, `D067`). The long-term concept is an action performed by a
participant, which may not be a person. The schema change is required before any
non-user participant produces audited actions, and is not made before then
(`D082` point 12).

An `UndoAccepted` that completes a crossed request and undo, or an `UndoRequested` that
is completed at once by one, carries `"crossing": true` in its payload (`D091`). A
`MoveRedone` names the restored `moves` and their count, `plies` (1 or 2) (`D092`).

`SeriesClosed` was recorded until `M19.5` removed the path that closed a series
after its current game; leaving a series records `SeriesLeft`, and a resignation it
causes records `PlayerResigned` with `"leftSeries": true` (`D095`). A `SeriesLeft` for a
zero-move game it deleted names that game as `deletedGameId`. Friendship
and group changes are not events: their rows are kept rather than deleted, with
`removed_at` and `left_at` saying what changed (§15, §15.1, §17).

Do not use full event sourcing.

Normal game loads should not require replaying every event.

Persist:

```text
canonical current state
+
active move history
+
normally append-only audit events
```

## 10. Game Versioning

Each game has a monotonically increasing version.

Every accepted state-changing command increments it.

Example:

```text
version 24
→ MakeMove
version 25
→ UndoMove
version 26
```

Commands include `expectedVersion`.

If the canonical version no longer matches, the command is stale and must be rejected.

This protects races such as:

```text
Jordan: UndoMove(version 25)
Alex:   MakeMove(version 25)
```

Only one transition can win.

## 11. Client State

Distinguish three categories.

### 11.1 UI State

Examples:

- selected square,
- legal-move highlights,
- dialogs,
- animation progress.

This is local only.

### 11.2 Local Game Snapshot

For an online game, the Android app holds the latest server-confirmed game state in
memory. (This is the client's copy of a server game, not a device-authoritative local
game, which is §11.4.)

It may use `chess-core` for:

- legal move display,
- selection behavior,
- pre-validation,
- local rendering.

It is not authoritative.

### 11.3 Canonical State

Ktor + PostgreSQL define what is actually true.

Canonical state survives:

- app closure,
- device restart,
- both users being offline,
- client crashes,
- WebSocket disconnects.

This applies to remotely coordinated games. Local games have their own canonical
state (§11.4).

### 11.4 Device-Authoritative Local Games

*Decided 2026-09-29 (`D084`–`D086`). Built by `M21.1`–`M21.7`.*

A game whose players are all on this device (pass-and-play, or against the
computer) is canonical **on the device**. It never goes through Ktor or
PostgreSQL, and it works when server startup fails.

- **Storage.** A SQLDelight database in `chess-app` (`D085`), with two tables:
  - `local_games`: the canonical current or final state, kind, status, timestamps,
    result, and for a computer game the human's colour and the difficulty;
  - `local_moves`: one row per ply, holding the move and its recorded
    `positionBefore`.

  There are no local series, users, participants or statistics tables.
- **One unfinished local game of each kind** (`D090`, built by `M21.13`): a
  pass-and-play game and a game against the computer may both be unfinished. The
  unique partial index is per kind, and starting a game replaces, deleting it, only
  its own kind's unfinished game. Finished games are kept for read-only review.
- **Writes append and truncate** (`D061`). A move appends; an undo or a computer-game
  takeback truncates and restores the recorded prior position. Each change touching
  state and history is one transaction, so the stored board always matches the
  stored history.
- **What a takeback truncates is kept for Redo** (`D092`, built by `M21.21`). It
  becomes one Undo operation in `local_games.redo_path`, in the same transaction, and
  `LocalGameStore.redo` appends its records again and restores its recorded state. As
  built, a move or result writes the path empty. `M22.2` changes a move that exactly
  matches the next retained action to consume that action and preserve the suffix;
  only a divergent move or final result clears it (`D102`). Schema version 4's `3.sqm`
  adds the column with the default `'[]'`, so every existing game starts with no path.
- **Restoring never replays rules.** `ChessGame(state, history, redoPath)` is rebuilt
  from the stored state, move records and Redo path (`D029`). Persistence models stay
  in `chess-app`.
- **Excluded from backup.** The database is excluded from Android cloud backup and
  device transfer, so local games stay on the installation that made them.
- **The computer's moves** come from a `ChessEngine` (`chess-ai`, `D086`), run off
  the main thread. The app discards any result that no longer matches the current
  game and position.
- **The level is stored as its number,** 1 (Very Easy) to 4 (Hard), in
  `local_games.difficulty` (`D089`). Schema version 2's `1.sqm` (`M21.12`) moved
  stored Hard games from 3 to 4 when the new Medium took 3; it changes data only, so
  the version-2 snapshot `2.db` has the same tables as `1.db`.
- **One unfinished game per kind, as built by `M21.13`.** Schema version 3's `2.sqm`
  drops `local_games_one_active`, on `(status)`, and creates
  `local_games_one_active_per_kind`, on `(kind)`, both `WHERE status = 'ACTIVE'`; no
  row changes. The queries `activeOfKind`, `deleteActiveOfKind` and
  `deleteForActiveOfKind` take the kind. `LocalGameStore.activeGame(kind)` reads one
  kind, and `startGame` deletes only its own kind's unfinished game, with its moves.
  `LocalGameSession.resume()` always returns a pass-and-play game, and
  `resumeComputer()` reads only the computer's. Neither entry has a question about the
  other kind any more.

**The store, as built by `M21.1`** (package `com.jmussel.chessgame.local`):

- **Schema.** `LocalGames.sq` and `LocalMoves.sq` under `src/main/sqldelight` generate
  `LocalGameDatabase`. The schema is versioned from version 1: each released version's
  schema is kept as `src/main/sqldelight/databases/N.db`, and
  `verifySqlDelightMigration`, part of `check`, proves the `.sqm` migrations turn each
  one into the current schema. A change to the tables is a new `N.sqm` and a new
  snapshot, never an edit to a released one.
- **Constraints in the database, too.** A unique partial index allows one `ACTIVE`
  row (one of each kind since `M21.13`). `CHECK`s tie the status to the completion time and result, and the kind to the
  computer's colour and difficulty. `local_moves` is keyed by `(game_id, ply)` and
  references its game; foreign keys are switched on for every connection.
- **`LocalGameStore`** is the only way in. `startGame` deletes the unfinished game (of
  its own kind since `M21.13`) and creates the new one in one transaction. `recordMove` appends one record and updates
  the state, and completes the game if the move ended it. `recordResult` completes a
  game ended by resignation or a claim. `takeBack(plies)` truncates and restores the
  position recorded before the first removed ply, keeping the removed plies as one Undo
  operation, and `redo` restores the next one (`D092`). Each refuses a finished game, and a
  move or result that does not follow from the stored position. `activeGame`, `game`
  and `completedGames` (newest first) read. Calls block, so callers run them off the
  main thread.
- **Positions are JSON** (`LocalStateDocument`, the server's `GameStateDocument`
  shape), keeping the repetition counts and halfmove clock, so a restored game offers
  the same draw claims. Moves are coordinate notation (`e7e8q`).
- **The file** is `local_games.db` (`LOCAL_GAME_DATABASE_NAME`), opened by
  `openLocalGameStore(context)`. `backup_rules.xml` and `data_extraction_rules.xml`
  exclude it and its `-journal`, `-wal` and `-shm` files.
- **Versions.** The SQLDelight plugin and runtime are 2.4.0, the first release that
  works with AGP 9's built-in Kotlin. The Android driver stays on 2.2.1, because 2.3.0
  raised its minSdk to 23 and the app supports 22.

**Pass-and-play on the store, as built by `M21.2`.** `ChessAppViewModel` owns a
`LocalGameSession`, which holds the stored game's id and keeps the store in step with
the screen:

- **Intent from the game, not the tap.** The screen still hands back a whole
  `LocalGameUiState` for every tap. `LocalGameChange.between(before, after)` compares
  the two `ChessGame`s: no change (a selection or a prompt) writes nothing; one more
  move is `recordMove`; fewer moves are `takeBack(n)`; a result with the same history
  is `recordResult`. Anything else is a bug and throws.
- **Ordered, off the main thread.** Every store call runs on `Dispatchers.IO` behind
  one mutex, in the order asked, so a takeback never overtakes its move and a load
  waits for earlier saves. Saves run in the session's own scope, not the view model's,
  so a save made as the app closes is not cancelled with it.
- **The store wins.** If a save fails, the session reloads the stored game onto the
  screen.
- **Opening.** "Local game" shows a loading state, then the unfinished pass-and-play
  game, or a new one. Back leaves it in the store. New game deletes an unfinished game
  after the screen has asked.

**Games against the computer, as built by `M21.6`.** `ChessAppViewModel.computerGame` is
a `ComputerGame` (package `computer`), which owns the game's state, its searches and its
saves; `M21.7` gives it a screen.

- **Searches.** Whenever the computer is to move, one search starts on
  `ChessAppDependencies.engineDispatcher` (`Dispatchers.Default`), and starting one
  cancels any other. The engine polls the coroutine's `isActive`. Leaving the game
  cancels the search; resuming on the computer's turn starts exactly one.
- **Stale results.** Every search carries a turn number and the game it was asked
  about. Its move is applied only if both are still current, so a result that arrives
  as it is cancelled, after a takeback, a new game or the game's end, changes nothing.
- **Input.** On the computer's turn the board ignores the human, except a resignation.
  A draw claim is only ever the human's.
- **Takeback** undoes two plies after a reply, or one while the computer is thinking,
  with `ChessRules.undoLastMove`, and is saved as a store takeback. It is offered while
  the game is unfinished and has a human move to take back.
- **Saving** goes through `LocalGameSession.save(id, …)` like pass-and-play. The
  session takes the stored game's id on every call rather than remembering one, so the
  two kinds of local game cannot write to each other's.
- **The engine** is `ChessAppDependencies.chessEngine`, an `AlphaBetaEngine` seeded
  afresh each launch. Tests pass scripted engines.

**Playing the computer, as built by `M21.7`.** `Destination.ComputerGame` shows
`ComputerGameScreen` (package `computer`), which reuses the local game's board,
prompts and controls. `ComputerGame.open` resumes the unfinished computer game or
sets `ComputerSetup`: `ConfirmReplacing` when a pass-and-play game is unfinished, then
`ChooseDifficulty`. The first game's colour comes from `chooseSide` (random); Play
again swaps it and keeps the level. The other way round, `LocalGameSession.resume`
returns nothing when the unfinished game is against the computer, and the
pass-and-play screen asks (`LocalGameUiState.replacingComputerGame`) before
`startNewLocalGame` deletes it.

**Past local games, as built by `M21.3`.** `Destination.PastLocalGames` lists
`LocalGameStore.completedGames()`; `Destination.PastLocalGame(id)` reviews one. Both
read through `LocalGameSession`, so they come after any save still in flight.
The review is `ui/review`'s `GameReview` (`M22.10`), shared with online review:
`GameReviews.ofLocal` takes each move's recorded `positionBefore` and the stored final
state as its positions, with SAN from those same positions. Stepping reads the recorded
positions and never replays a move. An unfinished game is not reviewed.

**Online completed-game review (`D100`, scheduled in `M22.10`).** A finished game
offers the same Start, Previous, Next and End interaction from its end screen and
from History. The server returns a chess-specific review projection built from the
stored `MoveRecord.positionBefore` values and final canonical state. The participant-
only `GET /games/{gameId}/review` returns `CompletedGameReviewView`, identified by
`gameId` and `version`: each `ReviewPlyView` contains a required-SAN `MoveView` and a
safe `ReviewPositionView` (`board`, `sideToMove`, `inCheck`) projected from its
recorded pre-move snapshot; a final position and result follow the plies. Its
`inCheck` describes the displayed position even at final checkmate. Android steps
through that projection and never runs old moves through its installed rules to
manufacture past positions (`D101`). The server derives each SAN against that ply's
own snapshot, not by replaying a historical chain. An active game cannot enter
review. Missing or invalid move context makes Review unavailable while the ordinary
read-only completed-game view remains.

**Move-history presentation (`D105`, built by `M23.2`).** Android projects both live
and reviewed chess moves into one typed presentation model, `MoveHistory`
(`MoveHistoryRow`, `MoveHistoryEntry`).
Each individual entry retains its ply index, move number, side, required SAN and
whether it is current; rendered text is an output, never the source of that state.
Review uses only the existing projected prefix through `GameReview.ply`, so a White
half-move cannot reveal Black's later reply (`GameReview.history`). Live local and
computer games use active `MoveRecord`s (`GameControls.moveHistory`), while live online
games use the server's existing `MoveView` SAN (`OnlineGame.moveHistoryFor`). No path
parses SAN, reconstructs history from the board or replays moves.

The shared `MoveHistoryList` composable owns a bounded vertical scroll state separate
from `GameLayout`'s board and controls. `GameLayout` places it below a fixed region in
every mode (`FixedThenHistory`): the fixed content takes the height it needs, leaving a
nonempty list at least one row (`oneMoveRowHeight`) so its current move stays in view, and
scrolls in its own region only when that is not enough. An empty list is not drawn and
takes no room. The current entry is the selected review ply, or the final active ply in a
live game. A change to that identity scrolls it into view;
an unrelated recomposition or refresh leaves a manual scroll alone. Back, headings,
status, captured-piece rows, review navigation and live actions stay outside this
scroll in one-column and two-pane layouts. If an extremely short or large-text window
cannot display all fixed content at once, its fixed region uses a separate bounded
overflow path rather than joining the move-history scroll. `D073`'s board dimensions
and `D104`'s captured-piece placement remain unchanged. This is Android UI state and
adds no DTO, database column, migration or canonical `GameState` field.

## 12. Realtime Architecture

Use:

- HTTPS for commands and ordinary queries,
- WebSockets for incoming realtime updates.

WebSocket delivery is a convenience layer, not the source of truth.

On reconnect, Android must be able to reload canonical state over HTTPS.

Coming back to the app counts as a reconnect. The app reloads what is on screen
and replaces the socket rather than trusting it, because a socket can die
silently while the app is in the background (`D075`).

Nothing is replayed to a client that was away. The server registers the
connection before it sends the `connected` greeting, so a client that reloads on
receiving that greeting cannot fall into a gap: every change committed from then
on is pushed to it, and every earlier one is already in the reload. Missed
messages therefore cost a client a reload and nothing else.

A push names only the game and the version it reached. Clients must not treat
one as state, and a command built on a version the server has moved past is
refused with the canonical state attached rather than applied.

`RealtimeHub` is currently process-local and in-memory. That is sufficient for
a single Ktor beta instance because HTTPS/PostgreSQL remain authoritative, but
running more than one instance needs shared pub/sub first. Sticky sessions
alone would not deliver a move to an opponent connected to another process.

`D032` resolved this for the beta with a single Render Free Web Service, and was
accepted at `M15.1`. The process-local hub is therefore appropriate, because a
Free Web Service cannot scale beyond one instance. Treat
that as a deployment constraint, not a coincidence — moving to a topology with
more than one server process without shared pub/sub would silently lose moves
between players connected to different processes.

## 13. Authentication

Use Supabase anonymous authentication for MVP.

Flow:

```text
Android
→ anonymous Supabase sign-in
→ access token / JWT
→ Ktor request with Bearer token
→ Ktor verifies token
→ internal user identity resolved
```

The Supabase auth subject maps to the application's immutable `userId` through
`user_auth_subjects` (§14). A subject seen for the first time is mapped to a new,
nameless user.

Username is human-facing and must never be used as the authentication credential.

Account recovery is deferred.

A lost anonymous account's username remains reserved for MVP.

### Shared users and the prototype username claim (`D082`)

The two apps share one user. Three things are kept distinct:

```text
username    human-facing shared identity, used only to claim
users.id    permanent logical user identity
Supabase    the anonymous subject is this installation's authentication
            principal; its session/token authenticates it on every request
```

The anonymous Supabase subject is an installation-scoped authentication
principal. It is not the logical user. Many subjects may map to one `users.id`
through `user_auth_subjects(auth_subject PRIMARY KEY, user_id → users)` (§14).
Token verification is unchanged: authentication verifies the token as before and
then resolves its subject through the mapping.

On first launch of either app the user enters a username:

- a name that does not exist creates the user and maps this subject to it;
- a name that already exists maps this subject to that existing user.

"Exists" follows the existing normalization and uniqueness rules (§14, `D007`).
There is no password, verification or ownership challenge. That is deliberate and
**prototype-only**: anyone who types a name reaches that account, and it must be
replaced by real authentication before any untrusted or public production use
(`F13`). Keeping Supabase as the credential leaves its account-linking path open,
so replacing the claim changes no friendship, group, game or `users.id`.

*Implemented by `M20.4`* (`POST /username`). What a claim does depends on the
installation's current user (`D083`):

- **nameless, new name:** the nameless user takes it;
- **nameless, existing name:** the subject is re-pointed to that user and the
  nameless user is deleted in the same transaction, so no second user is left
  behind. It owns nothing to delete: a nameless caller is refused everything that
  would make a row about it (`D045`, `D081`, `D076`);
- **named, the same name in any casing:** nothing changes;
- **named, any other name:** refused with `409`, like a rename.

Taking and attaching both answer `200` with the stored name, so the reply does not
say whether the name was in use. The app then reads `GET /me`, because attaching
changes its `userId`, and reopens its socket if so. The claim locks the caller's
row, and the unique index on `username_normalized` settles a race for a new name:
the loser starts again and attaches to the winner.

## 14. User Model

Conceptually:

```text
User
- userId
- username
- usernameNormalized
- lastSeenAt
- lastLoginAt
- lastActionAt
- createdAt
```

Database requirements:

- `userId` primary key,
- case-insensitive uniqueness through `usernameNormalized`,
- username validation enforced server-side,
- database uniqueness constraint is the final race-safe authority.

The Supabase subjects that authenticate as a user live in `user_auth_subjects`,
not in `users` (`V11`, `M20.4`): the subject is the primary key, so it maps to one
user, and `user_id` references `users`, so a user can have several installations
(§13, `D082`). `V11` carried every former `users.auth_subject` over to it and
dropped the column.

### Activity timestamps

Three, and they are not interchangeable (`D060`, `M19.11`):

| column           | written when                       | accuracy                                         |
| ---------------- | ---------------------------------- | ------------------------------------------------ |
| `last_seen_at`   | any authenticated request (`D010`) | throttled to one write per user per five minutes |
| `last_login_at`  | a session starting — `GET /me`     | exact                                            |
| `last_action_at` | a command being **accepted**       | exact, inside the command's transaction          |

`GET /me` is what "session start" means here: every other authenticated route is
a session being *used*, and this is the one the app calls after restoring or
creating its session, so it is the only place the server can tell the two apart.

A failed `last_seen_at` or `last_login_at` write never fails the request it
describes. It is logged and the request goes on (`D060`, `D080`). A failed
`last_seen_at` write also gives its throttle window back, so the next request
retries it (`D043`). `last_action_at` is different on purpose: it commits or
fails with its command.

"Accepted command" means an accepted *mutation*. A refused command writes
nothing, and neither does reading a game — `GameCommandService.load` returns
`Applied` as well, so the write hangs off the accepted-mutation path rather than
off that result type. It is written in the command's own transaction, so it
cannot claim an action the database did not take.

**None of the three is exposed through the API.** `StoredUser` carries them
because it is a persistence type; `CurrentUser` and `UserSummary` enumerate what
the wire sees and these are not among them.

## 15. Friendship Model

Conceptually:

```text
Friendship
- userAId
- userBId
- status
- createdAt
- removedAt
```

`status` is `ACTIVE` for every friendship the MVP creates; `PENDING` and
`DECLINED` are reserved for a later approval flow and are never written
(`D047`). Removing a friend sets `removedAt`; the row is kept (§17).

Prevent:

- self-friendship,
- duplicate pair,
- reversed duplicate pair.

Use normalized pair ordering or an equivalent database constraint.

Friendship is mutual immediately.

`GET /friends` lists the caller's friends, each with the caller's lifetime chess record
against them, `record: {wins, losses, draws}` (`D096`, `M22.4`). The server counts every
finished chess game between the two across every series from stored results, in two
queries for the whole list (`DashboardQueries.chessRecordsFor`); the field is required, so
a real `0-0-0` is sent rather than left out.

A friend is found by exact username, `GET /users/{username}`, and added with
`POST /friends` (`D009`). As a testing aid, `GET /users` lists
every named user except the caller: people the caller could add first, then
friends, each group ordered by `last_seen_at`, newest first, without sending
the time. Each entry is `userId`, `username` and `friend`, capped at 200 with
people to add first. The app's "All users" page adds from it through the same
`POST /friends`. It is always on and part of the MVP. Removing it or
restricting it to admins is parked post-MVP work (`D071`; `F12` in `docs/FUTURE.md`).

## 15.1 Groups

A **group** is a named, standing pool of people whose only function is
invite-eligibility (`D049`, built in `M19.2`).

```text
Group                 GroupMember
- id                  - groupId
- name                - userId
- createdBy           - addedBy
- createdAt           - joinedAt
                      - leftAt
```

Membership deliberately mirrors friendship (`D009`), so there is one mental model
for "someone added me to something": any member may create a group they belong
to, any member may add one of their **own** friends, membership takes effect
immediately with no accept step, and any member may leave unilaterally. There is
no owner and no admin, and no way to remove anyone else — leaving is the only
exit, which is why the route for it is addressed at the caller
(`DELETE /groups/{id}/members/me`).

**A group has no game-state role at all.** It appears in no game, series, table,
dashboard, or rule; nothing in `groups` or `group_members` references a game or a
series, and leaving revokes future eligibility and touches nothing else.

Two of `D049`'s rules are enforced in `GroupRepository` inside one transaction
rather than by a column constraint, because each needs a row another table owns:

- **a group always contains its creator** — the group and that first membership
  are inserted together, so a memberless group never exists to be observed;
- **the person added is a friend of the adder** — that lives in `friendships`,
  and like series creation (`D046`) the check is a gate at the moment of the
  request, not a standing condition. Unfriending someone afterwards does not
  eject them from a group; leaving is how they get out.

One row per person per group, as with `friendships`: leaving records `left_at`
and being added again revives that row, so "are they in it right now" stays a
single lookup. An empty group is kept rather than deleted, and makes nobody
eligible to anybody.

### Invite eligibility

`D048` routes table invites **through the host only**: each invited participant
needs a relationship to whoever is assembling the table and none to each other.
`InviteEligibility.canInvite(host, target)` is that one question — a friendship,
or a group in common. Table and series creation do not call it (`D046`, `D064`):
the invite UI is the gate, and it follows the same rule.

In chess, that invite UI is the app's Groups screen (`D076`, `M17.11`). It offers
Play for every other current member of a group the player is in, through the same
`POST /series` as Play from Friends. The server needs nothing new for it. Group
changes are not realtime events; the screen reloads when opened and when the app
comes back (`D075`).

Eligibility through a group is **transitive**, which is the point of groups: a
four-person table needs one relationship to the host, not six pairwise
friendships. Two members of the same group may invite each other without ever
having been friends.

Eligibility is not the whole invite check. Whether the *table* is valid — its
size within the game type's range, its participant set distinct — belongs with
the table and is `M19.3` work.

## 15.2 Relationship Profiles

`M22.5` adds a per-viewer ChessGame profile addressed by immutable user ID
(`D099`), `GET /profiles/{userId}` (`user/ProfileRoutes.kt`). Every read authorizes the current friendship or shared-group relationship
and returns only username, UTC join month/year, viewer-relative chess record and the
current group intersection. It returns `404` for both an unknown subject and a known
subject without a qualifying relationship. The viewer's own group-member row is not
a profile entry point. Profile responses are viewer-specific and are never cached
across users (`D069`, `D070`).

## 16. Game Series Model

A `GameSeries` represents the ongoing sequence of games at one **table**: an exact
set of participants for one game type (`D048`). Since `M19.3` the series no longer
names a pair of players; it names its table, and the table names its participants.

Conceptually:

```text
Table
- tableId
- gameType            -- whose registered range decides how many it seats
- participants        -- in seat order; unique as a set per game type

GameSeries
- seriesId
- tableId
- currentGameId
- status
- automaticRematch
- createdAt
- updatedAt
```

Recommended status:

```text
ACTIVE
CLOSED
```

How many participants a table seats is **stored per game type** (`game_types`),
read at table creation, and never assumed (`D063`). Chess is registered as exactly
2 and is the only game type. Nothing in the schema caps a table at 2.

Series identity is an **exact-set match**: the same people reach the same table,
and a different set is a different table and a different series.

For chess after `M22.1`:

- an exact participant pair has at most one `ACTIVE` series (`D095`, superseding
  `D053` rule 1),
- `POST /series` starts a series and its first game when the pair has none (`201`),
  and otherwise returns the existing series; there is no `SeriesOffer` or supported
  `another=true` path,
- whether a table has a series is decided under the table row's lock, so two
  simultaneous first taps start one series, not two,
- the database enforces this for chess only by locking the table row in a trigger (or
  an equivalently strong mechanism) and rejecting a second active chess series; no
  global series constraint chooses behavior for another game type,
- a participant leaves a series with `POST /series/{seriesId}/leave`. In the same
  transaction it closes the series and ends its current game: a started game is
  finalized as the leaver's resignation; a zero-move game is deleted without a
  result (`D095`),
- closed series remain historical,
- creation does not check that the pair are friends (`D046`), so a series may
  outlive — or never have had — a friendship between its two players,
- creation does refuse a caller who has not claimed a username (`403`, `D081`), as
  `POST /friends` does (`D045`), so both players of every new series can see it.

## 17. Friend Removal and Series Lifecycle

Removing a friend affects the friends list only (`D053`, superseding `D013`):

1. the friendship row is deactivated rather than deleted, so it stays in history,
2. no series is closed, marked, or otherwise written,
3. no game is touched, and automatic rematches carry on.

A series persists independently of the friend graph. Friendship matters only when
Play is first offered (`D046`, `D048`). A series ends only when a participant leaves
it (`D052`, `M19.8`): `POST /series/{seriesId}/leave` closes it under its row lock and
records `SeriesLeft`. Leaving also ends its current game (`D095`), unlike ordinary
resignation, which ends one game while an active series carries on. A started game is
finalized as a resignation; a zero-move game is deleted. Closed series have no
unfinished game and remain readable as history when they contain finished games.
Leaving remains idempotent. `games.has_started` records whether an ordinary move has
ever been accepted and is never cleared by Undo or Redo, so an empty active move list
cannot turn a played game into a zero-move game.

Every command path that may end a game and settle its series locks the series row
before the game row; Leave follows the same order. A shared helper owns this ordering
so a Leave/game-end race cannot deadlock.

Until `M19.5`, removal marked the pair's active series to close after its current
game (`game_series.close_after_current_game`). `V7` dropped that column along with
the code that wrote and read it.

## 18. Game Model

Generic game lifecycle data belongs outside the chess engine.

Conceptually:

```text
Game
- gameId
- seriesId
- participants        -- one per seat, in turn order (game_participants)
- status
- currentTurnPlayerId
- version
- currentState
- result
- hasStarted (`M22.1`: true after the first accepted ordinary move; never cleared)
- createdAt
- completedAt
```

The exact persistence representation of `currentState` may be refined during implementation.

Seat order is turn order. Chess maps White to seat 0 and Black to seat 1
(`ChessSeats`); that mapping lives in chess code, and the participants relation
itself knows nothing about colours. A seat is a participant of a kind (`D051`,
`D067`): `USER` is a person with a `users` row, `COMPUTER` is not a person but
rotates like one, and `SCRIPTED` never rotates and always moves last. A non-user
participant lives in `non_user_participants`, with an opaque `state` document for
its game type's rules. Anything person-shaped (friends, dashboards, `lastSeenAt`,
usernames, realtime recipients, chess colours) reads only the `USER` seats.

## 19. Chess State Persistence

The in-memory chess model remains strongly typed.

Persistence may use FEN plus additional structured history/state as appropriate.

Do not treat SAN as canonical move data.

Persist enough information to support:

- legal continuation,
- draw rules,
- exact undo,
- history,
- debugging.

## 20. Move History

Store structured active/historical moves.

A move record may contain:

```text
moveId
gameId
ply
playerId
from
to
promotionPiece
san
positionBefore
positionAfter
createdAt
undoneAt
```

The exact schema can be refined.

Undo must restore the full prior chess state, including:

- board,
- side to move,
- castling rights,
- en passant,
- half-move clock,
- full-move number,
- repetition state.

## 21. Undo Semantics

`UndoMove` is a server-authoritative command.

It succeeds only if:

- game is active,
- requester made the latest active move,
- opponent has not responded,
- expected version is current,
- the latest move was not game-ending.

If the opponent had moved and later undoes their response, the prior player's move becomes the latest active unanswered move again and can become undoable.

**Undo requests (`D091`).** Once the opponent has responded, the player whose move it
was may send an undo request (`requestUndo`) on their turn. The opponent accepts
(`acceptUndo`) or declines (`declineUndo`), and each command increments the version.
While a request waits on the opponent, the opponent's `UndoMove` at the request's
version is an accept. Accepting restores the position before the requester's move:
the opponent's reply and the requester's move are taken back together, as one two-ply
Undo operation that Redo restores whole (`ChessRules.undoLastMoves`, `D092`). Restoring
never replays. An accept succeeds only at the version the request was made at, with one
exception. A request and the opponent's `UndoMove` that cross, each stale by exactly
the other, are combined in either order: the second completes the first. The game row
records the last accepted change (kind, actor, version applied to, and the move an
undo took back, which the crossing's note names) for that check (`V12`).
Every other accepted command on the game clears a pending request in the same
transaction; leaving the series is not such a command. The game row stores the
pending request and the last answer, which the next accepted command also clears.
Both stay outside `GameState` and `chess-core`, and are projected per viewer
(`D069`). The dashboard names what waits on the viewer in its own field; `yourTurn`
keeps meaning "it is your move".

`M22.7` added required `san` to the existing `MoveView` and changed
`GameView.moves` from coordinate strings to `List<MoveView>`; `V16` dropped the
pending requests, notes and last-change records that predated it. The existing
`from`/`to`/`promotion` fields remain the canonical move identity; SAN is derived by
the server from the recorded `positionBefore`. Undo request and note fields use the
same move view. Pending and declined requests use active move records; accepted and
crossed notes use the retained Redo operation. Android always renders SAN. Missing
SAN or position context is an invariant/protocol failure, not a coordinate fallback.
The beta cutover clears pre-existing request and answer metadata plus
`last_change_kind`, `last_change_actor`, `last_change_version` and `last_change_move`
instead of carrying unrenderable or stale crossing metadata forward (`D101`). SAN
remains derived display data, never canonical persistence.

An undone move disappears from active chess history but remains represented in audit history.

**History layers (`D101`).** Chess keeps canonical current state, active
`MoveRecord`s, the retained `redoPath`, and normally append-only audit as different
records with different lifecycles. Player move lists, undo wording and completed review are
required server projections of the structured move records; they are not audit rows
and are not recreated by client rule replay. Chess and the Deck Builder independently
conform to `D101`'s common history layers. `D102` adds one common invariant for a
retained future: a ruleset-specific structured action with the exact recorded
predecessor advances that continuation, while a mismatch branches. The Deck Builder
has its own action identity, snapshot policy and visibility projection; neither game
follows the other's architecture. They may share infrastructure and presentation
contracts, not a generic action schema.

**Captured-piece and material projections (`D104`, built by `M23.1`).** Both are
derived display data, not canonical state or persistence columns, but they have
different sources. `ChessRules.capturedPiece` identifies the captured `Piece` from
each active `MoveRecord`'s recorded pre-move position, including en passant and
captures of promoted pieces, without replay. Local and computer games aggregate
those records on-device (`CapturedPieces.forGame`). The server adds the same typed,
optional fact to its recorded-move projection for online games (`MoveView.captured`, a
`CapturedPieceView` of side and type); Android aggregates that projection
(`OnlineGame.capturedRowsFor`) and does not replay coordinates, parse SAN or infer
captures from board inventory.

The `+N` indicator is instead the current position's material balance.
`ChessRules.materialBalance` sums each side's live non-king pieces at queen 9, rook 5,
bishop 3.25, knight 3 and pawn 1, in exact quarter-pawn units (36, 20, 13, 12 and 4,
`Material`), never floating-point arithmetic. Local and computer games query their
current `GameState`; the server projects it for an online game as optional
`whiteMaterialBalanceQuarterPoints` (White's total minus Black's), and Android maps its
sign to the correct player row and formats it in pawn-value points. A missing balance
shows no `+N`. This remains correct when a pawn promotes, whereas subtracting either
captured-piece totals or the current board from the starting inventory does not. Undo,
Redo and matching continuation update both projections through the current active
history and state. `GameLayout` places orientation-ordered rows around a one-column
board, whose size leaves room for them (`GameLayoutSpec.forWindow(..., captureRows =
true)`), and in the right panel beside an unchanged two-pane board. Completed-game
review does not show them.

**Redo (`D092`, built by `M21.21`).** An Undo no longer discards what it took back.
`ChessGame` carries a `redoPath`: the accepted Undo operations, each an
`UndoneOperation` holding the move records it removed (each with its `positionBefore`)
and the `stateAfter` it left, the next to restore last. `ChessRules.undoLastMoves`
records one operation of one or two plies; `undoLastMoveJoiningNext` joins a crossed
undo request's move to the reply the opponent had undone, so both `D091` orders leave
the same two-ply operation. `redoableSide`/`canRedo` and `redo` are the query and
transition: Redo belongs to the side to move, restores the recorded records and state
without replaying anything, and is never offered in a finished game. `claimDraw` and
`resign` clear the path, and so does `applyMove` for any move other than the next
retained one.

`M22.2` implemented `D102`'s correction in `ChessRules.applyMove` and its query
`ChessRules.retainedMove`, so the server, the local store and the computer share it. A submitted move whose canonical identity
and complete current state match the next retained `MoveRecord` consumes that one
record, restores its recorded resulting state, and preserves the remaining suffix. A
different legal move clears the suffix. Partial consumption turns the unconsumed part
of a two-ply operation into the next operation without silently moving for an online
opponent. A recorded computer reply is restored by the computer-turn driver with no
engine search. The locked online move transaction still increments the version once
and audits the submitted move as `MoveMade`; `MoveRedone` remains exclusive to the
explicit Redo command. Matching play is a board/history change and clears request
metadata under `D091` and `D097`.

Online, `Redo` (`POST /games/{gameId}/redo`, `expectedVersion`) is validated in the
locked transaction like every command, increments the version once whether it
restores one ply or two, records `ChangeKind.REDO` as the last change, clears a pending
undo request and the last answer, and writes one `MoveRedone` audit event. The path is
`games.redo_path` (`V13`), written in the same statement as state and history; the
per-viewer view exposes only `canRedo` (`D069`).

## 22. Final Moves

A terminal move is immediately final.

There is no:

- grace period,
- acknowledgement,
- pending-final state.

A game-ending move cannot be undone.

## 23. Draw Semantics

The chess engine must distinguish claimable and automatic draws.

Claimable:

```text
threefold repetition
fifty-move rule
```

Automatic:

```text
fivefold repetition
seventy-five-move rule
stalemate
insufficient material
```

A valid `ClaimDraw` command finalizes a claimable draw.

Invalid claims are rejected server-side.

The engine must expose enough information to determine claim entitlement from the canonical history and any relevant prospective legal move condition.

Draw offers by agreement are outside MVP and were built in `M22.3` (`D097`).
They are server-authoritative request metadata, not `chess-core` state. Either
player may offer during an unfinished game; one draw offer may coexist with one
undo request. Neither has a timer. Request-only changes preserve the other
request. A move, Undo, Redo, resignation, draw claim, or any other board, history
or result change invalidates both. Request validity therefore uses a persisted
gameplay-state revision, or an equivalent check that ignores intervening
request-only versions while rejecting any intervening gameplay change.

`M22.3` replaces legacy undo-only `pendingRequest` with required, non-null
`pendingRequests` in one beta contract cutover. The server always emits the field,
including an empty list, and the client supplies no missing-field default or legacy
fallback. Cross-version APK/server compatibility is not supported during this beta
change.

## 24. Game Completion and Automatic Rematch

When a game reaches a final result, the server performs the lifecycle transition atomically.

If the series remains active:

```text
persist final action/move
→ finalize game
→ persist result
→ create next game exactly once
→ alternate colors
→ set series.currentGameId
```

A series is never closed by a game ending. It closes only when a participant
leaves it, at once, under its row lock (§17). Leaving and ending the current game
are one transaction (`D095`), so a closed series never has an unfinished game.

The operation must be safe under retries and concurrent observation.

## 25. Color Assignment

Turn order is decided by seat rotation by cycle (`D050`, `M19.6`), and chess's
colours are its two-seat case (`D014`). Seat order is turn order, so seat 0 plays
White.

For a newly created series:

- the first cycle's base order is drawn at random: the coin toss for White.

For automatic rematches:

- the rotation moves one seat, which for two seats alternates colours every game,
  across cycle boundaries too (`D066`).

`SeatRotation` is pure. A series stores its place in the rotation in
`game_series.seat_rotation`, per series rather than per table. Chess permits only
one active series for an exact participant pair (`D095`), but historical series
retain their own rotation state (`D066`).

## 26. Database Ownership

The server owns privileged access to canonical application/game tables.

Android does not receive general database-write authority.

The implemented persistence stack is:

- JetBrains Exposed's typed SQL DSL (not its DAO/entity layer),
- HikariCP connection pooling, each request connection limited to a 30 s
  statement, 60 s idle in a transaction and 10 s waiting for a lock (`D077`),
- the PostgreSQL JDBC driver at runtime,
- Flyway applying forward-only SQL files from `database/migrations/`, on
  connections of its own with no limits (`D077`).

The SQL migrations are the schema source of truth. Exposed maps queries and
transactions but does not generate or own the schema. `D030` records the
selection and rationale.

## 27. Database Tables

Initial schema concepts:

```text
users
friendships
game_series
games
moves
game_events
```

Added since:

```text
groups              -- D049, M19.2: standing invite-eligibility pools
group_members       -- one row per person per group; left_at rather than deletion
game_types          -- D063, M19.3: each game type's min/max participants
tables              -- D048, M19.3: one row per exact participant set per game type
table_participants  -- who sits at a table, by seat
game_participants   -- who took which seat in one game
non_user_participants -- D051, D067, M19.7: participants that are not people, with opaque state
user_auth_subjects  -- D082, D083, M20.4: which user each installation's Supabase subject is
```

Columns removed since:

```text
game_series.user_a_id / user_b_id   -- M19.3: replaced by game_series.table_id
games.white_user_id / black_user_id -- M19.3: replaced by game_participants
users.auth_subject                  -- M20.4: replaced by user_auth_subjects
```

Columns added since:

```text
friendships.status                  -- D047
users.last_login_at                 -- D060, M19.11: a session starting
users.last_action_at                -- D060, M19.11: a command being accepted
game_series.table_id                -- D048, M19.3: the table a series belongs to
game_series.seat_rotation           -- D050, D066, M19.6: the series' place in its seat rotation
```

Use database constraints for race-sensitive invariants where possible, including:

- normalized username uniqueness,
- one user per Supabase subject,
- friendship uniqueness,
- one table per exact participant set per game type,
- one seat per participant per table and per game,
- rematch/idempotency-related uniqueness where appropriate,
- one group membership per person per group.

Invariants that need a row in another table cannot be constraints and are
enforced in a repository transaction instead — a group containing its creator,
and a group member being a friend of whoever added them (§15.1), a table's size
lying within its game type's range, and a table's participant-set key agreeing
with its seats (§16).

## 28. Security Boundary

The server must verify:

- valid authentication,
- internal user identity,
- game participation,
- turn ownership,
- expected version,
- move legality,
- undo eligibility,
- draw-claim eligibility,
- game status,
- series lifecycle.

Never trust client-supplied:

- username as identity,
- winner/result,
- replacement board state,
- client-only legality decisions.

## 29. Android Architecture

Prefer feature-oriented Android organization.

Example:

```text
chess-app/
├── onboarding/
├── dashboard/
├── friends/
├── game/
├── history/
├── navigation/
└── data/
```

Typical flow:

```text
Compose UI
→ ViewModel / screen state
→ repository / API client
→ Ktor
```

Do not introduce heavyweight Clean Architecture ceremony merely for pattern compliance.

Local games (§11.4, `D084`) follow a second flow that never reaches Ktor:

```text
Compose UI
→ ViewModel / screen state
→ LocalGameSession → LocalGameStore
→ SQLDelight (on the device)
```

The local entry points (the local game, Play the computer, and past local games)
stay reachable when startup cannot reach the server. As built by `M21.4`: the
startup screen offers "Local game" and "Past local games" in every state short of
`Ready` (starting, waking the server, failed), next to Retry. They open on top of
the startup screen, so Back returns to it. If startup succeeds while the player is
in a local screen, the dashboard (or onboarding) replaces the startup screen
*beneath* it (`AppNavigation.rebasedOn`) instead of replacing the whole stack:
the player stays where they are, and Back leads to the dashboard. Online screens
are unreachable until startup succeeds.

The online game state must be separate from local pass-and-play state. Android
may use `chess-core` for board rendering, legal-move previews, and deterministic
UX, but an online board changes only when an authenticated server response is
accepted or canonical state is reloaded. Every command carries the currently
loaded expected version.

The Android source has `AnonymousAuthenticator`, `SessionStore`,
`ChessApiClient`, `DashboardScreen`, `HistoryScreen`, and reusable board
components, and since `M14.5` an application shell — `MainActivity` →
`ChessApp` → `ChessAppViewModel` → `ChessAppDependencies` — that owns
navigation and the shared HTTP client (`D033`). `M14.6` added `AppStartup`,
which restores or creates the anonymous session before the app leaves the
startup screen, and the one access-token provider everything authenticated asks
per call. `M14.7` made `GET /me` a typed `CurrentUser` — the immutable user id
and a nullable username — so startup can tell a returning player from a new one
and send each to the dashboard or to username onboarding. `M14.8` added the
friends screen: lookup by exact username, add, remove with its confirmation, and
"Play", which opens whichever game `POST /series` says is current. `M14.9` made
the dashboard the live landing screen, loading the active series and the friends
list together. `M14.10` made `GET /games/{gameId}` carry the opponent and the
last move as structured data, so a game screen needs only a game id to draw
itself, and added the read-only online game screen. `M14.11` connected board
interaction to `POST /games/{gameId}/moves`, previewing legal destinations by
replaying the canonical move list and changing the board only from what the
server answered. `M14.12` added the authenticated WebSocket client: one socket,
messages used only as invalidation, and every reload over HTTPS (`D022`).
`M14.13`–`M14.15` added undo, draw claims, and resignation, each carrying the
version it was decided against and rendering only what came back; `M14.16` made
the app follow the series to the game the server created next without ever
creating a rematch itself; `M14.17` made history reachable and a finished game
readable. The typical flow above is therefore in place end to end. `M14.18` then
proved it with a two-client play-through, and `M17.1` proved it again where it
counts: one other person installed a signed APK on their own physical device,
got through onboarding unaided, and played an online game to the end with no
developer intervention.

## 30. Server Architecture

Recommended conceptual organization:

```text
server/
├── auth/
├── users/
├── friends/
├── series/
├── games/
├── commands/
├── realtime/
├── persistence/
└── application/
```

Command handlers orchestrate:

```text
authenticate
→ load state
→ validate permission/version
→ execute rules
→ persist transaction
→ publish update
```

### More than one ruleset (`D082`)

When the Deck Builder exists, `game_type` is the discriminator. Table, series and
social routes and services stay shared. At the application or service boundary, an
explicit `when` reaches the concrete implementation:

```kotlin
when (gameType) {
    CHESS -> chessCommandService.execute(...)
    DECK_BUILDER -> deckCommandService.execute(...)
}
```

No generic rules interface is introduced to make this look polymorphic (`D044`).

- **Each rules module owns** its state, legal commands, transitions, terminal and
  minimum-player rules, viewer projection semantics, and what an undo barrier
  means.
- **The server owns** serialising only the correct projection to each viewer
  (`D069`, `docs/GAME-STATE-VISIBILITY.md`), and each ruleset's own persistence document, as chess has
  `GameStateDocument`, rather than one generic state type. It also owns undo
  snapshot storage, append/truncate, pruning and transactions (`D061`), without
  knowing what a shuffle means.

### Logging failures

A failure the server recovers from still leaves a trace (`M19.12`). The level is
chosen by **signal, not severity**: a line reaches `INFO` when it means something
went wrong for a player — a realtime send that failed, a refused command, a
rejected bearer token — and stays at `DEBUG` when it is only detail, which
includes the whole socket lifecycle. A realtime send that *times out* is `WARN`,
because a socket that neither delivers nor fails is the `M12-01` pathology.
`ServerLogging.kt` carries the table.

Never logged, at any level: headers, bodies, and the *message* of a rejected
token — the JWT library quotes malformed input back in its own exception
messages, so relaying one could put credential material in a log (`M16.5`).

## 31. Future Deck-Builder Compatibility

This section used to list fourteen concepts "expected to survive beyond chess".
That was a prediction made before the MVP existed. `M18.1` reviewed the finished
implementation instead of predicting it, and the result is
`docs/PLATFORM-REVIEW.md`: what is chess-specific, what is a proven platform
concept and by what evidence, what is worth extracting later, what should stay
concrete, and — most usefully — what chess did not prove at all.

Read that document before designing the deck-builder. Two of its findings change
what this file says elsewhere:

- Client-side pre-validation (§7, §11.2) and shipping the whole position to both
  players (`GameView`) work because chess is a game of complete information.
  Neither survives hidden information.
- Undo's *mechanism* (§21) is a platform concept, but its lock predicate is
  chess's own. "Locked once the opponent responds" is retrospective and can flip
  back; a game with hidden information also needs an intrinsic lock for any
  action that revealed something to anyone, the actor included.

Undo *storage* beyond chess has since been decided. Under `D061`, deck-builder
undo keeps one full snapshot at every independently undoable command boundary,
persists it with append/truncate semantics, and prunes history at shuffle
barriers. Canonical restoration never replays commands through the rules. That
is §20's exact-restore principle (`D029`), carried forward.
`docs/UNDO-STORAGE.md` holds the measurements.

`D044` decides what follows: **nothing is extracted, generalised, or renamed
until a second ruleset exists**, and it names the six candidates for that moment.
No generic engine interface, no command hierarchy, no seat abstraction, no
generic action table, no platform module — §5, §8, and §32 already forbid these
and the review confirms they were right to.

`D082` (2026-09-26) decides where the second game goes. It is a separate app
(`deck-app`) with its own rules module (`deck-core`), on this repository's one
server, one database and shared users (§4, §13, §30). It changes none of the above:
the only renames it makes are `game-core` → `chess-core` and
`android-app` → `chess-app` (done by `M20.3`), and anything shared is
still extracted from two implementations, not one.

Do not add future deck-building mechanics yet.

Expected later concepts may include:

```text
Cards
Decks
Hands
Discard piles
Markets
Resources
Effects
Triggers
Action queue
Player choices
Hidden information
Seeded randomness
```

When the second game exists, compare concrete implementations before extracting additional shared abstractions.

## 32. Explicitly Rejected Approaches

Do not use as the primary MVP architecture:

- client-authoritative direct database writes,
- peer-to-peer canonical state,
- PHP backend,
- full event sourcing,
- microservices,
- premature universal board-game framework,
- Kotlin Multiplatform solely for hypothetical future clients.
