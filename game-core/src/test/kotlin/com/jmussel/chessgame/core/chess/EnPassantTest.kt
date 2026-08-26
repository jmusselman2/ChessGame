package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EnPassantTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        enPassantTarget: String? = null,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
            enPassantTarget = enPassantTarget?.let { Square.parse(it) },
        )

    @Test
    fun aDoublePawnAdvanceCreatesTheTarget() {
        val position =
            state(
                "e2" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("e2", "e4"))

        assertEquals(Square.parse("e3"), after.enPassantTarget)
    }

    @Test
    fun aBlackDoubleAdvanceCreatesTheTargetBehindIt() {
        val position =
            state(
                "d7" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
                sideToMove = Side.BLACK,
            )
        val after = ChessRules.applyMove(position, Move.of("d7", "d5"))

        assertEquals(Square.parse("d6"), after.enPassantTarget)
    }

    @Test
    fun aSingleAdvanceOrAnyOtherMoveCreatesNoTarget() {
        val position =
            state(
                "e2" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
            )

        assertNull(ChessRules.applyMove(position, Move.of("e2", "e3")).enPassantTarget)
        assertNull(ChessRules.applyMove(position, Move.of("e1", "d1")).enPassantTarget)
    }

    @Test
    fun theTargetExpiresAfterTheNextMove() {
        val position =
            state(
                "e2" to white(PieceType.PAWN),
                "a7" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
            )
        val afterDoubleAdvance = ChessRules.applyMove(position, Move.of("e2", "e4"))
        val afterReply = ChessRules.applyMove(afterDoubleAdvance, Move.of("a7", "a6"))

        assertEquals(Square.parse("e3"), afterDoubleAdvance.enPassantTarget)
        assertNull(afterReply.enPassantTarget)
    }

    @Test
    fun theCaptureIsOfferedOnlyWhileTheTargetStands() {
        val position =
            state(
                "d4" to black(PieceType.PAWN),
                "e2" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )
        val afterDoubleAdvance = ChessRules.applyMove(position, Move.of("e2", "e4"))

        assertTrue(ChessRules.isLegal(afterDoubleAdvance, Move.of("d4", "e3")))

        val afterQuietMoves =
            ChessRules.applyMove(
                ChessRules.applyMove(afterDoubleAdvance, Move.of("h8", "h7")),
                Move.of("e1", "d1"),
            )

        assertFalse(
            ChessRules.isLegal(afterQuietMoves, Move.of("d4", "e3")),
            "the right to capture en passant expires after one move",
        )
    }

    @Test
    fun capturingEnPassantRemovesThePawnThatMovedPast() {
        val position =
            state(
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )
        val after = ChessRules.applyMove(position, Move.of("d5", "e6"))

        assertEquals(white(PieceType.PAWN), after.board.pieceAt(Square.parse("e6")))
        assertTrue(after.board.isEmpty(Square.parse("e5")))
        assertTrue(after.board.isEmpty(Square.parse("d5")))
    }

    @Test
    fun blackCapturesEnPassantDownTheBoard() {
        val position =
            state(
                "d4" to black(PieceType.PAWN),
                "e4" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                sideToMove = Side.BLACK,
                enPassantTarget = "e3",
            )
        val after = ChessRules.applyMove(position, Move.of("d4", "e3"))

        assertEquals(black(PieceType.PAWN), after.board.pieceAt(Square.parse("e3")))
        assertTrue(after.board.isEmpty(Square.parse("e4")))
    }

    @Test
    fun onlyAPawnBesideTheTargetMayCapture() {
        val position =
            state(
                "a5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertTrue(EnPassant.availableMoves(position).isEmpty())
    }

    @Test
    fun bothNeighbouringPawnsMayCapture() {
        val position =
            state(
                "d5" to white(PieceType.PAWN),
                "f5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertEquals(
            setOf("d5e6", "f5e6"),
            EnPassant.availableMoves(position).map { it.toString() }.toSet(),
        )
    }

    @Test
    fun theCaptureAppearsInTheLegalMoveList() {
        val position =
            state(
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertTrue(ChessRules.legalMoves(position).contains(Move.of("d5", "e6")))
    }

    @Test
    fun anEnPassantCaptureThatWouldExposeItsOwnKingIsIllegal() {
        val position =
            state(
                "e5" to white(PieceType.KING),
                "d5" to white(PieceType.PAWN),
                "f5" to black(PieceType.PAWN),
                "h5" to black(PieceType.ROOK),
                "a1" to black(PieceType.KING),
                enPassantTarget = "f6",
            )

        assertFalse(
            ChessRules.isLegal(position, Move.of("d5", "f6")),
            "removing both pawns from the fifth rank would expose the king to the rook",
        )
    }

    @Test
    fun aPinnedPawnMayNotCaptureEnPassant() {
        val position =
            state(
                "d1" to white(PieceType.KING),
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "d8" to black(PieceType.ROOK),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertFalse(
            ChessRules.isLegal(position, Move.of("d5", "e6")),
            "the pawn is pinned down the d file",
        )
        assertTrue(ChessRules.isLegal(position, Move.of("d5", "d6")))
    }

    @Test
    fun anEnPassantCaptureResetsTheHalfmoveClock() {
        val position =
            GameState(
                board =
                    Board.of(
                        mapOf(
                            Square.parse("d5") to white(PieceType.PAWN),
                            Square.parse("e5") to black(PieceType.PAWN),
                            Square.parse("e1") to white(PieceType.KING),
                            Square.parse("h8") to black(PieceType.KING),
                        ),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
                enPassantTarget = Square.parse("e6"),
                drawRuleState = DrawRuleState(halfmoveClock = 17),
            )

        assertEquals(0, ChessRules.applyMove(position, Move.of("d5", "e6")).halfmoveClock)
    }

    @Test
    fun recognisesTheCaptureAndTheCapturedSquare() {
        val position =
            state(
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                enPassantTarget = "e6",
            )

        assertTrue(EnPassant.isCapture(position, Move.of("d5", "e6")))
        assertFalse(EnPassant.isCapture(position, Move.of("d5", "d6")))
        assertFalse(EnPassant.isCapture(position.copy(enPassantTarget = null), Move.of("d5", "e6")))
        assertEquals(Square.parse("e5"), EnPassant.capturedPawnSquare(Move.of("d5", "e6")))
    }

    @Test
    fun anOrdinaryDiagonalCaptureIsNotEnPassant() {
        val position =
            state(
                "d5" to white(PieceType.PAWN),
                "e6" to black(PieceType.KNIGHT),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )
        val after = ChessRules.applyMove(position, Move.of("d5", "e6"))

        assertFalse(EnPassant.isCapture(position, Move.of("d5", "e6")))
        assertEquals(white(PieceType.PAWN), after.board.pieceAt(Square.parse("e6")))
        assertEquals(position.board.pieceCount - 1, after.board.pieceCount)
    }
}
