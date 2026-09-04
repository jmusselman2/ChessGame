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

**Status:** Accepted

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
  **Superseded 2026-08-31 by `D035`:** the beta reuses `ChessGame Dev` instead,
  and no second project is created. Everything else in this decision stands.

If a free-tier limit gets in the way, the beta stops until a human decides
whether to reduce usage, wait for a quota reset, or authorize paid hosting.

### Acceptance

Accepted by the project owner on 2026-08-31, in conversation, together with the
operational limits below: Render Free sleep and cold starts and the rest of the
free-tier limits, Supabase Free limits and inactivity pausing, no paid backups
or point-in-time recovery, no automatic upgrade to a paid tier, and stopping or
suspending the beta rather than incurring cost whenever a free limit is reached.
The beta must remain `$0`/month.

That acceptance clears the recurring-cost Stop Condition for this decision. It
authorizes nothing else. It explicitly does **not** authorize creating or
changing paid resources, attaching or using a payment method, deploying the beta
server, triggering a Render deployment, or handling beta database credentials
beyond what planning requires. `M15.2` and `M15.3` remain gated on their own
separate human authorization.

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
- **A single Supabase project for both development and beta** — rejected here:
  it would mix disposable development data with beta players' games, and `M15.3`
  existed precisely to keep them apart. **Reversed 2026-08-31 by `D035`**, which
  accepts that mixing for the identity pool and the quotas, keeps game data
  apart through `DATABASE_URL`, and adds `M15.5`'s destructive-reset guard to
  replace the protection separation gave.

### Consequences

Limitations to accept explicitly before changing this decision to `Accepted`:

- **The server sleeps.** After roughly 15 minutes with no HTTP request or
  WebSocket traffic, Render spins the instance down; the next connection wakes
  it, and a cold start can take about a minute. That is not a guaranteed upper
  bound. Android needs a conservative, configurable deadline with capped
  retry/backoff, and a first call after idleness should read as "waking up"
  before it becomes an actionable failure.
- **The socket drops when it sleeps, and whenever the instance is replaced.**
  Render imposes no fixed WebSocket idle timeout but closes every connection
  when an instance is replaced, by a deploy or by platform maintenance, and asks
  services to send keepalive ping/pong frames and clients to reconnect with
  exponential backoff. That is the reconnect path `M12.3` and
  `M14.12` already specify — canonical state reloads over HTTPS — so sleeping
  costs latency, not correctness.
- **A free Supabase project pauses after about a week of inactivity.** A beta
  that goes quiet for a week needs the project resumed before the next session,
  or the server will fail to reach its database.
- **Free quotas are hard operating limits.** Rechecked 2026-08-31: Render
  grants 750 Free instance hours per workspace per calendar month and suspends
  every Free web service in the workspace until the next month once they are
  gone. The Hobby workspace (`$0`) includes 5 GB of outbound bandwidth and 500
  build pipeline minutes, and caps the workspace at 25 services. Without a
  payment method, exceeding bandwidth spins the workspace's services down until
  the start of the next month rather than creating a charge; exhausting pipeline
  minutes stops new builds for the rest of the month. This is the mechanism that
  makes the hard `$0` boundary enforceable at the provider rather than by
  convention. Render revised its workspace plans on 2026-04-23, so these
  numbers are the post-revision ones.
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
  beta.** Rechecked 2026-08-31: 500 MB database, 1 GB file storage, 5 GB egress
  plus 5 GB cached egress, 50,000 monthly active users, and no automatic backups
  or point-in-time recovery. Exceeding a Free quota does not create a charge —
  Supabase notifies the billing address, allows a grace period, and then applies
  service restrictions under its Fair Use Policy, with the project answering
  `402` and a reason such as `exceed_egress_quota` while the dashboard still
  reaches the data. Beta data is not treated as durable unless a manual export
  process is documented.
- **Two active Free projects is the whole allowance.** Under `D035` the beta
  reuses `ChessGame Dev`, so one slot stays spare — but that single project may
  no longer be paused or deleted casually, because it now holds beta data as
  well as development identities.
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
this decision or complete any `M15` task.

`M15.1` completed the recheck and the owner accepted the decision on 2026-08-31,
so the status above is now `Accepted`. Two facts about the Render workspace
itself — that no payment method is attached and that the service uses the Free
instance type — are account state this repository cannot read and cannot keep
true. They must be confirmed by a human in the Render dashboard before `M15.2`,
and they are what the `$0` boundary rests on.

### Provider References

Checked 2026-08-28; rechecked in full for `M15.1` on 2026-08-31:

- [Render Free services and limits](https://render.com/docs/free)
- [Render outbound bandwidth](https://render.com/docs/outbound-bandwidth)
- [Supabase database connection modes](https://supabase.com/docs/guides/database/connecting-to-postgres)
- [Supabase IPv4/IPv6 compatibility](https://supabase.com/docs/guides/troubleshooting/supabase--your-network-ipv4-and-ipv6-compatibility-cHe3BP)
- [Supabase pricing and Free-plan limits](https://supabase.com/pricing)
- [Supabase Free-project pausing](https://supabase.com/docs/guides/platform/free-project-pausing)
- [Railway pricing](https://railway.com/pricing)
- [Render web services (HTTPS, health checks, secrets)](https://render.com/docs/web-services)
- [Render WebSocket support](https://render.com/docs/websocket)
- [Render scaling (autoscaling is Pro and above)](https://render.com/docs/scaling)
- [Render new workspace plans, 2026-04-23](https://render.com/docs/new-workspace-plans)
- [Render outbound IP addresses (IPv4)](https://render.com/docs/outbound-ip-addresses)
- [Supabase billing FAQ (Free plan is restricted, not billed)](https://supabase.com/docs/guides/platform/billing-faq)

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

---

## D034 — The Development Server Address Is a Build Input, Because `10.0.2.2` Does Not Work on Android 16/17

**Date:** 2026-08-31

**Status:** Accepted

### Decision

`BuildConfig.CHESS_SERVER_URL` supplies the Chess server address the Android app
uses, filled from a `chessServerUrl` Gradle property, a `gradle.properties`
entry, or the `CHESS_SERVER_URL` environment variable, and defaulting to
`http://10.0.2.2:8080` when none is given. `ChessAppDependencies.create` reads
it; `ChessServerConfig`'s own default is unchanged.

A development build running against an Android 16/17 emulator is built with
`-PchessServerUrl=http://localhost:8080` and paired with
`adb reverse tcp:8080 tcp:8080`.

### Rationale

`10.0.2.2` is the emulator's alias for the host loopback and has been the
development address since `M14.5`. It does not work on the emulators this
project actually has. Measured during `M14.18` on both `ChessPlayer1` and
`ChessPlayer2` (`android-37.1`, Android 17, API 37, the project's own
`compileSdk`/`targetSdk`), a plain `HttpURLConnection` to
`http://10.0.2.2:8080/health`:

- returns 200 as the `shell` uid,
- times out after 10–15 s as the app's uid,

while that same app uid reaches `https://example.com` and the host's LAN address
on port 8080 without trouble. So it is neither the app's HTTP stack, nor the
`INTERNET` permission, nor the debug cleartext configuration, nor a general
local-network restriction — connections from an ordinary app uid to the
`10.0.2.2` host-loopback alias simply do not complete.

`adb reverse` puts the server on the device's own loopback, which an app uid
reaches normally. `localhost` was already one of the two addresses the debug
network security configuration permits in the clear (`D033`), so nothing about
the trust boundary changes.

Making the address a build input rather than moving the default keeps the
documented behaviour for anyone on an older emulator or a physical device, and
matches how `SUPABASE_URL` and `SUPABASE_ANON_KEY` are already supplied.

### Alternatives Considered

- **Change the default to `http://localhost:8080`** — would silently require
  every developer to have run `adb reverse`, and would break a physical-device
  or older-emulator setup that works today.
- **Point the app at the host's LAN address** — verified to work, but it hard-codes
  a machine-specific address, stops working off that network, and puts session
  tokens on the LAN in the clear.
- **Keep `10.0.2.2` and find the kernel-level cause** — the behaviour is a
  property of the emulator image, not of this repository, and no change here
  would alter it.

### Consequences

- A debug build carries whatever address it was built with, so a build made for
  an emulator is not automatically right for a device, and vice versa.
- `adb reverse` maps do not survive an emulator restart; the map has to be
  re-established after a cold boot.
- A release build is unaffected: `D033` forbids cleartext outright, so it can
  reach only an HTTPS server, which is what `M15.4` will configure.

---

## D035 — The Beta Reuses `ChessGame Dev` Rather Than a Second Supabase Project

**Date:** 2026-08-31

**Status:** Accepted

**Supersedes:** the "second Supabase Free project" half of `D032`. The rest of
`D032` — Render Free for the Ktor server, the hard `$0` boundary, the
single-instance topology, and the Supavisor session-mode database route — is
unchanged.

### Decision

The beta uses the existing `ChessGame Dev` Supabase project for both
authentication and the beta PostgreSQL database. `ChessGame Beta` is not
created.

Development and beta therefore share one Supabase project. What that does and
does not mix:

- **Shared: identities.** `auth.users` holds development, test, and beta
  anonymous accounts together. Every debug app launch and every
  `SupabaseLiveAuthTest` / `AppStartupLiveTest` run adds a throwaway account to
  the same pool beta players are in.
- **Shared: quotas and availability.** One 500 MB database, 5 GB egress, 1 GB
  storage, one 50,000-MAU allowance, one pause-after-a-week timer, one Fair Use
  restriction. Development usage counts against the beta, and a pause or
  restriction takes both down at once.
- **Not shared: game data.** Local development and CI keep using the disposable
  Docker PostgreSQL from `docs/DEVELOPMENT.md`. The application schema goes into
  the Supabase database only for the beta, so `users`, `games`, `moves`, and
  the rest exist in two unrelated databases and never mix. The separation is
  the `DATABASE_URL` / `TEST_DATABASE_URL` value, not the project.

### Rationale

Nothing in the implementation, the migrations, the auth configuration, or the
remaining roadmap requires two projects. Checked directly:

- **Migrations.** `database/migrations/V1__initial_schema.sql` creates every
  table in `public` and never references Supabase's `auth` schema.
  `users.auth_subject` is an opaque `text ... unique` holding the Supabase
  subject. The application schema is fully decoupled from Supabase auth and can
  be applied to any PostgreSQL.
- **Auth configuration.** The beta needs exactly what development needs:
  anonymous sign-ins enabled (`D006`). There are no redirect URLs, OAuth
  providers, or other per-environment settings to diverge.
  `SupabaseTokenVerifier` checks the signature against the project's JWKS, the
  issuer `<SUPABASE_URL>/auth/v1`, and the `authenticated` audience — identical
  for both.
- **Server configuration.** `SUPABASE_URL` and `DATABASE_URL` are independent
  environment variables, and the server needs no exclusive ownership of the
  database: `Migrations.migrate` is idempotent and runs with Flyway's clean
  disabled.
- **Roadmap.** `M15.4` (Android beta endpoint), `M16.1`/`M16.2` (interruption
  and restart), `M17.1` (small beta distribution), and `M18.1` (architecture
  review) need a reachable beta, not a second project.

The separation in `D032` was blast-radius hygiene, not a technical constraint,
and the project owner has chosen to trade it for a simpler single-project setup.
Reusing the project also leaves a Free project slot spare instead of consuming
the organization's whole allowance of two.

### Consequences

Accepted costs of sharing:

- **Beta user counts are approximate.** Anonymous accounts created by
  development and by the live tests are indistinguishable from beta players'.
  `M17.1` observes onboarding against a pool that includes developer noise.
- **The project can no longer be wiped freely.** Deleting or recreating
  `ChessGame Dev` to clear development clutter would destroy beta data, and the
  Free plan has no backups or point-in-time recovery.
- **One outage, both environments.** A week of inactivity pauses the project, and
  a Fair Use restriction (`402`) stops development sign-in as well as the beta.
- **A development token is valid at the beta server.** Both environments trust
  the same issuer. This does not change the trust model in kind — every MVP
  identity is anonymous and unprivileged (`D004`, `D006`), and the server
  already treats every client as untrusted — but development and beta are no
  longer isolated identity domains.

Required safeguard, because sharing removes the one that separation provided:

- **`Migrations.reset` must refuse a non-disposable database.**
  `DatabaseTestSupport.withMigratedDatabase` calls it on every server test run,
  and it performs a Flyway `clean()` that drops everything in the schema, driven
  entirely by whatever `TEST_DATABASE_URL` names. Under `D032` a developer's
  `.env` never held beta credentials, so this could not reach beta data. Under
  this decision the same project holds both, so one mistaken environment
  variable destroys the beta irrecoverably. `M15.5` adds the guard, and `M15.3`
  depends on it.
- The beta `DATABASE_URL` belongs only in the Render environment. It must not be
  put in a developer's `.env`, and `.env.example` says so.

### Alternatives Considered

- **Keep `D032`'s second project** — the safest option and the reason `D032`
  specified it. Rejected by the owner in favour of a single project; the cost is
  the shared identity pool, quota, and availability listed above.
- **One project, separate PostgreSQL schema for the beta** — putting beta tables
  in a `beta` schema rather than `public` would survive a `clean()` aimed at
  `public`. Rejected as the primary safeguard: it depends on Flyway's configured
  schema staying right, which is the same class of mistake it is meant to
  prevent, and `M15.5`'s guard addresses the actual hazard directly. Worth
  revisiting only if the beta later shares a database with something else.
- **A separate Supabase project for auth only, sharing the database** — mixes the
  two concerns in the least useful direction: identities are the cheap thing to
  separate and the database is the expensive one.

---

## D036 — The Beta Server Ships as a Two-Stage Docker Image That Takes Its Port and Its Secrets From the Host

**Date:** 2026-09-01

**Status:** Accepted

**Relates to:** `D032` (Render Free, one instance), `D035` (the beta reuses
`ChessGame Dev`), `M15.2`

### Decision

The Ktor server is deployed as a `Dockerfile` at the repository root, built in
two stages: a JDK image runs `:server:installDist`, and a JRE image runs the
resulting distribution as a non-root user. Six things follow from the host being
a container platform rather than a developer's machine:

- **The build leaves `:android-app` out.** `settings.gradle.kts` includes it only
  when `-PserverOnly=true` (or `CHESSGAME_SERVER_ONLY=true`) is absent. A build
  image carries no Android SDK, and configuring that module without one fails
  before any server code compiles.
- **The port comes from `PORT`.** `serverPort` reads it and falls back to `8080`
  only when nothing sets it. A value that is not a port fails the start rather
  than falling back.
- **Secrets come from the environment, never the image.** `DATABASE_URL` and
  `SUPABASE_URL` are supplied by the host. The image holds no credential and is
  not itself a thing that has to be kept secret.
- **`/health` stays `200` in health-only mode, and says so in its body.** A
  server with no database still answers, because that is a documented local
  affordance; the body names the missing variables so a misconfigured deploy is
  one `curl` away from being understood rather than a healthy-looking service
  that serves nothing.
- **The WebSocket transport sends keepalive frames** — a 30-second ping with a
  60-second answer window — because a hosted connection crosses proxies that
  close a silent socket, and a game is silent while a player thinks.
- **The JVM is told the container's size.** `-XX:MaxRAMPercentage=75.0` instead
  of the default quarter of 512 MB, and `-XX:+UseSerialGC` because a free
  instance is a fraction of one CPU.

`render.yaml` records the service's configuration in the repository. The
`ChessGame` service stays dashboard-managed; the file is the reviewable copy of
those settings, not a second service.

### Rationale

Every one of these is a difference between "runs on a laptop" and "runs on
Render", and each was a way the first deploy could have failed or, worse,
succeeded while serving nothing. Doing them as configuration the repository owns
means the next host change is a diff rather than a dashboard archaeology
exercise.

Excluding the Android module is the least obvious and the most necessary: the
repository is a monorepo whose root build configures an Android application, and
a JDK build image has no SDK. Making it conditional in `settings.gradle.kts`
keeps the exclusion in the one place that decides what a build contains, rather
than spreading `-x` flags through a Dockerfile that could not fix it anyway,
because the failure is at configuration time.

### Consequences

- **`-PserverOnly=true` is a supported build mode that CI does not run.** A
  change that breaks it — a new server dependency on `:android-app`, say — is
  caught by building the image, not by `./gradlew build`.
  `scripts/verify-server-image.sh` is where that is checked.
- **A health-only deploy looks successful to Render.** The health check passes
  because the process is alive. Whoever deploys must read the body, which is why
  it names the missing variables.
- **The image must be rebuilt to pick up a migration.** The SQL lives on the
  server's classpath, so `database/migrations/` has to stay out of
  `.dockerignore`; the verification script asserts the migrations are inside the
  image for exactly that reason.
- **`render.yaml` can drift from the dashboard.** Nothing enforces the match
  while the service is dashboard-managed. Adopting the service into a Blueprint
  would remove the drift and is a deliberate human step, not something the
  autonomous loop does.

### Alternatives Considered

- **Build the distribution outside Docker and `COPY` it in.** Faster builds, but
  the image would then depend on an artifact built somewhere else, and Render
  builds from a repository checkout. Rejected: the Dockerfile has to be
  self-contained to be what Render runs.
- **Install an Android SDK in the build image.** Hundreds of megabytes and
  several minutes of a 500-minute monthly budget to build a module the server
  does not use. Rejected outright.
- **Make `/health` fail in health-only mode.** It would turn a misconfigured
  deploy into a failed deploy, which is arguably the louder signal. Rejected
  because health-only is a deliberate local affordance and `/health` is the
  liveness check; the body carries the distinction instead.
- **A Blueprint-managed service created from `render.yaml`.** Rejected for now:
  the service already exists and was created by hand, and applying the file as a
  new Blueprint would create the second service `D032` warns against.

---

## D037 — A Sleeping Beta Is Waited Through, Said Out Loud, and Never Retried for a Command

**Date:** 2026-09-02

**Status:** Accepted

**Relates to:** `D021` (every mutation carries its expected version), `D032`
(Render Free sleeps), `D033` (only a debug build may use cleartext), `D034` (the
server address is a build input), `M15.4`

### Decision

The beta's free instance spins down after about fifteen idle minutes, and the
first request afterwards took **59.0 s** and **64.5 s** when `M15.2` measured it.
Three things follow.

- **Safe requests wait, rather than failing.** `ServerWakePolicy` and
  `withServerWake` retry with capped exponential backoff under an overall
  deadline — 150 s, 1 s growing to 8 s, all constructor parameters. The cap
  matters: an uncapped backoff spends its last sleep overshooting the deadline,
  so a long wake becomes a steady poll instead. Applied to the startup probe and
  to canonical reloads (the game and the dashboard), which are reads and safe to
  repeat. The session is safe too: `AnonymousAuthenticator` holds a mutex, so a
  retry cannot create a second anonymous account (`D031`).
- **Waking is a state, not an error.** `StartupState.Waking` is distinct from
  `Failed`, and the startup screen says *"Waking the server…"* with the reason,
  plus a retry that genuinely restarts the wake rather than a dead button. A cold
  start reported as a failure is a beta tester filing a bug about the free tier.
- **A mutating command is attempted exactly once.** `MakeMove`, `UndoMove`,
  `ClaimDraw`, and `Resign` do not go through the retry. Each carries the version
  it was decided against, which is what makes it unique, so re-sending one is
  settled by the server's version guard and the canonical state attached to the
  refusal (`D021`, `M16.3`) — not by a client loop that cannot know whether the
  attempt it lost the reply to was applied.

A failure the server actually issued is never waited through, by either path: a
`ChessApiException` or a refused command means the service is awake and has
answered, and so does a `SupabaseAuthException`, since Supabase is a separate
always-on service. Only transport failure looks like a cold start.

Separately, the beta endpoint stays build configuration (`D034`'s
`chessServerUrl`), and `ChessServerConfig` refuses a cleartext address when the
build forbids cleartext — `ChessAppDependencies` passes `BuildConfig.DEBUG`. A
release build left on the emulator-loopback default therefore fails immediately
and by name instead of installing and failing every request against the network
security configuration (`D033`).

### Rationale

The alternative to waiting is a beta whose normal first request of the day fails,
which is not a beta anyone can test. The alternative to *saying* it is waiting is
a screen that looks broken for a minute. Both are properties of the free tier
rather than of this code, and `D032` accepted the free tier deliberately.

The command exception is the important half. Retrying a command is exactly the
hazard the versioning rules exist to remove, and doing it in the client would put
a second mechanism against the same problem — one that, unlike the server's, has
no way to tell "the request was lost" from "the reply was lost". `M16.3` already
proved exactly-once holds at the API boundary; a client retry loop would be the
one way to reintroduce the question.

### Alternatives Considered

- **A single long timeout instead of retrying.** Simpler, but it cannot report
  progress, and it turns a server that comes back after 20 s into a 150 s wait.
- **Retrying commands too, with an idempotency key.** The version already *is*
  the idempotency key (`D021`), and a refusal carries the canonical state, so the
  client can already see its own effect. Adding a retry would buy nothing and
  risk the one invariant that must not bend.
- **Failing the Gradle build when a release build has a cleartext URL.** The
  loudest option, and rejected: `./gradlew build` assembles the release APK with
  the ordinary defaults, so it would break the normal build and CI. The refusal
  happens at construction instead, where it still names the cause.
- **Fitting the deadline to the measurements.** Rejected — `D032` records that
  Render publishes no guaranteed maximum, so the deadline is deliberately
  generous against what was seen rather than derived from it.

### Consequences

- The default deadline is a guess informed by two samples. If beta cold starts
  prove longer, `ServerWakePolicy` is the one place to change.
- A genuinely dead server costs a player up to 150 s before the error appears.
  That is the deliberate trade against reporting a sleeping server as broken, and
  the retry button is offered throughout.
- Tests that assert retrying happens must use a policy whose deadline is real
  against the wall clock. The deadline is measured with `System.nanoTime` even
  under `runTest`, which skips the `delay` but not the clock, so a very short
  deadline makes such a test pass without a single retry. This was found by
  wiring commands through the retry on purpose and watching the test still pass.
- **Starting and retrying are separate methods, and must stay so.**
  `MainActivity` calls `start()` from `onCreate`, so it runs again whenever the
  activity is recreated. `start()` is therefore idempotent and never interrupts a
  wake; only `retryStartup()`, which the retry button calls, does. The first
  version of this work conflated them, and the emulator play-through found it:
  the activity relaunched during a cold start, which would have reset the
  deadline and, on a device that recreates the activity repeatedly, meant the
  wake could never finish.
- A test that fails through Ktor's engine cannot observe the wake: the engine
  runs on its own dispatcher, so the failure is invisible to `runTest`'s virtual
  clock and the model still reads `Loading`. Fail through something read on the
  calling coroutine — the session store — instead.

---

## D038 — A Draw Claim May Be Declared on the Next Move, and Bishops Are Dead by Colour Complex, Not by Count

**Date:** 2026-09-02

**Status:** Accepted

**Relates to:** `D019` (claimable versus automatic draws), `M3.11`, `M3.12`,
`M3.13`, `M3.14`, `PRODUCT` *Draw Semantics*, `ARCHITECTURE` §23

### Decision

Two standard-chess rules that `M3` implemented only in part.

- **Claim entitlement is also prospective, and the declared move binds it.**
  Standard chess lets the player to move claim a threefold repetition or the
  fifty-move rule on the position their *declared next move* is about to
  produce, not only on the position already in front of them. `ChessRules`
  therefore gains declared-move overloads of `availableDrawClaims`,
  `canClaimDraw`, and `claimDraw` (on both `GameState` and `ChessGame`). The
  move is part of the claim: it must be legal, and only that exact move counts.
  The existing no-move overloads keep their meaning — the claims valid for the
  position as it stands — so an entitlement that depends on a declaration is
  never granted without one.

  A granted claim ends the game from the position it was made in; the declared
  move is never played. A declared move that delivers checkmate offers no draw
  claim, mirroring the precedence `terminalResult` already applies. Captures and
  pawn moves reset the halfmove clock and clear the repetition history, so they
  can never be the move a claim is declared on — that falls out of the rules
  rather than being special-cased.

- **Insufficient material is decided by colour complex, not by piece count.**
  Once no pawn, rook, or queen remains, a position is dead when at most one
  piece besides the kings remains, **or** when every remaining piece is a bishop
  and every one of those bishops stands on the same square colour — whatever the
  count, and whoever owns them. A bishop never leaves its square colour, so
  bishops confined to one complex can only check a king standing on that
  complex, and that king always keeps neighbouring squares of the other colour
  which no bishop attacks, no remaining piece can occupy, and the opposing king
  cannot cover without standing next to it.

### Rationale

`PRODUCT` *Draw Semantics* requires the engine to decide claims "according to
the game history and current/prospective legal move state", and `ARCHITECTURE`
§23 requires it to expose enough information for "any relevant prospective legal
move condition". Neither was reachable: every public claim query took the
current state alone.

The material rule had been written as a list of the *named* textbook endings —
including "king and bishop versus king and bishop on the same square colour" as
a two-bishop special case — and rejected everything with more than two pieces
left. Promotion makes that wrong: a side can hold two or more bishops on one
colour against a bare king, which is dead, and which the count test reported as
live. Stating the rule by colour complex covers the textbook cases and the
promoted ones with less code than enumerating them.

### Alternatives Considered

- **Make the existing no-move claim query answer "could this player claim by
  declaring something?"** Rejected. It would let a claim be granted with no
  move behind it — the server's `ClaimDraw` command carries no declared move —
  which is effectively an unconditional claim one ply early, and a silent change
  to product behavior at the 99-halfmove and second-occurrence boundaries.
- **Special-case two same-coloured bishops.** Rejected: it fixes one arrangement
  and leaves three, four, or mixed-ownership same-colour bishop sets wrong.
- **Model dead positions by search rather than by material.** Rejected as far
  beyond MVP; the colour-complex rule is exact for the material that remains
  after pawns, rooks, and queens are gone.

### Consequences

- `ChessRules` has three claim queries in two shapes each. The no-move shape
  stays the one the server and Android already call, so no caller changes.
- A future transport that wants prospective claims must carry the declared move;
  today's `ClaimDraw` command deliberately does not, and so continues to answer
  the current-position question only.
- Evaluating a declared move costs an `applyMove`, which computes a terminal
  result. That is one query, not a loop, and buys exactness.
- More positions now end automatically as `INSUFFICIENT_MATERIAL`. Opposite-
  colour bishops and any position still holding a knight alongside a second
  piece stay live, because a cooperative mate remains possible there.

---

## D039 — A `401` From the Chess Server Means the Token, So Startup Renews It Once

**Date:** 2026-09-02

**Status:** Accepted

**Relates to:** `D004` (the server is authoritative and trusts no client), `D006`
(invisible anonymous accounts), `D031` (the app talks to Supabase auth directly),
`D035` (the beta was re-pointed at `ChessGame Dev`), `M16.2`

### Decision

When `GET /me` refuses startup with `401`, the app asks the authenticator for a
newly issued token and tries once more. If that is refused too, the refusal is
reported as it was before.

This is sound because of what a `401` can and cannot mean. The server resolves a
verified token to an internal user, **creating it on the first request from a new
anonymous account** (`D006`). So a token that verifies always has a user, and a
`401` is therefore always about the token — never about the account having gone
missing.

Renewal reuses the rule already in `AnonymousAuthenticator`: refresh the session,
and if the refresh token is dead too, create a new anonymous account rather than
leave the app unusable. The only new part is the trigger.

### Rationale

The app can only judge a token by the expiry it stored with it. A token that was
*invalidated* rather than expired looks perfectly valid: a rotated signing key, a
build pointed at a different Supabase project, or a device whose clock is wrong
all produce exactly that. Before this, such a token turned every launch into
`Failed(canRetry = true)` — a retry button that could never work, because
retrying sent the same dead token again. The only way back into the app was to
clear its data, which also destroys the anonymous account and every game with it.

`MVP` *Reliability* requires "App restart restores state". A launch that can
never succeed is the sharpest way to fail that.

### Alternatives Considered

- **Renew on `401` for every request, not just startup.** Rejected for now as
  more than the problem needs. After startup the token is known good, a token
  that dies mid-session degrades to an ordinary error whose retry works, and the
  next launch recovers. Doing it everywhere invites several calls renewing at
  once for one underlying cause.
- **Discard the session and create a new account on `401`.** Rejected: it throws
  away a live account, and its games, on evidence that may be a server-side
  problem. Renewal reaches the same place when the refresh token really is dead,
  and does nothing irreversible when it is not.
- **Leave it and tell testers to clear app data.** Rejected: it destroys the
  account to fix a token.

### Consequences

- One `401` at startup costs one extra auth round trip and one extra `/me`.
- Exactly one further attempt is made, so a genuinely refused caller is still
  told, and this cannot become a loop. `AppRestartTest` locks both halves.
- `AnonymousAuthenticator.renewedSession()` forces what `currentSession()` does
  only near expiry. The mutex still serialises it, so two screens cannot race
  into two anonymous accounts.
- A token that dies mid-session is still surfaced as an error until the next
  launch. That is the deliberate boundary above, and the place to revisit if the
  beta shows it happening.

---

## D040 — The Beta Is a Signed APK Handed Over Directly, and Its Key Never Enters the Repository

**Date:** 2026-09-02

**Status:** Accepted

**Relates to:** `D032` (the beta runs on free tiers, at a hard `$0`), `D033`
(only debug builds talk cleartext), `D034` (the server address is a build
input), `D035` (the beta reuses `ChessGame Dev`), `M17.1`

### Decision

A beta build is distributed as a signed release APK, given to a handful of
friends directly, and installed by sideloading. No app store, console, or
distribution service is involved.

The signing key is created and held by the project owner, outside this
repository. The build reads its location and passwords the same way it reads the
Supabase publishable key — `-PchessKeystoreFile`, `-PchessKeystorePassword`,
`-PchessKeyAlias`, `-PchessKeyPassword`, or the matching `CHESS_KEYSTORE_*`
environment variables — and none of them is ever committed. `*.jks`,
`*.keystore`, and `keystore.properties` are git-ignored so that a key placed in
the working tree cannot be added by accident.

Naming a keystore is what turns signing on. Supplying nothing is the normal case
and leaves the release APK unsigned exactly as before, so `./gradlew build` and
CI are unchanged. Naming a keystore without its passwords, or naming one that
does not exist, **fails the build**.

### Rationale

The `$0` boundary decides most of this. Google Play requires a one-time $25
registration; Firebase App Distribution is free but means another account,
another console, and testers signing in to it. A handful of friends do not need
either — `M17.1` is a *small* beta, and an APK sent directly is the whole
mechanism.

Signing is not optional, though: Android refuses to install an unsigned APK at
all. Before this, `docs/DEVELOPMENT.md` told a developer to sign the release APK
with the local **debug** keystore to try it out. That is fine as a private
convenience and wrong as distribution — the debug key is shared by every Android
developer's machine, is not the app's identity, and cannot be used to ship an
update anyone would trust.

Failing loudly on half-configured signing is the important half of the decision.
The natural failure is silent: a forgotten password variable produces a perfectly
good *unsigned* APK, and nothing goes wrong until a friend cannot install it and
says so a day later.

### Alternatives Considered

- **Google Play internal testing.** Rejected for the MVP beta: a $25 fee against
  a hard `$0` budget, plus review and release-track process for an audience of a
  few friends. Revisit if the beta outgrows sideloading.
- **Firebase App Distribution.** Free, and genuinely nicer for updates, but it
  adds a Google Cloud project, another console, and tester onboarding to a beta
  small enough not to need any of it. Also revisit later.
- **Keep signing with the debug keystore.** Rejected: it is not the app's
  identity, it is a key every machine already has, and it makes "who built this"
  unanswerable.
- **Commit a keystore for the beta and treat it as throwaway.** Rejected. A
  committed signing key is published to everyone with repository access, and the
  key is not throwaway in practice: Android will not install an update signed by
  a different key, so replacing it later means every tester uninstalls first.
- **Generate a key inside the build when none is supplied.** Rejected: it would
  make every machine's APK a different app to Android, and hide the real
  requirement instead of stating it.

### Consequences

- The owner must create the beta keystore once and keep it safe. Losing it means
  no tester can be given an update without uninstalling first.
- The autonomous loop cannot produce an installable beta APK on its own, and
  should not: the key is a credential, and handling credentials and distributing
  builds are human steps (`D026` stop conditions). It prepares and verifies the
  configuration instead.
- `versionCode` and `versionName` became build inputs alongside the rest, so a
  second beta build can install over the first and a tester's report can name a
  build.
- `scripts/verify-beta-apk.sh` proves the whole arrangement against a throwaway
  key it generates and deletes, including that the ordinary build stays unsigned.
- The beta APK is self-signed, so Android will warn about an unknown source and
  Play Protect may ask for confirmation. That is inherent to sideloading, and is
  something to tell testers rather than something to fix.

---

## D041 — The Local Board Says "Check", and a Draw Claim Is Declared by Tapping the Move It Depends On

**Date:** 2026-09-04

**Status:** Accepted

**Relates to:** `D038` (declared-move draw claims), `D019`, `M5.4`, `M5.6`,
`M5.7`, `PRODUCT` *Game Screen* and *Draw Semantics*, `ARCHITECTURE` §23,
independent M5 evaluation findings `M5-01` and `M5-02`
(`evals/M5/critic-report.md`)

### Decision

Two gaps between the local pass-and-play screen and what `game-core` already
knows.

- **A live check is part of the status line.** `PRODUCT`'s *Game Screen* asks a
  board to show a check indication, and the online screen already does
  (`"Your move • Check"`). The local screen said only `"<side> to move"` for
  every position that had not ended. The status line now reads
  `"BLACK to move — Check"` when `ChessRules.isInCheck` says so, and a terminal
  result still takes precedence over everything — a checkmated game reads
  `"BLACK wins — CHECKMATE"`, not a check. The text moved out of the composable
  into `GameControls.statusFor`, next to the other questions the screen asks
  `game-core`, so it is testable without rendering.

- **A move a draw claim depends on is declared, not played.** `D038` gave
  `ChessRules` declared-move claim overloads, but the local screen could only
  ask the current position, and its destination tap committed the move at once.
  A player one move from the third occurrence or from the hundredth halfmove
  therefore could not claim: playing the move handed the position — and the
  claim with it — to the other player. Tapping such a destination now raises a
  `DeclaredMove` holding that exact move and the claims declaring it would
  entitle, and the screen offers the claim, playing the move anyway, or backing
  out. Claiming ends the game from the position in front of the player and
  never plays the declared move, exactly as `D038` specifies.

  What is offered is what declaring the move *adds*: `availableDrawClaims(game,
  move)` less `availableDrawClaims(game)`. A claim the position already carries
  is already a button on screen and needs no declaration, so it raises no
  prompt and the tap plays normally.

### Rationale

Both were found by an independent M5 evaluation against
`8afe530cf8dd467a6063c5514767eb7814bcc0f4` and reproduce unchanged on the
current branch; neither is new behavior in `game-core`, and neither adds a chess
rule to the UI. The check is `ChessRules.isInCheck`; the entitlement is
`ChessRules.availableDrawClaims`.

Interrupting the tap is how this screen already handles a move that needs a
decision: a pawn reaching the last rank raises the promotion prompt rather than
moving, because the player must choose the piece (`M5.4`). A move carrying a
claim is the same shape of choice, and it is the only point at which the exact
contemplated move is known and the entitlement still exists. Subtracting the
current-position claims keeps the interruption rare: the fifty-move prompt can
only appear at halfmove 99, and the threefold prompt stops once the claim
stands on the board for both players.

### Alternatives Considered

- **Offer prospective claims as buttons beside the board while a piece is
  selected, leaving the tap alone.** Rejected. It computes a claim for every
  legal destination of the selected piece rather than the one move the player
  named, and an ordinary tap on the destination silently throws the entitlement
  away — the failure the finding is about.
- **Let the no-move claim query answer "could this player claim by declaring
  something?"** Already rejected by `D038`: it grants a claim with no move
  behind it. The local screen keeps calling that query for exactly what it
  means, and `GameControls.canClaimDraw` is unchanged.
- **Recompute check in the composable.** Rejected: rule logic stays out of
  Compose, and the status line belongs with the rest of `GameControls`.

### Consequences

- A destination tap no longer always plays the move. It plays it unless the
  move needs a choice first — the promotion piece, or a draw claim only that
  move entitles. `M5.4`'s and `M5.6`'s notes record this.
- Local play now exercises `game-core`'s declared-move claim overloads, which
  until now no caller used. The server still does not: today's `ClaimDraw`
  command carries no declared move (`D038`), and that is unchanged here.
- The online screen is untouched. It already shows check, and its claims come
  from the server.
