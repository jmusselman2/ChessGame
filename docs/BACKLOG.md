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

The required M1.7 CI verification passed for commit
`595c124c7e1e757b15a7cbeb7c9dbefd9c42fa8e`: GitHub Actions run
[32922786058](https://github.com/jmusselman2/ChessGame/actions/runs/32922786058)
(workflow `CI`, job `Build and Test`), `event` `push`, `headBranch`
`claude-autopilot`, `conclusion` `success` — verified with `gh run view` and a
`check-runs` query against that SHA. This is a historical milestone record, not
a statement about the branch's current HEAD.

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

**Status:** DONE

**Depends on:** M3.10

**Completed:** 2026-08-26 — `MoveCountDraws.canClaimFiftyMove` becomes true at
100 halfmoves without a pawn move or capture, and
`isSeventyFiveMoveDraw` at 150; `ChessRules.terminalResult` ends the game
automatically with `SEVENTY_FIVE_MOVE_RULE`, while the fifty-move draw stays
claimable (`D019`). Checkmate on the 75th move is still checkmate because
`terminalResult` checks it first. The halfmove clock advances on quiet moves
for both sides and resets to `0` on any pawn move or capture. Verified locally
with `.\gradlew.bat :game-core:test` (11 new `MoveCountDrawsTest` cases
covering the 99/100 and 148/149/150 boundaries, 266 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

- expose valid fifty-move claim,
- automatically end at seventy-five moves,
- reset counter correctly after pawn move/capture.

### Verification

Boundary tests pass.

---

## M3.14 — `ClaimDraw` game-core behavior

**Status:** DONE

**Depends on:** M3.12, M3.13

**Completed:** 2026-08-26 — `ChessRules.availableDrawClaims`, `canClaimDraw`,
and `claimDraw` implement the `ClaimDraw` action in `game-core`: a valid claim
finalises the game as `THREEFOLD_REPETITION_CLAIM` or `FIFTY_MOVE_RULE_CLAIM`,
an invalid claim (too early, wrong claim for the situation, or a finished game)
is rejected, and the automatic draws — fivefold repetition, the
seventy-five-move rule, stalemate, insufficient material — still end the game
with no claim at all. Verified locally with `.\gradlew.bat :game-core:test`
(13 new `ClaimDrawTest` cases, 279 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

- valid claim finalizes draw,
- invalid claim rejected,
- automatic draw conditions need no claim.

### Verification

Game-core tests pass.

---

# M4 — Undo Semantics

## M4.1 — Active move history

**Status:** DONE

**Depends on:** M3

**Completed:** 2026-08-26 — `ChessGame` holds the current `GameState` plus a
`MoveRecord` history, each record pairing a played move with the complete
position it was played from, so an undo restores the board, side to move,
castling rights, en passant target, both counters, the repetition history, and
the result exactly (`D029`). `ChessRules` gained `ChessGame` overloads of
`legalMoves`, `isLegal`, `availableDrawClaims`, `applyMove`, and `claimDraw`,
plus `undoLastMove` for the mechanical restoration; undo *eligibility* is
`M4.2`. Verified locally with `.\gradlew.bat :game-core:test` (15 new
`MoveHistoryTest` cases round-tripping quiet moves, captures, double pawn
advances, en passant, castling, promotion, an irreversible move that clears the
repetition history, and a game-ending move, 294 game-core tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

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

**Status:** DONE

**Depends on:** M4.1

**Completed:** 2026-08-26 — `ChessRules.undoableSide`, `canUndo`, and `undo`
implement `D016`: only the latest move may be taken back, and only by the player
who made it. That move is unanswered by definition, so the opponent's reply
locks it; when the opponent takes their own reply back, the previous move
becomes undoable again. Undo hands the turn back to the player who took the
move back, who may then play something else. The terminal-result lock is
`M4.3`. Verified locally with `.\gradlew.bat :game-core:test` (11 new
`UndoEligibilityTest` cases covering all four acceptance criteria, 305
game-core tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

- player can undo own latest unanswered move,
- cannot undo after opponent responds,
- opponent can undo their response,
- prior player's move becomes undoable again.

### Verification

Sequence tests pass.

---

## M4.3 — Final move cannot be undone

**Status:** DONE

**Depends on:** M4.2, M3.10, M3.11, M3.12, M3.13

**Completed:** 2026-08-26 — `ChessRules.undoableSide` returns `null` for a
finished game, so `canUndo` is false for both sides and `undo` is rejected once
any terminal result is recorded: checkmate, stalemate, insufficient material,
fivefold repetition, the seventy-five-move rule, a claimed draw, or a
resignation. This is `D017` (a game-ending move is final immediately, with no
grace period) and `D018`. The history of a finished game stays readable.
Verified locally with `.\gradlew.bat :game-core:test` (10 new
`TerminalUndoLockTest` cases, one per terminal reason plus the still-undoable
cases, 315 game-core tests total) and `.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Any terminal game result locks the final action.

### Verification

Terminal/undo tests pass.

---

# M5 — Local Android Chess

## M5.1 — Render board

**Status:** DONE

**Depends on:** M3

**Completed:** 2026-08-26 — `BoardRendering` turns a `game-core` `Board` into
the eight rows the board is drawn from (rank 8 first, file `a` on the left),
each `BoardSquare` carrying its square, occupant, and shade, plus the piece
glyphs and file/rank labels. The `ChessBoard` composable renders those rows as
a square grid and holds no chess rules — both sides use the solid glyphs and
colour tells them apart. `MainActivity` now shows the starting position and
whose turn it is. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (12 new `BoardRenderingTest`
cases, including the familiar starting grid) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, includes the Android debug/release APKs and Android lint).

### Acceptance Criteria

Correct board/pieces render from `game-core` state.

### Verification

Android debug build + relevant UI/unit tests.

---

## M5.2 — Piece selection

**Status:** DONE

**Depends on:** M5.1

**Completed:** 2026-08-26 — `BoardUiState` carries the game plus the player's
local selection, and `BoardInteraction.onSquareTapped` decides what a tap means:
tapping one of the moving side's pieces selects it, tapping it again clears the
selection, and tapping an empty square, an opponent piece, or anywhere in a
finished game selects nothing. Selection never reaches `game-core`. `ChessBoard`
takes `selectedSquare` and `onSquareClick` and tints the selected square;
`GameScreen` holds the state. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (10 new `BoardInteractionTest`
cases, 23 Android unit tests total) and `.\gradlew.bat build`
(BUILD SUCCESSFUL).

### Acceptance Criteria

Tap own piece and highlight selected square.

---

## M5.3 — Legal move highlights

**Status:** DONE

**Depends on:** M5.2

**Completed:** 2026-08-26 — `BoardInteraction.legalDestinations` /
`isLegalDestination` filter `ChessRules.legalMoves` for the selected square, so
every highlight comes from `game-core` and the UI derives no chess rules of its
own — pins, castling, and captures are all whatever the engine says. A
promotion square is highlighted once rather than once per choice. `ChessBoard`
draws a dot on an empty destination and a ring around a capture. Verified
locally with `.\gradlew.bat :android-app:testDebugUnitTest` (11 new
`LegalMoveHighlightTest` cases, 34 Android unit tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

Highlights come from `game-core`; UI does not duplicate rules.

---

## M5.4 — Apply local move

**Status:** DONE

**Depends on:** M5.3

**Completed:** 2026-08-26 — Tapping a highlighted destination plays the move
through `ChessRules.applyMove`, so the local game state, history, and any
terminal result all come from `game-core`; the selection clears and the turn
passes. A pawn reaching the last rank raises a `PendingPromotion` prompt
instead, because the player must choose the piece (no automatic queen);
choosing plays the move, and cancelling or tapping the board backs out.
`GameScreen` renders the prompt and the game status. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (13 new `ApplyLocalMoveTest`
cases including castling, capture, every promotion choice, and a mate that
stops further play, 47 Android unit tests total) and `.\gradlew.bat build`
(BUILD SUCCESSFUL).

### Acceptance Criteria

Tap legal destination and update local game state.

---

## M5.5 — Board orientation

**Status:** DONE

**Depends on:** M5.1

**Completed:** 2026-08-26 — `BoardRendering.rows` / `squares` / `fileLabels` /
`rankLabels` take an orientation and draw the requested side's own pieces at the
bottom, reversing both ranks and files; square shades and identities are
unaffected. `BoardUiState.orientation` carries it, and because pass-and-play on
one device changes who is at the board every move, the board turns to the side
to move after each move; `BoardInteraction.flipBoard` also turns it by hand.
When a player has a fixed colour in a multiplayer game the same field will hold
that colour. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (11 new `BoardOrientationTest`
cases, 58 Android unit tests total) and `.\gradlew.bat build`
(BUILD SUCCESSFUL).

### Acceptance Criteria

Own side appears at bottom.

---

## M5.6 — Move history, Undo, and Claim Draw UI

**Status:** DONE

**Depends on:** M4, M3.14

**Completed:** 2026-08-26 — `GameControls` supplies the move list (numbered
rows pairing each side's move, promotion choice included), `canUndo` /
`undoableSide` / `undo`, and `availableDrawClaims` / `canClaimDraw` /
`claimDraw`. Every availability question is answered by `game-core`, so Undo is
offered exactly while `ChessRules.undoableSide` names a side — never before the
first move and never after the game ends — and a Claim Draw button appears only
for a claim `ChessRules.availableDrawClaims` currently allows. `GameScreen`
renders the move list, the Undo button, and one button per valid claim.
Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest` (13 new
`GameControlsTest` cases, 71 Android unit tests total) and
`.\gradlew.bat build` (BUILD SUCCESSFUL).

### Acceptance Criteria

- move history visible,
- Undo visible only when eligible,
- Claim Draw visible only when valid.

---

## M5.7 — Local game completion

**Status:** BLOCKED

**Depends on:** M5.4, M5.6, M14.15

**Blocked on:** the one remaining verification step — a representative *manual*
local game on an emulator or device. This machine has the Android SDK but no
AVD configured (`emulator -list-avds` is empty) and no device attached
(`adb devices` lists none), so the on-device play-through could not be
performed here. Everything else this task asks for is implemented and verified;
a human with an emulator or phone can clear this by playing one game and
switching the status to `DONE`.

**Verified 2026-08-26 (automated):** pass-and-play is complete end to end
through the same interaction layer taps go through — `LocalGameTest` plays
whole games: Scholar's mate to checkmate, Fool's mate followed by taps that a
finished game must ignore, a take-back replaced by a different move, a
threefold-repetition draw claimed, a pawn promoted after running the board, and
kingside castling in a real opening. `.\gradlew.bat :game-core:test` passes
(315 tests), `.\gradlew.bat :android-app:testDebugUnitTest` passes (79 tests),
and `.\gradlew.bat build` succeeds (BUILD SUCCESSFUL, including the Android
APKs and Android lint).

**Scope correction (2026-08-28):** this task covers the existing local
pass-and-play path; it is not evidence that Android multiplayer is integrated.
The MVP's missing Android resignation control is now `M14.15`. Clear this
blocker during `M14.18`, after that control exists, by verifying a representative
local game and persisted session on an emulator or device.

### Acceptance Criteria

Complete standard chess game can be played pass-and-play on one device.

### Verification

All game-core tests pass, Android builds, and representative manual local game works.

---

# M6 — Server + PostgreSQL Foundation

## M6.1 — Development PostgreSQL

**Status:** DONE

**Depends on:** M1

**Completed:** 2026-08-26 — `compose.yaml` at the repository root defines a
disposable `postgres:18-alpine` container (`chessgame-postgres`) on host port
`55432`, so an installed local PostgreSQL on `5432` is untouched. It creates
`chessgame_dev`, and `database/init/01-create-test-database.sql` creates
`chessgame_test` beside it. The credentials are deliberately non-secret local
throwaway values; `.env.example` holds `DATABASE_URL` and `TEST_DATABASE_URL`
and `.env` stays git-ignored. This is a development/test environment only,
separate from any production or beta data. Verified locally on 2026-08-26:
`docker compose up -d` reaches a healthy container (PostgreSQL 18.6), both
databases are listed by `\l`, a create/insert/select/drop round trip succeeds in
`chessgame_test`, host port `55432` accepts connections, and
`docker compose down -v` followed by `up -d` rebuilds both databases from
scratch — which is also how the test database is reset. `.\gradlew.bat build`
still succeeds. Commands are recorded in `docs/DEVELOPMENT.md`.

### Objective

Create local/test PostgreSQL separate from production.

### Acceptance Criteria

Disposable development/test database is available.

---

## M6.2 — Select PostgreSQL access library

**Status:** DONE

**Depends on:** M6.1

**Completed:** 2026-08-26 — JetBrains Exposed `1.5.0` (typed SQL DSL, not the
DAO) over HikariCP `7.1.0` with the PostgreSQL JDBC driver `42.7.13`, recorded
as `D030` in `docs/DECISIONS.md` against every listed selection criterion.
Versions live in `gradle/libs.versions.toml` and the dependencies are declared
in `server/build.gradle.kts` (driver `runtimeOnly`). Verified locally:
`.\gradlew.bat :server:dependencies --configuration runtimeClasspath` resolves
`exposed-core`, `exposed-jdbc`, `exposed-java-time`, `HikariCP`, and
`postgresql` at those versions, and `.\gradlew.bat build` succeeds.

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

**Status:** DONE

**Depends on:** M6.2

**Completed:** 2026-08-26 — migrations are plain forward-only SQL files in
`database/migrations/` named `V<version>__<description>.sql`, applied by Flyway
`13.4.0` through `com.jmussel.chessgame.server.db.Migrations` (`migrate`,
`appliedVersions`, `reset`). The build copies those files onto the server's
classpath at `db/migration`, so the server and the tests migrate from exactly
the same files, and `DatabaseConfig` builds the pooled `DataSource` from
`DATABASE_URL` / `TEST_DATABASE_URL` without printing the password. Applying is
repeatable: Flyway records applied versions in `flyway_schema_history`, so the
same call is safe on a fresh, half-migrated, or current database. Verified
locally against the `M6.1` container: `MigrationsTest` migrates an empty
database, confirms the history table, runs `migrate` again for zero further
migrations, and resets and re-applies; `flyway_schema_history` was confirmed in
`chessgame_test` afterwards. The tests no-op when `TEST_DATABASE_URL` is unset —
confirmed by a run with it cleared — and CI now starts a `postgres:18-alpine`
service container and sets the variable, so they run for real there.
`.\gradlew.bat build` succeeds both with and without a database configured.
Process documented in `docs/DEVELOPMENT.md`; `database/migrations/.gitkeep`
stays until `M6.4` adds the first real migration.

### Acceptance Criteria

Repeatable migration process exists and is documented.

`database/migrations/.gitkeep` is only a placeholder to keep the empty directory
tracked. Delete it in the same change that adds the first real migration file
(`M6.4`).

---

## M6.4 — Initial schema

**Status:** DONE

**Depends on:** M6.3

**Completed:** 2026-08-26 — `database/migrations/V1__initial_schema.sql`
creates `users`, `friendships`, `game_series`, `games`, `moves`, and
`game_events`, and `database/migrations/.gitkeep` was deleted in the same
change. Constraints encode the product rules rather than leaving them to
application code: a unique `username_normalized` and a unique `auth_subject`
(`D007`), username length and character checks, friendships and series stored
as an ordered pair with a `user_a_id < user_b_id` check so a self-friendship or
a reversed duplicate cannot exist (`D009`), a partial unique index giving at
most one `ACTIVE` series per pair (`D011`), a `game_series` status check with
`closed_at` agreeing with it (`D012`) plus `close_after_current_game`
(`D013`), games unique per `(series_id, sequence_number)` with distinct
players, `version` defaulting to `0` (`D021`), a result that must agree with
`COMPLETE` status, moves unique per `(game_id, ply)` with square and promotion
checks and `position_before` for exact undo (`D029`), and append-only
`game_events`. Verified locally against the `M6.1` container:
`InitialSchemaTest` (19 cases) applies the migration and confirms each
constraint rejects what it should; `.\gradlew.bat build` succeeds.

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

**Status:** DONE

**Depends on:** M6.4

**Completed:** 2026-08-26 — the server can persist and load canonical game
state transactionally. Exposed `Table` objects in `Tables.kt` map the migrated
schema (the SQL stays the source of truth — nothing generates or alters a
schema from Kotlin), `GameStateDocument` is the `jsonb` persistence DTO living
in the server so `game-core` stays free of serialization, and `GameRepository`
provides `create`, `load`, `save`, and `auditTrail`. Each call is one Exposed
transaction, so a game and its move history commit or roll back together, and
`save` enforces `D021` by rejecting a write whose `expectedVersion` no longer
matches (`StaleGameVersionException`) and otherwise incrementing the version.
`kotlinx.serialization` and `exposed-json` were added for the `jsonb` columns.
Verified locally against the `M6.1` container: `GameRepositoryTest` (16 cases)
round-trips an empty game, a game with history, castling rights and counters,
an en passant target, the repetition history, a promotion, a checkmated game,
and a claimed draw; it also proves the version guard leaves a losing write with
no trace, that saving replaces rather than appends history, that a failed
transaction leaves no game or move rows behind, and that an audit event is
written with the change. `.\gradlew.bat build` succeeds (47 server tests).

### Acceptance Criteria

Server can persist/load representative state transactionally.

### Verification

Integration tests pass.

---

# M7 — Identity and Username

## M7.1 — Supabase project

**Status:** DONE

**Depends on:** M6

**Completed:** 2026-08-26 — the Supabase development project `ChessGame Dev`
(ref `rkwymrtqayyyfahfgmbm`, region `us-east-2`) provides both halves of this
task: PostgreSQL 17.6 reporting `ACTIVE_HEALTHY`, and anonymous authentication
(`D006`) already enabled. Verified on 2026-08-26 by posting to
`/auth/v1/signup` with the publishable key and receiving HTTP 200 with a session
whose token carries `"is_anonymous": true` and `amr` method `anonymous`; the
JWKS endpoint returns an `ES256` EC key, which is what `M7.3` will verify tokens
against. That check left one throwaway anonymous user in the development
project. Project ref, API URL, issuer, JWKS URL, and the key-retrieval command
are recorded in `docs/DEVELOPMENT.md`, and `.env.example` now carries
`SUPABASE_URL` and `SUPABASE_JWKS_URL` with `SUPABASE_ANON_KEY` left blank — no
key is committed. The local disposable database from `M6.1` remains the target
for tests; the `database/migrations/` schema has deliberately **not** been
applied to the Supabase database yet.

### Acceptance Criteria

PostgreSQL and anonymous Auth configured for development environment.

---

## M7.2 — Android anonymous auth primitives

**Status:** DONE

**Depends on:** M7.1

**Completed:** 2026-08-26 — the Android auth layer can create an anonymous
Supabase session and restore it afterwards, without requiring a sign-in UI
(`D006`). This task completed the client, persistence, and orchestration
primitives; invoking them from the real application startup path is tracked
separately by `M14.6`.
`SupabaseAuthClient` makes the two calls the app needs directly rather than
adding the Supabase SDK (`D031`), `SessionStore` keeps the session in
app-private DataStore across launches, and `AnonymousAuthenticator` restores it,
refreshes a token within 60 seconds of expiry, and starts a new anonymous
account if the refresh token is dead — all under a mutex so two screens cannot
create two accounts. The publishable key is supplied at build time through
`-PsupabaseAnonKey`, `gradle.properties`, or `SUPABASE_ANON_KEY` and is never
committed. Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest`
(10 `AnonymousAuthTest` cases over Ktor's `MockEngine` covering create, store,
restore-without-network, refresh, dead-refresh recovery, the `apikey` header,
and sign-out) and 3 `SupabaseLiveAuthTest` cases run against the real
`ChessGame Dev` project with the key exported, which created, refreshed, and
restored a real anonymous session; those live tests no-op without the key, which
was confirmed by a run with it cleared. `.\gradlew.bat build` succeeds
(92 Android unit tests). The DataStore-backed store and real startup invocation
still need device coverage; `M14.6` wires the invocation and `M14.18` performs
the end-to-end device verification.

### Acceptance Criteria

The Android auth layer can create and restore an anonymous session.

---

## M7.3 — Ktor token verification

**Status:** DONE

**Depends on:** M7.1

**Completed:** 2026-08-26 — `SupabaseTokenVerifier` checks a bearer token's
`ES256` signature against the project's published JWKS, along with its issuer,
audience (`authenticated`), and expiry, so the server holds no signing secret
and trusts nothing in the token body until the signature allows it (`D004`).
`SupabaseAuthenticationProvider` turns a verified token into an
`AuthenticatedUser`, and `UserRepository.resolveBySubject` maps the Supabase
subject to the internal `userId`, creating the row the first time an anonymous
account is seen and falling back to the winner's row if two first requests race
on the unique `auth_subject` constraint. `/me` sits behind the provider and
returns that id; `/health` stays open, and `.\gradlew.bat :server:run` still
serves health alone when `DATABASE_URL`/`SUPABASE_URL` are unset. Verified
locally with `.\gradlew.bat :server:test` (10 `SupabaseTokenVerifierTest` cases
rejecting a forged signature, another project's issuer, an expired token, the
wrong audience, an unknown key id, an `alg: none` token, and rubbish; 9
`AuthenticatedRouteTest` cases over the real database confirming 401 without a
usable token and a stable internal id per account) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, 66 server tests).

### Acceptance Criteria

Ktor verifies Supabase-issued token and resolves internal user ID.

---

## M7.4 — Username claim

**Status:** DONE

**Depends on:** M7.3, M6.4

**Completed:** 2026-08-26 — `Username` validates 3–24 characters of letters,
numbers, underscore, or hyphen and exposes the lowercase `normalized` form;
`UserRepository.findByUsername` looks up by that normalized form, so `Jordan`
and `jordan` are one identity (`D007`). `claimUsername` writes both columns in
its own transaction and treats a unique-constraint violation (SQLSTATE `23505`)
as `Taken`, leaving the database as the final race-safe authority; a name is
never released, so a lost anonymous account keeps it reserved (`D008`), and a
rename is refused because username changes are outside the MVP
(`docs/PRODUCT.md`). `POST /username` sits behind Supabase authentication and
always claims for the calling user — 200, 400 for an invalid name, 409 for a
taken name or an attempted rename, 401 without a token. Verified locally with
`.\gradlew.bat :server:test`: 9 `UsernameTest` cases on validation and
normalization, and 11 `UsernameClaimTest` cases against the real database
including the required concurrency test — eight users claiming the same name
through a `CyclicBarrier`, exactly one `Claimed` and seven `Taken`, with exactly
one user row named afterwards. `.\gradlew.bat build` succeeds (87 server tests).

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

**Status:** DONE

**Depends on:** M7.4

**Completed:** 2026-08-26 — `LastSeenTracker` records activity through
`UserRepository.touchLastSeen`, and the Supabase authentication provider calls
it after every successfully authenticated request, so a move, an undo, or simply
opening the app all count as the meaningful activity `docs/PRODUCT.md` lists.
Writes are throttled to at most one per user per five minutes, which keeps
`D010`'s "no continuous heartbeat" — the stored value is accurate to within the
throttle, which is all the MVP needs. An unauthenticated or rejected request
records nothing. Verified locally with `.\gradlew.bat :server:test` (8 new
`LastSeenTest` cases against the real database: a new user has never been seen,
activity is recorded, a burst inside the throttle writes once, activity after
the throttle writes again, users are throttled separately, an authenticated
`/me` counts, and `/health` or a forged token does not) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 95 server tests).

### Acceptance Criteria

Meaningful activity updates `lastSeenAt` without continuous heartbeat.

---

# M8 — Friends

## M8.1 — Username lookup

**Status:** DONE

**Depends on:** M7.4

**Completed:** 2026-08-26 — `GET /users/{username}` behind Supabase
authentication matches exactly on the normalized name and returns one
`UserSummary` (internal id and the spelling its owner chose) or 404. There is no
search and no partial matching, which is all `D009`'s "add a friend by exact
unique username" needs, and the response deliberately carries neither the auth
subject nor `lastSeenAt`. A user who has not claimed a username cannot be found,
and a syntactically invalid name is a 400 rather than a lookup. JSON responses
are served through Ktor `ContentNegotiation` with `kotlinx.serialization`.
Verified locally with `.\gradlew.bat :server:test` (9 new `UserLookupTest`
cases, including case-insensitive hits, three partial-match misses, and a check
that nothing private leaks) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 104
server tests).

### Acceptance Criteria

Exact normalized lookup returns one user or not found.

---

## M8.2 — Add friend

**Status:** DONE

**Depends on:** M8.1

**Completed:** 2026-08-26 — `FriendshipRepository.add` stores a friendship as
one row with the lower user id first, so it is mutual the moment it is made
(`D009`) and the three forbidden cases are impossible rather than merely
checked for: a self-friendship is refused (and the schema's ordering check would
refuse it anyway), a duplicate returns `AlreadyFriends`, and the reversed pair
is the same row. Re-adding a removed friend revives that row rather than
creating a second, keeping the history `D013` calls for. `POST /friends` behind
Supabase authentication adds by username for the calling user — 200, 400 for
yourself or a malformed name, 404 for an unknown user, 409 for a duplicate in
either direction. Verified locally with `.\gradlew.bat :server:test` (13 new
`AddFriendTest` cases over the real database, covering mutuality from both
sides, the ordered pair, re-adding after removal, and every endpoint response)
and `.\gradlew.bat build` (BUILD SUCCESSFUL, 117 server tests).

### Acceptance Criteria

Prevent:

- self-friendship,
- duplicate friendship,
- reversed duplicate.

Friendship is mutual immediately.

---

## M8.3 — Friends list

**Status:** DONE

**Depends on:** M8.2

**Completed:** 2026-08-26 — `GET /friends` behind Supabase authentication
returns the calling user's current friends as `UserSummary` values, oldest
friendship first, using `FriendshipRepository.friendsOf`. It lists only the
caller's own friends, both sides of a friendship see each other, a removed
friend disappears from both lists, and the response carries no auth subject or
activity. Verified locally with `.\gradlew.bat :server:test` (7 new
`FriendsListTest` cases) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 124 server
tests).

---

## M8.4 — Remove friend

**Status:** DONE

**Depends on:** M8.2

**Completed:** 2026-08-26 — `FriendshipRepository.remove` deactivates the
friendship by setting `removed_at` and, in the same transaction, sets
`close_after_current_game` on the pair's `ACTIVE` series, so the two can never
disagree. Nothing is deleted: the friendship row stays for history, the series
stays `ACTIVE`, and the current game is untouched and still playable — only the
next automatic rematch is disabled, exactly as `D013` describes. The series'
own transition to `CLOSED` when that game ends is `M9.3`/`M13.4`.
`DELETE /friends/{username}` behind Supabase authentication removes for the
calling user from either side — 200 (saying the current game finishes first when
a series was marked), 404 for an unknown user or someone who is not a friend,
400 for a malformed name. Verified locally with `.\gradlew.bat :server:test`
(12 new `RemoveFriendTest` cases, including a real in-progress game left intact
and another pair's series left alone) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, 136 server tests).

### Acceptance Criteria

- friendship removed/deactivated,
- history preserved,
- current game preserved,
- active series marked to close after current game.

---

# M9 — Game Series

## M9.1 — Start/open active series

**Status:** DONE

**Depends on:** M8.2

**Completed:** 2026-08-26 — `GameSeriesRepository.openOrCreate` returns the
pair's `ACTIVE` series if there is one and creates it otherwise, always storing
the pair with the lower user id first, so either player's "Play" opens the same
series (`D011`). The partial unique index is what enforces it: when an insert
loses a race the repository re-reads and returns the winner's series rather than
creating a parallel one. A closed series stays for history and does not block a
new one (`D012`). `POST /series` behind Supabase authentication opens the series
with a named friend — 201 when created, 200 when reopened, 403 for someone who
is not a friend, 400 for yourself or a malformed name, 404 for an unknown user.
Verified locally with `.\gradlew.bat :server:test` (13 new `OpenSeriesTest`
cases) including the required concurrency test: eight simultaneous opens from
both sides through a `CyclicBarrier` produce exactly one creation, one series id
for everyone, and one row. `.\gradlew.bat build` succeeds (149 server tests).

### Acceptance Criteria

- one `ACTIVE` series per friend pair,
- existing active series opens instead of duplicate creation.

### Verification

Concurrent create test prevents duplicate active series.

---

## M9.2 — Initial game

**Status:** DONE

**Depends on:** M9.1

**Completed:** 2026-08-26 — `SeriesService.openWithGame` opens the pair's active
series and, when it has no current game, starts one: game 1 from the standard
position with the colours decided by a coin toss (`D014`), then points the
series at it. Creating the game and attaching it happen in one transaction, so a
series never claims a game that was not written, and opening the series again
from either side returns the same game rather than starting a second. The
`POST /series` route now hands the caller a series that already has a game to
play, which is the one-tap "play with this friend" `docs/PRODUCT.md` asks for.
The random source is injectable, so the colour rule is tested rather than
assumed. Verified locally with `.\gradlew.bat :server:test` (8 new
`InitialGameTest` cases: the game is created and attached, both players are on
opposite sides, each coin toss produces the matching assignment, twenty series
produce both assignments, reopening starts nothing new, and the game begins with
White to move at version 0) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 157
server tests).

### Acceptance Criteria

Initial colors randomized and current game attached to series.

---

## M9.3 — Series close-after-current lifecycle

**Status:** DONE

**Depends on:** M9.1, M8.4

**Completed:** 2026-08-26 — `GameSeriesRepository` gained
`markCloseAfterCurrentGame`, `close`, and `closeIfMarked`, giving the series the
`ACTIVE` → marked → `CLOSED` lifecycle `D012` and `D013` describe. Every step is
idempotent: marking an already-marked or already-closed series changes nothing,
closing twice keeps the first `closedAt` rather than moving it, and
`closeIfMarked` leaves an unmarked series active so its automatic rematch still
happens (`D015`). A closed series stops being the pair's active one but stays
readable for history, which also frees the pair to start a new series. The
actual call when a game ends is `M13.4`. Verified locally with
`.\gradlew.bat :server:test` (12 new `SeriesLifecycleTest` cases, including
removing a friend feeding into the same transition and repeated closes being
harmless) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 169 server tests).

### Acceptance Criteria

Series can be marked to close after its current game and transitions idempotently.

---

## M9.4 — Dashboard discovery

**Status:** DONE

**Depends on:** M9.1, M9.2

**Completed:** 2026-08-26 — `DashboardQueries.activeSeriesFor` loads every
active series a player is in together with the game each is at, in two queries
regardless of how many series there are: one left join of `game_series` onto its
current game, and one lookup of the opponents those rows name. There is no
query per series. Each `ActiveSeriesView` carries the opponent, game id,
version, the side the viewer is playing, whose move it is, the move number, and
whether the series closes after this game — everything the `docs/PRODUCT.md`
dashboard hierarchy needs. `GET /dashboard` behind Supabase authentication
returns them as `DashboardEntry` values with a `yourTurn` flag, which `M14.1`
and `M14.2` will group. Verified locally with `.\gradlew.bat :server:test` (11
new `DashboardTest` cases: turn flipping after a real move, several series at
once, a closed series dropping off, a series marked to close saying so, a
series with no game yet, and nothing private in the response) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 180 server tests).

### Acceptance Criteria

Current active series/game can be loaded efficiently.

---

# M10 — Authoritative Game Command API

## M10.1 — `MakeMove`

**Status:** DONE

**Depends on:** M4, M6.5, M9

**Completed:** 2026-08-26 — `GameCommandService.makeMove` runs the exact
sequence this task specifies, all inside one transaction: load the game,
validate the caller is a participant, validate the game is still running,
validate it is their turn, validate `expectedVersion`, run `game-core`, then
persist and increment the version. The client sends intent only — `from`, `to`,
optional promotion, and the version it read — and never a board state
(`ARCHITECTURE.md` §7, `D004`); the stored position is whatever
`ChessRules.applyMove` produced, and an accepted move records a `MoveMade` audit
event (`D020`). The version is checked twice on purpose: once for a clear answer
and once as the guarded write that actually settles a race (`D021`).
`POST /games/{gameId}/moves` returns the canonical `GameView` on success and
carries that same state on every refusal a caller could correct from — 409 for
game over, wrong turn, or a stale version; 422 for an illegal move; 403 for a
stranger; 404 for an unknown game. `GET /games/{gameId}` reads the canonical
game for either player. Verified locally with `.\gradlew.bat :server:test` (21
new `MakeMoveTest` cases, including Fool's mate played move by move through the
service, a promotion with its choice, stale and future versions writing nothing,
and only accepted commands appearing in the audit trail) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 201 server tests).

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

**Status:** DONE

**Depends on:** M10.1

**Completed:** 2026-08-26 — a command whose `expectedVersion` no longer matches
is refused cleanly and the caller is given what it needs to carry on. Refusals
now carry a `CommandRejection` with a machine-readable `reason`
(`STALE_VERSION`, `NOT_YOUR_TURN`, `GAME_OVER`, `ILLEGAL_MOVE`) plus the
canonical `GameView`, so a client can tell a stale command — worth retrying from
the attached state — apart from a premature one, which only waiting fixes; both
are `409`s and were previously indistinguishable. Validation now checks the
version first, because a wrong version makes everything else the client believes
suspect. `GET /games/{gameId}` returns the same state for a client that prefers
to refresh explicitly.

Writing this task's concurrency test found a real defect in `M6.5`'s
`GameRepository.save`: it ignored the guarded update's row count, so a command
that lost a race still reported success and went on to rewrite the move history.
The row count is now checked and a lost race raises `StaleGameVersionException`,
which makes the guarded write — not the read before it — the thing that settles
a race (`D021`).

Verified locally with `.\gradlew.bat :server:test` (10 new `StaleVersionTest`
cases, including six commands racing on one version where exactly one is applied
and the other five are stale, run three times without flaking, and a client
retrying straight from the rejection body) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, 211 server tests).

### Acceptance Criteria

Stale command rejected cleanly and client can refresh.

---

## M10.3 — `ClaimDraw`

**Status:** DONE

**Depends on:** M3.14, M10.1

**Completed:** 2026-08-26 — `GameCommandService.claimDraw` runs the same
authoritative chain as a move — participant, version, game still running, turn —
and then asks `game-core` whether the claim is real: `ChessRules.canClaimDraw`
answers from the position's own repetition history and halfmove clock, never
from anything the client asserts (`D019`). A valid claim finalises the game as
`THREEFOLD_REPETITION_CLAIM` or `FIFTY_MOVE_RULE_CLAIM` and records a
`DrawClaimed` audit event; an invalid one — no repetition yet, the wrong claim
for the position, or a game that has already ended — writes nothing and comes
back as `NO_SUCH_CLAIM`. Only the player to move may claim, as in standard
chess, since the claim is about the position they are being asked to play from.
`POST /games/{gameId}/draw-claims` carries it, and `GameView` now lists
`availableDrawClaims` for the player to move so a client can offer the button
only when the claim is genuinely available. Verified locally with
`.\gradlew.bat :server:test` (15 new `ClaimDrawCommandTest` cases, including a
threefold repetition reached by playing real moves through the server, the
fifty-move clock, the wrong claim for the position, an opponent trying to claim,
a stale claim, and a second claim on a finished game) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, 226 server tests).

### Acceptance Criteria

Server authoritatively accepts valid claim and rejects invalid claim.

---

## M10.4 — Two-client turn-taking

**Status:** DONE

**Depends on:** M10.1

**Completed:** 2026-08-26 — `TwoClientGameTest` plays the whole loop over HTTP
with two different tokens and nothing shared between the players but the server:
each signs in, claims a username, they become friends, one opens the series, and
they alternate moves. Each client reads only its own view — opposite colours,
exactly one of them to move — and sees the other's move on its next read. A
player who tries to move twice in a row is refused with `NOT_YOUR_TURN` and
writes nothing; both dashboards report the same canonical version from opposite
sides; a whole game is played to checkmate through the API, after which neither
player can move; and a third person can neither read the game nor play in it.
Verified locally with `.\gradlew.bat :server:test` (7 new `TwoClientGameTest`
cases) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 233 server tests). No
production code needed changing — `M10.1`–`M10.3` already made this work; this
task is the demonstration that it does.

### Acceptance Criteria

Two authenticated users can alternate server-authoritative moves.

---

# M11 — Authoritative Undo

## M11.1 — `UndoMove`

**Status:** DONE

**Depends on:** M4, M10.1

**Completed:** 2026-08-26 — `GameCommandService.undoMove` enforces `D016`
server-side, with `game-core` applying the rule: a player may take back their
own latest move only while the opponent has not answered. The exact sequence
from `docs/PRODUCT.md` is covered by tests — Jordan plays `Nf3` and may take it
back; once Alex answers `Nc6` Jordan may not, but Alex may take back `Nc6`; and
when Alex does, Jordan's `Nf3` becomes the latest unanswered move and is
takeable again. A game-ending move is never takeable (`D017`), the opponent can
never take back your move, and a stranger can do neither. An accepted undo is a
mutation like any other: it increments the version (`D021`) and records a
`MoveUndone` audit event, so a client holding the old version cannot play into a
position that no longer exists. `POST /games/{gameId}/undo` carries it, refusing
with `NOTHING_TO_UNDO`, and `GameView.canUndo` tells each player whether the
control belongs on their screen. Verified locally with
`.\gradlew.bat :server:test` (17 new `UndoMoveTest` cases) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 250 server tests).

### Acceptance Criteria

Exact product rule enforced server-side.

---

## M11.2 — Move-vs-undo concurrency

**Status:** DONE

**Depends on:** M11.1

**Completed:** 2026-08-26 — `MoveVersusUndoTest` sets up exactly the race
`ARCHITECTURE.md` §10 names: White has played, so White may take that move back
and Black may answer it, both against the same version and both individually
legal, leading to different positions. Fired simultaneously through a
`CyclicBarrier`, exactly one is applied and the other comes back
`STALE_VERSION`; the stored game is then whichever of the two states won, never
a mixture; its move history is self-consistent; and only the winning command
appears in the audit trail. The loser recovers with no special handling — the
version attached to its rejection is the one that won, and playing on from it is
accepted. Each assertion runs 15 rounds on fresh games, and the whole test was
run three times over. No production code needed changing: the guarded write
fixed in `M10.2` is what makes this hold. Verified locally with
`.\gradlew.bat :server:test` (5 new cases, 75 races) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 255 server tests).

### Acceptance Criteria

Near-simultaneous Move/Undo results in exactly one valid transition.

### Verification

Integration/concurrency test passes repeatedly.

---

# M12 — Realtime

## M12.1 — WebSocket connection

**Status:** DONE

**Depends on:** M10

**Completed:** 2026-08-26 — an authenticated client can hold a realtime
connection. Ktor's WebSockets plugin is installed and `/ws` sits behind the same
Supabase authentication as every other route: the handshake carries the bearer
token, an unauthenticated or forged one is refused before any session exists,
and connecting resolves the internal user like any other authenticated request.
One socket per client covers every game that client plays in, so the app does
not open a connection per game. `RealtimeHub` tracks who is connected — a user
may hold several sockets at once (two devices, or one that has not noticed it is
gone) — and drops a connection as soon as its read loop ends. Delivery is
best-effort by design: the socket only ever says *that* something changed, and
canonical state is reloaded over HTTPS (`D022`). Publishing updates is `M12.2`.
Verified locally with `.\gradlew.bat :server:test` (8 new
`RealtimeConnectionTest` cases: connect and receive the `connected` frame, the
connection is registered while open and forgotten when closed, two devices for
one person, two people tracked separately, and both refusal paths) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 263 server tests).

### Acceptance Criteria

Authenticated client can establish realtime connection.

---

## M12.2 — Publish game updates

**Status:** DONE

**Depends on:** M12.1

**Completed:** 2026-08-26 — a move by one player now reaches the other player's
open connection without them asking. Every accepted command — a move, an undo,
a claimed draw — announces the game to both sides through `RealtimeHub`, and a
refused one announces nothing, because nothing changed. Both players are told
rather than only the opponent: the player who acted may have a second device
open, and the message is a nudge to reload rather than state, so the extra one
costs nothing. The push still carries only the game id and the version it
reached; the client reloads canonical state over HTTPS (`D022`). Announcing is
deliberately outside the command itself: publishing is best-effort, so a move is
accepted whether or not anyone is listening, and a connection that fails to take
a message is dropped rather than retried. Verified locally with
`.\gradlew.bat :server:test` (8 new `GameUpdateBroadcastTest` cases: the
opponent hears about a move and at which version, the mover's other device hears
it too, every move of a rally is announced in order, an undo and a claimed draw
are announced like a move, a refused command is silent, an onlooker is told
nothing about someone else's game, and a move is still accepted with no socket
open) and `.\gradlew.bat build` against the local test database (BUILD
SUCCESSFUL, 271 server tests, 0 skipped).

### Acceptance Criteria

Move on client A updates client B automatically.

---

## M12.3 — Reconnect recovery

**Status:** DONE

**Depends on:** M12.2

**Completed:** 2026-08-26 — a player who was disconnected while the game moved
on loses nothing but a reload. Nothing is replayed to a returning client, and
nothing needs to be: whatever it missed is already in the canonical state it
reloads over HTTPS (`D022`), and one `/dashboard` request tells it every game it
plays in and which version to resume from. What makes that reload safe is the
ordering in `/ws`: the connection is registered *before* the `connected`
greeting goes out, so a client that reloads on the greeting cannot fall into a
gap — every change committed from then on is pushed to it, and every earlier one
is in the reload. Greeting first and subscribing after would open exactly that
gap, so the ordering is now stated in the route and in `ARCHITECTURE.md` §12
rather than left to be rediscovered. A command built on a version the server has
moved past is still refused with `STALE_VERSION` and the current state attached,
so a client that missed a message recovers from the refusal alone instead of
writing to the wrong position. The realtime tests now share one fixture
(`RealtimeFixture.kt`) instead of a third copy of the two-player setup. Verified
locally with `.\gradlew.bat :server:test` (8 new `ReconnectRecoveryTest` cases:
the missed moves are in the reload, the returning player sees exactly what the
player who never left sees, no backlog is replayed, the socket is live from the
greeting onwards, the dashboard names the version to resume from, a stale
command is refused with the canonical state and the retry succeeds from it, a
whole game played across repeated drops ends in the right position, and a dead
connection is dropped without disturbing the live one) and `.\gradlew.bat build`
against the local test database (BUILD SUCCESSFUL, 279 server tests, 0 skipped).

### Acceptance Criteria

Missed realtime messages do not corrupt state; canonical state reloads over HTTPS.

---

# M13 — Game End and Automatic Rematch

## M13.1 — Finalize game transactionally

**Status:** DONE

**Depends on:** M10, M11

**Completed:** 2026-08-26 — a terminal result is written exactly once, with the
move that caused it. Finalization is part of the same guarded write as the move:
the result, the termination reason, `ended_at`, and one `GameEnded` audit event
(`ARCHITECTURE.md` §9) commit together or not at all, so no request can ever see
a game that is over without knowing how, or a mating move without a result.
Exactly-once falls out of `D021` rather than from a second check: only one write
can move the row off the version it was read at, and only the write that finds a
running game and leaves a finished one finalizes it. A game that has already
ended keeps the moment it ended, and a finished game refuses every further
command (`D017`), so nothing can restate how it finished. `GameEnded` carries
the result and the reason in its payload, so the audit trail answers "how did
this game end" without reading the row it describes; `StoredGame` now carries
`endedAt`, and `auditEvents` exposes payloads as well as types. Verified locally
with `.\gradlew.bat :server:test` (9 new `FinalizeGameTest` cases: a mating move
finalizes the game, a running game does not, exactly one event is recorded and
it says how the game ended, the result and the move that caused it are stored
together, a finished game is not finalized again by a move, an undo or a claim,
two identical mating commands racing at the same version end the game once, and
a claimed draw and an automatic seventy-five-move draw finalize the same way)
and `.\gradlew.bat build` against the local test database (BUILD SUCCESSFUL, 288
server tests, 0 skipped). `ClaimDrawCommandTest.anAcceptedClaimIsAudited` now
asserts the trail ends `DrawClaimed, GameEnded` rather than just `DrawClaimed` —
the claim is still audited, and the game ending is audited after it.

### Acceptance Criteria

Terminal result persists exactly once.

---

## M13.2 — Create next game exactly once

**Status:** DONE

**Depends on:** M13.1, M9.2

**Completed:** 2026-08-26 — finishing a game in an active series starts the next
one without either player asking (`D015`), and starts exactly one. The rematch
happens inside the finishing command's own transaction, so the finished game,
its result, and the game that follows it are one commit: no client can ever see
a series whose current game is over and whose next game does not exist yet.
Exactly-once rests on the series row itself. `startNextGameAfter` locks it with
`SELECT … FOR UPDATE` and decides from what it says under that lock — a series
whose current game is no longer the finished one has already had its rematch and
is handed back untouched — which covers a retry, a duplicated command, and two
transactions arriving together. A series marked to close after its current game,
or already closed, gets no rematch; closing it is `M13.4`. Colours are carried
over as they were, because reversing them is `M13.3`. The new game records a
`RematchCreated` audit event (`ARCHITECTURE.md` §9) naming the game it followed.
`GameCommandService` now takes the `SeriesService` it needs to do this, so the
wiring passes one instance to both. Verified locally with
`.\gradlew.bat :server:test` (12 new `AutomaticRematchTest` cases: finishing a
game starts exactly one next game at the next sequence number, the series points
at it, the finished game is left as it ended, both players are in the new game,
asking again after the rematch changes nothing, two end-of-games arriving
together still create one game, a marked-to-close or closed series gets none, an
unfinished game owes nothing, the event names the game it followed, and a series
keeps going game after game) and `.\gradlew.bat build` against the local test
database (BUILD SUCCESSFUL, 300 server tests, 0 skipped).

### Acceptance Criteria

For active series:

- one next game created,
- series points to it,
- operation idempotent.

---

## M13.3 — Alternate colors

**Status:** DONE

**Depends on:** M13.2

**Completed:** 2026-08-26 — an automatic rematch reverses the colours of the
game it follows (`D014`): whoever had Black plays White in the next game. The
sides are taken from the game that just ended rather than counted from the
sequence number, so the series stays consistent even if a game is ever created
out of band, and the first game's random colours (`M9.2`) still decide where the
alternation starts. Verified locally with `.\gradlew.bat :server:test` (2 new
`AutomaticRematchTest` cases: the rematch reverses the colours, and the colours
keep alternating over four games with nobody ever playing themselves) and
`.\gradlew.bat build` against the local test database (BUILD SUCCESSFUL, 302
server tests, 0 skipped).

### Acceptance Criteria

Rematch colors reverse from prior game.

---

## M13.4 — Close series without rematch

**Status:** DONE

**Depends on:** M13.1, M9.3

**Completed:** 2026-08-26 — a series marked to close finishes its current game
and then ends, which is the second half of `D013`. The decision now lives in one
place: `SeriesService.settleAfter` asks what a finished game leaves its series
and either starts the rematch (`M13.2`) or closes it, under the same series-row
lock, so a marked series can never do both. The game itself is untouched — it is
played and finalized normally, and the closed series keeps pointing at it as the
last game played, because a closed series stays readable as history (`D012`).
Closing records a `SeriesClosed` audit event (`ARCHITECTURE.md` §9) naming the
last game and why it closed, and it happens once: the guarded update fires only
while the series is still `ACTIVE`, so a repeat leaves the first `closedAt`
where it was. Verified locally with `.\gradlew.bat :server:test` (9 new
`SeriesClosesAfterLastGameTest` cases: the last game still finishes normally,
the series closes and records when, no rematch follows, closing is audited, an
unmarked series is not closed, marking mid-game still lets that game finish,
closing happens once however often it is asked and under two simultaneous
end-of-games, and the pair can open a fresh series afterwards) and
`.\gradlew.bat build` against the local test database (BUILD SUCCESSFUL, 311
server tests, 0 skipped). The two-friends-and-a-series fixture the rematch tests
used moved to `SeriesEndFixture.kt` so both suites share it.

### Acceptance Criteria

If series is marked to close:

- game finalizes,
- no rematch created,
- series becomes `CLOSED`.

---

## M13.5 — Resignation path

**Status:** DONE

**Depends on:** M13.2, M13.4

**Completed:** 2026-08-26 — a player can give up, and the series treats that
exactly like any other finished game. `ChessRules.resign` is the rules half:
resignation depends on neither the position nor whose turn it is, so a player
may resign while waiting for their opponent, and it leaves the move history
alone because giving up is not a move. `POST /games/{gameId}/resignation` is the
command half, carrying the same `expectedVersion` guard as every other command
and recording a `PlayerResigned` audit event; because it is an ordinary accepted
mutation it finalizes the game once (`M13.1`), announces it to both players
(`M12.2`), and then hits the same fork as a checkmate — the automatic rematch
with reversed colours while the series is active (`D015`, `D014`), the end of
the series when it was marked to close (`D013`). It is final once accepted: a
resigned game refuses undo from either player (`D018`, `D017`), and a duplicate
resignation is refused as stale rather than producing a second rematch. The
confirmation stays where `D018` puts it, in the UI. Verified locally with
`.\gradlew.bat :game-core:test` and `.\gradlew.bat :server:test` (8 new
`ResignTest` cases in `game-core`, 11 new `ResignationTest` cases for the
command and the series lifecycle, and 5 new `ResignRouteTest` cases over HTTP)
and `.\gradlew.bat build` against the local test database (BUILD SUCCESSFUL, 327
server tests and 323 `game-core` tests, 0 skipped).

### Acceptance Criteria

Resignation follows the same active-vs-closing series lifecycle.

---

# M14 — Android Multiplayer Client, Dashboard, and History

**Milestone status:** INCOMPLETE. `M14.1`–`M14.4` completed server queries,
API-client reads, and isolated Compose presentation components; `M14.5`–`M14.12`
built the application shell, startup, onboarding, friends, the live dashboard,
the online game screen, moves, and realtime reloads. Undo, draw claims,
resignation, the rematch flow, reachable history, and the device play-through
remain: `M14.13`–`M14.18` are the client integration work required before beta
deployment.

## M14.1 — Your Turn data and presentation component

**Status:** DONE

**Depends on:** M9, M10

**Completed:** 2026-08-26 — the dashboard's first section, and the connection
that feeds it. `ChessApiClient` is the app's link to the Chess server, which is
authoritative for everything about a game (`D004`): it reads `GET /dashboard`
with the anonymous session's bearer token, asked for per request rather than
captured so a refresh between calls is picked up (`D006`), and it is lenient
about fields it does not know so a newer server does not break an older app.
`DashboardSections.yourTurn` decides what appears — the games waiting on the
player, in the order the server sent them, each reading "Alex / White • Move 18"
as `docs/PRODUCT.md` lays out — and it is pure, so the grouping and the wording
are tested without a screen. `DashboardScreen` renders that section and nothing
else; Their Turn is `M14.2` and Friends is `M14.3`. Wiring the screen into the
authenticated application and making it the landing destination is `M14.9`. A
series between games contributes no line, because there is nothing yet to open.
`ChessServerConfig` defaults to `http://10.0.2.2:8080`, the host machine as an
emulator sees it, which is what a developer running the server locally needs;
the beta endpoint is `M15.4`. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (9 new `ChessApiClientTest` cases
over Ktor's `MockEngine` — the dashboard is read, the token is sent and re-asked
for every call, an empty dashboard and a game-less series are read correctly,
unknown fields are ignored, a refusal carries its status, and the base URL joins
without doubled slashes — and 8 new `DashboardSectionsTest` cases) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 109 Android unit tests, 0 skipped).

---

## M14.2 — Their Turn presentation component

**Status:** DONE

**Depends on:** M14.1

**Completed:** 2026-08-26 — the dashboard's second section, below the games
waiting on the player as `docs/PRODUCT.md` orders them. A THEIR TURN line reads
exactly like a YOUR TURN one and is just as tappable: there is nothing to do in
those games, but a player still wants to look at them. Whose turn it is comes
from the server and is never worked out in the app, which would only be guessing
at state it does not own (`D004`), and the two sections partition the active
series between them, so nothing a player is in can go missing from the screen. A
series between games is in neither, because there is still nothing to open. An
empty THEIR TURN is left off the screen entirely rather than given a heading and
an apology; the empty YOUR TURN keeps its line, because that one is worth
saying. Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest`
(5 new `DashboardSectionsTest` cases: the opponent's games are Their Turn, the
row reads the same way, every active series lands in exactly one section, a
game-less series is in neither, and the server's order is kept) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 114 Android unit tests, 0 skipped).

---

## M14.3 — Friends dashboard component

**Status:** DONE

**Depends on:** M8

**Completed:** 2026-08-26 — the dashboard's last section, and the way a player
reaches a friend they have no game with. Every friend is listed, ordered by
name, because this is a list to find someone in rather than a feed, and friends
who already appear above under a turn heading are listed again — the section is
the way to reach a friend, not a leftovers pile. Each row carries the one thing
to do about that friend: `Open` when there is a game under way with them,
`Play` when there is not, which is `docs/PRODUCT.md`'s `[Play/Open]` exactly.
Which of the two an action turns out to be is still the server's business
(`D011`), so `Play` posts to `/series` and takes back whatever series that
opens. `ChessApiClient` gained `GET /friends` and `POST /series` for this. A
series between games leaves its friend as someone to `Play`, and a game against
someone who is no longer a friend — which `D013` allows to outlive the
friendship — is not a friend row. An account with no friends is told that adding
one by username is how anything starts, rather than shown an empty heading.
Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest` (8 new
`DashboardSectionsTest` cases and 2 new `ChessApiClientTest` cases) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 124 Android unit tests, 0 skipped).

---

## M14.4 — History API and presentation components

**Status:** DONE

**Depends on:** M13

**Completed:** 2026-08-26 — what has been played stays readable, and stays off
the home screen (`docs/PRODUCT.md`). `GET /history` returns the caller's series
with the games in them that are over: a closed series stays there forever
(`D012`), and a finished game inside a series that is still running is history
too, because what makes a game history is that it has finished, not what became
of the series around it. A series with nothing finished in it is not listed.
`HistoryQueries` costs three queries whatever the number of series — the
caller's series, the finished games in them, and the opponents those series name
— and never loads a move history, since a game is summarised here and read in
full through `GET /games/{gameId}` when someone opens it. Read-only needed no
new rule to enforce: a finished game refuses every command (`D017`) and a closed
series never gets another one (`D013`), so there is nothing to write and no
endpoint that would. On Android, `HistoryList` turns a stored result into a line
— "Game 2 • White • Won by checkmate • 31 moves", written from the side the
viewer played, and a closed series headed "Alex (closed)" because "no more games
with Alex" is what a player would otherwise wonder about. Verified locally with
`.\gradlew.bat :server:test` (12 new `HistoryTest` cases: a finished game is
listed and the game in progress is not, each player sees the side they played,
games keep their order, a closed series stays readable with its `closedAt`, a
stranger's series is not in your history, `/history` is served over HTTP and
needs a token, an empty history is an empty list, and a historical game can
still be read in full but not played), `.\gradlew.bat :android-app:testDebugUnitTest`
(14 new `HistoryListTest` cases and 2 new `ChessApiClientTest` cases), and
`.\gradlew.bat build` against the local test database (BUILD SUCCESSFUL, 339
server tests and 140 Android unit tests, 0 skipped).

### Acceptance Criteria

The authenticated server API keeps historical games/series read-only and the
Android presentation components can render their summaries. Reachable app
navigation and full-game review are `M14.17`.

---

## M14.5 — Android application shell and navigation

**Status:** DONE

**Depends on:** M5.6, M7.2, M14.1, M14.4

**Completed:** 2026-08-28 — the app is now an application rather than a board.
`MainActivity` holds no state: it renders `ChessApp` and hands back presses to
`ChessAppViewModel`, which outlives it, so a rotation redraws the same app
instead of restarting it. What is showing is `AppNavigation`, an immutable stack
of `Destination`s with `open`, `restartAt`, and `back` — free of Compose and
Android, so every transition the shell supports is tested on the JVM (`D033`).
`restartAt` is how startup and onboarding hand over: neither is somewhere to go
back to, so the dashboard becomes the screen the player leaves the app from, and
`back()` returning `null` there is the signal that the press belongs to the
system. `ChessAppDependencies` builds one Ktor `HttpClient` shared by Supabase
auth and the Chess server, with the `AnonymousAuthenticator` and `ChessApiClient`
on top of it; it is constructed from the application context by a factory that
runs only when there is no model yet, and closed in `onCleared`, so no client is
ever made during composition or left unclosed. The manifest asks for
`INTERNET` and points at a network security configuration that forbids cleartext;
the `debug` source set replaces that file with one allowing `10.0.2.2` and
`localhost` only, so a developer can reach a local server and a beta or release
build cannot talk in the clear at all (verified in the merged release manifest
and both packaged configurations). The local pass-and-play screen moved out of
`MainActivity` to `ui/board/LocalGameScreen.kt` unchanged. Screens whose contents
are later tasks say so rather than pretending: startup (`M14.6`), username
(`M14.7`), friends (`M14.8`), and an online game (`M14.10`); the dashboard and
history render their real components with no data until `M14.9` and `M14.17`
load it. Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest`
(20 new `AppNavigationTest` cases covering the startup destination and every
supported transition, and 8 new `ChessAppTest` cases covering the shared token,
the closed client, the lazily built factory, and the shell's own transitions) and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 168 Android unit tests, 0 skipped).

### Objective

Replace the direct `MainActivity` local-game entry point with a small
application shell that owns top-level state and navigation between startup,
username onboarding, dashboard, friends, online game, local game, and history.

### Acceptance Criteria

- `MainActivity` renders the application shell rather than unconditionally
  creating a local game.
- The Android manifest permits network access, and development builds can reach
  the emulator-loopback HTTP server without weakening beta/release transport
  security.
- The shell owns and closes its shared HTTP client at an appropriate lifecycle
  boundary, defines the owner for the later realtime client, and never
  constructs clients per recomposition.
- Destinations and back behavior are explicit and testable.
- Screen dependencies are constructed outside leaf composables.
- Process recreation can rebuild the shell from persisted/session state
  without retaining an `Activity` reference.
- The implementation remains proportionate; no speculative navigation or
  Clean Architecture framework is introduced.

### Verification

Navigation/state tests cover the startup destination and every supported
top-level transition; Android unit tests and aggregate build pass.

---

## M14.6 — Restore or create the anonymous session on startup

**Status:** DONE

**Depends on:** M14.5, M7.2

**Completed:** 2026-08-28 — the app now signs itself in. `MainActivity.onCreate`
calls `ChessAppViewModel.start()`, which runs `AppStartup` in the model's own
scope; startup asks `AnonymousAuthenticator` for a session and turns the outcome
into something a screen can show, so restoring, refreshing, and creating stay
where `D031` put them. A stored session that is still good is restored without
touching the network, one near expiry is refreshed, an absent or dead one
becomes a new anonymous account, and only then does the app leave the startup
screen for the dashboard — nothing authenticated is called before there is a
session. `start()` ignores a call while a run is in flight or once a session is
in hand, so a recreated activity, a recomposition, and a retry cannot end up
with two anonymous accounts. `ChessAppDependencies.accessToken` is now one named
provider asked per call: the HTTP client uses it today and the WebSocket client
(`M14.12`) can use the same one, so a token that nears expiry hours into a
session is refreshed underneath both without rebuilding the shell. A failure
says what happened and offers "Try again" when trying again could help; a build
with no `SUPABASE_ANON_KEY` explains itself and offers no button, because a
rebuild is the only fix — and it makes no request at all. No failure message
quotes a token, a refresh token, or the key, which is asserted rather than
assumed. Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest`
(9 new `AppStartupTest` cases over Ktor's `MockEngine` covering restore,
refresh, create, dead-refresh recovery, a refused sign-in, an unreachable
service, a keyless build, and the no-secrets rule, and 6 new `ChessAppTest`
cases covering loading, landing, first run, retry, the keyless stop, and the
single token provider — the model's coroutines run on a `StandardTestDispatcher`
so "loading" is a state a test can see) and `.\gradlew.bat build` (BUILD
SUCCESSFUL, 185 Android unit tests, 0 skipped). The configured-build half of the
verification was run against the real `ChessGame Dev` project with
`SUPABASE_URL`/`SUPABASE_ANON_KEY` exported: 2 new `AppStartupLiveTest` cases
(2.79s and 0.27s of real network) created an anonymous session through
`AppStartup` and restored the same account on the second run, alongside the 3
existing `SupabaseLiveAuthTest` cases. Those live tests no-op without the key,
so CI and the aggregate build above are unaffected. The DataStore-backed store
on a real device and the end-to-end play-through remain `M14.18`.

### Objective

Run `AnonymousAuthenticator` from the real application startup flow, expose
loading/recoverable-error state, and provide the current access token to the
Chess server client.

### Acceptance Criteria

- A valid stored session is restored before authenticated Chess API calls.
- An absent or irrecoverably expired session creates a new anonymous account.
- The shell exposes a current-session/token provider: HTTP calls use it now and
  the WebSocket client added in `M14.12` can use the same provider, so a token
  that nears expiry after startup is refreshed without rebuilding the shell.
- Startup does not create duplicate sessions when recomposed or retried.
- Missing build-time Supabase configuration produces an actionable UI error,
  not a crash or a permanently blank screen.
- No token, refresh token, or publishable key is logged.

### Verification

Application-shell tests cover restored, refreshed, newly created, loading, and
failed startup states; a configured development build authenticates against the
Supabase development project.

---

## M14.7 — Username onboarding

**Status:** DONE

**Depends on:** M14.6, M7.4

**Completed:** 2026-08-28 — the app can now tell who is playing and let a new
account say who they are. `GET /me` returns a typed `CurrentUser` — the
immutable user id and a username that is `null` until it is claimed — which is
the difference between a returning player and a new one and could not be read
from the plain user id it used to return. The route moved out of
`Application.kt` into `user/IdentityRoutes.kt`; its two existing consumers,
`AuthenticatedRouteTest` and `RealtimeFixture.userId()`, read the typed body
now. Startup asks the two questions in order — a Supabase session, then the
identity the server keeps for it — and lands on the dashboard or on username
onboarding accordingly, so a returning named user never sees onboarding.
`UsernameScreen` says the name cannot be changed later before the player commits
to one, and disables its button while a claim is in flight so a second tap
cannot claim twice. Whether a name is allowed and whether it is still free stay
the server's and the database's answers (`D007`): the app keeps no copy of the
rules, `ChessApiException` now carries the server's own explanation, and a
refusal is shown in those words — "That username is taken" — with the box left
ready for another try. An empty box is not sent at all. A claim that never
reaches the server is a message the player can act on rather than a silent
failure. Verified locally with `.\gradlew.bat :server:test --rerun-tasks`
against the local test database (6 new `IdentityRouteTest` cases: a new account
has an id and no username, a named user is reported with theirs, claiming a name
does not change who you are, the same account is always the same user, two
accounts are two users, and an unauthenticated or unverifiable request is
refused), `.\gradlew.bat :android-app:testDebugUnitTest` (5 new
`ChessApiClientTest` cases for `/me` and the claim, including a taken name and an
invalid one carrying the server's explanation, 2 new `AppStartupTest` cases for
a named and an unnamed player and one for a server that will not say who you
are, and 5 new `ChessAppTest` cases: a named player skips onboarding, a first run
is sent to choose a name, claiming goes on to the dashboard, a refused name is
explained and another can be tried, and an empty box is not sent), and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 372 server tests and 198 Android unit
tests, 0 skipped). The live Supabase check was re-run with the key exported and
still passes; `AppStartupLiveTest` now stubs the Chess server half, because a
unit test has no server to talk to, and says so.

### Objective

Complete the authenticated identity-read contract and add the Android API/UI
flow needed to discover whether the current identity already has a username and
to claim one when it does not. The current server `GET /me` returns only a plain
user id, so it cannot yet distinguish a returning named user from a new one.

### Acceptance Criteria

- Returning named users bypass onboarding.
- `GET /me` (or an equivalently narrow authenticated endpoint) returns a typed
  current-user response with the immutable user id and nullable username.
- A new anonymous user can claim a valid username.
- Validation, conflict, authentication, and retryable network errors are
  presented clearly.
- Successful onboarding transitions to the dashboard without restarting the
  app.

### Verification

Server identity-route tests cover named and unnamed users, stable user ids, and
authentication failures. If `GET /me` changes from plain text to a typed
response, update its existing response consumers, including
`AuthenticatedRouteTest` and `RealtimeFixture.userId()`.

API-client and screen-state tests cover existing usernames, successful claim,
invalid input, duplicate username, and retry.

---

## M14.8 — Friend discovery and management on Android

**Status:** DONE

**Depends on:** M14.7, M8

**Completed:** 2026-08-28 — friends are reachable from the app at last. The
shell's Friends destination now shows `FriendsScreen`: the list as the server
has it, a box to find someone by their exact username, and two things to do
about each friend. `ChessApiClient` gained the three calls it was missing —
`GET /users/{username}`, `POST /friends`, and `DELETE /friends/{username}` — so
adding is a lookup the player confirms rather than a name typed into the void:
"Found Alex." comes back before "Add friend" appears. Who exists, who is already
a friend, and whether you just tried to add yourself stay the server's answers
(`D009`): the app keeps no copy of those rules and repeats the refusal in the
server's own words — "No such user", "Already friends with Alex", "You cannot
add yourself". The list is reloaded from the server after every add and removal
rather than being edited locally, and it is fetched again each time the screen
is opened, because the other side of a friendship can change it while the app is
elsewhere. A load that fails leaves the screen honest — nothing is claimed to
have arrived — and offers to try again. Removing a friend asks first, and the
question says what it will really do: the game being played now finishes as
normal, there just will not be another one (`D013`); the server's own sentence
about the game in progress is what the player reads afterwards. "Play" posts to
`/series` and opens whichever game the server says is current, so opening an
existing series and starting one are the same tap (`D011`); a series between
games has nothing to open yet and says so instead of pretending. Every request
from this screen runs one at a time, so a double tap cannot add or remove twice.
Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest` (6 new
`ChessApiClientTest` cases for lookup, add, duplicate, self, and removal, 6 new
`FriendsTest` cases for the wording decided in the app, and 13 new `ChessAppTest`
cases covering opening and loading the screen, an empty list, a failed load and
its retry, lookup then add with the refresh, duplicate and self refusals, an
empty box, the removal confirmation and its cancellation, a confirmed removal
with its refresh, and Play opening the server-selected game or saying there is
none) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 223 Android unit tests, 0
skipped). The dashboard's own Friends section still renders with no data until
`M14.9` loads it.

### Objective

Expose the existing username lookup, add-friend, list-friends, and
remove-friend server behavior through the Android API client and reachable UI.

### Acceptance Criteria

- Exact username lookup can lead to adding a friend.
- Self, missing-user, and duplicate-friend responses are handled.
- The friends list refreshes after add or remove.
- Removal requires a clear confirmation and explains that the current game is
  preserved while the next rematch is disabled.
- A friend row can open an existing game or request the active series through
  the server.

### Verification

API-client and UI-state tests cover lookup, add, duplicate/self rejection,
remove, refresh, and Play/Open behavior.

---

## M14.9 — Authenticated dashboard landing flow

**Status:** DONE

**Depends on:** M14.6, M14.7, M14.8, M14.1, M14.2, M14.3

**Completed:** 2026-08-28 — the dashboard is real. A returning named player lands
on it straight from startup, never on a local game, and the games and the friends
list are fetched together in one load, because they are one screen: what is
waiting on the player, what is waiting on the opponent, and everyone else worth
playing. There is one friends list in the app, loaded by whichever screen asks
first and shared by both. `loaded` is what tells "no games" from "not yet", so a
player with nothing to play sees an answer rather than a spinner, and a load that
fails says so and offers to try again instead of pretending the account is empty.
Selecting a line opens the `gameId` the server put in it — the app never works
out which game that is (`D004`). Play and Open are the same request: both post to
`/series` and open whichever game the server says is current, which is the only
thing that can tell an existing series from a new one (`D011`); the dashboard is
reloaded afterwards, because starting a series changes it. A refusal is shown in
the server's words and leaves the player where they were. Friends, history, and
the local game stay reachable from the shell's chrome, which `M14.5` put there.
Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest` (9 new
`ChessAppTest` cases: landing loads both lists, an empty dashboard is loaded
rather than waiting, a failed load can be retried, a new player goes to
onboarding and no dashboard is fetched for them, claiming a name lands on a
loaded dashboard, selecting a game opens the server's id, Play asks the server
and refreshes, Open goes through the server too, and a refused series leaves a
message on the dashboard) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 232
Android unit tests, 0 skipped). Opening a game still reaches a placeholder;
loading and rendering canonical game state is `M14.10`.

### Objective

Make the useful dashboard the landing destination after identity restoration
and username onboarding, backed by live authenticated server data.

### Acceptance Criteria

- Startup reaches the dashboard without first opening a local game.
- Your Turn, Their Turn, and Friends load together with loading, empty, error,
  and retry states.
- Selecting a game navigates using its server `gameId`.
- Play/Open uses `POST /series`, refreshes the dashboard, and navigates to the
  server-selected current game.
- History, friend management, and optional local play are reachable from the
  application shell.

### Verification

Application-flow tests cover restored-user landing, new-user onboarding,
dashboard load/retry, and Play/Open navigation.

---

## M14.10 — Load and render a canonical online game

**Status:** DONE

**Depends on:** M14.9, M10.1

**Completed:** 2026-08-28 — an online game can be looked at. `GET /games/{gameId}`
now carries the two things a screen could otherwise only get from the line it was
opened from: the opponent, as the `UserSummary` the rest of the API uses, and the
move just played, as `MoveView` — `from`, `to`, and the promotion — rather than a
display string to be picked apart. So only a game id travels through navigation,
and a screen rebuilt after the process was recreated draws the same game as one
opened from the dashboard. The opponent is read once per answer in the routes
rather than threaded through every command path for one name. On Android,
`OnlineGame` turns that answer into what is drawn: the position is read back into
a `game-core` `Board` so the same renderer draws an online game and a local one;
the board faces the side the viewer plays; the move just played is highlighted on
its two squares, which `ChessBoard` now understands; and the status line is the
server's own — whose move it is, the check, or how it ended and by what, written
from the viewer's side. Nothing is recomputed from the position, because none of
it is the app's to decide (`D004`). The version is on screen because a command has
to carry it (`M14.11`). Loading, "not yours", "gone", a refusal, and an
unreachable server are all visible states; the first two offer no retry, because
trying again cannot change either. A finished game and a game in progress are
drawn the same way and neither can be changed from here — read-only is what the
screen is, not a mode it is put into. Verified locally with
`.\gradlew.bat :server:test` against the local test database (8 new
`GameViewTest` cases: each player is told who their opponent is and which side
they play, the opponent carries the id the rest of the API uses, a game with no
moves has no last move, the last move is reported in squares to both players and
is the latest one rather than the first, a stranger is refused, and a missing game
is not found), `.\gradlew.bat :android-app:testDebugUnitTest` (17 new
`OnlineGameTest` cases for the board round trip, orientation for both colours, the
last-move squares, the heading, the status for a turn, a check and three
endings, the version line, the numbered move list, and which refusals are worth
retrying, and 7 new `ChessAppTest` cases for opening, loading, both non-retryable
refusals, the retry, opening from a dashboard line, and going back), and
`.\gradlew.bat build` (BUILD SUCCESSFUL, 380 server tests, 255 Android unit
tests, 323 game-core tests, 0 skipped). Playing a move from this screen is
`M14.11`.

### Objective

Add the Android game DTO/API mapping and online screen state needed to load
`GET /games/{gameId}` and render server-authoritative state using the existing
board presentation. Extend the narrow server read contract where the current
`GameView` lacks display metadata required by the product, rather than relying
on transient navigation arguments.

### Acceptance Criteria

- The screen can recover opponent identity and structured last-move data after
  process recreation, not only when it was opened from a populated dashboard.
- It shows opponent, player side, board orientation, side to move, move history,
  last move, check state, version, and terminal result from canonical server
  data.
- Loading, forbidden, missing-game, offline, and retry states are visible.
- Finished historical games open read-only.
- Online state is distinct from the local pass-and-play state: rendering may
  use `game-core`, but opening a screen never invents or mutates canonical
  state locally.

### Verification

DTO mapping and screen-state tests cover active games for both colors,
completed games, history, and error/retry paths.

---

## M14.11 — Submit moves through the authoritative server

**Status:** DONE

**Depends on:** M14.10, M10.2

**Completed:** 2026-08-28 — a move can be played from Android, and only the server
can make it happen. Tapping a piece shows where it may go — a preview, replayed
from the moves the server listed, so no castling right or en-passant square has
to be guessed at and no new field was needed on the read contract; a move list
that cannot be replayed costs the preview and nothing else. Tapping a destination
sends `POST /games/{gameId}/moves` with the version the move was decided at and
nothing else moves: the board is redrawn only from what came back (`D004`). A
pawn reaching the last rank raises the promotion prompt first, and the chosen
piece travels with the move. While a command is in flight the board is closed to
input, so a second tap sends nothing and the same move cannot be sent twice by
tapping. A retry that the player does make is held to exactly once by the version
it carries (`D021`, `M16.3`): the server refuses it as stale and attaches the
canonical state, which becomes what is on screen — so a client whose reply was
lost sees its own move rather than playing it again. Every refusal is said in
plain words — moved on, not your move, finished, not legal — and an unreachable
server leaves the game where it was with something to read. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (14 new `OnlineMoveTest` cases:
the move list replays into the position that was sent, unreplayable moves cost
only the preview, selecting shows destinations, a destination submits with input
closed, tapping the selection clears it, nothing happens when it is not your
move, while sending, or after the game is over, a promotion prompts and then
sends its piece, backing out sends nothing, and each refusal reads plainly; 5 new
`ChessApiClientTest` cases for the posted version, a promotion, a refusal
carrying canonical state, a refusal without one, and a non-command refusal; and
6 new `ChessAppTest` cases: a move is sent and what came back is shown, the board
does not move until the server says so, a second tap in flight sends nothing, a
stale refusal replaces the screen with the state it carried, a refused move
leaves the game where it was, and an unreachable server leaves something to read)
and `.\gradlew.bat build` (BUILD SUCCESSFUL, 280 Android unit tests, 0 skipped).
Undo, draw claims, and resignation are `M14.13`–`M14.15`; hearing about the
opponent's move without reloading is `M14.12`.

### Objective

Connect board interaction and promotion selection to the authenticated
`MakeMove` endpoint using the currently loaded expected game version.

### Acceptance Criteria

- Legal destinations may be previewed locally, but the board changes only from
  a canonical server response.
- Move and promotion commands carry `expectedVersion`.
- Input is disabled while a command is in flight.
- Accepted, illegal, not-your-turn, game-over, and stale-version responses are
  handled; a stale response replaces local screen state with the attached
  canonical state.
- Retrying a lost response cannot apply a move twice.

### Verification

API-client and online-game state tests cover normal moves, promotion, server
refusals, stale recovery, duplicate submission protection, and rendering of the
returned state.

---

## M14.12 — Receive opponent updates through WebSockets

**Status:** DONE

**Depends on:** M14.11, M12

**Completed:** 2026-08-28 — the app hears about the opponent's move without being
asked to look. `ChessRealtimeClient` opens one authenticated socket at `/ws`,
carrying a token asked for at connect time through the same provider the HTTP
calls use, so a connection made hours into a session is not holding the token the
app started with. The address follows the server's scheme — `https` becomes
`wss`, `http` becomes `ws` — so the socket is exactly as protected as the rest of
the traffic (`D033`). Nothing on it is treated as state: `connected` means only
"you are live, so what you are looking at may be old", and everything on screen is
reloaded over HTTPS; a `game-updated` names a game, and if it is the game being
looked at, that game is reloaded, while any other game refreshes the dashboard
and leaves the open one untouched. The version the message carries is never
written anywhere — it is a fact about the game, not the game (`D022`) — which is
what makes a duplicate, a late, and an out-of-order message all come to the same
harmless reload, and what stops a pushed version from ever standing in for state.
A dropped or refused connection is reopened after a pause, for as long as the
app's state lives; the loop ends when the model is cleared, along with the HTTP
client it shares (`D033`). `RealtimeSource` is the one seam: the real socket, and
a source a test can drive, so what the app does with a message is checked without
standing up a WebSocket server in a unit test. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (8 new `ChessAppTest` cases: a
fresh connection refreshes what is on screen, an update for the open game reloads
it over HTTPS and the pushed version is not the version shown, an update for
another game leaves the open game alone and refreshes the dashboard, the same
update twice is harmless, an unknown message type is ignored, a dropped
connection is reopened, a failed connection is tried again, and the socket
address follows the server's scheme) and `.\gradlew.bat build` (BUILD SUCCESSFUL,
288 Android unit tests, 0 skipped). The socket against a real server is part of
the device play-through, `M14.18`.

### Objective

Add an authenticated Android WebSocket client and use realtime messages only
as invalidation signals that trigger an HTTPS reload of canonical state.

### Acceptance Criteria

- The client connects to `/ws` with the current access token and closes with
  the owning application lifecycle.
- The `connected` greeting triggers a safe dashboard/open-game refresh.
- A `game-updated` message for the visible game reloads it over HTTPS; messages
  for other games refresh dashboard state without corrupting the open game.
- Duplicate, delayed, or out-of-order notifications are harmless.
- Reconnect never treats a pushed version as game state.

### Verification

Client tests cover authentication, connection greeting, matching and
non-matching updates, duplicate messages, close/reconnect, and HTTPS reload.

---

## M14.13 — Authoritative undo integration

**Status:** DONE

**Depends on:** M14.11, M11

**Completed:** 2026-08-28 — a move can be taken back from the game screen, and
only when the server says so. The Undo button appears exactly when the canonical
state's `canUndo` says this player may take their latest move back (`D016`), so
the app never works out eligibility from the position; the server decides again
when the command arrives. `POST /games/{gameId}/undo` carries the version the undo
was decided against, so a retry after the undo landed is refused as stale rather
than taking back a second move (`D021`). Every command on this screen now goes
through one path: the version travels with it, the screen becomes the canonical
state that came back, and a refusal's attached state replaces what was showing —
nothing is ever rewritten locally. Nothing to take back, not your move, a
finished game, and a stale version are each said in plain words. An undo the
opponent made arrives as an ordinary `game-updated` and is just another reload
(`M14.12`). Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest`
(6 new `ChessAppTest` cases: a game with nothing to take back sends nothing, an
eligible undo travels with its version and the answer becomes the screen, nothing
to undo is explained and changes nothing, a stale undo recovers from the state
the refusal carried, an undo in a finished game is explained, and an undo
announced over the socket is just another reload) and `.\gradlew.bat build`
(BUILD SUCCESSFUL, 294 Android unit tests, 0 skipped).

### Objective

Expose `UndoMove` through the Android API client and online game controls.

### Acceptance Criteria

- Undo is offered only when the canonical state says the current user is
  eligible.
- The command carries `expectedVersion` and accepted state replaces the screen
  state.
- Nothing-to-undo, not-your-move, game-over, and stale-version responses are
  explained and recovered without local board rewrites.
- The opponent receives the resulting update through the normal realtime path.

### Verification

API-client and screen-state tests cover eligible undo, all refusals, stale
recovery, and realtime reload.

---

## M14.14 — Authoritative draw-claim integration

**Status:** DONE

**Depends on:** M14.11, M10.3

**Completed:** 2026-08-28 — a draw can be claimed from the game screen, and the
server is what makes it one. A claim button appears for each entry in the
canonical state's `availableDrawClaims` and for nothing else, so the app never
works out an entitlement the server has not granted (`D019`); threefold
repetition and the fifty-move rule are labelled apart, because a player may be
entitled to either and they are different rules. The claim carries the version it
was decided against and goes through the same command path as a move and an undo
(`D021`); the result and the termination reason come back from the server, so the
app never decides that a game is drawn — a refused claim leaves the game exactly
where it was and says there is no draw to claim here. Stale and finished-game
refusals recover from the canonical state the refusal carried. An accepted claim
simply becomes a finished game on screen, which is the same terminal state every
other ending produces; following the series into its next game is `M14.16`.
Verified locally with `.\gradlew.bat :android-app:testDebugUnitTest` (7 new
`ChessAppTest` cases: a game with no claim available sends nothing, a threefold
claim travels with its version and comes back drawn, a fifty-move claim is sent
as its own rule, the two labels differ, a refused claim leaves the game running,
a stale claim recovers from the attached state, and a claim in a finished game is
explained) and `.\gradlew.bat build` (BUILD SUCCESSFUL, 301 Android unit tests,
0 skipped).

### Objective

Expose `ClaimDraw` through the Android API client and show claim actions only
when the canonical state allows them.

### Acceptance Criteria

- Threefold-repetition and fifty-move claims are labeled distinctly.
- Claims carry `expectedVersion`; the client does not decide that a draw was
  accepted before the server does.
- Invalid, stale, and game-over responses are handled with canonical recovery.
- An accepted claim enters the shared completion/rematch flow.

### Verification

API-client and screen-state tests cover each claim type, unavailable claims,
stale recovery, and accepted terminal state.

---

## M14.15 — Android resignation controls

**Status:** DONE

**Depends on:** M5.6, M14.11, M13.5

**Completed:** 2026-08-28 — a player can give up, in both kinds of game, and is
asked first because it cannot be taken back (`D018`). Resign is offered whenever
the game is still running, on the opponent's move as readily as on the player's
own, and cancelling the question leaves everything exactly as it was — no
selection cleared, no request sent. On one device both sides are offered, because
there are two players at one board, and the local game ends through
`ChessRules.resign`. Online, `POST /games/{gameId}/resignation` carries the
version it was decided against like every other command (`D021`), and the result
and termination reason are the server's — the app renders what came back and
never decides that a game is lost. A resignation that arrives after the game has
already finished, a stale one, and one for a game that is not the caller's are
each explained, and none of them changes the board. Verified locally with
`.\gradlew.bat :android-app:testDebugUnitTest` (7 new `GameControlsTest` cases:
either player may resign while the game runs, White and Black resigning each end
it for the other, a player may resign on the opponent's move, resigning clears
the selection, a finished game offers no resignation, and the label names the
side; and 7 new `ChessAppTest` cases: the question comes before anything is sent,
cancelling leaves the game as it was, a confirmed resignation travels with its
version and comes back as the server's result, resigning is offered on the
opponent's move, and an already-finished, a stale, and a not-yours refusal are
each handled without deciding anything locally) and `.\gradlew.bat build` (BUILD
SUCCESSFUL, 315 Android unit tests, 0 skipped). Two test-harness fixes came with
it: the recorded requests are now a snapshot-safe list, because assertions read
them while another job may still be sending, and each test now stops the jobs its
models started — a realtime loop left running would still be using
`Dispatchers.Main` when the next test set it. Following the series into its next
game is `M14.16`.

### Objective

Add the missing resign action to local pass-and-play and authenticated online
games, with the confirmation required by `D018`.

### Acceptance Criteria

- Resign is available whether or not it is the player's turn.
- Cancellation leaves the game unchanged.
- Local resignation uses `ChessRules.resign`; online resignation sends
  `expectedVersion` to the server and renders only the returned canonical
  result.
- Accepted online resignation enters the shared completion/rematch flow.
- Duplicate, stale, unauthorized, and already-finished responses are handled.

### Verification

Local-control, API-client, and online-game tests cover confirmation, cancel,
both player colors, server refusals, and accepted resignation.

---

## M14.16 — Game completion and automatic rematch flow on Android

**Status:** TODO

**Depends on:** M14.12, M14.14, M14.15, M13

### Objective

Make every terminal path understandable on Android and follow the server-created
next game when the series remains active.

### Acceptance Criteria

- Checkmate, stalemate, automatic draws, claimed draws, and resignation show
  the persisted result and disable further commands.
- The client never creates or confirms a rematch.
- After terminal state, the app refreshes the series/dashboard and opens or
  clearly offers the server-created current game.
- Rematch colors and board orientation update from canonical data.
- A series marked to close returns the user to history/dashboard without
  waiting for a game that will not exist.

### Verification

Application-flow tests cover every terminal class, active-series rematch,
closing-series completion, exactly-once refresh behavior, and color reversal.

---

## M14.17 — Reachable history and read-only game review

**Status:** TODO

**Depends on:** M14.5, M14.10, M14.4

### Objective

Wire the completed history API and Compose components into navigation and allow
a finished game to be opened in the shared read-only game renderer.

### Acceptance Criteria

- History is reachable from dashboard/application navigation.
- Loading, empty, error, and retry states are implemented.
- Selecting a historical game loads its canonical final position and move
  history.
- No move, undo, draw-claim, or resign action is available in review mode.

### Verification

Navigation and screen-state tests cover history loading, closed and active
series history, opening a finished game, and absence of mutating controls.

---

## M14.18 — End-to-end Android multiplayer verification

**Status:** TODO

**Depends on:** M14.6, M14.7, M14.8, M14.9, M14.10, M14.11, M14.12, M14.13, M14.14, M14.15, M14.16, M14.17

### Objective

Verify the actual Android application—not only isolated clients and server test
fixtures—through the complete MVP path with two authenticated users.

### Acceptance Criteria

- Two Android clients can restore/create identities, claim usernames, become
  friends, start a series, alternate legal moves, undo when eligible, claim a
  draw when eligible, resign, observe game completion, and enter the automatic
  rematch.
- Opponent changes arrive without manual refresh while connected.
- Dashboard and history reflect the same canonical state after returning from
  the game.
- A representative local pass-and-play game and the DataStore-backed session
  restore are manually verified on an emulator or device, clearing `M5.7`.

### Verification

Automate application/state seams where practical, run the aggregate build
against PostgreSQL, and complete a documented two-client emulator/device
play-through against the development server and Supabase auth project.

This task needs infrastructure, not permission: an accessible emulator or
device, plus the existing development credentials. Where those are available it
is ordinary autonomous work. Where they are not, it is the **missing
prerequisite** Stop Condition and the report should name what is missing. That
is a different kind of stop from `M15`, which requires human authorization even
when everything needed is to hand.

---

# M15 — Beta Deployment Environment

> **Proposed target: a $0 beta.** `D032` proposes one Dockerized Ktor service on
> Render's Free Web Service tier, with auth and PostgreSQL on a second Supabase
> Free project, `ChessGame Beta`. The hard budget means no payment method is
> attached to Render and no service is upgraded automatically: reaching a free
> limit suspends the beta until a human decides what to do. Render sleep and
> cold starts, shared usage quotas, Supabase inactivity pauses and storage/
> egress limits, lack of automatic database backups, and the one-instance
> topology must be accepted explicitly rather than discovered in use.
>
> External state (2026-08-28): a Render Free Web Service named `ChessGame`
> already exists at `https://chessgame-hi7.onrender.com`, created by hand as an
> early test of the hosting resource. **Do not create another one.** It is not
> functional: its first deploy, of `0ef3228`, failed during the Docker build
> because the repository has no `Dockerfile` yet. Nothing else about the beta
> follows from the service existing — being deployment-ready (`M15.2`), a
> successful deploy with a reachable `/health` (`M15.2`), the beta Supabase
> environment (`M15.3`), and the Android beta endpoint (`M15.4`) are all still
> outstanding. `docs/DEVELOPMENT.md` **Beta Deployment** holds the service
> details and that breakdown.
>
> Sequencing: this milestone begins only after `M14.18` proves the Android
> multiplayer client end to end.
>
> Authorization: `D032` is a proposal until a human accepts it in the
> conversation. Accepting its hard-$0/no-payment-method boundary clears the
> recurring-cost Stop Condition, but does not authorize deployment. `M15.2` is
> a **beta deployment**; `M15.3` needs **accounts and credentials** the loop
> cannot create or hold; and `M15.4` follows `M15.2`. An agent may prepare
> deployable artifacts — a Dockerfile, configuration, documentation — but may
> not create accounts, deploy, or handle live credentials. Do not mark any
> `M15` task `IN PROGRESS` without explicit human authorization in the
> conversation.
>
> The loop therefore excludes this milestone from selection. It does **not**
> route around it: in the current dependency graph `M16.1`/`M16.2` require
> `M15`, `M17.1` requires `M15` and `M16`, and `M18.1` requires `M17`, so once
> `M14.18` is `DONE` nothing selectable remains. The loop reaches the **backlog
> exhausted** Stop Condition and reports, which is the intended outcome — `M15`
> is where autonomous development ends until a human authorizes it. Do not mark
> any `M15` task `IN PROGRESS` without explicit human authorization in the
> conversation.

## M15.1 — Confirm Ktor hosting provider and current terms

**Status:** TODO  
**Depends on:** M14.18

### Objective

After the local Android play-through is complete, confirm or replace `D032`'s
proposed provider using the terms available at deployment time.

### Acceptance Criteria

- A human explicitly accepts the provider decision and its operational limits;
  `D032` is then changed from `Proposed` to `Accepted`.
- The chosen provider is shown to support what the server actually needs:
  public HTTPS, WebSocket connections held open, environment secrets,
  outbound connectivity to the beta PostgreSQL, and a health check.
- The hard `$0` boundary is enforceable. For the Render proposal, no payment
  method is attached, the service uses the Free instance type, and quota
  exhaustion suspends the beta rather than creating a charge.
- The topology is a single Ktor instance, or shared realtime pub/sub is
  implemented first. `RealtimeHub` is process-local, so a horizontally scaled
  service would silently fail to deliver moves between players on different
  processes (`ARCHITECTURE.md` §12, `D032`).
- Current limits are rechecked from provider documentation, including Render
  instance hours, outbound bandwidth, build minutes, external-database traffic,
  sleeping/cold starts, and Supabase project, storage, egress, pausing, and
  backup limits.

**Proposed 2026-08-28 (`D032`):** one Render Free Web Service with no payment
method, plus a second Supabase Free project. Keep this task `TODO` until
`M14.18` is complete, a human accepts the proposal, and the current terms have
been checked. The task therefore has real work remaining rather than becoming
`DONE` automatically when its dependency clears.

---

## M15.2 — Deploy Ktor beta server

**Status:** TODO  
**Depends on:** M15.1, M15.3

The beta database and auth project come first, even though they are numbered
second: without `DATABASE_URL` and `SUPABASE_URL` the server starts in
health-only mode, so deploying before `M15.3` would put up a service that
cannot serve the API.

### Objective

Run the server on Render Free as a Docker image, reachable over HTTPS.

The Render service already exists (see the milestone note and
`docs/DEVELOPMENT.md`): this task makes the repository deployable to it and
gets a successful deploy, rather than provisioning hosting.

### Acceptance Criteria

- A `Dockerfile` builds the server distribution and runs it on the port Render
  provides, without baking any secret into the image. Its absence is why the
  first manual deploy failed.
- The branch Render deploys from holds that `Dockerfile`; the service currently
  tracks `main`.
- `DATABASE_URL` and `SUPABASE_URL` are supplied as Render environment
  secrets and never committed.
- The server preserves the PostgreSQL SSL settings supplied by the beta
  configuration and connects securely through the Supabase session-pooler URL
  selected in `M15.3`.
- `/health` is configured as the service's health check.
- The Android test build reaches the deployed server over HTTPS, and a
  WebSocket connects over WSS.
- The service runs as exactly one instance (`D032`); horizontal scaling stays
  off while `RealtimeHub` is process-local.
- Cold starts after idle sleep are exercised and their observed durations are
  recorded. Measurements are evidence for `M15.4` and `M16.1`, not a guaranteed
  upper bound or the sole source of a client timeout value.
- Render usage is checked after the play-through, including outbound traffic
  to Supabase; no payment method is attached and no automatic upgrade is
  enabled.

### Verification

Documented deploy; a successful authenticated, database-backed request and
WebSocket connection from an Android build; recorded cold-start observations;
and confirmation that the service remains on one free instance with no payment
method.

---

## M15.3 — Configure beta Supabase environment

**Status:** TODO  
**Depends on:** M7.1, M7.2, M7.3, M15.1

### Objective

Stand up `ChessGame Beta` as a second Supabase Free project so beta players'
data never shares a database with disposable development data.

### Acceptance Criteria

- `ChessGame Beta` exists separately from `ChessGame Dev`, with anonymous auth
  enabled (`D006`), and an active-project slot is available within the current
  Free-plan limit.
- The Flyway migrations are applied to the beta database, which is that
  project's PostgreSQL rather than Render's free PostgreSQL (`D032`).
- `DATABASE_URL` uses the Supabase Shared Pooler (Supavisor) in **session mode
  on port 5432** because Render is IPv4-only and the Free direct database
  endpoint is IPv6-only. Transaction mode on port 6543 is not used because it
  does not support prepared statements.
- The server preserves explicit PostgreSQL SSL connection properties instead
  of dropping URL query parameters, and the beta connection is verified to use
  SSL.
- The server's `SUPABASE_URL` and the Android beta build's Supabase
  configuration name the *same* project, so issued tokens verify against the
  JWKS the server reads.
- Beta credentials live in the deployment environment and in a git-ignored
  local `.env`; only names and non-secret URLs are committed.
- The project's pause-after-inactivity, database-size and egress limits are
  noted for whoever runs the beta (`D032`). The absence of automatic backups
  and point-in-time recovery is explicitly accepted, or a manual export runbook
  is documented before beta data is treated as durable.

### Verification

Using the exact session-pooler and SSL configuration intended for Render, an
anonymous sign-in against the beta project produces a token the server accepts,
the server completes a database-backed request, and the beta database shows the
migrated schema. This is checked without the deployment — `M15.2` depends on
this task, so deployment cannot be a prerequisite for it. The same flow against
the deployed service is `M15.2` and `M15.4`.

---

## M15.4 — Configure Android beta endpoint

**Status:** TODO  
**Depends on:** M15.2

### Objective

Point a beta Android build at the deployed API without hard-coding secrets.

### Acceptance Criteria

- The beta build targets the Render HTTPS base URL through build configuration,
  not a literal in source; development builds keep the emulator-loopback
  default.
- Beta and release traffic is HTTPS/WSS only; the development-only cleartext
  allowance from `M14.5` does not apply to them.
- No Supabase key or server secret is committed; the beta build reads its
  configuration the same way the development build does.
- Safe startup probes and canonical reloads use capped retry/backoff with a
  conservative, configurable deadline. The cold-start observations from
  `M15.2` inform that policy but are not treated as a guaranteed upper bound.
- The UI distinguishes a service that is waking from a terminal network error,
  eventually offers an actionable retry, and never blindly retries a mutating
  command outside the existing version/idempotency safeguards.

### Verification

A beta build authenticates, loads the dashboard, and plays a move against the
deployed server, including one request issued after the service has slept.

---

# M16 — Hardening

## M16.1 — Network interruption

**Status:** TODO  
**Depends on:** M12, M14.18, M15

### Acceptance Criteria

- HTTP and WebSocket interruption during an active game recovers by reconnecting
  and reloading canonical state without duplicating or losing a move.
- Recovery is exercised after a Render idle cold start as well as an ordinary
  short disconnect; the Android waking, retry, and terminal-error states remain
  distinguishable.

---

## M16.2 — App restart/reconnect

**Status:** TODO  
**Depends on:** M12, M14.18, M15

---

## M16.3 — Duplicate commands

**Status:** DONE

**Depends on:** M10, M11, M13

**Completed:** 2026-08-26 — the same command arriving twice was held to
exactly-once end to end, over HTTP, for all four commands. No defect was found
and no code changed: the version a command is written against is what makes it
unique (`D021`), so a duplicate is by construction a stale one, and the guarded
write settles it. What was missing was the proof that this holds at the API
boundary rather than only inside `GameRepository`, and that a client can act on
the answer — a retry is refused with `STALE_VERSION` and the canonical state
attached, so a client whose reply was lost can see its own move in the refusal
without a second request. The distinction that matters is kept: retrying at the
*old* version is a duplicate and refused, while sending the same squares at the
version the game is now at is a new move and allowed. Verified locally with
`.\gradlew.bat :server:test` (11 new `DuplicateCommandTest` cases: a repeated
move, undo, draw claim, and resignation each count once, a retry can see its own
move in the refusal, an undo retried at the new version reports nothing to take
back and a resignation reports the game finished, ten identical requests leave
one move, a duplicate does not disturb the opponent's view, and a duplicated
game-ending move still creates exactly one rematch) and `.\gradlew.bat build`
against the local test database (BUILD SUCCESSFUL, 350 server tests, 0 skipped).

---

## M16.4 — Series/rematch idempotency

**Status:** DONE

**Depends on:** M13

**Completed:** 2026-08-26 — "Play with this friend" held to one series and one
game however many times it is tapped, at the API boundary where the taps
actually arrive. `D011`'s partial unique index settles two opens racing, and the
rematch is created by the game that finished rather than by anyone asking for it
(`D015`), so `POST /series` has nothing to create. What was missing was the
proof end to end — both players
tapping at once get one series and one game, tapping mid-game opens the game in
progress with its moves intact, tapping after a game ends opens the rematch
rather than reopening the finished game, and tapping repeatedly across a
finished game still leaves exactly two games. A series closed by removing a
friend is not revived by making up again: that opens a new series and leaves the
old one and its game as history (`D012`, `D013`). Verified locally with
`.\gradlew.bat :server:test` (10 new `SeriesIdempotencyTest` cases, counting
games through `/dashboard` and `/history` so the assertions are what a client
can actually see) and `.\gradlew.bat build` against the local test database
(BUILD SUCCESSFUL, 360 server tests, 0 skipped).

**Correction (2026-08-26, during `M16.5`):** this note first said no defect was
found. That was wrong — the race is real and the new test caught it on a later
run: two players tapping "Play" at the same moment on a series that had no game
yet both found `currentGameId` empty and both tried to be game one, and the
database's `games_series_sequence` index refused the second, so one player got a
`500` instead of the game. The data was never wrong, but the answer to one of
them was. `SeriesService.openWithGame` now locks the series row and re-reads it
before starting the first game, so the second request waits, finds the game the
first created, and hands that back — the same mechanism `settleAfter` already
used for the same class of race. The concurrency test is what surfaced it and
now passes repeatedly (three consecutive runs of the series suites).

---

## M16.5 — Server logging

**Status:** DONE

**Depends on:** M10

**Completed:** 2026-08-26 — the server now says enough to debug the beta and
nothing that would be dangerous to keep. Every request is logged as its method,
path, and status, and every command decision as one line naming the user, the
game, the version it was written against, and what the server decided — "this
player asked at version 7 and the game was at 8" is a complete account of a
refusal, and none of it is a secret. Refusals log at `INFO` and accepted
commands at `DEBUG`, because an accepted write is already recorded as an audit
event (`D020`) and a busy game would otherwise fill the log. What is left out is
the point: headers are never logged, so the bearer token on every authenticated
request and the Supabase key cannot leak into a file that is read in weaker
places than the database; bodies are never logged, so the log does not become a
second copy of the game state; and `/health` is filtered out so polling does not
bury everything else. Ids in paths stay as they are — a game id is a reference,
not a secret, and without it a log cannot answer "what happened to this game".
`logback.xml` writes to the console for a host that collects stdout, and quiets
Netty, Exposed, and Hikari. Verified locally with `.\gradlew.bat :server:test`
(6 new `ServerLoggingTest` cases that capture *everything* logged during real
requests through a Logback appender and assert on all of it: the request line is
there, the token and the word `Authorization` are not, a refusal is explained,
no board reaches the log, and `/health` is silent) and `.\gradlew.bat build`
against the local test database (BUILD SUCCESSFUL, 366 server tests, 0 skipped).
This build also caught and fixed the `M16.4` concurrency defect recorded above.

### Acceptance Criteria

Log enough for debugging without credentials/secrets.

---

# M17 — Friend Beta

## M17.1 — Small beta distribution

**Status:** TODO  
**Depends on:** M14.18, M15, M16

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
