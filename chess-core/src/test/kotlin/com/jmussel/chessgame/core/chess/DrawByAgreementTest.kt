package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A draw by agreement (`D097`): final, a draw, and no Undo or Redo past it. */
class DrawByAgreementTest {
    private fun played(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, move -> ChessRules.applyMove(game, Move.of(move.take(2), move.drop(2))) }

    @Test
    fun anAgreedDrawEndsTheGameAsADrawByAgreement() {
        val game = played("e2e4", "e7e5")

        val drawn = ChessRules.agreeDraw(game)

        assertEquals(GameResult.draw(TerminationReason.AGREEMENT), drawn.result)
        assertTrue(TerminationReason.AGREEMENT.isDraw)
        assertEquals(game.history, drawn.history, "no move is played")
        assertNull(ChessRules.undoableSide(drawn), "final (D017-D019)")
        assertTrue(ChessRules.legalMoves(drawn.state).isEmpty())
    }

    @Test
    fun itClearsTheRedoPath() {
        val undone = ChessRules.undoLastMove(played("e2e4", "e7e5"))

        val drawn = ChessRules.agreeDraw(undone)

        assertTrue(drawn.redoPath.isEmpty())
        assertNull(ChessRules.redoableSide(drawn))
    }

    @Test
    fun aFinishedGameCannotBeDrawnAgain() {
        val resigned = ChessRules.resign(played("e2e4"), Side.BLACK)

        assertFailsWith<IllegalArgumentException> { ChessRules.agreeDraw(resigned) }
    }
}
