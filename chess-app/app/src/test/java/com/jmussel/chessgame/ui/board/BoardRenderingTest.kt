package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardRenderingTest {
    private val start = StandardPosition.BOARD

    @Test
    fun drawsEightRowsOfEightSquares() {
        val rows = BoardRendering.rows(start)

        assertEquals(8, rows.size)
        assertTrue(rows.all { it.size == 8 })
        assertEquals(64, BoardRendering.squares(start).size)
    }

    @Test
    fun drawsRankEightFirstAndFileAOnTheLeft() {
        val rows = BoardRendering.rows(start)

        assertEquals(Square.parse("a8"), rows.first().first().square)
        assertEquals(Square.parse("h8"), rows.first().last().square)
        assertEquals(Square.parse("a1"), rows.last().first().square)
        assertEquals(Square.parse("h1"), rows.last().last().square)
    }

    @Test
    fun putsWhiteAtTheBottom() {
        val rows = BoardRendering.rows(start)

        assertTrue(rows.last().all { it.piece?.side == Side.WHITE })
        assertTrue(rows.first().all { it.piece?.side == Side.BLACK })
    }

    @Test
    fun readsThePiecesFromTheBoard() {
        val rows = BoardRendering.rows(start)

        assertEquals(Piece(Side.BLACK, PieceType.ROOK), rows[0][0].piece)
        assertEquals(Piece(Side.BLACK, PieceType.KING), rows[0][4].piece)
        assertEquals(Piece(Side.WHITE, PieceType.KING), rows[7][4].piece)
        assertEquals(Piece(Side.WHITE, PieceType.PAWN), rows[6][0].piece)
        assertNull(rows[3][3].piece)
    }

    @Test
    fun rendersAnEmptyBoardWithNoPieces() {
        assertTrue(BoardRendering.squares(Board.EMPTY).all { it.piece == null })
    }

    @Test
    fun shadesTheSquaresLikeARealBoard() {
        assertFalse(BoardRendering.isLight(Square.parse("a1")))
        assertTrue(BoardRendering.isLight(Square.parse("b1")))
        assertTrue(BoardRendering.isLight(Square.parse("a2")))
        assertTrue(BoardRendering.isLight(Square.parse("h1")))
        assertFalse(BoardRendering.isLight(Square.parse("h8")))
    }

    @Test
    fun shadesAlternateAlongEveryRowAndColumn() {
        val rows = BoardRendering.rows(start)

        rows.forEach { row ->
            row.zipWithNext { left, right ->
                assertFalse("neighbours share a shade", left.isLight == right.isLight)
            }
        }
        (0 until 8).forEach { column ->
            rows.map { it[column] }.zipWithNext { above, below ->
                assertFalse("neighbours share a shade", above.isLight == below.isLight)
            }
        }
    }

    @Test
    fun halfTheBoardIsLight() {
        assertEquals(32, BoardRendering.squares(start).count { it.isLight })
    }

    @Test
    fun hasAGlyphForEveryPieceType() {
        val glyphs = PieceType.entries.map { BoardRendering.glyphFor(it) }

        assertEquals(PieceType.entries.size, glyphs.toSet().size)
        assertEquals('♚', BoardRendering.glyphFor(PieceType.KING))
        assertEquals('♟', BoardRendering.glyphFor(PieceType.PAWN))
    }

    @Test
    fun bothSidesUseTheSameGlyphSoColourTellsThemApart() {
        val rows = BoardRendering.rows(start)
        val blackRook = rows[0][0].piece!!
        val whiteRook = rows[7][0].piece!!

        assertEquals(BoardRendering.glyphFor(blackRook.type), BoardRendering.glyphFor(whiteRook.type))
        assertFalse(blackRook.side == whiteRook.side)
    }

    @Test
    fun labelsTheFilesAndRanksAsDrawn() {
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), BoardRendering.fileLabels())
        assertEquals(listOf("8", "7", "6", "5", "4", "3", "2", "1"), BoardRendering.rankLabels())
    }

    @Test
    fun rendersTheStartingPositionAsTheFamiliarGrid() {
        val drawn =
            BoardRendering.rows(start).joinToString("\n") { row ->
                row.joinToString("") { cell ->
                    cell.piece?.symbol?.toString() ?: "."
                }
            }

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
            drawn,
        )
    }

    // --- Coordinates (M22.11) -----------------------------------------------------------

    /** The label of every square as drawn, keyed by the true square it is drawn on. */
    private fun labelsOn(orientation: Side): Map<Square, EdgeLabels> =
        BoardRendering
            .rows(StandardPosition.BOARD, orientation)
            .flatMapIndexed { row, squares ->
                squares.mapIndexed { column, it ->
                    it.square to
                        BoardRendering.edgeLabels(row, column, orientation)
                }
            }.toMap()

    @Test
    fun fromWhitesSideTheBottomRowNamesFilesAToHAndTheLeftColumnRanks8To1() {
        val labels = labelsOn(Side.WHITE)

        assertEquals(EdgeLabels(file = "a", rank = "1"), labels[Square.parse("a1")])
        assertEquals(EdgeLabels(file = "h"), labels[Square.parse("h1")])
        assertEquals(EdgeLabels(rank = "8"), labels[Square.parse("a8")])
        assertEquals(EdgeLabels(), labels[Square.parse("e4")])
        assertEquals(
            "every label names its own square",
            true,
            labels.all { (square, it) ->
                it.file == null ||
                    it.file == square.fileChar.toString()
            },
        )
        assertTrue(labels.all { (square, it) -> it.rank == null || it.rank == square.rankNumber.toString() })
    }

    @Test
    fun fromBlacksSideTheFilesRunHToAAndTheRanks1To8FromTheTop() {
        val labels = labelsOn(Side.BLACK)

        assertEquals("the bottom-left square from Black's side", EdgeLabels(file = "h", rank = "8"), labels[Square.parse("h8")])
        assertEquals(EdgeLabels(file = "a"), labels[Square.parse("a8")])
        assertEquals(EdgeLabels(rank = "1"), labels[Square.parse("h1")])
        assertTrue(labels.all { (square, it) -> it.file == null || it.file == square.fileChar.toString() })
        assertTrue(labels.all { (square, it) -> it.rank == null || it.rank == square.rankNumber.toString() })
        assertEquals(15, labels.values.count { !it.isEmpty })
    }

    @Test
    fun aDropIsReadAsTheSquareDrawnThereWithEitherSideAtTheBottom() {
        assertEquals(Square.parse("a8"), BoardRendering.squareDrawnAt(0, 0, Side.WHITE))
        assertEquals(Square.parse("h1"), BoardRendering.squareDrawnAt(7, 7, Side.WHITE))
        assertEquals(Square.parse("h1"), BoardRendering.squareDrawnAt(0, 0, Side.BLACK))
        assertEquals(Square.parse("h8"), BoardRendering.squareDrawnAt(7, 0, Side.BLACK))
        assertNull(BoardRendering.squareDrawnAt(-1, 3, Side.WHITE))
        assertNull(BoardRendering.squareDrawnAt(2, 8, Side.BLACK))

        Side.entries.forEach { orientation ->
            BoardRendering.rows(StandardPosition.BOARD, orientation).forEachIndexed { row, squares ->
                squares.forEachIndexed { column, drawn ->
                    assertEquals("$orientation $row $column", drawn.square, BoardRendering.squareDrawnAt(row, column, orientation))
                }
            }
        }
    }
}
