package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M4AdversarialTest {
    @Test
    fun everyTerminalReasonLocksTheGuardedUndoAction() {
        val played = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

        TerminationReason.entries.forEach { reason ->
            val result = if (reason.isDraw) GameResult.draw(reason) else GameResult.win(Side.WHITE, reason)
            val finished = played.copy(state = played.state.copy(result = result))

            assertNull(ChessRules.undoableSide(finished), reason.name)
            Side.entries.forEach { side ->
                assertFalse(ChessRules.canUndo(finished, side), "$reason for $side")
                assertFailsWith<IllegalArgumentException>("$reason for $side") {
                    ChessRules.undo(finished, side)
                }
            }
            assertEquals(listOf(Move.of("e2", "e4")), finished.moves, "$reason history remains readable")
        }
    }

    @Test
    fun aProspectiveDrawClaimIsFinalAndDoesNotCreateAnUndoableMove() {
        val position =
            ChessGame(
                GameState(
                    board =
                        Board.of(
                            mapOf(
                                Square.parse("a1") to Piece(Side.WHITE, PieceType.KING),
                                Square.parse("d1") to Piece(Side.WHITE, PieceType.ROOK),
                                Square.parse("h8") to Piece(Side.BLACK, PieceType.KING),
                                Square.parse("e8") to Piece(Side.BLACK, PieceType.ROOK),
                            ),
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                    drawRuleState = DrawRuleState(halfmoveClock = 99),
                ),
            )
        val declaredMove = Move.of("d1", "d2")
        val claimed = ChessRules.claimDraw(position, DrawClaim.FIFTY_MOVE_RULE, declaredMove)

        assertEquals(position.history, claimed.history)
        assertEquals(position.state.board, claimed.state.board)
        assertEquals(99, claimed.state.halfmoveClock)
        assertNull(ChessRules.undoableSide(claimed))
        Side.entries.forEach { side -> assertFalse(ChessRules.canUndo(claimed, side)) }
    }

    @Test
    fun undoingAndReplayingAThirdOccurrenceDoesNotCreateAPhantomFourthOccurrence() {
        var position = ChessGame.newGame()
        listOf(
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
            Move.of("f6", "g8"),
            Move.of("g1", "f3"),
            Move.of("g8", "f6"),
            Move.of("f3", "g1"),
        ).forEach { move -> position = ChessRules.applyMove(position, move) }
        val returnToStart = Move.of("f6", "g8")

        val third = ChessRules.applyMove(position, returnToStart)
        assertEquals(3, Repetition.occurrences(third.state))
        assertTrue(ChessRules.canClaimDraw(third.state, DrawClaim.THREEFOLD_REPETITION))

        val restored = ChessRules.undo(third, Side.BLACK)
        assertEquals(position, restored)
        assertFalse(ChessRules.canClaimDraw(restored.state, DrawClaim.THREEFOLD_REPETITION))

        val replayed = ChessRules.applyMove(restored, returnToStart)
        assertEquals(3, Repetition.occurrences(replayed.state))
        assertEquals(third, replayed)
    }
}
