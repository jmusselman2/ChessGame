package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PawnMovesTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun destinations(
        board: Board,
        from: String,
    ): Set<String> = PseudoLegalMoves.pawnDestinations(board, Square.parse(from)).map { it.name }.toSet()

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    @Test
    fun pawnsAdvanceTowardsTheOpponent() {
        assertEquals(Direction.NORTH, PseudoLegalMoves.pawnAdvanceDirection(Side.WHITE))
        assertEquals(Direction.SOUTH, PseudoLegalMoves.pawnAdvanceDirection(Side.BLACK))
    }

    @Test
    fun advancesOneSquare() {
        assertEquals(setOf("e5"), destinations(board("e4" to white(PieceType.PAWN)), "e4"))
        assertEquals(setOf("e4"), destinations(board("e5" to black(PieceType.PAWN)), "e5"))
    }

    @Test
    fun advancesOneOrTwoSquaresFromItsStartingRank() {
        assertEquals(setOf("e3", "e4"), destinations(board("e2" to white(PieceType.PAWN)), "e2"))
        assertEquals(setOf("e6", "e5"), destinations(board("e7" to black(PieceType.PAWN)), "e7"))
    }

    @Test
    fun theTwoSquareAdvanceIsOnlyAvailableFromTheStartingRank() {
        assertFalse(destinations(board("e3" to white(PieceType.PAWN)), "e3").contains("e5"))
        assertFalse(destinations(board("e6" to black(PieceType.PAWN)), "e6").contains("e4"))
    }

    @Test
    fun aPieceDirectlyAheadBlocksAllAdvances() {
        val blockedByEnemy =
            board(
                "e2" to white(PieceType.PAWN),
                "e3" to black(PieceType.KNIGHT),
            )
        val blockedByFriend =
            board(
                "e2" to white(PieceType.PAWN),
                "e3" to white(PieceType.KNIGHT),
            )

        assertTrue(destinations(blockedByEnemy, "e2").isEmpty())
        assertTrue(destinations(blockedByFriend, "e2").isEmpty())
    }

    @Test
    fun aPieceTwoSquaresAheadBlocksOnlyTheDoubleAdvance() {
        val position =
            board(
                "e2" to white(PieceType.PAWN),
                "e4" to black(PieceType.KNIGHT),
            )

        assertEquals(setOf("e3"), destinations(position, "e2"))
    }

    @Test
    fun capturesDiagonallyForward() {
        val position =
            board(
                "e4" to white(PieceType.PAWN),
                "d5" to black(PieceType.KNIGHT),
                "f5" to black(PieceType.BISHOP),
            )

        assertEquals(setOf("e5", "d5", "f5"), destinations(position, "e4"))
    }

    @Test
    fun blackCapturesDiagonallyDownTheBoard() {
        val position =
            board(
                "e5" to black(PieceType.PAWN),
                "d4" to white(PieceType.KNIGHT),
                "f4" to white(PieceType.BISHOP),
            )

        assertEquals(setOf("e4", "d4", "f4"), destinations(position, "e5"))
    }

    @Test
    fun doesNotCaptureFriendlyPiecesOrEmptyDiagonals() {
        val position =
            board(
                "e4" to white(PieceType.PAWN),
                "d5" to white(PieceType.KNIGHT),
            )
        val reachable = destinations(position, "e4")

        assertFalse(reachable.contains("d5"))
        assertFalse(reachable.contains("f5"))
        assertEquals(setOf("e5"), reachable)
    }

    @Test
    fun doesNotCaptureStraightAhead() {
        val position =
            board(
                "e4" to white(PieceType.PAWN),
                "e5" to black(PieceType.KNIGHT),
            )

        assertTrue(destinations(position, "e4").isEmpty())
    }

    @Test
    fun neverMovesBackwardsOrSideways() {
        val reachable = destinations(board("e4" to white(PieceType.PAWN)), "e4")

        listOf("e3", "d4", "f4", "d3", "f3").forEach {
            assertFalse(reachable.contains(it), "$it should not be reachable")
        }
    }

    @Test
    fun aPawnOnTheAOrHFileHasOnlyOneCaptureDiagonal() {
        assertEquals(
            listOf("b5"),
            PseudoLegalMoves.pawnCaptureSquares(Square.parse("a4"), Side.WHITE).map { it.name },
        )
        assertEquals(
            listOf("g5"),
            PseudoLegalMoves.pawnCaptureSquares(Square.parse("h4"), Side.WHITE).map { it.name },
        )
        assertEquals(
            listOf("g3"),
            PseudoLegalMoves.pawnCaptureSquares(Square.parse("h4"), Side.BLACK).map { it.name },
        )
    }

    @Test
    fun captureSquaresIgnoreWhatStandsOnThem() {
        assertEquals(
            setOf("d5", "f5"),
            PseudoLegalMoves.pawnCaptureSquares(Square.parse("e4"), Side.WHITE).map { it.name }.toSet(),
        )
        assertEquals(
            setOf("d3", "f3"),
            PseudoLegalMoves.pawnCaptureSquares(Square.parse("e4"), Side.BLACK).map { it.name }.toSet(),
        )
    }

    @Test
    fun everyStartingPawnHasItsTwoOpeningAdvances() {
        (0 until Square.FILES).forEach { file ->
            val whitePawn = Square.of(file, 1)
            val blackPawn = Square.of(file, 6)

            assertEquals(
                setOf(Square.of(file, 2).name, Square.of(file, 3).name),
                destinations(StandardPosition.BOARD, whitePawn.name),
            )
            assertEquals(
                setOf(Square.of(file, 5).name, Square.of(file, 4).name),
                destinations(StandardPosition.BOARD, blackPawn.name),
            )
        }
    }

    @Test
    fun movesReportTheirOriginSquare() {
        val moves = PseudoLegalMoves.pawnMoves(board("e2" to white(PieceType.PAWN)), Square.parse("e2"))

        assertEquals(2, moves.size)
        assertTrue(moves.all { it.from == Square.parse("e2") })
        assertTrue(moves.contains(Move.of("e2", "e4")))
    }

    @Test
    fun rejectsAnEmptySquareOrANonPawn() {
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.pawnDestinations(Board.EMPTY, Square.parse("e2"))
        }
        assertFailsWith<IllegalArgumentException> {
            PseudoLegalMoves.pawnDestinations(board("e2" to white(PieceType.ROOK)), Square.parse("e2"))
        }
    }
}
