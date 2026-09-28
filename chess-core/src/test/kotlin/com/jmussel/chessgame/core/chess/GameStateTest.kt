package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameStateTest {
    private val position =
        GameState(
            board =
                Board.of(
                    mapOf(
                        Square.parse("e1") to Piece(Side.WHITE, PieceType.KING),
                        Square.parse("e8") to Piece(Side.BLACK, PieceType.KING),
                    ),
                ),
            sideToMove = Side.WHITE,
            castlingRights = CastlingRights.NONE,
        )

    @Test
    fun holdsEverythingTheRulesNeed() {
        assertEquals(2, position.board.pieceCount)
        assertEquals(Side.WHITE, position.sideToMove)
        assertEquals(CastlingRights.NONE, position.castlingRights)
        assertNull(position.enPassantTarget)
        assertEquals(DrawRuleState(), position.drawRuleState)
        assertEquals(1, position.fullmoveNumber)
    }

    @Test
    fun isInProgressUntilAResultIsSet() {
        assertFalse(position.isOver)
        assertNull(position.result)

        val finished = position.copy(result = GameResult.checkmate(loser = Side.BLACK))

        assertTrue(finished.isOver)
        assertEquals(Side.WHITE, finished.result?.winner)
    }

    @Test
    fun exposesTheHalfmoveClockFromDrawRuleState() {
        assertEquals(0, position.halfmoveClock)
        assertEquals(12, position.copy(drawRuleState = DrawRuleState(halfmoveClock = 12)).halfmoveClock)
    }

    @Test
    fun carriesAnEnPassantTargetWhenOneExists() {
        val afterDoubleStep = position.copy(enPassantTarget = Square.parse("e3"))

        assertEquals(Square.parse("e3"), afterDoubleStep.enPassantTarget)
    }

    @Test
    fun copyingLeavesTheOriginalUnchanged() {
        position.copy(sideToMove = Side.BLACK)

        assertEquals(Side.WHITE, position.sideToMove)
    }

    @Test
    fun statesWithIdenticalContentAreEqual() {
        assertEquals(position, position.copy())
        assertEquals(position.hashCode(), position.copy().hashCode())
    }

    @Test
    fun rejectsAFullmoveNumberBelowOne() {
        assertFailsWith<IllegalArgumentException> { position.copy(fullmoveNumber = 0) }
    }
}
