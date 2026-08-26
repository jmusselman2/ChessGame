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

Autonomous work happens on the `claude-autopilot` branch. `main` stays
protected; no direct pushes, no force-push, no rewriting published history;
PR/merge into `main` stays human-controlled; production and beta deployment
require explicit approval.

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
