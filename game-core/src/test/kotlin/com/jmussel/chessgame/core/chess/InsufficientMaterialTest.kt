package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InsufficientMaterialTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun board(vararg placement: Pair<String, Piece>): Board =
        Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece })

    @Test
    fun kingVersusKingIsADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board("e1" to white(PieceType.KING), "e8" to black(PieceType.KING)),
            ),
        )
    }

    @Test
    fun kingAndOneBishopVersusKingIsADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                ),
            ),
        )
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "e8" to black(PieceType.KING),
                    "c8" to black(PieceType.BISHOP),
                ),
            ),
        )
    }

    @Test
    fun kingAndOneKnightVersusKingIsADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "b1" to white(PieceType.KNIGHT),
                    "e8" to black(PieceType.KING),
                ),
            ),
        )
    }

    @Test
    fun bishopsOnTheSameColourAreADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                    "f8" to black(PieceType.BISHOP),
                ),
            ),
            "c1 and f8 are both dark squares",
        )
    }

    @Test
    fun bishopsOnOppositeColoursAreNotAnAutomaticDraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                    "c8" to black(PieceType.BISHOP),
                ),
            ),
        )
    }

    @Test
    fun twoBishopsForOneSideAreNotADraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "f1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                ),
            ),
        )
    }

    @Test
    fun twoKnightsAreNotAnAutomaticDraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "b1" to white(PieceType.KNIGHT),
                    "g1" to white(PieceType.KNIGHT),
                    "e8" to black(PieceType.KING),
                ),
            ),
        )
    }

    @Test
    fun bishopAgainstKnightIsNotAnAutomaticDraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                    "b8" to black(PieceType.KNIGHT),
                ),
            ),
        )
    }

    @Test
    fun anyPawnRookOrQueenIsSufficient() {
        listOf(PieceType.PAWN, PieceType.ROOK, PieceType.QUEEN).forEach { type ->
            assertFalse(
                InsufficientMaterial.isDraw(
                    board(
                        "e1" to white(PieceType.KING),
                        "a2" to white(type),
                        "e8" to black(PieceType.KING),
                    ),
                ),
                "a $type is enough material",
            )
        }
    }

    @Test
    fun theStartingPositionHasPlentyOfMaterial() {
        assertFalse(InsufficientMaterial.isDraw(StandardPosition.BOARD))
    }

    @Test
    fun aDeadPositionIsReportedAsATerminalDraw() {
        val position =
            GameState(
                board =
                    board(
                        "e1" to white(PieceType.KING),
                        "b1" to white(PieceType.KNIGHT),
                        "e8" to black(PieceType.KING),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            )

        assertEquals(
            GameResult.draw(TerminationReason.INSUFFICIENT_MATERIAL),
            ChessRules.terminalResult(position),
        )
    }

    @Test
    fun theCaptureThatEmptiesTheBoardEndsTheGame() {
        val position =
            GameState(
                board =
                    board(
                        "e1" to white(PieceType.KING),
                        "d2" to white(PieceType.KNIGHT),
                        "e8" to black(PieceType.KING),
                        "c4" to black(PieceType.KNIGHT),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            )
        val after = ChessRules.applyMove(position, Move.of("d2", "c4"))

        assertTrue(after.isOver)
        assertEquals(TerminationReason.INSUFFICIENT_MATERIAL, after.result?.reason)
        assertEquals(GameOutcome.DRAW, after.result?.outcome)
    }
}
