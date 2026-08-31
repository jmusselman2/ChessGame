# Chess MVP — Decision Log

This file is the highest-precedence source for accepted product and architecture decisions.

When a decision changes:

- do not erase the old entry,
- add a new decision,
- mark the old one `Superseded` when appropriate,
- identify what supersedes it.

Date format: `YYYY-MM-DD`.

---

## D001 — Kotlin Is the Primary Language

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use Kotlin for:

- Android,
- shared game logic,
- Ktor server.

Use SQL for PostgreSQL schema/migrations.

### Rationale

Using Kotlin across client, shared rules, and server reduces language switching and allows the same pure chess engine to run on Android and the JVM server.

### Alternatives Considered

- Java
- TypeScript/Node backend
- C#/Unity
- PHP

### Consequences

The backend is intentionally Kotlin/Ktor rather than a JavaScript/TypeScript serverless-first architecture.

---

## D002 — Native Android + Jetpack Compose

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Build the MVP as a native Android application with Kotlin and Jetpack Compose.

### Rationale

The immediate product target is Android, and native Compose provides a direct modern Android development path without introducing a cross-platform UI framework.

### Alternatives Considered

- Flutter
- React Native
- Unity

### Consequences

iOS/web UI is outside MVP and would require a separate future client strategy.

---

## D003 — Pure Kotlin/JVM Shared `game-core`

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use a pure Kotlin/JVM `game-core` shared by Android and the Ktor JVM server.

Do not use Kotlin Multiplatform initially.

### Rationale

Both current consumers are JVM-based. Kotlin/JVM provides the desired game-rule sharing with less Gradle/build complexity.

The important architectural property is platform independence of the domain code, not use of KMP itself.

### Alternatives Considered

- Kotlin Multiplatform from day one
- separate Android and server rule implementations

### Consequences

`game-core` must stay free of Android/server/database dependencies so a future KMP migration remains possible if a concrete non-JVM client is added.

### Supersedes

Any earlier planning language that treated Kotlin Multiplatform as an MVP requirement.

---

## D004 — Ktor Server Is Authoritative

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Canonical multiplayer game state changes are validated and committed by the Ktor server.

Android may pre-validate for UX but cannot authoritatively replace game state.

### Rationale

This supports concurrency, anti-corruption, future hidden information, and future deck-building rules.

### Alternatives Considered

- client-authoritative Firestore writes
- direct Android-to-database state mutation
- peer-to-peer state ownership

### Consequences

Game commands must go through Ktor.

---

## D005 — PostgreSQL Hosted by Supabase

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use PostgreSQL hosted by Supabase for persistent application data.

### Rationale

The data model is naturally relational:

- users,
- friendships,
- series,
- games,
- moves,
- events.

PostgreSQL also provides strong constraints and transactional semantics useful for username races, move/undo races, and rematch idempotency.

### Alternatives Considered

- Firebase/Firestore
- self-hosted PostgreSQL
- MySQL

### Consequences

Supabase is managed infrastructure, not the application architecture. Android should not directly manipulate canonical game tables.

---

## D006 — Supabase Anonymous Authentication

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use Supabase anonymous authentication for MVP.

The user should not see a conventional password/login flow.

### Rationale

The desired product experience is:

```text
install
→ choose username
→ play
```

while still retaining a secure immutable internal identity.

### Alternatives Considered

- username-only identity with no auth
- Google sign-in required up front
- email/password

### Consequences

Account recovery is deferred. Anonymous identities must be designed so they can later be linked to a permanent identity.

---

## D007 — Usernames Are Globally Unique

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Visible usernames are globally unique, case-insensitively.

Use an immutable internal `userId` for authorization and references.

### Rationale

The product should not contain confusing duplicate visible identities.

### Alternatives Considered

- duplicate display names + friend codes
- generated friend codes as primary lookup

### Consequences

A normalized username must have a database uniqueness constraint.

---

## D008 — Lost Anonymous Usernames Remain Reserved

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

If an anonymous MVP account becomes inaccessible, do not automatically recycle its username.

### Rationale

Automatic recycling could let a new account impersonate a previously known friend.

### Alternatives Considered

- automatic username release after inactivity
- manual immediate recycling

### Consequences

A future recovery/admin policy may be needed, but not for MVP.

---

## D009 — Friends Are Added by Username

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Users add friends by exact unique username.

Friendships are mutual immediately.

### Rationale

Unique usernames make a separate permanent friend code unnecessary and keep the UX simple.

### Alternatives Considered

- friend codes
- approval-based friend requests

### Consequences

The MVP friend system is intentionally lightweight and assumes use among known people.

---

## D010 — Track `lastSeenAt`

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Track meaningful recent activity internally.

Do not maintain constant heartbeat writes.

### Rationale

It is inexpensive to establish now and useful for future social/activity features.

### Consequences

The MVP does not need to prominently display it.

---

## D011 — One Active Game Series per Friend Pair

**Date:** 2026-08-21  
**Status:** Accepted for MVP

### Decision

At most one `ACTIVE` series exists for a given friend pair.

### Rationale

Automatic rematches make a series the natural ongoing relationship between two players. Parallel active series would make the dashboard and rematch semantics more confusing.

### Alternatives Considered

- unlimited simultaneous games between the same pair

### Consequences

Selecting Play/Open for a friend with an active series opens that series.

---

## D012 — Game Series Have `ACTIVE` and `CLOSED` Lifecycle

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

A series has at least:

```text
ACTIVE
CLOSED
```

### Rationale

A series must have a durable lifecycle independent of the friendship record, especially when friendship removal should not destroy the current game.

### Consequences

Closed series remain available for history.

---

## D013 — Removing a Friend Finishes Current Game Then Closes Series

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Removing a friend:

1. removes/deactivates the friendship,
2. does not terminate the current game,
3. disables creation of the next automatic rematch,
4. lets the current game finish normally,
5. closes the series when the current game ends,
6. preserves all history.

### Rationale

This respects the user's explicit friend removal without abruptly destroying an in-progress game.

### Alternatives Considered

- immediately terminate the current game
- let automatic rematches continue indefinitely after friend removal

### Consequences

The server needs a race-safe `closeAfterCurrentGame` or equivalent lifecycle mechanism.

---

## D014 — Initial Colors Random, Rematch Colors Alternate

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Randomly assign colors for the first game in a series.

Alternate colors on subsequent automatic rematches.

### Rationale

Rematches should feel continuous and fair without additional setup.

---

## D015 — Automatic Rematches Are Default

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

When a game ends in an active series, the next game is created automatically.

No request, acceptance, or acknowledgement is required.

### Rationale

Seamless repeat play is a core product principle.

### Consequences

Rematch creation must be idempotent.

A future setting may disable automatic rematches, but the MVP does not need the toggle.

---

## D016 — Takebacks Are Unilateral Until Opponent Responds

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

A player may undo their latest active non-final move while it remains unanswered.

Once the opponent responds, the prior move is locked.

If the opponent undoes their response, the prior move can become undoable again.

### Rationale

This matches the desired casual play model: correct your latest move until the other player responds.

### Consequences

Undo eligibility depends on current active move history, not a permanent historical lock flag.

---

## D017 — Game-Ending Moves Are Immediately Final

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

A terminal move cannot be undone.

There is no grace period or pending-final state.

### Rationale

Adding acknowledgement before finalization conflicts with the product principle of seamless transition into the next game.

### Consequences

Game finalization and rematch creation can occur immediately after a terminal result.

---

## D018 — Resignation Is Final After Confirmation

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

The UI confirms resignation before submission.

After the server accepts it, resignation cannot be undone.

### Consequences

If the series remains active, the automatic rematch is created. If the series is closing, no next game is created.

---

## D019 — Standard Chess Draws Distinguish Claimable and Automatic Rules

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Claimable draws:

- threefold repetition,
- fifty-move rule.

Automatic draws:

- fivefold repetition,
- seventy-five-move rule,
- stalemate,
- insufficient material.

Add a `ClaimDraw` action/command for valid claimable draws.

Draw offers by agreement remain outside MVP.

### Rationale

This more accurately models standard chess than automatically ending the game at threefold repetition or fifty moves.

### Alternatives Considered

- automatically draw at threefold/50
- omit draw claims entirely

### Consequences

The engine must distinguish claim entitlement from automatic terminal conditions and preserve sufficient history.

---

## D020 — Commands + Audit Events, Not Full Event Sourcing

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use explicit state-changing commands and append-only audit events while persisting current canonical state directly.

### Rationale

Commands fit game interactions naturally, while audit events help debugging and future analytics without requiring full replay-based state reconstruction.

### Alternatives Considered

- generic CRUD state replacement
- full event sourcing

### Consequences

Normal game loads should be efficient and not require replaying the full event log.

---

## D021 — Version Every Game Mutation

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Every accepted game-state mutation increments a monotonically increasing game version.

Commands include `expectedVersion`.

### Rationale

Required for deterministic concurrency handling, especially move-vs-undo races.

### Consequences

Stale commands are rejected and clients must refresh.

---

## D022 — HTTPS Commands + WebSocket Updates

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Use HTTPS for commands and queries.

Use WebSockets for incoming realtime updates.

### Rationale

HTTP provides simple request/response semantics for authoritative commands. WebSockets improve realtime UX without becoming the source of truth.

### Consequences

Reconnect logic must reload canonical state over HTTPS.

---

## D023 — No Premature Generic Deck-Builder Framework

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Implement chess concretely first.

Do not create speculative abstractions for cards, decks, effects, action queues, hidden information, or generic game types.

### Rationale

The eventual deck-builder should inform abstractions through a second concrete implementation rather than guesses.

### Consequences

A post-chess architecture review is required before generalizing further.

---

## D024 — Server Hosting Provider Deferred

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Do not choose the Ktor hosting provider until deployment is required.

### Rationale

Hosting choice is operational and should not constrain application architecture prematurely.

---

## D025 — PostgreSQL Library Deferred Until Bootstrap

**Date:** 2026-08-21  
**Status:** Accepted

### Decision

Choose the Kotlin/JVM PostgreSQL library during bootstrap after checking current support.

### Selection Criteria

- active maintenance,
- PostgreSQL support,
- transaction support,
- Kotlin ergonomics,
- migration compatibility,
- testability.

### Rationale

Library quality changes faster than the architecture itself.

---

## D026 — Continuous Autonomous Development on `claude-autopilot`

**Date:** 2026-08-25

**Status:** Accepted

### Decision

After M1.1-M1.6 were reconciled and locally verified, autonomous development
switched from a "stop at every milestone boundary" checkpoint to a continuous
loop:

- select the highest-priority unblocked `TODO` from `docs/BACKLOG.md` (using its
  Task Selection Order),
- implement, test, run targeted verification, run `./gradlew build`, fix
  failures, mark `DONE` (local gate), update docs, review the diff, commit,
- push `claude-autopilot` and wait for the required GitHub Actions run for that
  commit; only advance to the next task once that run is green,
- continue to the next task **across** milestone boundaries,
- stop only for the explicit blockers listed in
  `docs/AUTONOMOUS-DEVELOPMENT.md`.

`DONE` (task implementation + local `./gradlew build`) and *advancing to the
next task* (green required remote CI for the pushed commit) are separate gates.
A task whose acceptance criteria require GitHub Actions to run successfully
(e.g. `M1.7`) is not `DONE` until that CI run passes. Remote CI is monitored via
the GitHub CLI, matching the run to the pushed commit's SHA; if `gh` cannot be
used, that is a stop condition (no silent skipping).

Continuous autonomous work happens entirely on the `claude-autopilot` branch.
The autonomous loop does not merge, rebase, synchronize with, or otherwise
manage `main`; `main` stays protected (no direct pushes, no force-push, no
rewriting published history), and integrating `claude-autopilot` into `main` is
outside the loop and human-controlled. Production and beta deployment require
explicit approval.

The security stop condition covers only a *new or changed* security decision or
a change to the approved security model. Implementing an authentication,
authorization, or trust model that the authoritative documentation already
specifies is normal autonomous work.

`./gradlew build` is adopted as the single aggregate local verification command
and is what CI runs.

### Rationale

The milestone-boundary checkpoint in the original protocol was explicitly
described as a temporary safeguard to be loosened once the workflow proved
reliable. M1 bootstrap and local verification are now in place, so the
checkpoint adds friction without adding safety. Genuine risks (contradictions, missing
infrastructure, irreversible architecture changes, cost, security, destructive
operations) are covered by explicit stop conditions and a failure-escalation
ladder instead.

A single aggregate command removes ambiguity about "what counts as verified" and
keeps local verification identical to CI.

### Supersedes

The "Default Autonomy Scope" section of `docs/AUTONOMOUS-DEVELOPMENT.md` that
limited autonomous work to the current milestone and required a stop, summary,
and hand-off at each milestone boundary.

### Consequences

- `docs/AUTONOMOUS-DEVELOPMENT.md`, `CLAUDE.md`, `docs/BACKLOG.md`, and
  `docs/DEVELOPMENT.md` were updated to describe the loop, the branch workflow,
  the stop conditions, the escalation ladder, and the aggregate command.
- CI also triggers on `claude-autopilot` so autonomous commits get feedback
  before any pull request into `main`.
- Merging `claude-autopilot` into `main` remains a human step unless explicitly
  authorized.

---

## D027 — Chess Domain Types Live in `core.chess` Without a `Chess` Prefix

**Date:** 2026-08-26

**Status:** Accepted

### Decision

The chess domain types introduced by `M2.1` live in the `game-core` package
`com.jmussel.chessgame.core.chess` and are named without a redundant `Chess`
prefix:

```text
Side, PieceType, Piece, Square, Board, Move,
CastlingSide, CastlingRights,
PositionKey, DrawClaim, DrawRuleState,
GameOutcome, TerminationReason, GameResult,
GameState
```

Supporting shape decisions:

- `Square` is a `@JvmInline value class` over a `0..63` index
  (`index = rank * 8 + file`, file `0` = `a`, rank `0` = rank 1). Squares
  compare by value, not identity.
- `Board` holds only piece placement and is immutable; every mutator returns a
  new board.
- `Move` carries `from`, `to`, and an optional explicit `promotion`. Castling is
  the king's two-square move and en passant the pawn's diagonal move, so neither
  needs its own field — both are derivable from the position the move is applied
  to.
- `TerminationReason` carries `isDraw` and `requiresClaim`, so the engine can
  distinguish claimable draws (threefold repetition, fifty-move rule) from
  automatic ones (`D019`) without a second parallel type. `GameResult` rejects an
  outcome inconsistent with its reason.
- `DrawRuleState` owns the halfmove clock and repetition counts, keyed by
  `PositionKey`. The claim/automatic thresholds (3, 5, 100, 150) are constants on
  it.
- `GameState` has a nullable `result`: `null` means in progress.

### Rationale

`docs/ARCHITECTURE.md` §5 lists the expected concepts as `ChessBoard`,
`ChessMove`, and so on, while explicitly noting that "the exact class names may
change". Inside a package already named `chess`, the prefix is redundant, and
unprefixed names read better at every call site. The package keeps the chess
ruleset separated from the small amount of non-chess `game-core` surface
(`GameCore`) and leaves room for a second concrete ruleset later without
implying a generic framework (`D023`).

Encoding claim-vs-automatic on `TerminationReason` keeps `D019` expressed once,
in the type system, rather than duplicated in the engine and again on the
clients.

### Alternatives Considered

- `ChessBoard`/`ChessMove`-style prefixed names in the existing
  `com.jmussel.chessgame.core` package — rejected as redundant once the package
  names the ruleset.
- `Square` as a `data class` of file and rank — rejected; the value class gives
  the same ergonomics with no allocation and a natural array index.
- A separate `DrawClaimState` type alongside repetition counts — rejected as an
  extra type with no behavior of its own; `DrawRuleState` plus `DrawClaim`
  covers it.

### Consequences

- `docs/ARCHITECTURE.md` §5's illustrative names differ from the implemented
  ones. That section already permits this; no behavior changed, so it is left as
  written.
- Later milestones (`M3` legal moves, `M4` undo history) extend these types
  rather than replacing them. `M4.1` will add active move history, which is
  deliberately not part of `GameState` yet.

---

## D028 — `ChessRules.applyMove` Is the Single State-Transition Function

**Date:** 2026-08-26

**Status:** Accepted

### Decision

`ChessRules` is `game-core`'s entry point: `legalMoves(state)`,
`isLegal(state, move)`, and `applyMove(state, move)`. `applyMove` requires a
legal move in an unfinished game and returns the next `GameState`, updating:

- the board (including the castling rook and the en passant captured pawn),
- the side to move,
- castling rights,
- the en passant target,
- the halfmove clock,
- the fullmove number.

It deliberately does **not** decide repetition counts (`M3.12`) or terminal
results (`M3.10`, `M3.11`, `M3.13`); those tasks extend it.

Move generation is layered underneath: `PseudoLegalMoves` (geometry) →
`LegalMoves` (self-check filtering, plus the `GameState` overloads that add
castling and en passant) → `ChessRules`. A `Move` stays `from`/`to`/`promotion`;
castling is recognised as a king moving two files, and en passant as a pawn
moving diagonally onto `GameState.enPassantTarget`.

### Rationale

`M3.8`'s acceptance criteria require en passant *creation* and *expiration*,
which cannot be demonstrated without applying moves to a state. Castling-rights
maintenance has no later backlog task of its own, so it belongs with the
transition function that first exists — otherwise it would fall through the
gap between `M3.7` (validation only) and `M4` (undo).

Keeping one transition function avoids two divergent notions of "what a move
does", which is exactly the kind of duplication the server-authoritative design
cannot tolerate: Android pre-validation and the Ktor server must agree move for
move.

### Alternatives Considered

- Applying moves inside `LegalMoves` — rejected; move generation and state
  transition are different responsibilities, and the layering keeps the pure
  geometry testable on a bare `Board`.
- Encoding move kind (`CASTLE`, `EN_PASSANT`, `PROMOTION`) on `Move` — rejected
  for now; the kind is derivable from the position, and a client-supplied kind
  would be another untrusted field the server has to re-derive anyway.
- Deferring castling-rights maintenance to `M4` — rejected; it would leave
  `applyMove` knowingly wrong in the meantime.

### Consequences

- `M3.9` (promotion) extends generation; `M3.10`–`M3.13` extend `applyMove` with
  repetition counting and terminal results; `M4.1` adds active move history.
- The board-only `LegalMoves` overloads remain for ordinary movement and tests,
  but callers that need a complete legal move list must pass a `GameState`.

---

## D029 — Undo Restores a Recorded Prior Position, Not a Reversed Move

**Date:** 2026-08-26

**Status:** Accepted

### Decision

`GameState` stays the position alone. A new `ChessGame` holds the current
`GameState` plus `history: List<MoveRecord>`, where each `MoveRecord` pairs the
move played with the **complete position it was played from**.

Undo pops the last record and returns that position verbatim.

### Rationale

`M4.1` requires restoring the *exact* prior state: board, side to move, castling
rights, en passant target, halfmove clock, fullmove number, repetition counts,
and result. Reversing a move by hand would have to un-derive every one of those
— restoring a captured piece, a castling rook, an en passant pawn, rights lost
several moves earlier, and a repetition history cleared by an irreversible move.
Each is a separate opportunity to be subtly wrong, and `D016` makes undo a
normal part of play rather than a rare operation.

Keeping the history on `ChessGame` rather than inside `GameState` avoids a
recursive type: a state that contains snapshots of states.

### Alternatives Considered

- An undo delta per move (captured piece, prior rights, prior clock) — rejected;
  it is strictly more code to get exactly as correct, and the memory saved is
  irrelevant at chess scale.
- Replaying the game from the start on every undo — rejected; it is slower, and
  a series' current game would have to keep every move forever to support it.

### Consequences

- Memory holds one position per active move. Positions are immutable and the
  repetition map is cleared by every pawn move or capture, so this stays small.
- Persistence (`M6.4`, `M10`, `M11`) stores the active move history alongside
  canonical current state, which is what `D020` already calls for.
- `M4.2` adds *who* may undo and until when; `M4.3` locks a terminal move.

---

## D030 — Exposed over HikariCP for PostgreSQL Access

**Date:** 2026-08-26

**Status:** Accepted

### Decision

The Ktor server reaches PostgreSQL through:

- **JetBrains Exposed 1.5.0** (`exposed-core`, `exposed-jdbc`,
  `exposed-java-time`) — the typed SQL DSL, not the DAO/entity layer,
- **HikariCP 7.1.0** for connection pooling,
- **PostgreSQL JDBC 42.7.13** as the driver (runtime only).

Migrations stay plain SQL files under `database/migrations/`; Exposed's schema
generation is not the source of truth. The migration tool itself is `M6.3`.

This settles the choice `D025` deferred.

### Rationale

Against the selection criteria in `M6.2`:

- **Maintained** — Exposed 1.5.0 is JetBrains' current stable line (the
  `org.jetbrains.exposed:exposed-core` metadata on Maven Central was last
  updated the same day this was chosen); HikariCP and the PostgreSQL driver are
  both long-standing and current.
- **PostgreSQL support** — first-class, over the standard JDBC driver.
- **Transaction support** — explicit `transaction { }` blocks with the isolation
  level under our control, which is what the move/undo races in `D021` need.
- **Kotlin ergonomics** — a Kotlin-first typed DSL, so queries are checked by
  the compiler rather than assembled as strings.
- **Migration compatibility** — Exposed does not insist on owning the schema, so
  plain SQL migrations remain the source of truth.
- **Testability** — it runs against the real disposable PostgreSQL from `M6.1`,
  so integration tests exercise actual PostgreSQL behaviour (constraints,
  isolation) rather than an in-memory substitute.

The DAO layer is deliberately unused: it is an ORM-style abstraction the MVP has
no need for, and `CLAUDE.md` asks for the simplest thing consistent with the
architecture.

### Alternatives Considered

- **Plain JDBC + HikariCP** — the smallest dependency set, but every query
  becomes a hand-written string with manual `ResultSet` mapping. Rejected as
  more error-prone for no architectural gain.
- **Ktorm** — comparable Kotlin DSL, smaller community and slower release
  cadence than Exposed.
- **jOOQ** — excellent SQL fidelity, but code generation against a live schema
  adds a build step, and its commercial licensing model is a consideration the
  MVP does not need to take on.
- **R2DBC / reactive drivers** — asynchronous access does not pay off at this
  scale and complicates transactional reasoning.

### Consequences

- `server/build.gradle.kts` declares the three Exposed modules and HikariCP,
  with the PostgreSQL driver as `runtimeOnly`; versions live in
  `gradle/libs.versions.toml`. Resolution and `./gradlew build` were verified on
  2026-08-26.
- Exposed 1.x lives under the `org.jetbrains.exposed.v1.*` packages, which is
  what `M6.5` will import.
- Persistence code and its DTOs stay in `server`; `game-core` gains no database
  dependency (`D003`).

---

## D031 — Android Talks to Supabase Auth Directly, Without the Supabase SDK

**Date:** 2026-08-26

**Status:** Accepted

### Decision

The Android app calls the two Supabase auth endpoints it needs —
`POST /auth/v1/signup` for an anonymous session and
`POST /auth/v1/token?grant_type=refresh_token` to renew it — with the Ktor HTTP
client, rather than adding the Supabase Kotlin SDK.

The pieces are `SupabaseAuthClient` (the two calls), `AnonymousSession` (tokens,
subject, expiry), `SessionStore` (a DataStore-backed store on the device, an
in-memory one in tests), and `AnonymousAuthenticator`, which restores, refreshes,
or creates a session under a mutex so two screens starting at once cannot create
two anonymous accounts.

The publishable key is never committed. It reaches the app as a `BuildConfig`
field from `-PsupabaseAnonKey`, `gradle.properties`, or the `SUPABASE_ANON_KEY`
environment variable; the Supabase URL is public and has a default.

### Rationale

`D004` already forbids Android from touching canonical game tables, and `D022`
routes commands and realtime through the Chess server. So of everything the
Supabase SDK offers — Postgrest, Realtime, Storage, Functions — the app is only
allowed to use auth, and of auth only anonymous sign-in and refresh. Two HTTP
calls against a documented, stable API is less to carry and far easier to test:
the whole flow runs against Ktor's `MockEngine` on the JVM, with no Android
runtime and no network.

The app needs a Ktor HTTP client for its own server regardless, so this adds no
new kind of dependency.

### Alternatives Considered

- **supabase-kt** — the conventional Kotlin client. It would bring session
  persistence and refresh scheduling for free, but also a large surface the
  architecture forbids using, and its own HTTP stack to test around. Worth
  revisiting if the app ever legitimately needs Realtime or Storage from
  Supabase.
- **Rolling session storage on `SharedPreferences`** — DataStore is the current
  Android convention and is coroutine-friendly, which the rest of this code
  already is.

### Consequences

- Token refresh, expiry margin, and recovery from a dead refresh token are this
  repository's code and are covered by tests, rather than being the SDK's
  problem.
- `SupabaseLiveAuthTest` exercises the real project when `SUPABASE_ANON_KEY` is
  present, so a change in Supabase's response shape is caught locally; it is a
  no-op elsewhere, including CI.
- The session is stored in app-private storage. That is the platform's
  protection, and it is enough here because the server trusts no client (`D004`)
  and the token only ever represents an anonymous account.

---

## D032 — Beta Runs on Free Tiers: Render Free Web Service + a Second Supabase Free Project

**Date:** 2026-08-28

**Status:** Proposed

### Decision

The beta environment targets `$0` out of pocket while it remains within the
providers' current free quotas:

- **Ktor server:** one Render Free Web Service, deployed as a Docker image
  because Kotlin/JVM is not one of Render's native runtimes. No payment method
  is attached to the Render workspace, and the service is never upgraded
  automatically.
- **Auth and PostgreSQL:** a second Supabase Free project, `ChessGame Beta`,
  separate from `ChessGame Dev`. The Free plan allows two active projects, so
  development data and beta data never share a database.

If a free-tier limit gets in the way, the beta stops until a human decides
whether to reduce usage, wait for a quota reset, or authorize paid hosting.

### Rationale

The beta exists to answer questions that do not need paid infrastructure: can
Android authenticate against a real project, reach an internet-hosted Ktor
server over HTTPS, hold a WebSocket, and let two people play from different
places. A free tier answers all of them.

Render's free tier gives public HTTPS, WebSockets, environment secrets, health
checks, and Docker deploys, and does not scale past a single instance — which
matches the process-local `RealtimeHub` (`ARCHITECTURE.md` §12) instead of
fighting it.

### Alternatives Considered

- **Railway Free or Hobby** — still viable. Railway currently describes its
  Free tier as `$0` with a small usage allowance and Hobby as `$5` minimum usage
  with `$5` of monthly usage included. Render is proposed because its documented
  no-payment-method failure mode makes the hard `$0` boundary explicit and its
  idle-sleep model is acceptable for this beta.
- **Render's free PostgreSQL** — rejected: free databases expire after 30 days,
  which would destroy beta data mid-test. Supabase's free project has no such
  expiry and is already the authentication provider.
- **A single Supabase project for both development and beta** — rejected: it
  would mix disposable development data with beta players' games, and `M15.3`
  exists precisely to keep them apart.

### Consequences

Limitations to accept explicitly before changing this decision to `Accepted`:

- **The server sleeps.** After roughly 15 minutes with no HTTP request or
  WebSocket traffic, Render spins the instance down; the next connection wakes
  it, and a cold start can take about a minute. That is not a guaranteed upper
  bound. Android needs a conservative, configurable deadline with capped
  retry/backoff, and a first call after idleness should read as "waking up"
  before it becomes an actionable failure.
- **The socket drops when it sleeps.** That is the reconnect path `M12.3` and
  `M14.12` already specify — canonical state reloads over HTTPS — so sleeping
  costs latency, not correctness.
- **A free Supabase project pauses after about a week of inactivity.** A beta
  that goes quiet for a week needs the project resumed before the next session,
  or the server will fail to reach its database.
- **Free quotas are hard operating limits.** Render currently grants 750 Free
  instance hours per workspace per month and its Hobby workspace includes 5 GB
  of outbound bandwidth. Without a payment method, exhausting bandwidth spins
  down the workspace instead of creating an overage charge. Build minutes and
  instance hours are also shared at workspace level and can stop builds or
  services until reset.
- **The external database is public-internet traffic.** Render counts traffic
  to Supabase as service-initiated outbound traffic and may suspend a Free
  service for unusually high volume. Usage must be monitored during the beta.
- **Render and Supabase need a compatible database route.** Render is IPv4-only
  while a Supabase Free direct database endpoint is IPv6-only. The server must
  use the Shared Pooler (Supavisor) in session mode on port 5432. Transaction
  mode on port 6543 is unsuitable because it does not support prepared
  statements. PostgreSQL SSL settings must survive `DATABASE_URL` parsing and
  be verified before deployment.
- **Supabase Free has data limits but no managed recovery guarantee for this
  beta.** It currently includes a 500 MB database and 5 GB egress, allows two
  active projects, and has no automatic backups or point-in-time recovery. Beta
  data is not treated as durable unless a manual export process is documented.
- **One instance only.** Do not scale the Render service horizontally while
  `RealtimeHub` is process-local; a second instance would silently fail to
  deliver moves between players connected to different processes.
- Free-tier terms change. Re-check both providers' current limits at deploy
  time rather than trusting this record.

### Current State

The Render side of this proposal has a resource already: a Free Web Service
named `ChessGame` at `https://chessgame-hit7.onrender.com`, created by hand on
2026-08-28 to test the hosting path. It is not functional — its first Docker
build failed, there being no `Dockerfile` — and creating it does not accept
this decision or complete any `M15` task. The status above stays `Proposed`
until `M15.1`.

### Provider References

Checked 2026-08-28:

- [Render Free services and limits](https://render.com/docs/free)
- [Render outbound bandwidth](https://render.com/docs/outbound-bandwidth)
- [Supabase database connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase IPv4/IPv6 compatibility](https://supabase.com/docs/guides/troubleshooting/supabase--your-network-ipv4-and-ipv6-compatibility-cHe3BP)
- [Supabase pricing and Free-plan limits](https://supabase.com/pricing)
- [Supabase Free-project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Railway pricing](https://railway.com/pricing)

---

## D033 — The Android Shell Is a Back Stack and a `ViewModel`, and Only Debug Builds Talk Cleartext

**Date:** 2026-08-28

**Status:** Accepted

### Decision

The Android application shell is three small pieces:

- `AppNavigation` — an immutable list of `Destination`s with `open`,
  `restartAt`, and `back`. No navigation library, no routes, no deep links.
- `ChessAppViewModel` — holds that navigation and owns `ChessAppDependencies`,
  closing it in `onCleared`.
- `ChessAppDependencies` — one Ktor `HttpClient` shared by Supabase auth and the
  Chess server, plus the `AnonymousAuthenticator` and `ChessApiClient` built on
  it, constructed from the application context and never from a composable.

Navigation state is not persisted. After process death the app starts at
`Destination.Startup` again and reaches the dashboard through the stored
session, which is the only thing that has to survive (`D006`).

Transport security is set by a network security configuration rather than by
`usesCleartextTraffic`: the release configuration forbids cleartext entirely,
and the `debug` source set replaces that file with one permitting cleartext to
`10.0.2.2` and `localhost` only.

### Rationale

The MVP has seven destinations and no deep links, so `androidx.navigation` would
add a dependency, a serialization format for arguments, and a second state
model, to replace a list and three methods. A plain immutable stack is free of
Compose and Android, so every transition the app supports is tested on the JVM.

The dependencies have to outlive the `Activity` — rotating the device must not
sign in again or drop the HTTP client — and must not outlive the app's state, or
nothing would ever close the client. A `ViewModel` is exactly that lifetime, and
holds no `Activity` reference, so a recreated screen attaches to the state
already there.

A developer runs the Chess server over plain HTTP on their own machine; a beta
player's device must never send a session token in the clear. Splitting the
configuration by source set makes that difference a build-time fact rather than
a runtime flag someone can leave on.

### Alternatives Considered

- **`androidx.navigation` (or Navigation 3)** — the conventional choice, and the
  right one when destinations are numerous, deep-linked, or contributed by
  feature modules. None of that is true here. Worth revisiting if deep links or
  a multi-module client appear.
- **Dependencies in an `Application` subclass** — process-lifetime is simple, but
  gives no close point at all and makes the container awkward to substitute in
  tests.
- **`android:usesCleartextTraffic="true"` on debug only** — equivalent for the
  emulator, but it permits cleartext to *anywhere* in a debug build rather than
  to the development server.

### Consequences

- Adding a destination means adding a `Destination` and a branch in `ChessApp`;
  there is no route table to keep in step.
- Nothing survives process death except the session, so any screen state worth
  keeping (for example a loaded game) must be reloaded from the server rather
  than restored — which `D004` requires anyway.
- A beta or release build cannot reach a plain-HTTP server at all. `M15.4`'s
  endpoint has to be HTTPS, which is what `D032`'s hosting provides.
