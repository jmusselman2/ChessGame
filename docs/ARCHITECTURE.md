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

| Area | Choice |
|---|---|
| Android language | Kotlin |
| Android UI | Jetpack Compose |
| Shared game logic | Pure Kotlin/JVM module |
| Backend | Kotlin + Ktor |
| Database | PostgreSQL 18 locally/CI; beta on `ChessGame Dev`'s PostgreSQL (`D035`) |
| SQL access | JetBrains Exposed DSL over HikariCP |
| Migrations | Flyway applying forward-only SQL files |
| Authentication | Supabase anonymous auth |
| Serialization | Kotlin serialization + JSON |
| Commands / queries | HTTPS |
| Realtime updates | WebSockets |
| Build | Gradle Kotlin DSL |
| Repository | Monorepo |

`game-core` starts as Kotlin/JVM because both current consumers are JVM-based: Android and the Ktor server.

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
│        └──── uses game-core locally for rules/UX            │
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
│        └──── uses the same game-core                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌──────────────────── POSTGRESQL ─────────────────────────────┐
│ users                                                       │
│ friendships                                                 │
│ game_series                                                 │
│ games                                                       │
│ moves                                                       │
│ game_events                                                 │
└─────────────────────────────────────────────────────────────┘
```

## 4. Repository Modules

Recommended high-level layout:

```text
.
├── game-core/
├── android-app/
├── server/
├── database/
│   └── migrations/
└── docs/
```

Allowed dependencies:

```text
android-app ──→ game-core
server      ──→ game-core
```

Forbidden dependencies:

```text
game-core ──→ android-app
game-core ──→ server
game-core ──→ database
game-core ──→ Supabase
```

### Implementation status

This document describes the intended structure, not how much of it is built.
**`docs/BACKLOG.md` is the source of truth for implementation status**, task by
task, and this section deliberately does not restate it: the snapshot that used
to live here was pinned to a single commit and went stale as the work passed it.

What is durable is the shape. Every module the diagram above names exists and is
exercised by `./gradlew build` — `game-core`, the Ktor command/query surface with
PostgreSQL persistence and realtime publication, and an Android application wired
end to end from `MainActivity` through `ChessApp`. `docs/PLATFORM-REVIEW.md`
reviews what building all of it proved about the boundaries described here, and
`M18`'s completion note in `docs/BACKLOG.md` says where that leaves the project.

## 5. `game-core`

`game-core` contains pure chess rules and state.

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

`game-core` must not depend on:

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

Keep them in the Android/server application layers rather than `game-core`.

## 7. Server Authority

The Android client is untrusted.

Android may use `game-core` to pre-validate a move and provide immediate UX feedback, but it does not decide the canonical result.

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
→ execute through game-core
→ persist atomically
→ increment version
→ publish update
```

Android must not directly read or write canonical game tables through Supabase database APIs.

Normal canonical game access goes through Ktor.

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
```

The implementation does not need one universal generic command hierarchy if concrete command types are simpler.

## 9. Audit Events

Persist an append-only audit history for meaningful changes.

Examples:

```text
MoveMade
MoveUndone
DrawClaimed
PlayerResigned
GameEnded
RematchCreated
SeriesClosed
FriendAdded
FriendRemoved
```

Do not use full event sourcing.

Normal game loads should not require replaying every event.

Persist:

```text
canonical current state
+
active move history
+
append-only audit events
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

The Android app holds the latest server-confirmed game state in memory.

It may use `game-core` for:

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

## 12. Realtime Architecture

Use:

- HTTPS for commands and ordinary queries,
- WebSockets for incoming realtime updates.

WebSocket delivery is a convenience layer, not the source of truth.

On reconnect, Android must be able to reload canonical state over HTTPS.

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

The Supabase auth subject maps to the application's immutable `userId`.

Username is human-facing and must never be used as the authentication credential.

Account recovery is deferred.

A lost anonymous account's username remains reserved for MVP.

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

### Activity timestamps

Three, and they are not interchangeable (`D060`, `M19.11`):

| column | written when | accuracy |
|---|---|---|
| `last_seen_at` | any authenticated request (`D010`) | throttled to one write per user per five minutes |
| `last_login_at` | a session starting — `GET /me` | exact |
| `last_action_at` | a command being **accepted** | exact, inside the command's transaction |

`GET /me` is what "session start" means here: every other authenticated route is
a session being *used*, and this is the one the app calls after restoring or
creating its session, so it is the only place the server can tell the two apart.

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
```

`status` is `ACTIVE` for every friendship the MVP creates; `PENDING` and
`DECLINED` are reserved for a later approval flow and are never written
(`D047`).

Prevent:

- self-friendship,
- duplicate pair,
- reversed duplicate pair.

Use normalized pair ordering or an equivalent database constraint.

Friendship is mutual immediately.

A friend is found by exact username, `GET /users/{username}`, and added with
`POST /friends` (`D009`). As a temporary testing aid, `GET /users` lists
everyone the caller could add: not the caller, not a current friend, not an
unnamed account. It is ordered by `last_seen_at`, newest first, without sending
the time, and returns `UserSummary` entries only, capped at 200. The app's
"All users" page adds from it through the same `POST /friends`. It is always on
and is to be removed or restricted to admins before the app has real users
(`D071`).

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
or a group in common — and it is where `M19.3`'s table creation will get its
gate instead of reimplementing the rule.

Eligibility through a group is **transitive**, which is the point of groups: a
four-person table needs one relationship to the host, not six pairwise
friendships. Two members of the same group may invite each other without ever
having been friends.

Eligibility is not the whole invite check. Whether the *table* is valid — its
size within the game type's range, its participant set distinct — belongs with
the table and is `M19.3` work.

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

For MVP:

- a table may have **several** `ACTIVE` series at once (`D053`, superseding
  `D011`; `M19.4`),
- "Play" never silently reuses one: `POST /series` starts a series and its first
  game when the pair has none (`201`), and otherwise starts nothing and answers
  `409` with a `SeriesOffer` listing the active series, newest first;
  `POST /series?another=true` is the player choosing another (`D065`),
- whether a table has a series is decided under the table row's lock, so two
  simultaneous first taps start one series, not two,
- a participant leaves a series with `POST /series/{seriesId}/leave`, which closes it at
  once and touches no game (`D052`, `D068`); the dashboard keeps a closed series while its
  last game is unfinished, marked `seriesActive: false`,
- closed series remain historical,
- creation does not check that the pair are friends (`D046`), so a series may
  outlive — or never have had — a friendship between its two players.

## 17. Friend Removal and Series Lifecycle

Removing a friend affects the friends list only (`D053`, superseding `D013`):

1. the friendship row is deactivated rather than deleted, so it stays in history,
2. no series is closed, marked, or otherwise written,
3. no game is touched, and automatic rematches carry on.

A series persists independently of the friend graph. Friendship matters only when
Play is first offered (`D046`, `D048`). A series ends only when a participant leaves
it (`D052`, `M19.8`): `POST /series/{seriesId}/leave` closes it under its row lock and
records `SeriesLeft`. Leaving is separate from resigning, which ends one game and lets the
series carry on. A closed series lets its current game finish, gets no rematch, and stays
readable as history (`D012`). Leaving is idempotent (`D068`).

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

An undone move disappears from active chess history but remains represented in audit history.

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

Draw offers by agreement are outside MVP.

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

If the series is marked to close:

```text
persist final action/move
→ finalize game
→ persist result
→ do not create next game
→ set series.status = CLOSED
```

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
`game_series.seat_rotation`, per series rather than per table, because a table may
hold parallel series (`D053`, `D066`).

## 26. Database Ownership

The server owns privileged access to canonical application/game tables.

Android does not receive general database-write authority.

The implemented persistence stack is:

- JetBrains Exposed's typed SQL DSL (not its DAO/entity layer),
- HikariCP connection pooling,
- the PostgreSQL JDBC driver at runtime,
- Flyway applying forward-only SQL files from `database/migrations/`.

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
```

Columns removed since:

```text
game_series.user_a_id / user_b_id   -- M19.3: replaced by game_series.table_id
games.white_user_id / black_user_id -- M19.3: replaced by game_participants
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
android-app/
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

The online game state must be separate from local pass-and-play state. Android
may use `game-core` for board rendering, legal-move previews, and deterministic
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
