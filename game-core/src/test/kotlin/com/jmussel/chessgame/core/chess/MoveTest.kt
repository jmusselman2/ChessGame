package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class MoveTest {
    @Test
    fun describesFromAndTo() {
        val move = Move.of("e2", "e4")

        assertEquals(Square.parse("e2"), move.from)
        assertEquals(Square.parse("e4"), move.to)
        assertNull(move.promotion)
        assertEquals("e2e4", move.toString())
    }

    @Test
    fun carriesAnExplicitPromotionChoice() {
        PieceType.PROMOTION_CHOICES.forEach { promotion ->
            assertEquals(promotion, Move.of("a7", "a8", promotion).promotion)
        }
        assertEquals("a7a8n", Move.of("a7", "a8", PieceType.KNIGHT).toString())
    }

    @Test
    fun rejectsPromotionToPawnOrKing() {
        assertFailsWith<IllegalArgumentException> { Move.of("a7", "a8", PieceType.PAWN) }
        assertFailsWith<IllegalArgumentException> { Move.of("a7", "a8", PieceType.KING) }
    }

    @Test
    fun rejectsAMoveThatDoesNotChangeSquare() {
        assertFailsWith<IllegalArgumentException> { Move.of("e2", "e2") }
    }

    @Test
    fun movesWithTheSameSquaresAndPromotionAreEqual() {
        assertEquals(Move.of("e2", "e4"), Move(Square.parse("e2"), Square.parse("e4")))
        assertEquals(Move.of("a7", "a8", PieceType.QUEEN), Move.of("a7", "a8", PieceType.QUEEN))
    }

    @Test
    fun promotionChoiceIsPartOfIdentity() {
        assertNotEquals(Move.of("a7", "a8", PieceType.QUEEN), Move.of("a7", "a8", PieceType.KNIGHT))
    }
}
