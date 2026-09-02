package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class M3AdversarialTest {
    @Test
    fun standardPositionMatchesKnownPerftThroughDepthFour() {
        val start = StandardPosition.newGame()

        assertEquals(20, perft(start, 1))
        assertEquals(400, perft(start, 2))
        assertEquals(8_902, perft(start, 3))
        assertEquals(197_281, perft(start, 4))
    }

    @Test
    fun aFinishedGameHasNoLegalMoves() {
        val finished =
            StandardPosition.newGame().copy(
                result = GameResult.draw(TerminationReason.STALEMATE),
            )

        assertTrue(ChessRules.legalMoves(finished).isEmpty())
    }

    @Test
    fun noMoveIsLegalAfterTheGameIsFinished() {
        val finished =
            StandardPosition.newGame().copy(
                result = GameResult.draw(TerminationReason.STALEMATE),
            )

        assertFalse(ChessRules.isLegal(finished, Move.of("e2", "e4")))
    }

    @Test
    fun theChessGameQueryOverloadsAlsoStopAtAFinishedGame() {
        val finished =
            ChessGame(
                StandardPosition.newGame().copy(
                    result = GameResult.draw(TerminationReason.STALEMATE),
                ),
            )

        assertTrue(ChessRules.legalMoves(finished).isEmpty())
        assertFalse(ChessRules.isLegal(finished, Move.of("e2", "e4")))
    }

    @Test
    fun aRealCheckmateEmptiesTheLegalMoveList() {
        var mated = StandardPosition.newGame()
        listOf(
            Move.of("f2", "f3"),
            Move.of("e7", "e5"),
            Move.of("g2", "g4"),
            Move.of("d8", "h4"),
        ).forEach { mated = ChessRules.applyMove(mated, it) }

        assertTrue(mated.isOver)
        assertEquals(TerminationReason.CHECKMATE, mated.result?.reason)
        assertTrue(ChessRules.legalMoves(mated).isEmpty())
        assertFalse(ChessRules.isLegal(mated, Move.of("e1", "f2")))
    }

    @Test
    fun enPassantTargetWithoutBypassedPawnOffersNoCapture() {
        val state = enPassantState()

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetBesideFriendlyPawnOffersNoCapture() {
        val state = enPassantState("e5" to Piece(Side.WHITE, PieceType.PAWN))

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetBesideNonPawnOffersNoCapture() {
        val state = enPassantState("e5" to Piece(Side.BLACK, PieceType.KNIGHT))

        assertNoEnPassant(state, Move.of("d5", "e6"))
    }

    @Test
    fun enPassantTargetOnImpossibleRankOffersNoCapture() {
        val state =
            state(
                "d4" to Piece(Side.WHITE, PieceType.PAWN),
                "e4" to Piece(Side.BLACK, PieceType.PAWN),
                enPassantTarget = "e5",
            )

        assertNoEnPassant(state, Move.of("d4", "e5"))
    }

    @Test
    fun occupiedEnPassantTargetIsOnlyAnOrdinaryCapture() {
        val state =
            state(
                "d5" to Piece(Side.WHITE, PieceType.PAWN),
                "e5" to Piece(Side.BLACK, PieceType.PAWN),
                "e6" to Piece(Side.BLACK, PieceType.KNIGHT),
                enPassantTarget = "e6",
            )
        val move = Move.of("d5", "e6")

        assertFalse(EnPassant.isCapture(state, move))
        assertTrue(ChessRules.isLegal(state, move))

        val after = ChessRules.applyMove(state, move)
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), after.board.pieceAt(Square.parse("e6")))
        assertEquals(Piece(Side.BLACK, PieceType.PAWN), after.board.pieceAt(Square.parse("e5")))
    }

    @Test
    fun enPassantCaptureRecognitionRequiresPawnCaptureGeometry() {
        val state =
            state(
                "a2" to Piece(Side.WHITE, PieceType.PAWN),
                "e5" to Piece(Side.BLACK, PieceType.PAWN),
                enPassantTarget = "e6",
            )

        assertFalse(EnPassant.isCapture(state, Move.of("a2", "e6")))
    }

    private fun enPassantState(vararg capturedSquare: Pair<String, Piece>): GameState =
        state(
            "d5" to Piece(Side.WHITE, PieceType.PAWN),
            *capturedSquare,
            enPassantTarget = "e6",
        )

    private fun state(
        vararg placement: Pair<String, Piece>,
        enPassantTarget: String,
    ): GameState =
        GameState(
            board =
                Board.of(
                    buildMap {
                        put(Square.parse("e1"), Piece(Side.WHITE, PieceType.KING))
                        put(Square.parse("h8"), Piece(Side.BLACK, PieceType.KING))
                        placement.forEach { (square, piece) -> put(Square.parse(square), piece) }
                    },
                ),
            sideToMove = Side.WHITE,
            castlingRights = CastlingRights.NONE,
            enPassantTarget = Square.parse(enPassantTarget),
        )

    private fun assertNoEnPassant(
        state: GameState,
        move: Move,
    ) {
        assertFalse(EnPassant.isCapture(state, move))
        assertFalse(move in EnPassant.availableMoves(state))
        assertFalse(ChessRules.isLegal(state, move))
    }

    private fun perft(
        state: GameState,
        depth: Int,
    ): Int {
        if (depth == 0) return 1
        return ChessRules.legalMoves(state).sumOf { move -> perft(ChessRules.applyMove(state, move), depth - 1) }
    }
}
