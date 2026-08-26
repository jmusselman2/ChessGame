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

## Task Selection Order (Autonomous Mode)

When running the continuous autonomous workflow (`docs/AUTONOMOUS-DEVELOPMENT.md`),
select the next task deterministically:

1. Consider only tasks with `Status: TODO`.
2. Exclude any task whose `Depends on` tasks are not all `DONE`.
3. Exclude any task marked `BLOCKED` and any task that would require a
   documented stop condition.
4. Of the remainder, pick the one that comes first in this document
   (lowest milestone number, then lowest task number).
5. Mark it `IN PROGRESS` before editing code.
6. Mark it `DONE` only after its required **local** verification passes
   (implementation + acceptance criteria + `./gradlew build`).

### `DONE` is not the same as being allowed to start the next task

- `DONE` = the task's implementation and local acceptance criteria passed.
- The autonomous workflow may **not** select or begin the next task until the
  pushed `claude-autopilot` commit has passed its **required GitHub Actions
  run**. A green remote CI run is the branch-level progression gate; `DONE` is
  the task-level implementation gate. A task can legitimately be `DONE` while
  the branch is still waiting on CI.
- For a task whose acceptance criteria require GitHub Actions itself to run
  successfully (for example `M1.7`), do **not** mark it `DONE` until that CI run
  has actually succeeded — the successful run is part of that task's acceptance
  criteria, not only the progression gate.
- If remote CI cannot be verified (GitHub CLI unavailable, unauthenticated, or
  no repo access), stop per the Stop Conditions in
  `docs/AUTONOMOUS-DEVELOPMENT.md`. Do not silently skip remote CI.

Milestone boundaries are **not** stopping points. After a task is `DONE` and its
pushed commit's required CI is green, immediately select the next one,
continuing into the next milestone.

A single milestone-level task with no sub-tasks (for example `M2.1` inside a
milestone that only has `M2.1`, `M2.2`) is selected the same way.

---

# M1 — Repository and Build Bootstrap

**Milestone status:** COMPLETE. `M1.1`–`M1.7` are `DONE` (local verification
2026-08-25). The required CI workflow ran green on `claude-autopilot` commit
`595c124` (GitHub Actions run
[32922786058](https://github.com/jmusselman2/ChessGame/actions/runs/32922786058),
`headSha` `595c124c7e1e757b15a7cbeb7c9dbefd9c42fa8e`, conclusion `success`,
2026-08-25).

## M1.1 — Create monorepo structure

**Status:** DONE

**Depends on:** None

**Completed:** 2026-08-25 — `game-core/`, `android-app/`, `server/`,
`database/migrations/`, and `docs/` exist. `./gradlew projects` lists
`:android-app`, `:game-core`, `:server`. Root Gradle build (Kotlin DSL) is at
the repository root.

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

**Status:** DONE

**Depends on:** M1.1

**Completed:** 2026-08-25 — `game-core` applies only the Kotlin/JVM plugin, has
no Android/Ktor/database dependencies, builds via `./gradlew :game-core:build`,
and `./gradlew :game-core:test` passes (`GameCoreTest`). Command recorded in
`docs/DEVELOPMENT.md`.

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

**Status:** DONE

**Depends on:** M1.2

**Completed:** 2026-08-25 — `android-app` depends on `:game-core`,
`./gradlew :android-app:assembleDebug` and `./gradlew :android-app:build`
succeed, `MainActivity` references `com.jmussel.chessgame.core.GameCore`, and
the app has been manually verified to launch (see `docs/DEVELOPMENT.md`).

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

**Status:** DONE

**Depends on:** M1.2

**Completed:** 2026-08-25 — `server` depends on `:game-core`,
`./gradlew :server:build` and `:server:test` pass, `./gradlew :server:run`
starts locally, and `GET http://localhost:8080/health` returns HTTP 200
`ChessGame server is healthy`. `Application.kt` references `GameCore`.

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

**Status:** DONE

**Depends on:** M1.2, M1.3, M1.4

**Completed:** 2026-08-25 — ktlint Gradle plugin (`14.2.0`) is applied to every
subproject and wired into `check`; Android lint runs via `check`.
`./gradlew ktlintCheck` and `./gradlew ktlintFormat` run non-interactively.
Detekt is intentionally deferred (documented in `docs/DEVELOPMENT.md`).

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

**Status:** DONE

**Depends on:** M1.2, M1.3, M1.4, M1.5

**Completed:** 2026-08-25 — `docs/DEVELOPMENT.md` documents verified commands for
game-core tests/build, Android unit tests, Android debug build, server
tests/build/run, formatting/static checks, and the single aggregate command
`./gradlew build`. Every documented command was executed successfully on
2026-08-25.

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

**Status:** DONE

**Depends on:** M1.6

**Completed:** 2026-08-25 — `.github/workflows/ci.yml` runs on pushes to `main`
and `claude-autopilot` and on pull requests targeting `main`. It checks out,
sets up JDK 24 (Temurin) and Gradle, and runs a single `./gradlew build` step,
which covers ktlintCheck, game-core tests, server tests + build, Android unit
tests, and Android build/lint. Deprecated action majors were updated
(`actions/checkout@v7`, `actions/setup-java@v5`, `gradle/actions/setup-gradle@v6`)
to clear runner deprecation warnings.

The required CI run passed for the exact `claude-autopilot` HEAD: GitHub Actions
run [32922786058](https://github.com/jmusselman2/ChessGame/actions/runs/32922786058)
(workflow `CI`, job `Build and Test`), `event` `push`, `headBranch`
`claude-autopilot`, `headSha` `595c124c7e1e757b15a7cbeb7c9dbefd9c42fa8e`,
`conclusion` `success` — verified with `gh run view` and a `check-runs` query
against that SHA.

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

CI passes on the bootstrap branch/commit — confirmed by watching the required
GitHub Actions run for the specific pushed commit (not an unrelated later run).

---

# M2 — Chess Domain Model

## M2.1 — Define core chess types

**Status:** DONE

**Depends on:** M1

**Completed:** 2026-08-26 — `game-core` package
`com.jmussel.chessgame.core.chess` now defines `Side`, `PieceType`, `Piece`,
`Square`, `Board`, `Move`, `CastlingSide`/`CastlingRights`,
`PositionKey`/`DrawClaim`/`DrawRuleState`, `GameOutcome`/`TerminationReason`/
`GameResult`, and `GameState`. All types are immutable and depend on nothing
outside the Kotlin standard library. Verified locally with
`.\gradlew.bat :game-core:test` (54 tests across 8 new test classes) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, includes `ktlintCheck` and Android
lint). Naming/package choice recorded as `D027` in `docs/DECISIONS.md`.

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

**Status:** DONE

**Depends on:** M2.1

**Completed:** 2026-08-26 — `StandardPosition` in
`com.jmussel.chessgame.core.chess` exposes the standard starting `BOARD` and
`newGame()`. A new game has 32 pieces on their correct squares, White to move,
all four castling rights, no en passant target, no active history, halfmove
clock `0`, fullmove number `1`, and no result. (Since `M3.12` the starting
position is also recorded once in `DrawRuleState.positionCounts` for repetition
counting; no moves have been played.) Verified locally with
`.\gradlew.bat :game-core:test` (13 new
`StandardPositionTest` cases, 67 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M2.2

**Completed:** 2026-08-26 — `Direction` (with `Square.shifted`) and
`PseudoLegalMoves.slidingDestinations` / `slidingMoves` implement rook, bishop,
and queen geometry: each ray runs until it leaves the board or meets a piece, a
friendly piece blocks without being a destination, and the first enemy piece is
capturable but blocks everything behind it. Verified locally with
`.\gradlew.bat :game-core:test` (16 new `SlidingMovesTest` cases, 83 game-core
tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M2.2

**Completed:** 2026-08-26 — `PseudoLegalMoves.KNIGHT_STEPS` plus
`knightDestinations` / `knightMoves` implement knight movement: the eight steps
that stay on the board, landing on empty squares or enemy pieces, jumping over
anything in between; a friendly occupant removes that destination. Verified
locally with `.\gradlew.bat :game-core:test` (12 new `KnightMovesTest` cases, 95
game-core tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

- legal jumps/captures,
- illegal destinations rejected.

### Verification

Unit tests pass.

---

## M3.3 — King movement

**Status:** DONE

**Depends on:** M2.2

**Completed:** 2026-08-26 — `PseudoLegalMoves.kingDestinations` / `kingMoves`
implement plain king movement: the eight adjacent squares that stay on the
board, excluding those holding a friendly piece. Castling (`M3.7`) and
attacked-square filtering (`M3.5`/`M3.6`) are deliberately separate. Verified
locally with `.\gradlew.bat :game-core:test` (11 new `KingMovesTest` cases, 106
game-core tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Objective

Implement basic king movement before castling.

### Verification

Unit tests pass.

---

## M3.4 — Pawn movement

**Status:** DONE

**Depends on:** M2.2

**Completed:** 2026-08-26 — `PseudoLegalMoves.pawnAdvanceDirection`,
`pawnCaptureSquares`, `pawnDestinations`, and `pawnMoves` implement pawn
movement for both sides: single advance onto an empty square, the initial
two-square advance when both squares are empty, and diagonal capture of an
enemy piece only. A piece directly ahead blocks both advances, and a pawn
cannot capture straight ahead. En passant (`M3.8`) and promotion (`M3.9`) are
deliberately not applied here. Verified locally with
`.\gradlew.bat :game-core:test` (16 new `PawnMovesTest` cases, 122 game-core
tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M3.1, M3.2, M3.3, M3.4

**Completed:** 2026-08-26 — `Attacks` provides `attackedSquaresFrom`,
`attackersOf`, `isAttacked`, `attackedSquares`, `kingSquare`, `isInCheck`
(board or `GameState`), and `isSideToMoveInCheck`. Attack squares differ from
movement where the rules do: pawns attack only their capture diagonals, and a
square occupied by a friendly piece is still defended. `PseudoLegalMoves.ray`
was refactored to return every square up to and including the first blocker so
movement and attacks share one ray; `slidingDestinations` filters out a
friendly-occupied final square. Verified locally with
`.\gradlew.bat :game-core:test` (20 new `AttacksTest` cases, 142 game-core
tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Correctly detect attacks/check by every piece type.

### Verification

Representative check/non-check tests pass.

---

## M3.6 — Self-check prevention

**Status:** DONE

**Depends on:** M3.5

**Completed:** 2026-08-26 — `LegalMoves` adds `boardAfter`,
`leavesOwnKingInCheck`, `isLegal`, `from`, `forSide`, and `forSideToMove`: a
pseudo-legal move is discarded when the resulting board leaves the mover's own
king attacked. This covers pinned pieces (may slide along the pin line and
capture the pinner, nothing else), kings stepping onto attacked or defended
squares, retreating along a checking ray, and answering an existing check by
capture or block. `PseudoLegalMoves.from` / `forSide` dispatch pseudo-legal
generation by piece type. Verified locally with `.\gradlew.bat :game-core:test`
(18 new `LegalMovesTest` cases including the 20-legal-move opening position,
160 game-core tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

A pseudo-legal move that leaves own king in check is illegal.

Include pinned-piece behavior.

### Verification

Pinned/self-check tests pass.

---

## M3.7 — Castling

**Status:** DONE

**Depends on:** M3.3, M3.5, M3.6

**Completed:** 2026-08-26 — `Castling` names the standard king/rook squares and
implements `canCastle` / `availableMoves`, requiring the castling right, the
king and rook on their home squares, an empty path (including `b1`/`b8` on the
queen side), a king that is not in check, and neither the crossed square nor
the destination attacked. An attacked `b1`/`b8` or an attacked rook does not
prevent castling. `LegalMoves.boardAfter` moves the rook with the king, and
`LegalMoves.forSideToMove` / `isLegal(state, move)` include castling because it
needs the rights that only `GameState` carries. Verified locally with
`.\gradlew.bat :game-core:test` (19 new `CastlingTest` cases covering the
positive and every listed negative case, 179 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M3.4, M3.6

**Completed:** 2026-08-26 — `EnPassant` implements the target square created by
a two-square pawn advance, the capture itself, and its expiry after the very
next move; `LegalMoves` gained `GameState` overloads of `boardAfter` and
`leavesOwnKingInCheck` so an en passant capture that would expose its own king
(including the two-pawns-off-the-fifth-rank case) is rejected. Satisfying
"creation" and "expiration" required a state transition, so this task also
introduced `ChessRules.applyMove` (board, side to move, castling rights, en
passant target, halfmove clock, fullmove number) — see `D028`. Verified locally
with `.\gradlew.bat :game-core:test` (15 new `EnPassantTest` cases and 14 new
`ChessRulesApplyMoveTest` cases, 208 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Eligibility and expiration are correct.

### Verification

Unit tests cover creation, valid capture, expiration, and self-check interactions.

---

## M3.9 — Promotion

**Status:** DONE

**Depends on:** M3.4, M3.6

**Completed:** 2026-08-26 — `PseudoLegalMoves.pawnMoves` expands a pawn move
onto the promotion rank (`promotionRankOf`) into one move per choice in
`PieceType.PROMOTION_CHOICES` — Queen, Rook, Bishop, Knight — for advances and
captures alike. A bare move onto the promotion rank is never generated, so
there is no automatic queen promotion, and `LegalMoves.boardAfter` places the
chosen piece. Verified locally with `.\gradlew.bat :game-core:test` (10 new
`PromotionTest` cases covering each choice for both sides, 218 game-core tests
total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M3.6, M3.7, M3.8, M3.9

**Completed:** 2026-08-26 — `ChessRules` adds `isInCheck`, `hasNoLegalMoves`,
`isCheckmate`, `isStalemate`, and `terminalResult`, and `applyMove` now records
the result when a move ends the game (`D017`: a game-ending move is immediately
final). Checkmate is a win for the side that delivered it; stalemate is a draw
with no winner. Verified locally with `.\gradlew.bat :game-core:test` (12 new
`CheckmateStalemateTest` cases: back-rank, smothered, Fool's mate and Scholar's
mate played from the starting position, escapable/blockable/capturable checks,
two stalemates, and a near-stalemate with one pawn move left; 230 game-core
tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Correctly detect checkmate vs stalemate.

### Verification

Known positions pass.

---

## M3.11 — Insufficient material

**Status:** DONE

**Depends on:** M2.1

**Completed:** 2026-08-26 — `InsufficientMaterial.isDraw` recognises the
standard automatic draws: king vs king, king and one bishop vs king, king and
one knight vs king, and bishop vs bishop on the same square colour. Two
knights, two bishops for one side, bishop vs knight, opposite-coloured bishops,
and any pawn, rook, or queen are not automatic draws. `ChessRules.terminalResult`
reports it as `INSUFFICIENT_MATERIAL`, checked before stalemate because a dead
position ends the game the moment material becomes insufficient. Verified
locally with `.\gradlew.bat :game-core:test` (12 new `InsufficientMaterialTest`
cases covering positive and negative material combinations, 242 game-core tests
total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Recognize required standard automatic insufficient-material draws.

### Verification

Positive/negative positions pass.

---

## M3.12 — Repetition tracking and claims

**Status:** DONE

**Depends on:** M3.10

**Completed:** 2026-08-26 — `Repetition` computes a position's identity
(placement, side to move, castling rights, and an en passant target only while
a capture onto it is actually legal), counts occurrences in
`DrawRuleState.positionCounts`, exposes `canClaimThreefold`, and reports
`isFivefold`. `StandardPosition.newGame` records the starting position once,
`ChessRules.applyMove` records each new position and clears the history on an
irreversible pawn move or capture, and `terminalResult` ends the game
automatically at five occurrences with `FIVEFOLD_REPETITION`. A threefold
repetition stays claimable rather than automatic (`D019`). Verified locally
with `.\gradlew.bat :game-core:test` (13 new `RepetitionTest` cases driving
knight-shuffle repetitions up to fivefold, 255 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

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

`database/migrations/.gitkeep` is only a placeholder to keep the empty directory
tracked. Delete it in the same change that adds the first real migration file.

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
