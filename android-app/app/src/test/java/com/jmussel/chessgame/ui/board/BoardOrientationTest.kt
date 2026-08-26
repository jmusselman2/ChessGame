package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardOrientationTest {
    private val start = StandardPosition.BOARD

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState {
        var current = state
        squares.forEach { current = BoardInteraction.onSquareTapped(current, Square.parse(it)) }
        return current
    }

    @Test
    fun whiteSeesItsOwnPiecesAtTheBottom() {
        val rows = BoardRendering.rows(start, Side.WHITE)

        assertTrue(rows.last().all { it.piece?.side == Side.WHITE })
        assertEquals(Square.parse("a1"), rows.last().first().square)
        assertEquals(Square.parse("h8"), rows.first().last().square)
    }

    @Test
    fun blackSeesItsOwnPiecesAtTheBottom() {
        val rows = BoardRendering.rows(start, Side.BLACK)

        assertTrue(rows.last().all { it.piece?.side == Side.BLACK })
        assertEquals(Square.parse("h8"), rows.last().first().square)
        assertEquals(Square.parse("a1"), rows.first().last().square)
    }

    @Test
    fun turningTheBoardReversesFilesAsWellAsRanks() {
        val fromWhite = BoardRendering.squares(start, Side.WHITE).map { it.square }
        val fromBlack = BoardRendering.squares(start, Side.BLACK).map { it.square }

        assertEquals(fromWhite.reversed(), fromBlack)
    }

    @Test
    fun theKingsStayOnTheirOwnFilesWhicheverWayTheBoardFaces() {
        val fromBlack = BoardRendering.rows(start, Side.BLACK)

        assertEquals(Square.parse("e8"), fromBlack.last()[3].square)
        assertEquals(PieceType.KING, fromBlack.last()[3].piece?.type)
        assertEquals(Square.parse("e1"), fromBlack.first()[3].square)
        assertEquals(PieceType.KING, fromBlack.first()[3].piece?.type)
    }

    @Test
    fun theShadesDoNotChangeWithOrientation() {
        BoardRendering.squares(start, Side.BLACK).forEach { drawn ->
            assertEquals(BoardRendering.isLight(drawn.square), drawn.isLight)
        }
        assertEquals(32, BoardRendering.squares(start, Side.BLACK).count { it.isLight })
    }

    @Test
    fun theLabelsFollowTheOrientation() {
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h"), BoardRendering.fileLabels(Side.WHITE))
        assertEquals(listOf("h", "g", "f", "e", "d", "c", "b", "a"), BoardRendering.fileLabels(Side.BLACK))
        assertEquals(listOf("8", "7", "6", "5", "4", "3", "2", "1"), BoardRendering.rankLabels(Side.WHITE))
        assertEquals(listOf("1", "2", "3", "4", "5", "6", "7", "8"), BoardRendering.rankLabels(Side.BLACK))
    }

    @Test
    fun aNewGameFacesWhite() {
        assertEquals(Side.WHITE, BoardUiState.newGame().orientation)
    }

    @Test
    fun passAndPlayTurnsTheBoardToWhoeverIsToMove() {
        val afterWhite = tap(BoardUiState.newGame(), "e2", "e4")
        val afterBlack = tap(afterWhite, "e7", "e5")

        assertEquals(Side.BLACK, afterWhite.orientation)
        assertEquals(Side.WHITE, afterBlack.orientation)
    }

    @Test
    fun theBoardCanAlsoBeTurnedByHand() {
        val flipped = BoardInteraction.flipBoard(BoardUiState.newGame())

        assertEquals(Side.BLACK, flipped.orientation)
        assertEquals(Side.WHITE, BoardInteraction.flipBoard(flipped).orientation)
    }

    @Test
    fun turningTheBoardDoesNotTouchTheGame() {
        val state = BoardUiState.newGame()

        assertEquals(state.game, BoardInteraction.flipBoard(state).game)
    }

    @Test
    fun selectionStillRefersToRealSquaresWhenTheBoardIsTurned() {
        val fromBlack = BoardUiState.newGame().copy(orientation = Side.BLACK)
        val selected = BoardInteraction.onSquareTapped(fromBlack, Square.parse("e2"))

        assertEquals(Square.parse("e2"), selected.selectedSquare)
        assertEquals(Side.BLACK, selected.orientation)
    }
}
