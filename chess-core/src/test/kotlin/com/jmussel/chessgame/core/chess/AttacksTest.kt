package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AttacksTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun attacksFrom(
        board: Board,
        from: String,
    ): Set<String> = Attacks.attackedSquaresFrom(board, Square.parse(from)).map { it.name }.toSet()

    private fun attacked(
        board: Board,
        square: String,
        side: Side,
    ): Boolean = Attacks.isAttacked(board, Square.parse(square), side)

    @Test
    fun pawnsAttackOnlyTheirCaptureDiagonals() {
        val position = board("e4" to white(PieceType.PAWN))

        assertEquals(setOf("d5", "f5"), attacksFrom(position, "e4"))
        assertFalse(attacked(position, "e5", Side.WHITE))
        assertFalse(attacked(position, "e6", Side.WHITE))
    }

    @Test
    fun blackPawnsAttackDownTheBoard() {
        assertEquals(setOf("d4", "f4"), attacksFrom(board("e5" to black(PieceType.PAWN)), "e5"))
    }

    @Test
    fun knightsAttackTheirEightJumps() {
        assertEquals(
            setOf("e6", "f5", "f3", "e2", "c2", "b3", "b5", "c6"),
            attacksFrom(board("d4" to white(PieceType.KNIGHT)), "d4"),
        )
    }

    @Test
    fun kingsAttackAdjacentSquares() {
        assertEquals(
            setOf("c3", "c4", "c5", "d3", "d5", "e3", "e4", "e5"),
            attacksFrom(board("d4" to white(PieceType.KING)), "d4"),
        )
    }

    @Test
    fun slidersAttackAlongTheirRays() {
        assertEquals(14, attacksFrom(board("d4" to white(PieceType.ROOK)), "d4").size)
        assertEquals(13, attacksFrom(board("d4" to white(PieceType.BISHOP)), "d4").size)
        assertEquals(27, attacksFrom(board("d4" to white(PieceType.QUEEN)), "d4").size)
    }

    @Test
    fun slidersAreBlockedButStillAttackTheBlockingSquare() {
        val position =
            board(
                "d4" to white(PieceType.ROOK),
                "d6" to black(PieceType.PAWN),
            )
        val attacks = attacksFrom(position, "d4")

        assertTrue(attacks.contains("d5"))
        assertTrue(attacks.contains("d6"))
        assertFalse(attacks.contains("d7"))
    }

    @Test
    fun aSquareOccupiedByAFriendlyPieceIsStillDefended() {
        val position =
            board(
                "d4" to white(PieceType.ROOK),
                "d6" to white(PieceType.PAWN),
            )

        assertTrue(attacksFrom(position, "d4").contains("d6"))
        assertTrue(attacked(position, "d6", Side.WHITE))
        assertFalse(attacksFrom(position, "d4").contains("d7"))
    }

    @Test
    fun listsEveryAttackerOfASquare() {
        val position =
            board(
                "d1" to white(PieceType.ROOK),
                "a1" to white(PieceType.BISHOP),
                "c2" to white(PieceType.KNIGHT),
                "h8" to white(PieceType.QUEEN),
                "e5" to black(PieceType.KING),
            )

        assertEquals(
            listOf("a1", "d1", "c2"),
            Attacks.attackersOf(position, Square.parse("d4"), Side.WHITE).map { it.name },
        )
    }

    @Test
    fun collectsEveryAttackedSquareForASide() {
        val position =
            board(
                "a1" to white(PieceType.ROOK),
                "b1" to white(PieceType.KING),
            )
        val squares = Attacks.attackedSquares(position, Side.WHITE).map { it.name }

        assertTrue(squares.contains("a1"))
        assertTrue(squares.contains("a8"))
        assertTrue(squares.contains("c2"))
        assertEquals(squares.size, squares.toSet().size)
    }

    @Test
    fun findsTheKing() {
        assertEquals(Square.parse("e1"), Attacks.kingSquare(StandardPosition.BOARD, Side.WHITE))
        assertEquals(Square.parse("e8"), Attacks.kingSquare(StandardPosition.BOARD, Side.BLACK))
        assertNull(Attacks.kingSquare(Board.EMPTY, Side.WHITE))
    }

    @Test
    fun detectsCheckFromEveryPieceType() {
        val checks =
            listOf(
                "d8" to black(PieceType.ROOK),
                "a4" to black(PieceType.BISHOP),
                "h5" to black(PieceType.QUEEN),
                "c3" to black(PieceType.KNIGHT),
                "c2" to black(PieceType.PAWN),
                "d2" to black(PieceType.KING),
            )

        checks.forEach { (square, piece) ->
            val position = board("d1" to white(PieceType.KING), square to piece)
            assertTrue(Attacks.isInCheck(position, Side.WHITE), "$piece on $square should give check")
        }
    }

    @Test
    fun quietPositionsAreNotCheck() {
        val nonChecks =
            listOf(
                "e8" to black(PieceType.ROOK),
                "b5" to black(PieceType.BISHOP),
                "b3" to black(PieceType.KNIGHT),
                "d2" to black(PieceType.PAWN),
                "f3" to black(PieceType.KING),
            )

        nonChecks.forEach { (square, piece) ->
            val position = board("d1" to white(PieceType.KING), square to piece)
            assertFalse(Attacks.isInCheck(position, Side.WHITE), "$piece on $square should not give check")
        }
    }

    @Test
    fun aBlockedRayIsNotCheck() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e4" to black(PieceType.ROOK),
                "e2" to white(PieceType.PAWN),
            )

        assertFalse(Attacks.isInCheck(position, Side.WHITE))
        assertTrue(Attacks.isInCheck(position.withoutPiece(Square.parse("e2")), Side.WHITE))
    }

    @Test
    fun anEnemyPieceAlsoBlocksARay() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e4" to black(PieceType.ROOK),
                "e2" to black(PieceType.KNIGHT),
            )

        assertFalse(Attacks.isInCheck(position, Side.WHITE))
    }

    @Test
    fun aPawnDoesNotCheckTheSquareItAdvancesTo() {
        val position =
            board(
                "d1" to white(PieceType.KING),
                "d2" to black(PieceType.PAWN),
            )

        assertFalse(Attacks.isInCheck(position, Side.WHITE))
    }

    @Test
    fun checkIsSideSpecific() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.KING),
                "e7" to white(PieceType.ROOK),
            )

        assertTrue(Attacks.isInCheck(position, Side.BLACK))
        assertFalse(Attacks.isInCheck(position, Side.WHITE))
    }

    @Test
    fun readsCheckFromAGameState() {
        val state =
            GameState(
                board =
                    board(
                        "e1" to white(PieceType.KING),
                        "e8" to black(PieceType.KING),
                        "e7" to white(PieceType.ROOK),
                    ),
                sideToMove = Side.BLACK,
                castlingRights = CastlingRights.NONE,
            )

        assertTrue(Attacks.isInCheck(state, Side.BLACK))
        assertTrue(Attacks.isSideToMoveInCheck(state))
        assertFalse(Attacks.isSideToMoveInCheck(state.copy(sideToMove = Side.WHITE)))
    }

    @Test
    fun theStartingPositionHasNoChecks() {
        assertFalse(Attacks.isInCheck(StandardPosition.BOARD, Side.WHITE))
        assertFalse(Attacks.isInCheck(StandardPosition.BOARD, Side.BLACK))
    }

    @Test
    fun requiresAKingToAnswerCheck() {
        assertFailsWith<IllegalArgumentException> { Attacks.isInCheck(Board.EMPTY, Side.WHITE) }
    }

    @Test
    fun rejectsAnEmptySquare() {
        assertFailsWith<IllegalArgumentException> {
            Attacks.attackedSquaresFrom(Board.EMPTY, Square.parse("d4"))
        }
    }
}
