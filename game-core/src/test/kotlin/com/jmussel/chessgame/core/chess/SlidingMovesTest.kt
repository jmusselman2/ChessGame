package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SlidingMovesTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun destinations(
        board: Board,
        from: String,
    ): Set<String> = PseudoLegalMoves.slidingDestinations(board, Square.parse(from)).map { it.name }.toSet()

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    @Test
    fun rookSweepsItsFileAndRank() {
        val reachable = destinations(board("d4" to white(PieceType.ROOK)), "d4")

        assertEquals(14, reachable.size)
        assertEquals(
            setOf("d1", "d2", "d3", "d5", "d6", "d7", "d8", "a4", "b4", "c4", "e4", "f4", "g4", "h4"),
            reachable,
        )
    }

    @Test
    fun rookInACornerStillReachesFourteenSquares() {
        assertEquals(14, destinations(board("a1" to white(PieceType.ROOK)), "a1").size)
    }

    @Test
    fun rookNeverMovesDiagonally() {
        val reachable = destinations(board("d4" to white(PieceType.ROOK)), "d4")

        assertFalse(reachable.contains("e5"))
        assertFalse(reachable.contains("c3"))
    }

    @Test
    fun bishopSweepsItsDiagonals() {
        val reachable = destinations(board("d4" to white(PieceType.BISHOP)), "d4")

        assertEquals(13, reachable.size)
        assertEquals(
            setOf("c3", "b2", "a1", "e5", "f6", "g7", "h8", "c5", "b6", "a7", "e3", "f2", "g1"),
            reachable,
        )
    }

    @Test
    fun bishopStaysOnDarkSquaresFromADarkSquare() {
        val reachable = destinations(board("c1" to white(PieceType.BISHOP)), "c1")

        assertEquals(7, reachable.size)
        assertTrue(reachable.all { Square.parse(it).let { square -> (square.file + square.rank) % 2 == 0 } })
    }

    @Test
    fun queenCombinesRookAndBishopGeometry() {
        val reachable = destinations(board("d4" to white(PieceType.QUEEN)), "d4")

        assertEquals(27, reachable.size)
        assertEquals(
            destinations(board("d4" to white(PieceType.ROOK)), "d4") +
                destinations(board("d4" to white(PieceType.BISHOP)), "d4"),
            reachable,
        )
    }

    @Test
    fun friendlyPieceBlocksTheRayAndIsNotADestination() {
        val position =
            board(
                "d4" to white(PieceType.ROOK),
                "d6" to white(PieceType.PAWN),
            )
        val reachable = destinations(position, "d4")

        assertTrue(reachable.contains("d5"))
        assertFalse(reachable.contains("d6"))
        assertFalse(reachable.contains("d7"))
        assertFalse(reachable.contains("d8"))
    }

    @Test
    fun enemyPieceIsCapturableAndBlocksWhatIsBehindIt() {
        val position =
            board(
                "d4" to white(PieceType.ROOK),
                "d6" to black(PieceType.PAWN),
            )
        val reachable = destinations(position, "d4")

        assertTrue(reachable.contains("d5"))
        assertTrue(reachable.contains("d6"))
        assertFalse(reachable.contains("d7"))
        assertFalse(reachable.contains("d8"))
    }

    @Test
    fun anAdjacentFriendlyPieceRemovesTheDirectionEntirely() {
        val position =
            board(
                "d4" to white(PieceType.BISHOP),
                "e5" to white(PieceType.KNIGHT),
            )
        val reachable = destinations(position, "d4")

        assertFalse(reachable.contains("e5"))
        assertFalse(reachable.contains("f6"))
        assertTrue(reachable.contains("c5"))
    }

    @Test
    fun aBoxedInQueenHasOnlyCaptures() {
        val position =
            board(
                "d4" to white(PieceType.QUEEN),
                "c3" to black(PieceType.PAWN),
                "c4" to black(PieceType.PAWN),
                "c5" to black(PieceType.PAWN),
                "d3" to white(PieceType.PAWN),
                "d5" to white(PieceType.PAWN),
                "e3" to white(PieceType.PAWN),
                "e4" to white(PieceType.PAWN),
                "e5" to black(PieceType.PAWN),
            )

        assertEquals(setOf("c3", "c4", "c5", "e5"), destinations(position, "d4"))
    }

    @Test
    fun blackSlidersUseTheSameGeometry() {
        val position =
            board(
                "d4" to black(PieceType.ROOK),
                "d6" to white(PieceType.PAWN),
                "d2" to black(PieceType.PAWN),
            )
        val reachable = destinations(position, "d4")

        assertTrue(reachable.contains("d6"))
        assertTrue(reachable.contains("d3"))
        assertFalse(reachable.contains("d2"))
        assertFalse(reachable.contains("d7"))
    }

    @Test
    fun movesReportTheirOriginSquare() {
        val position = board("d4" to white(PieceType.ROOK))
        val moves = PseudoLegalMoves.slidingMoves(position, Square.parse("d4"))

        assertEquals(14, moves.size)
        assertTrue(moves.all { it.from == Square.parse("d4") && it.promotion == null })
        assertTrue(moves.contains(Move.of("d4", "d8")))
    }

    @Test
    fun onlyRookBishopAndQueenSlide() {
        assertEquals(Direction.ORTHOGONAL, PseudoLegalMoves.slidingDirectionsFor(PieceType.ROOK))
        assertEquals(Direction.DIAGONAL, PseudoLegalMoves.slidingDirectionsFor(PieceType.BISHOP))
        assertEquals(Direction.ALL, PseudoLegalMoves.slidingDirectionsFor(PieceType.QUEEN))

        listOf(PieceType.PAWN, PieceType.KNIGHT, PieceType.KING).forEach {
            assertEquals(null, PseudoLegalMoves.slidingDirectionsFor(it), "$it should not slide")
        }
    }

    @Test
    fun rejectsAnEmptySquareOrANonSlidingPiece() {
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.slidingDestinations(Board.EMPTY, Square.parse("d4"))
        }
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.slidingDestinations(board("d4" to white(PieceType.KNIGHT)), Square.parse("d4"))
        }
    }

    @Test
    fun startingRooksAndBishopsAreCompletelyBlocked() {
        val start = StandardPosition.BOARD

        listOf("a1", "h1", "c1", "f1", "a8", "h8", "c8", "f8").forEach {
            assertTrue(destinations(start, it).isEmpty(), "$it should have no sliding moves")
        }
        assertTrue(destinations(start, "d1").isEmpty())
        assertTrue(destinations(start, "d8").isEmpty())
    }
}
