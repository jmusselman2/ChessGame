package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnightMovesTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun destinations(
        board: Board,
        from: String,
    ): Set<String> = PseudoLegalMoves.knightDestinations(board, Square.parse(from)).map { it.name }.toSet()

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    @Test
    fun centralKnightHasEightJumps() {
        assertEquals(
            setOf("e6", "f5", "f3", "e2", "c2", "b3", "b5", "c6"),
            destinations(board("d4" to white(PieceType.KNIGHT)), "d4"),
        )
    }

    @Test
    fun cornerKnightHasTwoJumps() {
        assertEquals(setOf("b3", "c2"), destinations(board("a1" to white(PieceType.KNIGHT)), "a1"))
        assertEquals(setOf("g6", "f7"), destinations(board("h8" to black(PieceType.KNIGHT)), "h8"))
    }

    @Test
    fun edgeKnightHasFourJumps() {
        assertEquals(
            setOf("b1", "b5", "c2", "c4"),
            destinations(board("a3" to white(PieceType.KNIGHT)), "a3"),
        )
    }

    @Test
    fun knightNeverLandsOnAdjacentOrStraightSquares() {
        val reachable = destinations(board("d4" to white(PieceType.KNIGHT)), "d4")

        listOf("d5", "d3", "c4", "e4", "c5", "e5", "c3", "e3", "d6", "d2", "b4", "f4").forEach {
            assertFalse(reachable.contains(it), "$it should not be reachable")
        }
    }

    @Test
    fun knightJumpsOverEveryPieceInBetween() {
        val position =
            board(
                "d4" to white(PieceType.KNIGHT),
                "d5" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
                "e4" to white(PieceType.PAWN),
                "d3" to black(PieceType.PAWN),
                "c3" to white(PieceType.PAWN),
                "c4" to black(PieceType.PAWN),
                "c5" to white(PieceType.PAWN),
                "e3" to black(PieceType.PAWN),
            )

        assertEquals(
            setOf("e6", "f5", "f3", "e2", "c2", "b3", "b5", "c6"),
            destinations(position, "d4"),
        )
    }

    @Test
    fun ownPieceOnADestinationRemovesIt() {
        val position =
            board(
                "d4" to white(PieceType.KNIGHT),
                "f5" to white(PieceType.ROOK),
            )
        val reachable = destinations(position, "d4")

        assertFalse(reachable.contains("f5"))
        assertEquals(7, reachable.size)
    }

    @Test
    fun enemyPieceOnADestinationIsACapture() {
        val position =
            board(
                "d4" to white(PieceType.KNIGHT),
                "f5" to black(PieceType.ROOK),
            )

        assertTrue(destinations(position, "d4").contains("f5"))
        assertEquals(8, destinations(position, "d4").size)
    }

    @Test
    fun aFullySurroundedKnightStillJumpsOut() {
        val position =
            board(
                "b1" to white(PieceType.KNIGHT),
                "a1" to white(PieceType.ROOK),
                "c1" to white(PieceType.BISHOP),
                "a2" to white(PieceType.PAWN),
                "b2" to white(PieceType.PAWN),
                "c2" to white(PieceType.PAWN),
            )

        assertEquals(setOf("a3", "c3", "d2"), destinations(position, "b1"))
    }

    @Test
    fun startingKnightsHaveTheirTwoOpeningMoves() {
        val start = StandardPosition.BOARD

        assertEquals(setOf("a3", "c3"), destinations(start, "b1"))
        assertEquals(setOf("f3", "h3"), destinations(start, "g1"))
        assertEquals(setOf("a6", "c6"), destinations(start, "b8"))
        assertEquals(setOf("f6", "h6"), destinations(start, "g8"))
    }

    @Test
    fun movesReportTheirOriginSquare() {
        val moves = PseudoLegalMoves.knightMoves(board("d4" to white(PieceType.KNIGHT)), Square.parse("d4"))

        assertEquals(8, moves.size)
        assertTrue(moves.all { it.from == Square.parse("d4") && it.promotion == null })
        assertTrue(moves.contains(Move.of("d4", "e6")))
    }

    @Test
    fun rejectsAnEmptySquareOrANonKnight() {
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.knightDestinations(Board.EMPTY, Square.parse("d4"))
        }
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.knightDestinations(board("d4" to white(PieceType.QUEEN)), Square.parse("d4"))
        }
    }

    @Test
    fun hasEightDistinctSteps() {
        assertEquals(8, PseudoLegalMoves.KNIGHT_STEPS.size)
        assertEquals(8, PseudoLegalMoves.KNIGHT_STEPS.toSet().size)
        assertTrue(
            PseudoLegalMoves.KNIGHT_STEPS.all {
                setOf(kotlin.math.abs(it.fileStep), kotlin.math.abs(it.rankStep)) == setOf(1, 2)
            },
        )
    }
}
