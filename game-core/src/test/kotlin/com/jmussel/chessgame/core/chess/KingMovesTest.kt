package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KingMovesTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun destinations(
        board: Board,
        from: String,
    ): Set<String> = PseudoLegalMoves.kingDestinations(board, Square.parse(from)).map { it.name }.toSet()

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    @Test
    fun centralKingStepsToEightAdjacentSquares() {
        assertEquals(
            setOf("c3", "c4", "c5", "d3", "d5", "e3", "e4", "e5"),
            destinations(board("d4" to white(PieceType.KING)), "d4"),
        )
    }

    @Test
    fun cornerKingHasThreeSteps() {
        assertEquals(setOf("a2", "b1", "b2"), destinations(board("a1" to white(PieceType.KING)), "a1"))
        assertEquals(setOf("g8", "g7", "h7"), destinations(board("h8" to black(PieceType.KING)), "h8"))
    }

    @Test
    fun edgeKingHasFiveSteps() {
        assertEquals(
            setOf("d1", "d2", "e2", "f1", "f2"),
            destinations(board("e1" to white(PieceType.KING)), "e1"),
        )
    }

    @Test
    fun kingNeverMovesMoreThanOneSquare() {
        val reachable = destinations(board("d4" to white(PieceType.KING)), "d4")

        listOf("d6", "d2", "b4", "f4", "f6", "b2", "a1", "h8").forEach {
            assertFalse(reachable.contains(it), "$it should not be reachable")
        }
    }

    @Test
    fun friendlyPieceRemovesADestination() {
        val position =
            board(
                "d4" to white(PieceType.KING),
                "d5" to white(PieceType.PAWN),
            )
        val reachable = destinations(position, "d4")

        assertFalse(reachable.contains("d5"))
        assertEquals(7, reachable.size)
    }

    @Test
    fun enemyPieceOnAnAdjacentSquareIsACapture() {
        val position =
            board(
                "d4" to white(PieceType.KING),
                "d5" to black(PieceType.PAWN),
            )

        assertTrue(destinations(position, "d4").contains("d5"))
        assertEquals(8, destinations(position, "d4").size)
    }

    @Test
    fun aKingBoxedInByItsOwnPiecesHasNowhereToGo() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "d1" to white(PieceType.QUEEN),
                "f1" to white(PieceType.BISHOP),
                "d2" to white(PieceType.PAWN),
                "e2" to white(PieceType.PAWN),
                "f2" to white(PieceType.PAWN),
            )

        assertTrue(destinations(position, "e1").isEmpty())
    }

    @Test
    fun startingKingsAreBlockedByTheirOwnPieces() {
        assertTrue(destinations(StandardPosition.BOARD, "e1").isEmpty())
        assertTrue(destinations(StandardPosition.BOARD, "e8").isEmpty())
    }

    @Test
    fun plainKingMovementIgnoresAttackedSquares() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "d8" to black(PieceType.ROOK),
            )

        assertTrue(
            destinations(position, "e1").contains("d1"),
            "attacked squares are filtered by a later rule, not by king geometry",
        )
    }

    @Test
    fun movesReportTheirOriginSquare() {
        val moves = PseudoLegalMoves.kingMoves(board("d4" to white(PieceType.KING)), Square.parse("d4"))

        assertEquals(8, moves.size)
        assertTrue(moves.all { it.from == Square.parse("d4") && it.promotion == null })
        assertTrue(moves.contains(Move.of("d4", "e5")))
    }

    @Test
    fun rejectsAnEmptySquareOrANonKing() {
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.kingDestinations(Board.EMPTY, Square.parse("e1"))
        }
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.kingDestinations(board("e1" to white(PieceType.QUEEN)), Square.parse("e1"))
        }
    }
}
