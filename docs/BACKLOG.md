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

---

# M1 — Repository and Build Bootstrap

## M1.1 — Create monorepo structure

**Status:** TODO  
**Depends on:** None

### Objective

Create:

```text
game-core/
android-app/
server/
database/migrations/
docs/
```

### Acceptance Criteria

- repository structure exists,
- Gradle project recognizes code modules,
- docs are in expected locations.

### Verification

- inspect Gradle settings,
- run a basic Gradle task successfully.

---

## M1.2 — Configure pure Kotlin/JVM `game-core`

**Status:** TODO  
**Depends on:** M1.1

### Objective

Create a platform-independent Kotlin/JVM shared library.

### Acceptance Criteria

- no Android/Ktor/database dependencies,
- module builds,
- trivial unit test passes.

### Verification

Record exact verified test/build command in `docs/DEVELOPMENT.md`.

---

## M1.3 — Configure Android app

**Status:** TODO  
**Depends on:** M1.2

### Objective

Create native Android app using Kotlin + Jetpack Compose.

### Acceptance Criteria

- depends on `game-core`,
- debug build succeeds,
- launches on emulator/device,
- references a type from `game-core`.

### Verification

Record exact Android build/test command in `docs/DEVELOPMENT.md`.

---

## M1.4 — Configure Ktor server

**Status:** TODO  
**Depends on:** M1.2

### Objective

Create Kotlin/Ktor server with a health endpoint.

### Acceptance Criteria

- depends on `game-core`,
- server builds,
- server starts locally,
- `/health` returns success,
- server references a type from `game-core`.

### Verification

Record exact run/build/test commands in `docs/DEVELOPMENT.md`.

---

## M1.5 — Configure formatting and static analysis

**Status:** TODO  
**Depends on:** M1.2, M1.3, M1.4

### Objective

Select maintained Kotlin/Android formatting and static-analysis tooling.

### Acceptance Criteria

- project has automated formatting/static checks,
- commands are documented,
- checks can run non-interactively.

### Verification

Run configured checks successfully.

---

## M1.6 — Establish developer verification commands

**Status:** TODO  
**Depends on:** M1.2, M1.3, M1.4, M1.5

### Objective

Replace placeholders in `docs/DEVELOPMENT.md` with commands actually verified against the repository.

### Acceptance Criteria

Document verified commands for:

- game-core tests/build,
- Android unit tests,
- Android debug build,
- server tests/build/run,
- formatting/static checks,
- aggregate checks.

### Verification

Execute every documented command successfully.

---

## M1.7 — Add CI

**Status:** TODO  
**Depends on:** M1.6

### Objective

Add GitHub Actions or equivalent CI.

### Acceptance Criteria

CI runs:

- game-core tests,
- server tests,
- Android unit tests,
- Android build/check,
- static checks.

### Verification

CI passes on the bootstrap branch/commit.

---

# M2 — Chess Domain Model

## M2.1 — Define core chess types

**Status:** TODO  
**Depends on:** M1

### Objective

Create pure Kotlin domain representations for:

- side/color,
- piece type,
- piece,
- square/position,
- board,
- move,
- game state,
- result,
- castling rights,
- draw-rule tracking.

### Acceptance Criteria

Types remain independent of Android/server/database concerns.

### Verification

Unit tests compile and exercise representative values.

---

## M2.2 — Initial standard position

**Status:** TODO  
**Depends on:** M2.1

### Acceptance Criteria

Initial state has:

- 32 pieces,
- correct starting squares,
- White to move,
- correct castling rights,
- no active move history,
- correct move counters.

### Verification

Dedicated game-core tests pass.

---

# M3 — Chess Legal Move Engine

## M3.1 — Sliding piece pseudo-legal movement

**Status:** TODO  
**Depends on:** M2.2

### Objective

Implement rook, bishop, queen movement.

### Acceptance Criteria

- movement geometry correct,
- blockers respected,
- captures correct.

### Verification

Positive/negative unit tests pass.

---

## M3.2 — Knight movement

**Status:** TODO  
**Depends on:** M2.2

### Acceptance Criteria

- legal jumps/captures,
- illegal destinations rejected.

### Verification

Unit tests pass.

---

## M3.3 — King movement

**Status:** TODO  
**Depends on:** M2.2

### Objective

Implement basic king movement before castling.

### Verification

Unit tests pass.

---

## M3.4 — Pawn movement

**Status:** TODO  
**Depends on:** M2.2

### Acceptance Criteria

- one-square advance,
- initial two-square advance,
- diagonal capture,
- blocking.

Promotion and en passant are separate tasks.

### Verification

Unit tests pass.

---

## M3.5 — Attack and check detection

**Status:** TODO  
**Depends on:** M3.1, M3.2, M3.3, M3.4

### Acceptance Criteria

Correctly detect attacks/check by every piece type.

### Verification

Representative check/non-check tests pass.

---

## M3.6 — Self-check prevention

**Status:** TODO  
**Depends on:** M3.5

### Acceptance Criteria

A pseudo-legal move that leaves own king in check is illegal.

Include pinned-piece behavior.

### Verification

Pinned/self-check tests pass.

---

## M3.7 — Castling

**Status:** TODO  
**Depends on:** M3.3, M3.5, M3.6

### Acceptance Criteria

Validate:

- king/rook rights,
- empty path,
- king not currently in check,
- transit square not attacked,
- destination not attacked.

### Verification

Positive and all major negative cases pass.

---

## M3.8 — En passant

**Status:** TODO  
**Depends on:** M3.4, M3.6

### Acceptance Criteria

Eligibility and expiration are correct.

### Verification

Unit tests cover creation, valid capture, expiration, and self-check interactions.

---

## M3.9 — Promotion

**Status:** TODO  
**Depends on:** M3.4, M3.6

### Acceptance Criteria

Promotion supports:

- Queen,
- Rook,
- Bishop,
- Knight.

No automatic Queen promotion.

### Verification

Tests cover each promotion choice.

---

## M3.10 — Checkmate and stalemate

**Status:** TODO  
**Depends on:** M3.6, M3.7, M3.8, M3.9

### Acceptance Criteria

Correctly detect checkmate vs stalemate.

### Verification

Known positions pass.

---

## M3.11 — Insufficient material

**Status:** TODO  
**Depends on:** M2.1

### Acceptance Criteria

Recognize required standard automatic insufficient-material draws.

### Verification

Positive/negative positions pass.

---

## M3.12 — Repetition tracking and claims

**Status:** TODO  
**Depends on:** M3.10

### Acceptance Criteria

- track repetition-relevant position state correctly,
- expose valid threefold claim,
- automatically end on fivefold repetition.

### Verification

Sequence-based tests pass.

---

## M3.13 — Fifty-/seventy-five-move rules

**Status:** TODO  
**Depends on:** M3.10

### Acceptance Criteria

- expose valid fifty-move claim,
- automatically end at seventy-five moves,
- reset counter correctly after pawn move/capture.

### Verification

Boundary tests pass.

---

## M3.14 — `ClaimDraw` game-core behavior

**Status:** TODO  
**Depends on:** M3.12, M3.13

### Acceptance Criteria

- valid claim finalizes draw,
- invalid claim rejected,
- automatic draw conditions need no claim.

### Verification

Game-core tests pass.

---

# M4 — Undo Semantics

## M4.1 — Active move history

**Status:** TODO  
**Depends on:** M3

### Objective

Store enough history to restore exact prior game state.

### Acceptance Criteria

Restoration includes:

- board,
- side to move,
- castling,
- en passant,
- counters,
- repetition state.

### Verification

Round-trip move/undo tests pass.

---

## M4.2 — Undo latest unanswered move

**Status:** TODO  
**Depends on:** M4.1

### Acceptance Criteria

- player can undo own latest unanswered move,
- cannot undo after opponent responds,
- opponent can undo their response,
- prior player's move becomes undoable again.

### Verification

Sequence tests pass.

---

## M4.3 — Final move cannot be undone

**Status:** TODO  
**Depends on:** M4.2, M3.10, M3.11, M3.12, M3.13

### Acceptance Criteria

Any terminal game result locks the final action.

### Verification

Terminal/undo tests pass.

---

# M5 — Local Android Chess

## M5.1 — Render board

**Status:** TODO  
**Depends on:** M3

### Acceptance Criteria

Correct board/pieces render from `game-core` state.

### Verification

Android debug build + relevant UI/unit tests.

---

## M5.2 — Piece selection

**Status:** TODO  
**Depends on:** M5.1

### Acceptance Criteria

Tap own piece and highlight selected square.

---

## M5.3 — Legal move highlights

**Status:** TODO  
**Depends on:** M5.2

### Acceptance Criteria

Highlights come from `game-core`; UI does not duplicate rules.

---

## M5.4 — Apply local move

**Status:** TODO  
**Depends on:** M5.3

### Acceptance Criteria

Tap legal destination and update local game state.

---

## M5.5 — Board orientation

**Status:** TODO  
**Depends on:** M5.1

### Acceptance Criteria

Own side appears at bottom.

---

## M5.6 — Move history, Undo, and Claim Draw UI

**Status:** TODO  
**Depends on:** M4, M3.14

### Acceptance Criteria

- move history visible,
- Undo visible only when eligible,
- Claim Draw visible only when valid.

---

## M5.7 — Local game completion

**Status:** TODO  
**Depends on:** M5.4, M5.6

### Acceptance Criteria

Complete standard chess game can be played pass-and-play on one device.

### Verification

All game-core tests pass, Android builds, and representative manual local game works.

---

# M6 — Server + PostgreSQL Foundation

## M6.1 — Development PostgreSQL

**Status:** TODO  
**Depends on:** M1

### Objective

Create local/test PostgreSQL separate from production.

### Acceptance Criteria

Disposable development/test database is available.

---

## M6.2 — Select PostgreSQL access library

**Status:** TODO  
**Depends on:** M6.1

### Selection Criteria

- maintained,
- PostgreSQL support,
- transaction support,
- Kotlin ergonomics,
- migration compatibility,
- testability.

### Acceptance Criteria

Decision recorded in `DECISIONS.md`.

---

## M6.3 — Database migrations

**Status:** TODO  
**Depends on:** M6.2

### Acceptance Criteria

Repeatable migration process exists and is documented.

---

## M6.4 — Initial schema

**Status:** TODO  
**Depends on:** M6.3

### Objective

Create:

- users,
- friendships,
- game_series,
- games,
- moves,
- game_events.

### Acceptance Criteria

Required primary/foreign/unique constraints exist.

---

## M6.5 — Server persistence integration

**Status:** TODO  
**Depends on:** M6.4

### Acceptance Criteria

Server can persist/load representative state transactionally.

### Verification

Integration tests pass.

---

# M7 — Identity and Username

## M7.1 — Supabase project

**Status:** TODO  
**Depends on:** M6

### Acceptance Criteria

PostgreSQL and anonymous Auth configured for development environment.

---

## M7.2 — Android anonymous auth

**Status:** TODO  
**Depends on:** M7.1

### Acceptance Criteria

Anonymous session can be created and restored.

---

## M7.3 — Ktor token verification

**Status:** TODO  
**Depends on:** M7.1

### Acceptance Criteria

Ktor verifies Supabase-issued token and resolves internal user ID.

---

## M7.4 — Username claim

**Status:** TODO  
**Depends on:** M7.3, M6.4

### Acceptance Criteria

- lowercase normalized lookup,
- DB uniqueness constraint,
- 3–24 character validation,
- allowed-character validation,
- lost usernames are not auto-recycled.

### Verification

Concurrent claim test: exactly one succeeds.

---

## M7.5 — Track last seen

**Status:** TODO  
**Depends on:** M7.4

### Acceptance Criteria

Meaningful activity updates `lastSeenAt` without continuous heartbeat.

---

# M8 — Friends

## M8.1 — Username lookup

**Status:** TODO  
**Depends on:** M7.4

### Acceptance Criteria

Exact normalized lookup returns one user or not found.

---

## M8.2 — Add friend

**Status:** TODO  
**Depends on:** M8.1

### Acceptance Criteria

Prevent:

- self-friendship,
- duplicate friendship,
- reversed duplicate.

Friendship is mutual immediately.

---

## M8.3 — Friends list

**Status:** TODO  
**Depends on:** M8.2

---

## M8.4 — Remove friend

**Status:** TODO  
**Depends on:** M8.2

### Acceptance Criteria

- friendship removed/deactivated,
- history preserved,
- current game preserved,
- active series marked to close after current game.

---

# M9 — Game Series

## M9.1 — Start/open active series

**Status:** TODO  
**Depends on:** M8.2

### Acceptance Criteria

- one `ACTIVE` series per friend pair,
- existing active series opens instead of duplicate creation.

### Verification

Concurrent create test prevents duplicate active series.

---

## M9.2 — Initial game

**Status:** TODO  
**Depends on:** M9.1

### Acceptance Criteria

Initial colors randomized and current game attached to series.

---

## M9.3 — Series close-after-current lifecycle

**Status:** TODO  
**Depends on:** M9.1, M8.4

### Acceptance Criteria

Series can be marked to close after its current game and transitions idempotently.

---

## M9.4 — Dashboard discovery

**Status:** TODO  
**Depends on:** M9.1, M9.2

### Acceptance Criteria

Current active series/game can be loaded efficiently.

---

# M10 — Authoritative Game Command API

## M10.1 — `MakeMove`

**Status:** TODO  
**Depends on:** M4, M6.5, M9

### Acceptance Criteria

Server:

```text
authenticate
→ load game
→ validate participant/turn/version
→ run game-core
→ persist transaction
→ increment version
```

---

## M10.2 — Stale version handling

**Status:** TODO  
**Depends on:** M10.1

### Acceptance Criteria

Stale command rejected cleanly and client can refresh.

---

## M10.3 — `ClaimDraw`

**Status:** TODO  
**Depends on:** M3.14, M10.1

### Acceptance Criteria

Server authoritatively accepts valid claim and rejects invalid claim.

---

## M10.4 — Two-client turn-taking

**Status:** TODO  
**Depends on:** M10.1

### Acceptance Criteria

Two authenticated users can alternate server-authoritative moves.

---

# M11 — Authoritative Undo

## M11.1 — `UndoMove`

**Status:** TODO  
**Depends on:** M4, M10.1

### Acceptance Criteria

Exact product rule enforced server-side.

---

## M11.2 — Move-vs-undo concurrency

**Status:** TODO  
**Depends on:** M11.1

### Acceptance Criteria

Near-simultaneous Move/Undo results in exactly one valid transition.

### Verification

Integration/concurrency test passes repeatedly.

---

# M12 — Realtime

## M12.1 — WebSocket connection

**Status:** TODO  
**Depends on:** M10

### Acceptance Criteria

Authenticated client can establish realtime connection.

---

## M12.2 — Publish game updates

**Status:** TODO  
**Depends on:** M12.1

### Acceptance Criteria

Move on client A updates client B automatically.

---

## M12.3 — Reconnect recovery

**Status:** TODO  
**Depends on:** M12.2

### Acceptance Criteria

Missed realtime messages do not corrupt state; canonical state reloads over HTTPS.

---

# M13 — Game End and Automatic Rematch

## M13.1 — Finalize game transactionally

**Status:** TODO  
**Depends on:** M10, M11

### Acceptance Criteria

Terminal result persists exactly once.

---

## M13.2 — Create next game exactly once

**Status:** TODO  
**Depends on:** M13.1, M9.2

### Acceptance Criteria

For active series:

- one next game created,
- series points to it,
- operation idempotent.

---

## M13.3 — Alternate colors

**Status:** TODO  
**Depends on:** M13.2

### Acceptance Criteria

Rematch colors reverse from prior game.

---

## M13.4 — Close series without rematch

**Status:** TODO  
**Depends on:** M13.1, M9.3

### Acceptance Criteria

If series is marked to close:

- game finalizes,
- no rematch created,
- series becomes `CLOSED`.

---

## M13.5 — Resignation path

**Status:** TODO  
**Depends on:** M13.2, M13.4

### Acceptance Criteria

Resignation follows the same active-vs-closing series lifecycle.

---

# M14 — Dashboard and History

## M14.1 — Your Turn

**Status:** TODO  
**Depends on:** M9, M10

---

## M14.2 — Their Turn

**Status:** TODO  
**Depends on:** M14.1

---

## M14.3 — Friends

**Status:** TODO  
**Depends on:** M8

---

## M14.4 — Completed game and closed-series history

**Status:** TODO  
**Depends on:** M13

### Acceptance Criteria

Historical games/series are read-only and remain accessible.

---

# M15 — Beta Deployment Environment

## M15.1 — Select Ktor hosting provider

**Status:** TODO  
**Depends on:** M14

### Objective

Choose an inexpensive provider appropriate for a small beta.

### Acceptance Criteria

Decision recorded with cost/operational rationale.

---

## M15.2 — Deploy Ktor beta server

**Status:** TODO  
**Depends on:** M15.1

### Acceptance Criteria

Android test build can reach deployed server securely.

---

## M15.3 — Configure beta Supabase environment

**Status:** TODO  
**Depends on:** M7, M15.1

### Acceptance Criteria

Beta environment separated from local disposable development data.

---

## M15.4 — Configure Android beta endpoint

**Status:** TODO  
**Depends on:** M15.2

### Acceptance Criteria

Beta build targets deployed beta API without hard-coded production secrets.

---

# M16 — Hardening

## M16.1 — Network interruption

**Status:** TODO  
**Depends on:** M12, M15

---

## M16.2 — App restart/reconnect

**Status:** TODO  
**Depends on:** M12, M15

---

## M16.3 — Duplicate commands

**Status:** TODO  
**Depends on:** M10, M11, M13

---

## M16.4 — Series/rematch idempotency

**Status:** TODO  
**Depends on:** M13

---

## M16.5 — Server logging

**Status:** TODO  
**Depends on:** M10

### Acceptance Criteria

Log enough for debugging without credentials/secrets.

---

# M17 — Friend Beta

## M17.1 — Small beta distribution

**Status:** TODO  
**Depends on:** M15, M16

### Observe

- onboarding,
- username/friend discovery,
- turn clarity,
- undo behavior,
- draw-claim clarity,
- friend-removal behavior,
- automatic rematch continuity,
- synchronization reliability.

### Acceptance Criteria

Core play is stable enough to gather product feedback without developer intervention during normal moves.

---

# M18 — Post-Chess Architecture Review

## M18.1 — Identify genuinely reusable platform concepts

**Status:** TODO  
**Depends on:** M17

### Objective

Review what is genuinely reusable before designing the deck-builder.

### Acceptance Criteria

Document:

- chess-specific concepts,
- proven platform concepts,
- abstractions worth extracting,
- abstractions that should remain concrete.
