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
    fun twoBishopsForOneSideOnTheSameColourAreADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "g5" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                ),
            ),
            "c1 and g5 are both dark squares, so neither side can ever mate",
        )
    }

    @Test
    fun anyNumberOfBishopsConfinedToOneColourIsADraw() {
        assertTrue(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e3" to white(PieceType.BISHOP),
                    "g5" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                    "h8" to black(PieceType.BISHOP),
                ),
            ),
            "four bishops, every one of them dark-squared",
        )
    }

    @Test
    fun bishopsSpreadOverBothColoursAreNotADraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e3" to white(PieceType.BISHOP),
                    "f1" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                ),
            ),
            "f1 is light, so the bishops cover both colour complexes",
        )
    }

    @Test
    fun aKnightBesideSameColourBishopsIsNotAnAutomaticDraw() {
        assertFalse(
            InsufficientMaterial.isDraw(
                board(
                    "e1" to white(PieceType.KING),
                    "c1" to white(PieceType.BISHOP),
                    "e3" to white(PieceType.BISHOP),
                    "e8" to black(PieceType.KING),
                    "g5" to black(PieceType.KNIGHT),
                ),
            ),
            "a knight reaches both colours, so a cooperative mate remains possible",
        )
    }

    @Test
    fun promotingToASameColourBishopEndsTheGame() {
        val position =
            GameState(
                board =
                    board(
                        "e1" to white(PieceType.KING),
                        "c1" to white(PieceType.BISHOP),
                        "b7" to white(PieceType.PAWN),
                        "e8" to black(PieceType.KING),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            )
        assertFalse(InsufficientMaterial.isDraw(position), "the pawn is still on the board")

        val promoted = ChessRules.applyMove(position, Move.of("b7", "b8", PieceType.BISHOP))

        assertTrue(promoted.isOver, "b8 and c1 are both dark squares")
        assertEquals(TerminationReason.INSUFFICIENT_MATERIAL, promoted.result?.reason)
    }

    @Test
    fun promotingToTheOtherColourBishopLeavesTheGameRunning() {
        val position =
            GameState(
                board =
                    board(
                        "e1" to white(PieceType.KING),
                        "d1" to white(PieceType.BISHOP),
                        "b7" to white(PieceType.PAWN),
                        "e8" to black(PieceType.KING),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            )

        val promoted = ChessRules.applyMove(position, Move.of("b7", "b8", PieceType.BISHOP))

        assertFalse(promoted.isOver, "d1 is light and b8 is dark, so the bishops cover both colours")
        assertFalse(InsufficientMaterial.isDraw(promoted))
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
