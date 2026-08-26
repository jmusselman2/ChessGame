package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BoardTest {
    private val whiteKing = Piece(Side.WHITE, PieceType.KING)
    private val blackRook = Piece(Side.BLACK, PieceType.ROOK)

    @Test
    fun emptyBoardHoldsNoPieces() {
        assertEquals(0, Board.EMPTY.pieceCount)
        assertTrue(Board.EMPTY.isEmpty(Square.parse("e1")))
        assertNull(Board.EMPTY.pieceAt(Square.parse("e1")))
        assertTrue(Board.EMPTY.occupiedSquares().isEmpty())
    }

    @Test
    fun placingAPieceLeavesTheOriginalBoardUnchanged() {
        val e1 = Square.parse("e1")
        val placed = Board.EMPTY.withPiece(e1, whiteKing)

        assertEquals(whiteKing, placed.pieceAt(e1))
        assertNull(Board.EMPTY.pieceAt(e1))
        assertEquals(1, placed.pieceCount)
    }

    @Test
    fun placingReplacesAnyOccupant() {
        val a8 = Square.parse("a8")
        val board = Board.EMPTY.withPiece(a8, blackRook).withPiece(a8, whiteKing)

        assertEquals(whiteKing, board.pieceAt(a8))
        assertEquals(1, board.pieceCount)
    }

    @Test
    fun removingAPieceEmptiesTheSquare() {
        val a8 = Square.parse("a8")
        val board = Board.EMPTY.withPiece(a8, blackRook).withoutPiece(a8)

        assertNull(board.pieceAt(a8))
        assertEquals(Board.EMPTY, board)
    }

    @Test
    fun aNoOpPlacementReturnsTheSameBoard() {
        val board = Board.EMPTY.withPiece(Square.parse("e1"), whiteKing)
        assertSame(board, board.withPiece(Square.parse("e1"), whiteKing))
        assertSame(Board.EMPTY, Board.EMPTY.withoutPiece(Square.parse("e1")))
    }

    @Test
    fun buildsFromAnExplicitPlacement() {
        val board =
            Board.of(
                mapOf(
                    Square.parse("e1") to whiteKing,
                    Square.parse("a8") to blackRook,
                ),
            )

        assertEquals(2, board.pieceCount)
        assertEquals(
            listOf(Square.parse("e1") to whiteKing, Square.parse("a8") to blackRook),
            board.occupiedSquares(),
        )
    }

    @Test
    fun findsSquaresBySideAndType() {
        val board =
            Board.of(
                mapOf(
                    Square.parse("e1") to whiteKing,
                    Square.parse("a8") to blackRook,
                    Square.parse("h8") to blackRook,
                ),
            )

        assertEquals(listOf(Square.parse("e1")), board.squaresOf(Side.WHITE))
        assertEquals(listOf(Square.parse("a8"), Square.parse("h8")), board.squaresOf(Side.BLACK))
        assertEquals(listOf(Square.parse("e1")), board.squaresOf(Side.WHITE, PieceType.KING))
        assertTrue(board.squaresOf(Side.WHITE, PieceType.ROOK).isEmpty())
    }

    @Test
    fun boardsWithIdenticalPlacementAreEqual() {
        val placement = mapOf(Square.parse("e1") to whiteKing)

        assertEquals(Board.of(placement), Board.EMPTY.withPiece(Square.parse("e1"), whiteKing))
        assertEquals(Board.of(placement).hashCode(), Board.of(placement).hashCode())
        assertNotEquals(Board.of(placement), Board.EMPTY)
    }

    @Test
    fun rendersFromRankEightDown() {
        val board =
            Board.of(
                mapOf(
                    Square.parse("e1") to whiteKing,
                    Square.parse("a8") to blackRook,
                ),
            )

        assertEquals(
            listOf(
                "r.......",
                "........",
                "........",
                "........",
                "........",
                "........",
                "........",
                "....K...",
            ).joinToString("\n"),
            board.toString(),
        )
    }
}
