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
| Database | PostgreSQL 18 locally/CI; beta PostgreSQL host not selected |
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
PostgreSQL, not to that Supabase database. `M15.3` will additionally apply it to
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

### Current implementation boundary

As of `0ef3228`, `game-core`, the Ktor command/query surface, PostgreSQL
persistence, and server realtime publication are implemented. Android contains
a working local pass-and-play screen plus tested auth, API-read, dashboard, and
history components, but `MainActivity` still opens local play directly. The
application/runtime-network shell, startup authentication, online game client,
command calls, WebSocket consumption, and navigation are `M14.5`–`M14.18`;
isolated Android components must not be mistaken for an integrated multiplayer
application. The manifest does not yet grant network access or define a
development-only policy for the cleartext emulator-loopback server.

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

`D032` proposes resolving this for the beta with a single Render Free Web
Service. If that proposal is accepted at `M15.1`, the process-local hub is
appropriate because a Free Web Service cannot scale beyond one instance. Treat
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
- createdAt
```

Database requirements:

- `userId` primary key,
- case-insensitive uniqueness through `usernameNormalized`,
- username validation enforced server-side,
- database uniqueness constraint is the final race-safe authority.

## 15. Friendship Model

Conceptually:

```text
Friendship
- userAId
- userBId
- createdAt
```

Prevent:

- self-friendship,
- duplicate pair,
- reversed duplicate pair.

Use normalized pair ordering or an equivalent database constraint.

Friendship is mutual immediately.

## 16. Game Series Model

A `GameSeries` represents the ongoing sequence of games between two players.

Conceptually:

```text
GameSeries
- seriesId
- playerAId
- playerBId
- currentGameId
- status
- automaticRematch
- closeAfterCurrentGame
- createdAt
- updatedAt
```

Recommended status:

```text
ACTIVE
CLOSED
```

For MVP:

- at most one `ACTIVE` series per pair,
- an existing active series is opened instead of creating a parallel one,
- closed series remain historical.

## 17. Friend Removal and Series Lifecycle

When a friendship is removed:

1. delete/deactivate the friendship relationship,
2. retain all game history,
3. if an active series exists, set it to close after the current game,
4. do not terminate the current game,
5. suppress creation of the next automatic rematch,
6. when the current game ends, set the series to `CLOSED`.

The close transition must be idempotent.

If the friendship is restored later, a new active series may be created.

## 18. Game Model

Generic game lifecycle data belongs outside the chess engine.

Conceptually:

```text
Game
- gameId
- seriesId
- whitePlayerId
- blackPlayerId
- status
- currentTurnPlayerId
- version
- currentState
- result
- createdAt
- completedAt
```

The exact persistence representation of `currentState` may be refined during implementation.

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

For a newly created series:

- assign White/Black randomly.

For automatic rematches:

- alternate colors from the previous game.

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

Use database constraints for race-sensitive invariants where possible, including:

- normalized username uniqueness,
- friendship uniqueness,
- one active series per pair,
- rematch/idempotency-related uniqueness where appropriate.

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
readable. The typical flow above is therefore in place end to end. What remains
is `M14.18`: the two-client play-through on a real emulator or device, which
needs infrastructure this repository cannot provide for itself.

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

## 31. Future Deck-Builder Compatibility

The following are expected to survive beyond chess:

- identity,
- usernames,
- friendships,
- last seen,
- dashboard,
- game series,
- game lifecycle,
- command submission,
- audit events,
- versioning,
- concurrency,
- synchronization,
- server authority,
- persistence,
- history.

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
