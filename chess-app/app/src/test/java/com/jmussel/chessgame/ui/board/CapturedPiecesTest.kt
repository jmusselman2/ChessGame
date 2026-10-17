package com.jmussel.chessgame.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.computer.computerLabel
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The captured-piece rows as the local and computer games build them (`D104`, `M23.1`):
 * who each row is for, what it holds and in what order, how it counts repeats, its `+N`, and
 * how its pieces are drawn.
 */
class CapturedPiecesTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun played(
        vararg moves: String,
        from: ChessGame = ChessGame.newGame(),
    ): ChessGame =
        moves.fold(from) { game, move ->
            val promotion = move.getOrNull(4)?.let(PieceType::fromLetter)
            ChessRules.applyMove(game, Move.of(move.take(2), move.substring(2, 4), promotion))
        }

    /** `1. e4 d5 2. exd5 Qxd5 3. Nc3 Qxa2`: White has taken a pawn, Black two pawns. */
    private val blackAhead = played("e2e4", "d7d5", "e4d5", "d8d5", "b1c3", "d5a2")

    @Test
    fun eachRowHoldsWhatItsPlayerCapturedNotWhatTheyLost() {
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.BLACK)

        assertEquals(Side.BLACK, rows.top.side)
        assertEquals(listOf(white(PieceType.PAWN), white(PieceType.PAWN)), rows.top.captured)
        assertEquals(Side.WHITE, rows.bottom.side)
        assertEquals(listOf(black(PieceType.PAWN)), rows.bottom.captured)
    }

    @Test
    fun passAndPlayNamesTheColoursWithBlackAboveAndWhiteBelow() {
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.WHITE.opposite)

        assertEquals("Black", rows.top.label)
        assertEquals("White", rows.bottom.label)
    }

    @Test
    fun theComputerGameNamesTheComputerAndYouWithTheComputerAtTheTop() {
        val label = computerLabel(2)
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.WHITE) { side -> if (side == Side.BLACK) CapturedRow.YOU else label }

        assertEquals("$label · White", rows.top.label)
        assertEquals("You · Black", rows.bottom.label)
        assertEquals("the computer, White, took a black pawn", listOf(black(PieceType.PAWN)), rows.top.captured)
    }

    @Test
    fun piecesAreOrderedQueenRookBishopKnightPawn() {
        val captured =
            listOf(
                PieceType.PAWN,
                PieceType.KNIGHT,
                PieceType.QUEEN,
                PieceType.PAWN,
                PieceType.BISHOP,
                PieceType.ROOK,
            ).map(::black)
        val rows = CapturedPieces.rows(captured, whiteBalanceQuarterPoints = 0, topSide = Side.BLACK)

        assertEquals(
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN, PieceType.PAWN),
            rows.bottom.captured.map { it.type },
        )
    }

    @Test
    fun repeatsAreCountedInOrder() {
        val captured =
            listOf(
                PieceType.PAWN,
                PieceType.PAWN,
                PieceType.KNIGHT,
                PieceType.PAWN,
                PieceType.KNIGHT,
                PieceType.QUEEN,
            ).map(::black)
        val row = CapturedPieces.rows(captured, whiteBalanceQuarterPoints = null, topSide = Side.BLACK).bottom

        assertEquals(
            listOf(
                CapturedGroup(black(PieceType.QUEEN), 1),
                CapturedGroup(black(PieceType.KNIGHT), 2),
                CapturedGroup(black(PieceType.PAWN), 3),
            ),
            row.groups,
        )
        assertEquals(listOf(null, "×2", "×3"), row.groups.map { it.countText })
    }

    @Test
    fun aWideRowShowsEachPieceAndANarrowOneCountsThem() {
        val captured = List(8) { black(PieceType.PAWN) } + black(PieceType.ROOK) + black(PieceType.KNIGHT)
        val row = CapturedPieces.rows(captured, whiteBalanceQuarterPoints = null, topSide = Side.BLACK).bottom
        val individual = CapturedPieces.individualWidth(captured.size)

        assertEquals(CapturedFit(compact = false, scale = 1f), CapturedPieces.fit(row, individual))
        assertEquals(CapturedFit(compact = true, scale = 1f), CapturedPieces.fit(row, individual - 1.dp))
        assertTrue("counting saves room", CapturedPieces.countedWidth(row.groups) < individual)
    }

    @Test
    fun aRowTooNarrowEvenToCountShrinksItsPiecesRatherThanClipThem() {
        val captured =
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT, PieceType.PAWN).flatMap {
                listOf(black(it), black(it))
            }
        val row = CapturedPieces.rows(captured, whiteBalanceQuarterPoints = null, topSide = Side.BLACK).bottom
        val counted = CapturedPieces.countedWidth(row.groups)
        val fit = CapturedPieces.fit(row, counted / 2)

        assertTrue(fit.compact)
        assertEquals(0.5f, fit.scale, 0.001f)
    }

    @Test
    fun anEmptyRowKeepsItsLabelAndSaysNoCapturesOnlyInItsSummary() {
        // Nothing is drawn where the pieces would be (D104, amended 2026-10-18); the label stays,
        // and a screen reader still hears that the player has captured nothing.
        val rows =
            CapturedPieces.forGame(ChessGame.newGame(), topSide = Side.BLACK) { side ->
                if (side ==
                    Side.WHITE
                ) {
                    CapturedRow.YOU
                } else {
                    "Alex"
                }
            }

        assertTrue(rows.top.captured.isEmpty())
        assertTrue(rows.top.groups.isEmpty())
        assertNull(rows.top.advantageText)
        assertEquals("Alex · Black", rows.top.label)
        assertEquals("Alex, Black. No captures.", rows.top.summary)
        assertEquals("You · White", rows.bottom.label)
        assertEquals("You, White. No captures.", rows.bottom.summary)
        assertEquals(CapturedFit(compact = false, scale = 1f), CapturedPieces.fit(rows.top, 0.dp))
    }

    @Test
    fun onlyTheLeadingSideShowsItsAdvantage() {
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.BLACK)

        assertEquals("+1", rows.top.advantageText)
        assertNull(rows.bottom.advantageText)
    }

    @Test
    fun equalMaterialShowsNoAdvantageAnywhere() {
        val rows = CapturedPieces.forGame(played("e2e4", "d7d5", "e4d5", "d8d5"), topSide = Side.BLACK)

        assertNull(rows.top.advantageText)
        assertNull(rows.bottom.advantageText)
    }

    @Test
    fun aMissingBalanceShowsNoAdvantageRatherThanEquality() {
        val rows = CapturedPieces.rows(listOf(black(PieceType.QUEEN)), whiteBalanceQuarterPoints = null, topSide = Side.BLACK)

        assertNull(rows.top.advantageQuarterPoints)
        assertNull(rows.bottom.advantageQuarterPoints)
    }

    @Test
    fun advantagesAreShownExactlyInQuarterPoints() {
        assertEquals("0.25", CapturedPieces.pointsText(1))
        assertEquals("0.50", CapturedPieces.pointsText(2))
        assertEquals("0.75", CapturedPieces.pointsText(3))
        assertEquals("1", CapturedPieces.pointsText(4))
        assertEquals("1.25", CapturedPieces.pointsText(5))
        assertEquals("9.25", CapturedPieces.pointsText(37))
        assertEquals("39", CapturedPieces.pointsText(156))
        assertEquals("+0.25", CapturedPieces.rows(emptyList(), whiteBalanceQuarterPoints = -1, topSide = Side.BLACK).top.advantageText)
    }

    @Test
    fun aBishopAgainstAKnightIsAQuarterPointAhead() {
        val position =
            GameState(
                board =
                    Board.of(
                        mapOf(
                            Square.parse("e1") to white(PieceType.KING),
                            Square.parse("c1") to white(PieceType.BISHOP),
                            Square.parse("e8") to black(PieceType.KING),
                            Square.parse("b8") to black(PieceType.KNIGHT),
                        ),
                    ),
                sideToMove = Side.WHITE,
                castlingRights = CastlingRights.NONE,
            )

        assertEquals("+0.25", CapturedPieces.forGame(ChessGame(position), topSide = Side.BLACK).bottom.advantageText)
    }

    @Test
    fun aPromotionIsValuedByThePositionNotByTheCapturedTotals() {
        // 1. a4 b5 2. axb5 a6 3. bxa6 Bb7 4. axb7 Nc6 5. bxa8=Q Qxa8
        val game = played("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "b7a8Q", "d8a8")
        val rows = CapturedPieces.forGame(game, topSide = Side.BLACK)

        assertEquals(listOf(white(PieceType.QUEEN)), rows.top.captured)
        assertEquals(
            listOf(black(PieceType.ROOK), black(PieceType.BISHOP), black(PieceType.PAWN), black(PieceType.PAWN)),
            rows.bottom.captured,
        )
        assertEquals("+9.25", rows.bottom.advantageText)
        assertNull(rows.top.advantageText)
    }

    @Test
    fun undoTakesACaptureOffItsRowAndRedoPutsItBack() {
        val captured = played("e2e4", "d7d5", "e4d5")
        val undone = ChessRules.undo(captured, Side.WHITE)

        assertTrue(
            CapturedPieces
                .forGame(undone, topSide = Side.BLACK)
                .bottom.captured
                .isEmpty(),
        )
        assertEquals(listOf(black(PieceType.PAWN)), CapturedPieces.forGame(ChessRules.redo(undone), topSide = Side.BLACK).bottom.captured)
    }

    @Test
    fun aRowSaysWhatItShowsInOneSummary() {
        val label = computerLabel(1)
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.BLACK) { side -> if (side == Side.WHITE) CapturedRow.YOU else label }

        assertEquals("$label, Black. Captured 2 pawns. Ahead by 1 point.", rows.top.summary)
        assertEquals("You, White. Captured pawn.", rows.bottom.summary)

        val promotion = played("a2a4", "b7b5", "a4b5", "a7a6", "b5a6", "c8b7", "a6b7", "b8c6", "b7a8Q", "d8a8")
        assertEquals(
            "White. Captured rook, bishop, 2 pawns. Ahead by 9.25 points.",
            CapturedPieces.forGame(promotion, Side.BLACK).bottom.summary,
        )
    }

    @Test
    fun capturedGlyphsAreTheBoardsGlyphsInTheCapturedPiecesColours() {
        val rows = CapturedPieces.forGame(blackAhead, topSide = Side.BLACK)

        // Black's row holds White's pawns, drawn as the board draws them, white with a dark outline.
        val taken = rows.top.captured.first()
        assertEquals('♟', BoardRendering.glyphFor(taken.type))
        assertEquals(Color(0xFFFFFFFF), PieceStyle.fill(taken.side))
        assertEquals(PieceStyle.fill(Side.BLACK), PieceStyle.outline(taken.side))

        val other = rows.bottom.captured.first()
        assertEquals(Color(0xFF2B2B2B), PieceStyle.fill(other.side))
        assertEquals(PieceStyle.fill(Side.WHITE), PieceStyle.outline(other.side))
        assertFalse(PieceStyle.fill(Side.WHITE) == PieceStyle.fill(Side.BLACK))
    }
}
