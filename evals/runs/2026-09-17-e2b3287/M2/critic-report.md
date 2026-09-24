# M2 — Chess Domain Model: Critic Report

## Scope and selection

Evaluated the requirements of completed milestone **M2 — Chess Domain Model**
against the integrated implementation on current `origin/main` commit
`036a1a18c0a50a864d6c7356e9e40d860c2d82ac`.

M1 was skipped because it is repository/build bootstrap. M2 is the earliest
completed milestone with meaningful product behavior: it defines the chess
state model and constructs the standard initial position. Historical M2
snapshots were consulted only to recover the original wording; all behavioral
checks ran against the current implementation.

## Finding 1 — mutable alias can change an allegedly immutable position

1. **Requirement or invariant:** M2.1 says all domain types are immutable
   (`docs/BACKLOG.md:304`). A published `GameState` must therefore not change
   because an object retained by its constructor's caller changes.
2. **Adversarial scenario:** construct `DrawRuleState` with a `MutableMap` that
   says the current position occurred once, publish it in a `GameState`, then
   mutate the retained map to five occurrences. The already-published state now
   reports a fivefold repetition without any chess move or state transition.
3. **Implementation evidence:** `DrawRuleState.positionCounts` stores the
   supplied `Map` reference directly (`DrawRuleState.kt:29-31`).
   `repetitionsOf` reads that reference directly (`DrawRuleState.kt:37`), and
   `Repetition.isFivefold` trusts the resulting count (`Repetition.kt:46`). No
   defensive snapshot is made.
4. **Existing test coverage:** `DrawRuleStateTest.recordingLeavesTheOriginalStateUnchanged`
   checks only that `recording()` uses `copy`; it does not mutate an input map
   retained by the caller. Standard-position tests use maps created internally.
5. **Coverage sufficiency:** insufficient. The existing test proves functional
   update behavior, not constructor-boundary immutability. It cannot detect the
   aliasing defect.
6. **Classification:** **CONFIRMED DEFECT**.
7. **Smallest automated test:** create a state from a one-entry mutable map,
   mutate that map to the fivefold threshold, and assert that the state still
   has one occurrence and is not fivefold. Added as
   `M2DomainInvariantTest.externalMapMutationCannotManufactureAnAutomaticDraw`.

## Finding 2 — impossible repetition counts are accepted

1. **Requirement or invariant:** `DrawRuleState.positionCounts` records how
   often positions have occurred. An entry can only represent an occurrence if
   its count is positive. The type already rejects a negative halfmove clock,
   showing that numeric state invariants belong at this boundary.
2. **Adversarial scenario:** construct or deserialize a state containing a zero
   or negative repetition count. The state accepts it; `recording()` then
   increments from that impossible value, which can delay or suppress a valid
   threefold/fivefold result.
3. **Implementation evidence:** the initializer validates only
   `halfmoveClock >= 0` (`DrawRuleState.kt:33-35`). The public
   `positionCounts` constructor parameter has no key/count validation, and the
   server persistence DTO passes stored counts directly into it
   (`server/.../GameStateDocument.kt:52-55`).
4. **Existing test coverage:** `DrawRuleStateTest.rejectsANegativeHalfmoveClock`
   covers the clock. No existing test supplies zero or negative repetition
   counts.
5. **Coverage sufficiency:** insufficient for the semantic validity of the draw
   state, especially at the persistence reconstruction boundary.
6. **Classification:** **LIKELY DEFECT**. The state is semantically impossible,
   but the M2 acceptance criteria do not explicitly require constructor
   rejection, and normal command processing does not generate such counts.
7. **Smallest automated test:** construct `DrawRuleState` with counts `0` and
   `-1` and require `IllegalArgumentException`. Added as
   `M2DomainInvariantTest.rejectsNonPositiveRepetitionCounts`.

## Finding 3 — initial-position repetition tracking is not active move history

1. **Requirement or invariant:** M2.2 requires no active move history. The
   original completion note described an empty `DrawRuleState`, while later
   repetition work requires the initial position itself to count once.
2. **Adversarial scenario:** a literal regression to an empty repetition map
   would make a later return to the initial position undercount repetition; the
   opposite risk is treating the initial occurrence as if a move had been
   played.
3. **Implementation evidence:** `StandardPosition.newGame()` creates the exact
   initial state and passes it through `Repetition.recording`
   (`StandardPosition.kt:43-54`). Active moves are held separately in
   `ChessGame.history`, not in `GameState`.
4. **Existing test coverage:** `StandardPositionTest.hasNoActiveHistory` asserts
   no en-passant target, halfmove clock zero, one initial position occurrence,
   and one repetition-map entry. `MoveHistoryTest` asserts a fresh
   `ChessGame` has empty move history.
5. **Coverage sufficiency:** sufficient. The tests distinguish position
   occurrence bookkeeping from played-move history.
6. **Classification:** **ACCEPTABLE BEHAVIOR**. The current behavior composes
   the original no-moves requirement with later correct repetition rules.
7. **Smallest automated test:** assert both
   `ChessGame.newGame().history.isEmpty()` and
   `Repetition.occurrences(ChessGame.newGame().state) == 1`. Existing tests
   already prove both halves.

## Finding 4 — exact standard start remains intact in current main

1. **Requirement or invariant:** M2.2 requires 32 correctly placed pieces,
   White to move, all castling rights, no active move history, and counters at
   zero halfmoves/fullmove one (`docs/BACKLOG.md:354-361`).
2. **Adversarial scenario:** later rule work could swap king/queen placement,
   share a mutable board, remove a castling right, leave en passant set, or
   advance a counter.
3. **Implementation evidence:** `StandardPosition.BOARD` builds both back ranks
   and pawn ranks; `newGame()` explicitly sets White, all rights, null en
   passant, zeroed draw clock, fullmove one, and null result
   (`StandardPosition.kt:25-54`). `Board` snapshots placements into its own
   square list.
4. **Existing test coverage:** `StandardPositionTest` checks all 64 squares,
   both sides' piece inventories, king/queen squares, middle-rank emptiness,
   side to move, every castling right, counters, result, equality of new games,
   and rendered board orientation. Later legal-move tests also expect 20 legal
   moves from the start.
5. **Coverage sufficiency:** sufficient for the stated M2.2 requirements.
6. **Classification:** **ACCEPTABLE BEHAVIOR**.
7. **Smallest automated test:** compare the 64-square rendering plus side,
   castling, en-passant, history, and counters to one expected initial-state
   fixture. The existing dedicated tests are more diagnostic and already cover
   this.

## Finding 5 — game-core remains platform-independent

1. **Requirement or invariant:** M2.1 domain types must remain independent of
   Android, server, and database concerns (`docs/BACKLOG.md:327`).
2. **Adversarial scenario:** later persistence or Android work adds framework
   annotations/imports or dependencies to the shared chess state.
3. **Implementation evidence:** `game-core/build.gradle.kts` applies only the
   Kotlin/JVM plugin and has only `kotlin("test")` as a test dependency. Current
   main chess-domain sources contain no Android, Ktor, SQL, or serialization
   imports; persistence conversion remains in the server module.
4. **Existing test coverage:** compilation and `:game-core:test` exercise the
   module boundary, while the aggregate build compiles both consumers.
5. **Coverage sufficiency:** sufficient for current dependency state, though it
   is a build-time architecture check rather than a behavioral unit test.
6. **Classification:** **ACCEPTABLE BEHAVIOR**.
7. **Smallest automated test:** `./gradlew :game-core:compileKotlin` with the
   current isolated dependency graph. No additional regression test is needed.

## Critic conclusion

M2's standard position and module boundary remain correct. The main correctness
gap is that the allegedly immutable draw-rule state can be mutated through an
input alias, and that mutation can manufacture a fivefold-repetition decision.
The acceptance of non-positive occurrence counts is a second, lower-confidence
state-validation gap.
