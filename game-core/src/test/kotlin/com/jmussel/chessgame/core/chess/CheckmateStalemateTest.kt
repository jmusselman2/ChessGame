package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CheckmateStalemateTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
        )

    private fun play(vararg moves: Move): GameState {
        var position = StandardPosition.newGame()
        moves.forEach { position = ChessRules.applyMove(position, it) }
        return position
    }

    @Test
    fun backRankMateIsCheckmate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "g7" to black(PieceType.PAWN),
                "h7" to black(PieceType.PAWN),
                "a8" to white(PieceType.ROOK),
                "a1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertTrue(ChessRules.isCheckmate(position))
        assertFalse(ChessRules.isStalemate(position))
        assertEquals(GameResult.checkmate(loser = Side.BLACK), ChessRules.terminalResult(position))
    }

    @Test
    fun foolsMateEndsTheGameImmediately() {
        val position =
            play(
                Move.of("f2", "f3"),
                Move.of("e7", "e5"),
                Move.of("g2", "g4"),
                Move.of("d8", "h4"),
            )

        assertTrue(position.isOver)
        assertEquals(GameOutcome.BLACK_WINS, position.result?.outcome)
        assertEquals(TerminationReason.CHECKMATE, position.result?.reason)
        assertEquals(Side.BLACK, position.result?.winner)
    }

    @Test
    fun scholarsMateEndsTheGameImmediately() {
        val position =
            play(
                Move.of("e2", "e4"),
                Move.of("e7", "e5"),
                Move.of("f1", "c4"),
                Move.of("b8", "c6"),
                Move.of("d1", "h5"),
                Move.of("g8", "f6"),
                Move.of("h5", "f7"),
            )

        assertTrue(position.isOver)
        assertEquals(GameResult.checkmate(loser = Side.BLACK), position.result)
    }

    @Test
    fun smotheredMateIsCheckmate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "g8" to black(PieceType.ROOK),
                "g7" to black(PieceType.PAWN),
                "h7" to black(PieceType.PAWN),
                "f7" to white(PieceType.KNIGHT),
                "a1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertTrue(ChessRules.isCheckmate(position))
    }

    @Test
    fun aCheckTheKingCanEscapeIsNotCheckmate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "a8" to white(PieceType.ROOK),
                "a1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertTrue(ChessRules.isInCheck(position))
        assertFalse(ChessRules.isCheckmate(position))
        assertNull(ChessRules.terminalResult(position))
    }

    @Test
    fun aCheckThatCanBeBlockedIsNotCheckmate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "g7" to black(PieceType.PAWN),
                "h7" to black(PieceType.PAWN),
                "b7" to black(PieceType.ROOK),
                "a8" to white(PieceType.ROOK),
                "a1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertTrue(ChessRules.isInCheck(position))
        assertFalse(ChessRules.isCheckmate(position))
        assertTrue(ChessRules.isLegal(position, Move.of("b7", "b8")))
    }

    @Test
    fun aCheckingPieceThatCanBeCapturedIsNotCheckmate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "g7" to black(PieceType.PAWN),
                "h7" to black(PieceType.PAWN),
                "a7" to black(PieceType.ROOK),
                "a8" to white(PieceType.ROOK),
                "a1" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertFalse(ChessRules.isCheckmate(position))
        assertTrue(ChessRules.isLegal(position, Move.of("a7", "a8")))
    }

    @Test
    fun aKingWithNoMoveButNoCheckIsStalemate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "g6" to white(PieceType.QUEEN),
                "f7" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertFalse(ChessRules.isInCheck(position))
        assertTrue(ChessRules.isStalemate(position))
        assertFalse(ChessRules.isCheckmate(position))
        assertEquals(GameResult.draw(TerminationReason.STALEMATE), ChessRules.terminalResult(position))
    }

    @Test
    fun stalemateIsADrawWithNoWinner() {
        val position =
            state(
                "a8" to black(PieceType.KING),
                "c7" to white(PieceType.KING),
                "b6" to white(PieceType.BISHOP),
                "h1" to white(PieceType.ROOK),
                sideToMove = Side.BLACK,
            )
        val result = ChessRules.terminalResult(position)

        assertEquals(GameOutcome.DRAW, result?.outcome)
        assertNull(result?.winner)
    }

    @Test
    fun aBlockedSideThatStillHasOnePawnMoveIsNotStalemate() {
        val position =
            state(
                "h8" to black(PieceType.KING),
                "a7" to black(PieceType.PAWN),
                "g6" to white(PieceType.QUEEN),
                "f7" to white(PieceType.KING),
                sideToMove = Side.BLACK,
            )

        assertFalse(ChessRules.isStalemate(position))
        assertEquals(
            setOf(Move.of("a7", "a6"), Move.of("a7", "a5")),
            ChessRules.legalMoves(position).toSet(),
        )
    }

    @Test
    fun theStartingPositionIsNeitherMateNorStalemate() {
        val position = StandardPosition.newGame()

        assertFalse(ChessRules.isCheckmate(position))
        assertFalse(ChessRules.isStalemate(position))
        assertNull(ChessRules.terminalResult(position))
        assertFalse(position.isOver)
    }

    @Test
    fun aRecordedResultIsNotRecomputed() {
        val position =
            state(
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
                sideToMove = Side.WHITE,
            ).copy(result = GameResult.resignation(loser = Side.WHITE))

        assertEquals(GameResult.resignation(loser = Side.WHITE), ChessRules.terminalResult(position))
    }
}
