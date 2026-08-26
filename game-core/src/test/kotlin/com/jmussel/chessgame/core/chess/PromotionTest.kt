package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromotionTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun state(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
    ): GameState =
        GameState(
            board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
            sideToMove = sideToMove,
            castlingRights = CastlingRights.NONE,
        )

    private val whiteAboutToPromote =
        state(
            "a7" to white(PieceType.PAWN),
            "e1" to white(PieceType.KING),
            "h8" to black(PieceType.KING),
        )

    @Test
    fun namesThePromotionRankForEachSide() {
        assertEquals(7, PseudoLegalMoves.promotionRankOf(Side.WHITE))
        assertEquals(0, PseudoLegalMoves.promotionRankOf(Side.BLACK))
    }

    @Test
    fun advancingToTheLastRankOffersAllFourChoices() {
        val moves = ChessRules.legalMoves(whiteAboutToPromote).filter { it.from == Square.parse("a7") }

        assertEquals(4, moves.size)
        assertEquals(
            PieceType.PROMOTION_CHOICES.toSet(),
            moves.mapNotNull { it.promotion }.toSet(),
        )
        assertTrue(moves.all { it.to == Square.parse("a8") })
    }

    @Test
    fun thereIsNoAutomaticQueenPromotion() {
        assertFalse(
            ChessRules.isLegal(whiteAboutToPromote, Move.of("a7", "a8")),
            "a promotion move without an explicit choice is not legal",
        )
        assertTrue(ChessRules.isLegal(whiteAboutToPromote, Move.of("a7", "a8", PieceType.QUEEN)))
    }

    @Test
    fun eachChoiceProducesThatPiece() {
        PieceType.PROMOTION_CHOICES.forEach { choice ->
            val after = ChessRules.applyMove(whiteAboutToPromote, Move.of("a7", "a8", choice))

            assertEquals(white(choice), after.board.pieceAt(Square.parse("a8")), "promoting to $choice")
            assertTrue(after.board.isEmpty(Square.parse("a7")))
        }
    }

    @Test
    fun blackPromotesOnTheFirstRank() {
        val position =
            state(
                "b2" to black(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
                sideToMove = Side.BLACK,
            )
        val moves = ChessRules.legalMoves(position).filter { it.from == Square.parse("b2") }

        assertEquals(4, moves.size)

        val after = ChessRules.applyMove(position, Move.of("b2", "b1", PieceType.KNIGHT))

        assertEquals(black(PieceType.KNIGHT), after.board.pieceAt(Square.parse("b1")))
    }

    @Test
    fun promotingByCaptureAlsoOffersEveryChoice() {
        val position =
            state(
                "b7" to white(PieceType.PAWN),
                "a8" to black(PieceType.ROOK),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )
        val captures = ChessRules.legalMoves(position).filter { it.to == Square.parse("a8") }

        assertEquals(4, captures.size)

        val after = ChessRules.applyMove(position, Move.of("b7", "a8", PieceType.ROOK))

        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("a8")))
    }

    @Test
    fun aPromotionThatWouldExposeItsOwnKingIsStillIllegal() {
        val position =
            state(
                "a7" to white(PieceType.KING),
                "b7" to white(PieceType.PAWN),
                "h7" to black(PieceType.ROOK),
                "h1" to black(PieceType.KING),
            )

        assertTrue(
            ChessRules.legalMoves(position).none { it.from == Square.parse("b7") },
            "the pawn is pinned along the seventh rank, so no promotion is available",
        )
        assertTrue(ChessRules.isLegal(whiteAboutToPromote, Move.of("a7", "a8", PieceType.QUEEN)))
    }

    @Test
    fun anOrdinaryPawnMoveCarriesNoPromotion() {
        val position =
            state(
                "a5" to white(PieceType.PAWN),
                "e1" to white(PieceType.KING),
                "h8" to black(PieceType.KING),
            )
        val moves = ChessRules.legalMoves(position).filter { it.from == Square.parse("a5") }

        assertEquals(listOf(Move.of("a5", "a6")), moves)
    }

    @Test
    fun aPromotionResetsTheHalfmoveClock() {
        val position =
            whiteAboutToPromote.copy(drawRuleState = DrawRuleState(halfmoveClock = 40))

        assertEquals(0, ChessRules.applyMove(position, Move.of("a7", "a8", PieceType.QUEEN)).halfmoveClock)
    }

    @Test
    fun aPromotedPieceMovesAsItsNewType() {
        val after = ChessRules.applyMove(whiteAboutToPromote, Move.of("a7", "a8", PieceType.KNIGHT))
        val knightMoves = PseudoLegalMoves.from(after.board, Square.parse("a8")).map { it.to.name }.toSet()

        assertEquals(setOf("b6", "c7"), knightMoves)
    }
}
