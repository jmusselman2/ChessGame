package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Redo (`D092`): one Redo restores one accepted Undo operation, exactly as it was recorded,
 * and the whole forward path is kept until play diverges from it. Playing the next recorded
 * move by hand follows the path rather than diverging (`D102`): `RetainedContinuationTest`.
 */
class RedoTest {
    private fun played(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, move -> ChessRules.applyMove(game, Move.of(move.take(2), move.drop(2))) }

    private val fourPlies = played("e2e4", "e7e5", "g1f3", "b8c6")

    private fun undoOne(game: ChessGame): ChessGame = ChessRules.undo(game, requireNotNull(ChessRules.undoableSide(game)))

    @Test
    fun nothingToRedoUntilSomethingIsUndone() {
        assertNull(ChessRules.redoableSide(ChessGame.newGame()))
        assertNull(ChessRules.redoableSide(fourPlies))
        assertFailsWith<IllegalArgumentException> { ChessRules.redo(fourPlies) }
    }

    @Test
    fun redoRestoresTheUndoneMoveAndItsExactState() {
        val undone = undoOne(fourPlies)

        assertEquals(Side.BLACK, ChessRules.redoableSide(undone), "the side to move where the Undo left the game")
        assertTrue(ChessRules.canRedo(undone, Side.BLACK))
        assertFalse(ChessRules.canRedo(undone, Side.WHITE))
        assertEquals(fourPlies, ChessRules.redo(undone))
    }

    @Test
    fun undoUndoRedoRedoRestoresBothOperationsInOrder() {
        val twiceUndone = undoOne(undoOne(fourPlies))
        assertEquals(2, twiceUndone.redoPath.size)

        val once = ChessRules.redo(twiceUndone)
        assertEquals(played("e2e4", "e7e5", "g1f3"), once.copy(redoPath = emptyList()))
        assertEquals(listOf(Move.of("b8", "c6")), once.redoPath.single().moves)

        assertEquals(fourPlies, ChessRules.redo(once))
    }

    @Test
    fun undoUndoRedoUndoKeepsTheWholeForwardPath() {
        val twiceUndone = undoOne(undoOne(fourPlies))
        val redoneThenUndone = undoOne(ChessRules.redo(twiceUndone))

        assertEquals(twiceUndone, redoneThenUndone, "the same two operations, in the same order")
        assertEquals(fourPlies, ChessRules.redo(ChessRules.redo(redoneThenUndone)))
    }

    @Test
    fun aPathLongerThanTwoOperationsWalksBackToTheStartAndForwardAgain() {
        var game = fourPlies
        repeat(4) { game = undoOne(game) }
        assertSamePlay(ChessGame.newGame(), game)
        assertNull(ChessRules.undoableSide(game), "nothing left to undo at the start")
        assertEquals(Side.WHITE, ChessRules.redoableSide(game))

        repeat(4) { game = ChessRules.redo(game) }
        assertEquals(fourPlies, game)
        assertNull(ChessRules.redoableSide(game), "nothing left to redo at the end")
    }

    @Test
    fun aTwoPlyOperationIsRestoredWhole() {
        val takenBack = ChessRules.undoLastMoves(fourPlies, plies = 2)

        assertSamePlay(played("e2e4", "e7e5"), takenBack)
        assertEquals(listOf(Move.of("g1", "f3"), Move.of("b8", "c6")), takenBack.redoPath.single().moves)
        assertEquals(fourPlies, ChessRules.redo(takenBack), "both plies at once, never one at a time")
    }

    @Test
    fun aMoveJoinedToTheNextOperationMakesTheSameTwoPlyOperation() {
        // An undo request that crossed the opponent's undo of the reply (D091): the reply went
        // first, then the requester's move joins it.
        val replyUndone = undoOne(fourPlies)
        val joined = ChessRules.undoLastMoveJoiningNext(replyUndone)

        assertEquals(ChessRules.undoLastMoves(fourPlies, plies = 2), joined)
        assertEquals(fourPlies, ChessRules.redo(joined))
    }

    @Test
    fun aDifferentMoveClearsThePath() {
        val undone = undoOne(undoOne(fourPlies))
        val diverged = ChessRules.applyMove(undone, Move.of("d2", "d4"))

        assertTrue(diverged.redoPath.isEmpty())
        assertNull(ChessRules.redoableSide(diverged))
    }

    @Test
    fun resignationAndADrawClaimClearThePathAndOfferNoRedo() {
        val undone = undoOne(fourPlies)

        val resigned = ChessRules.resign(undone, Side.WHITE)
        assertTrue(resigned.redoPath.isEmpty())
        assertNull(ChessRules.redoableSide(resigned))

        var knights = played("g1f3", "g8f6", "f3g1", "f6g8", "g1f3", "g8f6", "f3g1", "f6g8", "e2e4")
        knights = undoOne(knights)
        assertTrue(DrawClaim.THREEFOLD_REPETITION in ChessRules.availableDrawClaims(knights))
        val drawn = ChessRules.claimDraw(knights, DrawClaim.THREEFOLD_REPETITION)
        assertTrue(drawn.redoPath.isEmpty())
        assertNull(ChessRules.redoableSide(drawn))
    }

    @Test
    fun aFinishedGameNeverOffersRedoEvenWithARecordedPath() {
        val undone = undoOne(fourPlies)
        val finishedWithPath = undone.copy(state = ChessRules.resign(undone.state, Side.WHITE))

        assertNull(ChessRules.redoableSide(finishedWithPath))
        assertFailsWith<IllegalArgumentException> { ChessRules.redo(finishedWithPath) }
    }

    @Test
    fun redoRestoresRecordedStateRatherThanReplaying() {
        // A recorded state the rules would never produce from this move: Redo returns it as it
        // was recorded, which shows it is restored, not recomputed.
        val undone = undoOne(fourPlies)
        val recorded = undone.redoPath.single()
        val marked = recorded.stateAfter.copy(fullmoveNumber = 99)
        val tampered = undone.copy(redoPath = listOf(UndoneOperation(recorded.records, marked)))

        assertEquals(marked, ChessRules.redo(tampered).state)
    }

    @Test
    fun aPathThatDoesNotStartFromThisPositionIsNotRedoable() {
        val undone = undoOne(fourPlies)
        val elsewhere = undone.copy(state = played("d2d4").state)

        assertNull(ChessRules.redoableSide(elsewhere))
    }
}
