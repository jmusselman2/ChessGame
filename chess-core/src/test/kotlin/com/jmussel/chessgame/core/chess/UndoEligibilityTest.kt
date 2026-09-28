package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UndoEligibilityTest {
    private val fresh = ChessGame.newGame()
    private val afterWhitesFirstMove = ChessRules.applyMove(fresh, Move.of("e2", "e4"))
    private val afterBlacksReply = ChessRules.applyMove(afterWhitesFirstMove, Move.of("e7", "e5"))

    @Test
    fun nobodyMayUndoBeforeAnyMoveIsPlayed() {
        assertNull(ChessRules.undoableSide(fresh))
        assertFalse(ChessRules.canUndo(fresh, Side.WHITE))
        assertFalse(ChessRules.canUndo(fresh, Side.BLACK))
    }

    @Test
    fun aPlayerMayUndoTheirOwnLatestUnansweredMove() {
        assertEquals(Side.WHITE, ChessRules.undoableSide(afterWhitesFirstMove))
        assertTrue(ChessRules.canUndo(afterWhitesFirstMove, Side.WHITE))

        val restored = ChessRules.undo(afterWhitesFirstMove, Side.WHITE)

        assertEquals(fresh, restored)
    }

    @Test
    fun theOpponentMayNotUndoAMoveTheyDidNotMake() {
        assertFalse(ChessRules.canUndo(afterWhitesFirstMove, Side.BLACK))
        assertFailsWith<IllegalArgumentException> {
            ChessRules.undo(afterWhitesFirstMove, Side.BLACK)
        }
    }

    @Test
    fun aMoveIsLockedOnceTheOpponentReplies() {
        assertFalse(
            ChessRules.canUndo(afterBlacksReply, Side.WHITE),
            "White's move has been answered",
        )
        assertFailsWith<IllegalArgumentException> { ChessRules.undo(afterBlacksReply, Side.WHITE) }
    }

    @Test
    fun theOpponentMayUndoTheirOwnReply() {
        assertEquals(Side.BLACK, ChessRules.undoableSide(afterBlacksReply))
        assertTrue(ChessRules.canUndo(afterBlacksReply, Side.BLACK))

        val restored = ChessRules.undo(afterBlacksReply, Side.BLACK)

        assertEquals(afterWhitesFirstMove, restored)
    }

    @Test
    fun undoingTheReplyMakesThePriorMoveUndoableAgain() {
        val afterBlackTakesItBack = ChessRules.undo(afterBlacksReply, Side.BLACK)

        assertTrue(ChessRules.canUndo(afterBlackTakesItBack, Side.WHITE))
        assertFalse(ChessRules.canUndo(afterBlackTakesItBack, Side.BLACK))
        assertEquals(fresh, ChessRules.undo(afterBlackTakesItBack, Side.WHITE))
    }

    @Test
    fun undoingHandsTheTurnBackToThePlayerWhoTookItBack() {
        val restored = ChessRules.undo(afterWhitesFirstMove, Side.WHITE)

        assertEquals(Side.WHITE, restored.sideToMove)
        assertEquals(Side.BLACK, afterWhitesFirstMove.sideToMove)
    }

    @Test
    fun aPlayerMayPlayADifferentMoveAfterTakingOneBack() {
        val restored = ChessRules.undo(afterWhitesFirstMove, Side.WHITE)
        val different = ChessRules.applyMove(restored, Move.of("d2", "d4"))

        assertEquals(Move.of("d2", "d4"), different.lastMove)
        assertEquals(1, different.history.size)
    }

    @Test
    fun onlyOneMoveMayBeTakenBackAtATime() {
        val restored = ChessRules.undo(afterBlacksReply, Side.BLACK)

        assertEquals(1, restored.history.size)
        assertFalse(ChessRules.canUndo(restored, Side.BLACK))
    }

    @Test
    fun eachSideMayUndoInTurnAllTheWayBack() {
        var position = afterBlacksReply

        assertTrue(ChessRules.canUndo(position, Side.BLACK))
        position = ChessRules.undo(position, Side.BLACK)

        assertTrue(ChessRules.canUndo(position, Side.WHITE))
        position = ChessRules.undo(position, Side.WHITE)

        assertEquals(fresh, position)
        assertNull(ChessRules.undoableSide(position))
    }

    @Test
    fun aLongerGameLocksEverythingButTheLatestMove() {
        var position = ChessGame.newGame()
        listOf(
            Move.of("e2", "e4"),
            Move.of("e7", "e5"),
            Move.of("g1", "f3"),
            Move.of("b8", "c6"),
        ).forEach { position = ChessRules.applyMove(position, it) }

        assertEquals(Side.BLACK, ChessRules.undoableSide(position))
        assertFalse(ChessRules.canUndo(position, Side.WHITE))
        assertEquals(4, position.history.size)
    }
}
