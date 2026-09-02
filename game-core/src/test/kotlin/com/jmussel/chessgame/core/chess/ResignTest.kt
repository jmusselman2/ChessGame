package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Giving up.
 *
 * Resignation is not a move: it depends on neither the position nor whose turn it is, it
 * leaves the history alone, and it is final the moment it is made (`D018`).
 */
class ResignTest {
    private fun afterOpeningMoves(): ChessGame =
        listOf(Move.of("e2", "e4"), Move.of("e7", "e5")).fold(ChessGame.newGame()) { game, move ->
            ChessRules.applyMove(game, move)
        }

    @Test
    fun resigningLosesTheGame() {
        val resigned = ChessRules.resign(afterOpeningMoves(), Side.WHITE)

        assertEquals(GameOutcome.BLACK_WINS, resigned.result?.outcome)
        assertEquals(TerminationReason.RESIGNATION, resigned.result?.reason)
    }

    @Test
    fun eitherPlayerCanResign() {
        val resigned = ChessRules.resign(afterOpeningMoves(), Side.BLACK)

        assertEquals(GameOutcome.WHITE_WINS, resigned.result?.outcome)
        assertEquals(TerminationReason.RESIGNATION, resigned.result?.reason)
    }

    @Test
    fun aPlayerMayResignWhenItIsNotTheirTurn() {
        val position = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

        assertEquals(Side.BLACK, position.sideToMove)

        // White has just moved and is waiting; they may still give up.
        val resigned = ChessRules.resign(position, Side.WHITE)

        assertEquals(GameOutcome.BLACK_WINS, resigned.result?.outcome)
    }

    @Test
    fun resigningFromTheStartingPositionIsAllowed() {
        val resigned = ChessRules.resign(ChessGame.newGame(), Side.WHITE)

        assertEquals(GameOutcome.BLACK_WINS, resigned.result?.outcome)
        assertTrue(resigned.moves.isEmpty())
    }

    @Test
    fun theHistoryIsUntouched() {
        val played = afterOpeningMoves()

        val resigned = ChessRules.resign(played, Side.BLACK)

        assertEquals(played.history, resigned.history, "resigning is not a move")
        assertEquals(played.state.board, resigned.state.board)
        assertEquals(played.sideToMove, resigned.sideToMove)
    }

    @Test
    fun aResignedGameIsOver() {
        val resigned = ChessRules.resign(afterOpeningMoves(), Side.WHITE)

        assertTrue(resigned.isOver)
        // Once the game is over the move-query API reports nothing playable, matching
        // applyMove's own refusal (`D017`, M3 terminal-state remediation); and nobody may
        // take a move back either.
        assertTrue(ChessRules.legalMoves(resigned).isEmpty(), "a finished game has no moves to offer")
        assertFalse(ChessRules.canUndo(resigned, Side.BLACK))
        assertFalse(ChessRules.canUndo(resigned, Side.WHITE))
    }

    @Test
    fun aFinishedGameCannotBeResigned() {
        val resigned = ChessRules.resign(afterOpeningMoves(), Side.WHITE)

        assertFailsWith<IllegalArgumentException> { ChessRules.resign(resigned, Side.BLACK) }
    }

    @Test
    fun aCheckmatedGameCannotBeResigned() {
        val mated =
            listOf(
                Move.of("f2", "f3"),
                Move.of("e7", "e5"),
                Move.of("g2", "g4"),
                Move.of("d8", "h4"),
            ).fold(ChessGame.newGame()) { game, move -> ChessRules.applyMove(game, move) }

        assertEquals(TerminationReason.CHECKMATE, mated.result?.reason)
        assertFailsWith<IllegalArgumentException> { ChessRules.resign(mated, Side.WHITE) }
    }
}
