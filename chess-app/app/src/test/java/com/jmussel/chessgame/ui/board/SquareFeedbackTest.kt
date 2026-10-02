package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What every playable board marks on a square, and in what order (`M21.17`): the last
 * move, a king in check, the selected square, and the selected piece's destinations above
 * them all.
 */
class SquareFeedbackTest {
    private val e4 = Square.parse("e4")

    private fun square(
        at: Square = e4,
        piece: Piece? = null,
    ) = BoardSquare(square = at, piece = piece, isLight = BoardRendering.isLight(at))

    private fun feedback(
        square: BoardSquare = square(),
        selected: Square? = null,
        destinations: Set<Square> = emptySet(),
        lastMove: Set<Square> = emptySet(),
        checkedKing: Square? = null,
    ) = BoardRendering.feedbackFor(square, selected, destinations, lastMove, checkedKing)

    @Test
    fun anOrdinarySquareShowsNothingAndSaysNothing() {
        val ordinary = feedback()

        assertEquals(SquareFeedback(), ordinary)
        assertNull(ordinary.description)
    }

    @Test
    fun eachCategoryIsMarkedOnItsOwn() {
        assertEquals(listOf(SquareHighlight.LAST_MOVE), feedback(lastMove = setOf(e4)).highlights)
        assertEquals(listOf(SquareHighlight.CHECK), feedback(checkedKing = e4).highlights)
        assertEquals(listOf(SquareHighlight.SELECTED), feedback(selected = e4).highlights)
        assertEquals(DestinationMark.MOVE, feedback(destinations = setOf(e4)).destination)
        assertEquals(
            "a destination with a piece on it is a capture",
            DestinationMark.CAPTURE,
            feedback(square(piece = Piece(Side.BLACK, PieceType.PAWN)), destinations = setOf(e4)).destination,
        )
    }

    @Test
    fun overlappingTreatmentsAreDrawnInOneSharedOrder() {
        // The order is the same whatever the reason each applies: last move, then check, then
        // selection, with the destination mark above them all.
        val all =
            feedback(
                square(piece = Piece(Side.WHITE, PieceType.KING)),
                selected = e4,
                destinations = setOf(e4),
                lastMove = setOf(e4),
                checkedKing = e4,
            )

        assertEquals(listOf(SquareHighlight.LAST_MOVE, SquareHighlight.CHECK, SquareHighlight.SELECTED), all.highlights)
        assertEquals(DestinationMark.CAPTURE, all.destination)
        assertEquals("last move, king in check, selected, legal capture", all.description)
        assertEquals(
            "the declared order is the drawing order",
            listOf(SquareHighlight.LAST_MOVE, SquareHighlight.CHECK, SquareHighlight.SELECTED),
            SquareHighlight.entries,
        )
    }

    @Test
    fun aCaptureOnTheLastMoveSquareShowsBoth() {
        val recapture = feedback(square(piece = Piece(Side.BLACK, PieceType.PAWN)), destinations = setOf(e4), lastMove = setOf(e4))

        assertEquals(listOf(SquareHighlight.LAST_MOVE), recapture.highlights)
        assertEquals(DestinationMark.CAPTURE, recapture.destination)
        assertEquals("last move, legal capture", recapture.description)
    }

    // --- The checked king comes from `chess-core` on a local board --------------------

    @Test
    fun noKingIsMarkedWithoutACheck() {
        assertNull(BoardRendering.checkedKing(ChessGame.newGame()))
        assertNull(BoardRendering.checkedKing(play("e2e4", "e7e5")))
    }

    @Test
    fun passAndPlayMarksTheKingOfTheSideInCheck() {
        val checked = play("e2e4", "f7f6", "d1h5")

        assertEquals(Square.parse("e8"), BoardRendering.checkedKing(checked))
        assertEquals(setOf(Square.parse("d1"), Square.parse("h5")), BoardRendering.lastMoveSquares(checked))
    }

    @Test
    fun aCheckmatedKingStaysMarked() {
        val mated = play("f2f3", "e7e5", "g2g4", "d8h4")

        assertEquals(Square.parse("e1"), BoardRendering.checkedKing(mated))
    }

    @Test
    fun theComputerGameMarksTheHumansKingWhenTheComputerGivesCheck() {
        val state =
            ComputerGameUiState(
                id = 1,
                opponent = ComputerOpponent(Side.WHITE, 1),
                boardState = BoardUiState(play("f2f3", "e7e5", "g2g4", "d8h4"), orientation = Side.WHITE),
            )

        assertEquals(Square.parse("e1"), BoardRendering.checkedKing(state.game))
        assertEquals(setOf(Square.parse("d8"), Square.parse("h4")), BoardRendering.lastMoveSquares(state.game))
    }

    @Test
    fun promotionChoicesAreNamedForAScreenReader() {
        assertEquals(
            listOf("Queen", "Rook", "Bishop", "Knight"),
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).map(BoardRendering::nameFor),
        )
    }

    private fun play(vararg moves: String): ChessGame =
        moves.fold(
            ChessGame.newGame(),
        ) { game, text -> ChessRules.applyMove(game, Move(Square.parse(text.take(2)), Square.parse(text.drop(2)))) }
}
