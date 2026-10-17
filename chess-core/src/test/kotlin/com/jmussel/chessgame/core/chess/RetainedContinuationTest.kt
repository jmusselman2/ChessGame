package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Matching play advances the retained continuation (`D102`, `M22.2`).
 *
 * A move with the next retained action's `from`, `to` and promotion, played from exactly the
 * position it was recorded from, consumes that one action and keeps everything after it. Any
 * other move branches and clears the retained future. Explicit Redo keeps restoring whole
 * operations (`D092`).
 */
class RetainedContinuationTest {
    private fun played(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, move -> ChessRules.applyMove(game, Move.of(move.take(2), move.drop(2))) }

    private val fourPlies = played("e2e4", "e7e5", "g1f3", "b8c6")

    private fun undoOne(game: ChessGame): ChessGame = ChessRules.undo(game, requireNotNull(ChessRules.undoableSide(game)))

    private fun move(text: String): Move = Move.of(text.take(2), text.drop(2))

    @Test
    fun repeatingTheNextMoveKeepsTheMoveAfterItRedoable() {
        val twiceUndone = undoOne(undoOne(fourPlies))
        assertEquals(move("g1f3"), ChessRules.retainedMove(twiceUndone))

        val repeated = ChessRules.applyMove(twiceUndone, move("g1f3"))

        assertSamePlay(played("e2e4", "e7e5", "g1f3"), repeated)
        assertEquals(listOf(move("b8c6")), repeated.redoPath.single().moves, "the consumed move is not still redoable")
        assertEquals(Side.BLACK, ChessRules.redoableSide(repeated))
        assertEquals(fourPlies, ChessRules.redo(repeated))
    }

    @Test
    fun repeatingTheWholeSuffixByHandReachesTheOriginalGameWithNothingLeftToRedo() {
        var game = fourPlies
        repeat(4) { game = undoOne(game) }

        listOf("e2e4", "e7e5", "g1f3", "b8c6").forEach { game = ChessRules.applyMove(game, move(it)) }

        assertEquals(fourPlies, game)
        assertNull(ChessRules.redoableSide(game))
        assertNull(ChessRules.retainedMove(game))
    }

    @Test
    fun aDifferentMoveBranchesAndClearsTheWholeSuffix() {
        var game = fourPlies
        repeat(3) { game = undoOne(game) }

        val diverged = ChessRules.applyMove(game, move("c7c5"))

        assertTrue(diverged.redoPath.isEmpty())
        assertSamePlay(played("e2e4", "c7c5"), diverged)
    }

    @Test
    fun aPromotionToAnotherPieceIsADifferentAction() {
        val start =
            ChessGame(
                GameState(
                    board =
                        Board.of(
                            mapOf(
                                Square.parse("a7") to Piece(Side.WHITE, PieceType.PAWN),
                                Square.parse("e1") to Piece(Side.WHITE, PieceType.KING),
                                Square.parse("h8") to Piece(Side.BLACK, PieceType.KING),
                            ),
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                ),
            )
        val queen = Move(Square.parse("a7"), Square.parse("a8"), PieceType.QUEEN)
        val knight = Move(Square.parse("a7"), Square.parse("a8"), PieceType.KNIGHT)
        val undone = undoOne(ChessRules.applyMove(start, queen))

        assertTrue(ChessRules.applyMove(undone, knight).redoPath.isEmpty(), "a8=N is not a8=Q")
        assertTrue(ChessRules.applyMove(undone, queen).redoPath.isEmpty(), "a8=Q consumed the only action")
        assertEquals(ChessRules.applyMove(start, queen), ChessRules.applyMove(undone, queen))
    }

    @Test
    fun theSameBoardWithDifferentCanonicalStateDoesNotMatch() {
        val undone = undoOne(fourPlies)
        val recorded = undone.redoPath.single()
        val from = recorded.records.single().positionBefore

        val differences =
            listOf(
                from.copy(castlingRights = CastlingRights.NONE),
                from.copy(enPassantTarget = Square.parse("e6")),
                from.copy(drawRuleState = from.drawRuleState.withHalfmoveClock(from.halfmoveClock + 1)),
                from.copy(drawRuleState = DrawRuleState(from.halfmoveClock, emptyMap())),
                from.copy(fullmoveNumber = from.fullmoveNumber + 1),
            )

        differences.forEach { other ->
            assertEquals(from.board, other.board, "the visible board is the same")
            val elsewhere = undone.copy(state = other)

            assertNull(ChessRules.retainedMove(elsewhere), "$other is not the recorded position")
            assertTrue(ChessRules.applyMove(elsewhere, move("b8c6")).redoPath.isEmpty(), "so the move branches")
        }
    }

    @Test
    fun consumingTheFirstActionOfATwoPlyOperationLeavesItsReplyAsTheNextOperation() {
        val takenBack = ChessRules.undoLastMoves(fourPlies, plies = 2)

        val repeated = ChessRules.applyMove(takenBack, move("g1f3"))

        assertSamePlay(played("e2e4", "e7e5", "g1f3"), repeated)
        val reply = repeated.redoPath.single()
        assertEquals(listOf(move("b8c6")), reply.moves)
        assertEquals(fourPlies.state, reply.stateAfter)
        assertEquals(fourPlies, ChessRules.redo(repeated))
        assertEquals(fourPlies, ChessRules.redo(takenBack), "explicit Redo still restores both plies at once")
    }

    @Test
    fun deeperOperationsSurviveTheConsumptionOfTheNextOne() {
        var game = fourPlies
        repeat(3) { game = undoOne(game) }
        assertEquals(3, game.redoPath.size)

        val advanced = ChessRules.applyMove(game, move("e7e5"))

        assertEquals(2, advanced.redoPath.size)
        assertEquals(fourPlies, ChessRules.redo(ChessRules.redo(advanced)))
    }

    @Test
    fun theRecordedResultIsRestoredRatherThanRecomputed() {
        // A recorded state the rules would never produce from this move: matching play lands on
        // it as it was recorded, which shows it is restored, not replayed (`D061`).
        val undone = undoOne(fourPlies)
        val recorded = undone.redoPath.single()
        val marked = recorded.stateAfter.copy(fullmoveNumber = 99)
        val tampered = undone.copy(redoPath = listOf(UndoneOperation(recorded.records, marked)))

        assertEquals(marked, ChessRules.applyMove(tampered, move("b8c6")).state)
    }

    @Test
    fun aMatchingMoveThatEndsTheGameClearsWhatIsLeft() {
        val takenBack = ChessRules.undoLastMoves(fourPlies, plies = 2)
        val operation = takenBack.redoPath.single()
        val ending = operation.records[1].positionBefore.copy(result = GameResult.checkmate(Side.BLACK))
        val endingRecords = listOf(operation.records[0], MoveRecord(operation.records[1].move, ending))
        val tampered = takenBack.copy(redoPath = listOf(UndoneOperation(endingRecords, operation.stateAfter)))

        val finished = ChessRules.applyMove(tampered, move("g1f3"))

        assertTrue(finished.isOver)
        assertTrue(finished.redoPath.isEmpty(), "a finished game retains nothing")
    }

    @Test
    fun aFinishedGameRetainsNoMove() {
        val undone = undoOne(fourPlies)
        val finished = undone.copy(state = ChessRules.resign(undone.state, Side.WHITE))

        assertNull(ChessRules.retainedMove(finished))
    }
}
