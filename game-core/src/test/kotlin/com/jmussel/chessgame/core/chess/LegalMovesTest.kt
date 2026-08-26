package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LegalMovesTest {
    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun legalFrom(
        board: Board,
        from: String,
    ): Set<String> = LegalMoves.from(board, Square.parse(from)).map { it.to.name }.toSet()

    @Test
    fun boardAfterMovesThePieceAndTakesTheOccupant() {
        val position =
            board(
                "d1" to white(PieceType.ROOK),
                "d7" to black(PieceType.PAWN),
            )
        val after = LegalMoves.boardAfter(position, Move.of("d1", "d7"))

        assertTrue(after.isEmpty(Square.parse("d1")))
        assertEquals(white(PieceType.ROOK), after.pieceAt(Square.parse("d7")))
        assertEquals(1, after.pieceCount)
    }

    @Test
    fun boardAfterAppliesAPromotionChoice() {
        val position = board("a7" to white(PieceType.PAWN))
        val after = LegalMoves.boardAfter(position, Move.of("a7", "a8", PieceType.KNIGHT))

        assertEquals(white(PieceType.KNIGHT), after.pieceAt(Square.parse("a8")))
    }

    @Test
    fun boardAfterLeavesTheOriginalBoardUnchanged() {
        val position = board("d1" to white(PieceType.ROOK))
        LegalMoves.boardAfter(position, Move.of("d1", "d7"))

        assertEquals(white(PieceType.ROOK), position.pieceAt(Square.parse("d1")))
    }

    @Test
    fun aPinnedPieceMayNotStepOffThePinLine() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e2" to white(PieceType.ROOK),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertEquals(setOf("e3", "e4", "e5", "e6", "e7", "e8"), legalFrom(position, "e2"))
        assertFalse(legalFrom(position, "e2").contains("d2"))
        assertFalse(legalFrom(position, "e2").contains("a2"))
    }

    @Test
    fun aPinnedPieceMayCaptureThePinner() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "d2" to white(PieceType.BISHOP),
                "a5" to black(PieceType.BISHOP),
                "a8" to black(PieceType.KING),
            )

        assertEquals(setOf("c3", "b4", "a5"), legalFrom(position, "d2"))
        assertTrue(LegalMoves.isLegal(position, Move.of("d2", "a5")))
    }

    @Test
    fun aDiagonallyPinnedKnightCannotMoveAtAll() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "d2" to white(PieceType.KNIGHT),
                "a5" to black(PieceType.BISHOP),
                "a8" to black(PieceType.KING),
            )

        assertTrue(legalFrom(position, "d2").isEmpty())
        assertTrue(PseudoLegalMoves.from(position, Square.parse("d2")).isNotEmpty())
    }

    @Test
    fun aPieceIsNotPinnedWhenAnotherPieceBlocksTheAttacker() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e2" to white(PieceType.ROOK),
                "e6" to black(PieceType.PAWN),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertTrue(legalFrom(position, "e2").contains("d2"))
    }

    @Test
    fun aKingMayNotStepOntoAnAttackedSquare() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "d8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )
        val reachable = legalFrom(position, "e1")

        assertFalse(reachable.contains("d1"))
        assertFalse(reachable.contains("d2"))
        assertEquals(setOf("e2", "f1", "f2"), reachable)
    }

    @Test
    fun aKingMayNotCaptureADefendedPiece() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e2" to black(PieceType.PAWN),
                "e3" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertFalse(LegalMoves.isLegal(position, Move.of("e1", "e2")))
        assertFalse(
            LegalMoves.isLegal(position, Move.of("e1", "f1")),
            "f1 is attacked by the pawn on e2",
        )
        assertTrue(LegalMoves.isLegal(position, Move.of("e1", "d2")))
    }

    @Test
    fun aKingMayCaptureAnUndefendedAttacker() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e2" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertTrue(LegalMoves.isLegal(position, Move.of("e1", "e2")))
    }

    @Test
    fun aKingMayNotStayOnACheckingRayByRetreatingAlongIt() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertFalse(
            LegalMoves.isLegal(position, Move.of("e1", "e2")),
            "the king cannot stay on the file the rook checks along",
        )
        assertTrue(LegalMoves.isLegal(position, Move.of("e1", "d1")))
    }

    @Test
    fun everyMoveWhileInCheckMustAnswerTheCheck() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "h2" to white(PieceType.PAWN),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )
        val moves = LegalMoves.forSide(position, Side.WHITE)

        assertTrue(Attacks.isInCheck(position, Side.WHITE))
        assertTrue(moves.none { it.from == Square.parse("h2") })
        assertTrue(moves.contains(Move.of("a1", "e1")) || moves.contains(Move.of("e1", "d1")))
        assertTrue(moves.all { !Attacks.isInCheck(LegalMoves.boardAfter(position, it), Side.WHITE) })
    }

    @Test
    fun blockingACheckIsLegal() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "a4" to white(PieceType.ROOK),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertTrue(LegalMoves.isLegal(position, Move.of("a4", "e4")))
        assertFalse(LegalMoves.isLegal(position, Move.of("a4", "a5")))
    }

    @Test
    fun capturingTheCheckingPieceIsLegal() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "a4" to white(PieceType.ROOK),
                "e4" to black(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )

        assertTrue(Attacks.isInCheck(position, Side.WHITE))
        assertTrue(LegalMoves.isLegal(position, Move.of("a4", "e4")))
        assertFalse(LegalMoves.isLegal(position, Move.of("a4", "a5")))
    }

    @Test
    fun aMoveThatIsNotPseudoLegalIsNotLegal() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "a1" to white(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )

        assertFalse(LegalMoves.isLegal(position, Move.of("a1", "b2")))
        assertFalse(LegalMoves.isLegal(position, Move.of("h1", "h2")))
    }

    @Test
    fun theStartingPositionHasTwentyLegalMoves() {
        assertEquals(20, LegalMoves.forSide(StandardPosition.BOARD, Side.WHITE).size)
        assertEquals(20, LegalMoves.forSide(StandardPosition.BOARD, Side.BLACK).size)
        assertEquals(20, LegalMoves.forSideToMove(StandardPosition.newGame()).size)
    }

    @Test
    fun legalMovesAreASubsetOfPseudoLegalMoves() {
        val position =
            board(
                "e1" to white(PieceType.KING),
                "e2" to white(PieceType.ROOK),
                "e8" to black(PieceType.ROOK),
                "a8" to black(PieceType.KING),
            )
        val pseudoLegal = PseudoLegalMoves.forSide(position, Side.WHITE).toSet()
        val legal = LegalMoves.forSide(position, Side.WHITE).toSet()

        assertTrue(legal.size < pseudoLegal.size)
        assertTrue(pseudoLegal.containsAll(legal))
    }

    @Test
    fun aSideWithNoLegalMovesReturnsAnEmptyList() {
        val position =
            board(
                "a1" to white(PieceType.KING),
                "b3" to black(PieceType.ROOK),
                "a3" to black(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )

        assertTrue(LegalMoves.forSide(position, Side.WHITE).isEmpty())
    }
}
