package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Telling a move, a takeback and a game's end apart from a tap that changed nothing (`M21.2`). */
class LocalGameChangeTest {
    private fun ChessGame.play(vararg moves: String): ChessGame =
        moves.fold(this) { game, move -> ChessRules.applyMove(game, Move.of(move.substring(0, 2), move.substring(2, 4))) }

    private val opening = ChessGame.newGame().play("e2e4", "e7e5")

    @Test
    fun theSameGameIsNoChange() {
        assertNull(LocalGameChange.between(opening, opening))
        assertNull(LocalGameChange.between(opening, ChessGame(opening.state, opening.history)))
    }

    @Test
    fun oneMoreMoveIsAMove() {
        assertEquals(LocalGameChange.Move, LocalGameChange.between(opening, opening.play("g1f3")))
    }

    @Test
    fun fewerMovesAreATakeback() {
        assertEquals(LocalGameChange.TakeBack(1), LocalGameChange.between(opening, ChessRules.undoLastMove(opening)))
        assertEquals(LocalGameChange.TakeBack(2), LocalGameChange.between(opening, ChessGame.newGame()))
    }

    @Test
    fun anEndWithNoMoveIsAResult() {
        assertEquals(LocalGameChange.Result, LocalGameChange.between(opening, ChessRules.resign(opening, Side.WHITE)))

        val shuffle = arrayOf("g1f3", "g8f6", "f3g1", "f6g8")
        val repeated = ChessGame.newGame().play(*shuffle, *shuffle)
        val drawn = ChessRules.claimDraw(repeated, DrawClaim.THREEFOLD_REPETITION)
        assertEquals(LocalGameChange.Result, LocalGameChange.between(repeated, drawn))
    }

    @Test
    fun aGameEndingMoveIsAMove() {
        val beforeMate = ChessGame.newGame().play("f2f3", "e7e5", "g2g4")
        assertEquals(LocalGameChange.Move, LocalGameChange.between(beforeMate, beforeMate.play("d8h4")))
    }

    @Test
    fun aDifferentGameIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            LocalGameChange.between(opening, ChessGame.newGame().play("d2d4", "d7d5"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LocalGameChange.between(opening, ChessGame.newGame().play("d2d4", "d7d5", "c2c4"))
        }
    }
}
