package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TerminalUndoLockTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun game(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        halfmoveClock: Int = 0,
    ): ChessGame =
        ChessGame(
            GameState(
                board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
                sideToMove = sideToMove,
                castlingRights = CastlingRights.NONE,
                drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
            ),
        )

    private fun assertLocked(finished: ChessGame) {
        assertTrue(finished.isOver, "the game should be over")
        assertNull(ChessRules.undoableSide(finished))
        Side.entries.forEach { side ->
            assertFalse(ChessRules.canUndo(finished, side), "$side should not be able to undo")
            assertFailsWith<IllegalArgumentException> { ChessRules.undo(finished, side) }
        }
    }

    @Test
    fun aCheckmatingMoveIsFinal() {
        val mateInOne =
            game(
                "a1" to white(PieceType.KING),
                "b7" to white(PieceType.ROOK),
                "c6" to white(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )
        val finished = ChessRules.applyMove(mateInOne, Move.of("c6", "c8"))

        assertEquals(TerminationReason.CHECKMATE, finished.result?.reason)
        assertLocked(finished)
    }

    @Test
    fun aStalematingMoveIsFinal() {
        val stalemateInOne =
            game(
                "f7" to white(PieceType.KING),
                "g1" to white(PieceType.QUEEN),
                "h8" to black(PieceType.KING),
            )
        val finished = ChessRules.applyMove(stalemateInOne, Move.of("g1", "g6"))

        assertEquals(TerminationReason.STALEMATE, finished.result?.reason)
        assertLocked(finished)
    }

    @Test
    fun theCaptureThatLeavesInsufficientMaterialIsFinal() {
        val position =
            game(
                "e1" to white(PieceType.KING),
                "d2" to white(PieceType.KNIGHT),
                "e8" to black(PieceType.KING),
                "c4" to black(PieceType.KNIGHT),
            )
        val finished = ChessRules.applyMove(position, Move.of("d2", "c4"))

        assertEquals(TerminationReason.INSUFFICIENT_MATERIAL, finished.result?.reason)
        assertLocked(finished)
    }

    @Test
    fun theFivefoldRepetitionMoveIsFinal() {
        var position = ChessGame.newGame()
        repeat(4) {
            listOf(
                Move.of("g1", "f3"),
                Move.of("g8", "f6"),
                Move.of("f3", "g1"),
                Move.of("f6", "g8"),
            ).forEach { position = ChessRules.applyMove(position, it) }
        }

        assertEquals(TerminationReason.FIVEFOLD_REPETITION, position.result?.reason)
        assertLocked(position)
    }

    @Test
    fun theSeventyFifthMoveIsFinal() {
        val position =
            game(
                "a1" to white(PieceType.KING),
                "d1" to white(PieceType.ROOK),
                "h8" to black(PieceType.KING),
                "e8" to black(PieceType.ROOK),
                halfmoveClock = 149,
            )
        val finished = ChessRules.applyMove(position, Move.of("d1", "d2"))

        assertEquals(TerminationReason.SEVENTY_FIVE_MOVE_RULE, finished.result?.reason)
        assertLocked(finished)
    }

    @Test
    fun aClaimedDrawIsFinal() {
        var position = ChessGame.newGame()
        repeat(2) {
            listOf(
                Move.of("g1", "f3"),
                Move.of("g8", "f6"),
                Move.of("f3", "g1"),
                Move.of("f6", "g8"),
            ).forEach { position = ChessRules.applyMove(position, it) }
        }

        assertTrue(ChessRules.canUndo(position, Side.BLACK), "before the claim the last move is undoable")

        val claimed = ChessRules.claimDraw(position, DrawClaim.THREEFOLD_REPETITION)

        assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, claimed.result?.reason)
        assertLocked(claimed)
    }

    @Test
    fun aResignationIsFinal() {
        val position = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))
        val resigned = position.copy(state = position.state.copy(result = GameResult.resignation(loser = Side.WHITE)))

        assertLocked(resigned)
    }

    @Test
    fun aNonFinalMoveIsStillUndoableInTheSameGame() {
        val mateInOne =
            game(
                "a1" to white(PieceType.KING),
                "b7" to white(PieceType.ROOK),
                "c6" to white(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )
        val beforeMate = ChessRules.applyMove(mateInOne, Move.of("b7", "b6"))
        val afterBlackMoves = ChessRules.applyMove(beforeMate, Move.of("h8", "h7"))
        val finished = ChessRules.applyMove(afterBlackMoves, Move.of("b6", "b7"))

        assertFalse(finished.isOver)
        assertTrue(ChessRules.canUndo(finished, Side.WHITE))
    }

    @Test
    fun anUnfinishedGameIsStillUndoable() {
        val position = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))

        assertFalse(position.isOver)
        assertEquals(Side.WHITE, ChessRules.undoableSide(position))
    }

    @Test
    fun theHistoryOfAFinishedGameIsStillReadable() {
        val position = ChessRules.applyMove(ChessGame.newGame(), Move.of("e2", "e4"))
        val resigned = position.copy(state = position.state.copy(result = GameResult.resignation(loser = Side.BLACK)))

        assertEquals(listOf(Move.of("e2", "e4")), resigned.moves)
        assertEquals(Side.WHITE, resigned.lastMover)
    }
}
