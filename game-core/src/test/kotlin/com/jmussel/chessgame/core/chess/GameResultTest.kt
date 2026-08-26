package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameResultTest {
    @Test
    fun checkmateIsWonByTheSideThatDeliveredIt() {
        val result = GameResult.checkmate(loser = Side.BLACK)

        assertEquals(GameOutcome.WHITE_WINS, result.outcome)
        assertEquals(TerminationReason.CHECKMATE, result.reason)
        assertEquals(Side.WHITE, result.winner)
    }

    @Test
    fun resignationIsWonByTheOpponent() {
        assertEquals(Side.BLACK, GameResult.resignation(loser = Side.WHITE).winner)
        assertEquals(TerminationReason.RESIGNATION, GameResult.resignation(loser = Side.WHITE).reason)
    }

    @Test
    fun drawsHaveNoWinner() {
        val result = GameResult.draw(TerminationReason.STALEMATE)

        assertEquals(GameOutcome.DRAW, result.outcome)
        assertNull(result.winner)
    }

    @Test
    fun claimableDrawsAreDistinguishedFromAutomaticOnes() {
        val claimable = TerminationReason.entries.filter { it.requiresClaim }

        assertEquals(
            listOf(TerminationReason.THREEFOLD_REPETITION_CLAIM, TerminationReason.FIFTY_MOVE_RULE_CLAIM),
            claimable,
        )
        assertTrue(claimable.all { it.isDraw })

        listOf(
            TerminationReason.STALEMATE,
            TerminationReason.INSUFFICIENT_MATERIAL,
            TerminationReason.FIVEFOLD_REPETITION,
            TerminationReason.SEVENTY_FIVE_MOVE_RULE,
        ).forEach {
            assertTrue(it.isDraw, "$it should be a draw")
            assertFalse(it.requiresClaim, "$it should not need a claim")
        }
    }

    @Test
    fun decisiveReasonsAreNotDraws() {
        assertFalse(TerminationReason.CHECKMATE.isDraw)
        assertFalse(TerminationReason.RESIGNATION.isDraw)
    }

    @Test
    fun rejectsAnOutcomeInconsistentWithItsReason() {
        assertFailsWith<IllegalArgumentException> {
            GameResult(GameOutcome.WHITE_WINS, TerminationReason.STALEMATE)
        }
        assertFailsWith<IllegalArgumentException> {
            GameResult(GameOutcome.DRAW, TerminationReason.CHECKMATE)
        }
    }
}
