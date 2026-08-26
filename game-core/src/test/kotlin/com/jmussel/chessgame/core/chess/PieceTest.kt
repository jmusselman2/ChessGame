package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PieceTest {
    @Test
    fun hasTwoSidesThatOppose() {
        assertEquals(Side.BLACK, Side.WHITE.opposite)
        assertEquals(Side.WHITE, Side.BLACK.opposite)
        assertEquals(Side.WHITE, Side.WHITE.opposite.opposite)
    }

    @Test
    fun hasSixPieceTypes() {
        assertEquals(6, PieceType.entries.size)
        assertEquals("PNBRQK", PieceType.entries.joinToString("") { it.letter.toString() })
    }

    @Test
    fun promotionChoicesExcludePawnAndKing() {
        assertEquals(
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT),
            PieceType.PROMOTION_CHOICES,
        )
    }

    @Test
    fun rendersSymbolsByCase() {
        assertEquals('N', Piece(Side.WHITE, PieceType.KNIGHT).symbol)
        assertEquals('n', Piece(Side.BLACK, PieceType.KNIGHT).symbol)
        assertEquals("q", Piece(Side.BLACK, PieceType.QUEEN).toString())
    }

    @Test
    fun readsPiecesBackFromSymbols() {
        assertEquals(Piece(Side.WHITE, PieceType.ROOK), Piece.fromSymbol('R'))
        assertEquals(Piece(Side.BLACK, PieceType.PAWN), Piece.fromSymbol('p'))
        assertFailsWith<IllegalArgumentException> { Piece.fromSymbol('x') }
    }

    @Test
    fun piecesOfSameSideAndTypeAreEqual() {
        assertEquals(Piece(Side.WHITE, PieceType.BISHOP), Piece(Side.WHITE, PieceType.BISHOP))
    }
}
