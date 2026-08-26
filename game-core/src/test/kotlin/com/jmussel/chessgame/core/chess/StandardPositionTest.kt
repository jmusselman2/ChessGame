package com.jmussel.chessgame.core.chess

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardPositionTest {
    private val start = StandardPosition.newGame()

    @Test
    fun startsWithThirtyTwoPieces() {
        assertEquals(32, start.board.pieceCount)
        assertEquals(16, start.board.squaresOf(Side.WHITE).size)
        assertEquals(16, start.board.squaresOf(Side.BLACK).size)
    }

    @Test
    fun placesBothBackRanksCorrectly() {
        val expected = "RNBQKBNR"

        expected.forEachIndexed { file, letter ->
            val type = PieceType.fromLetter(letter)
            assertEquals(
                Piece(Side.WHITE, type),
                start.board.pieceAt(Square.of(file, 0)),
                "white ${Square.of(file, 0)}",
            )
            assertEquals(
                Piece(Side.BLACK, type),
                start.board.pieceAt(Square.of(file, 7)),
                "black ${Square.of(file, 7)}",
            )
        }
    }

    @Test
    fun placesKingsAndQueensOnTheirOwnColour() {
        assertEquals(Piece(Side.WHITE, PieceType.QUEEN), start.board.pieceAt(Square.parse("d1")))
        assertEquals(Piece(Side.WHITE, PieceType.KING), start.board.pieceAt(Square.parse("e1")))
        assertEquals(Piece(Side.BLACK, PieceType.QUEEN), start.board.pieceAt(Square.parse("d8")))
        assertEquals(Piece(Side.BLACK, PieceType.KING), start.board.pieceAt(Square.parse("e8")))
    }

    @Test
    fun fillsBothPawnRanks() {
        (0 until Square.FILES).forEach { file ->
            assertEquals(Piece(Side.WHITE, PieceType.PAWN), start.board.pieceAt(Square.of(file, 1)))
            assertEquals(Piece(Side.BLACK, PieceType.PAWN), start.board.pieceAt(Square.of(file, 6)))
        }
        assertEquals(8, start.board.squaresOf(Side.WHITE, PieceType.PAWN).size)
        assertEquals(8, start.board.squaresOf(Side.BLACK, PieceType.PAWN).size)
    }

    @Test
    fun leavesRanksThreeThroughSixEmpty() {
        (2..5).forEach { rank ->
            (0 until Square.FILES).forEach { file ->
                assertTrue(start.board.isEmpty(Square.of(file, rank)), "${Square.of(file, rank)} should be empty")
            }
        }
    }

    @Test
    fun hasOneKingAndOneQueenPerSide() {
        Side.entries.forEach { side ->
            assertEquals(1, start.board.squaresOf(side, PieceType.KING).size)
            assertEquals(1, start.board.squaresOf(side, PieceType.QUEEN).size)
            assertEquals(2, start.board.squaresOf(side, PieceType.ROOK).size)
            assertEquals(2, start.board.squaresOf(side, PieceType.KNIGHT).size)
            assertEquals(2, start.board.squaresOf(side, PieceType.BISHOP).size)
            assertEquals(8, start.board.squaresOf(side, PieceType.PAWN).size)
        }
    }

    @Test
    fun whiteMovesFirst() {
        assertEquals(Side.WHITE, start.sideToMove)
    }

    @Test
    fun grantsEveryCastlingRight() {
        assertEquals(CastlingRights.ALL, start.castlingRights)
        Side.entries.forEach { side ->
            CastlingSide.entries.forEach { castlingSide ->
                assertTrue(start.castlingRights.has(side, castlingSide))
            }
        }
    }

    @Test
    fun hasNoActiveHistory() {
        assertNull(start.enPassantTarget)
        assertEquals(DrawRuleState(), start.drawRuleState)
        assertTrue(start.drawRuleState.positionCounts.isEmpty())
    }

    @Test
    fun startsTheMoveCountersAtTheBeginning() {
        assertEquals(0, start.halfmoveClock)
        assertEquals(1, start.fullmoveNumber)
    }

    @Test
    fun isNotOver() {
        assertFalse(start.isOver)
        assertNull(start.result)
    }

    @Test
    fun eachNewGameStartsIdentically() {
        assertEquals(StandardPosition.newGame(), StandardPosition.newGame())
        assertEquals(StandardPosition.BOARD, start.board)
    }

    @Test
    fun rendersTheFamiliarStartingBoard() {
        assertEquals(
            listOf(
                "rnbqkbnr",
                "pppppppp",
                "........",
                "........",
                "........",
                "........",
                "PPPPPPPP",
                "RNBQKBNR",
            ).joinToString("\n"),
            start.board.toString(),
        )
    }
}
