package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyLocalMoveTest {
    private fun white(type: PieceType) = Piece(Side.WHITE, type)

    private fun black(type: PieceType) = Piece(Side.BLACK, type)

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState {
        var current = state
        squares.forEach { current = BoardInteraction.onSquareTapped(current, Square.parse(it)) }
        return current
    }

    private fun uiState(
        vararg placement: Pair<String, Piece>,
        sideToMove: Side = Side.WHITE,
        rights: CastlingRights = CastlingRights.NONE,
    ): BoardUiState =
        BoardUiState(
            ChessGame(
                GameState(
                    board = Board.of(placement.associate { (square, piece) -> Square.parse(square) to piece }),
                    sideToMove = sideToMove,
                    castlingRights = rights,
                ),
            ),
        )

    @Test
    fun tappingALegalDestinationPlaysTheMove() {
        val after = tap(BoardUiState.newGame(), "e2", "e4")

        assertEquals(white(PieceType.PAWN), after.board.pieceAt(Square.parse("e4")))
        assertTrue(after.board.isEmpty(Square.parse("e2")))
        assertEquals(listOf(Move.of("e2", "e4")), after.game.moves)
    }

    @Test
    fun playingAMoveClearsTheSelection() {
        val after = tap(BoardUiState.newGame(), "e2", "e4")

        assertNull(after.selectedSquare)
        assertTrue(BoardInteraction.legalDestinations(after).isEmpty())
    }

    @Test
    fun theTurnPassesToTheOtherSide() {
        val after = tap(BoardUiState.newGame(), "e2", "e4")

        assertEquals(Side.BLACK, after.game.sideToMove)
        assertEquals(Square.parse("e7"), tap(after, "e7").selectedSquare)
    }

    @Test
    fun bothSidesCanPlayInTurn() {
        val after = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5", "g1", "f3")

        assertEquals(
            listOf(Move.of("e2", "e4"), Move.of("e7", "e5"), Move.of("g1", "f3")),
            after.game.moves,
        )
        assertEquals(white(PieceType.KNIGHT), after.board.pieceAt(Square.parse("f3")))
    }

    @Test
    fun tappingAnIllegalDestinationOnlyClearsTheSelection() {
        val after = tap(BoardUiState.newGame(), "e2", "e5")

        assertTrue(after.game.moves.isEmpty())
        assertNull(after.selectedSquare)
    }

    @Test
    fun aCaptureRemovesThePiece() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "d1" to white(PieceType.ROOK),
                "d5" to black(PieceType.KNIGHT),
                "h8" to black(PieceType.KING),
            )
        val after = tap(position, "d1", "d5")

        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("d5")))
        assertEquals(position.board.pieceCount - 1, after.board.pieceCount)
    }

    @Test
    fun castlingMovesTheRookToo() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "h1" to white(PieceType.ROOK),
                "e8" to black(PieceType.KING),
                rights = CastlingRights.ALL,
            )
        val after = tap(position, "e1", "g1")

        assertEquals(white(PieceType.KING), after.board.pieceAt(Square.parse("g1")))
        assertEquals(white(PieceType.ROOK), after.board.pieceAt(Square.parse("f1")))
    }

    @Test
    fun aPromotionAsksForTheChoiceInsteadOfMoving() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )
        val prompted = tap(position, "a7", "a8")

        assertNotNull(prompted.pendingPromotion)
        assertEquals(Square.parse("a7"), prompted.pendingPromotion?.from)
        assertEquals(Square.parse("a8"), prompted.pendingPromotion?.to)
        assertTrue("the move is not played yet", prompted.game.moves.isEmpty())
        assertEquals(PieceType.PROMOTION_CHOICES, prompted.pendingPromotion?.choices)
    }

    @Test
    fun choosingThePromotionPiecePlaysTheMove() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )

        PieceType.PROMOTION_CHOICES.forEach { choice ->
            val prompted = tap(position, "a7", "a8")
            val promoted = BoardInteraction.choosePromotion(prompted, choice)

            assertEquals(white(choice), promoted.board.pieceAt(Square.parse("a8")))
            assertNull(promoted.pendingPromotion)
            assertEquals(listOf(Move.of("a7", "a8", choice)), promoted.game.moves)
        }
    }

    @Test
    fun cancellingThePromotionLeavesTheGameAlone() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )
        val cancelled = BoardInteraction.cancelPromotion(tap(position, "a7", "a8"))

        assertNull(cancelled.pendingPromotion)
        assertTrue(cancelled.game.moves.isEmpty())
        assertEquals(white(PieceType.PAWN), cancelled.board.pieceAt(Square.parse("a7")))
    }

    @Test
    fun tappingTheBoardBacksOutOfThePromotionPrompt() {
        val position =
            uiState(
                "e1" to white(PieceType.KING),
                "a7" to white(PieceType.PAWN),
                "h8" to black(PieceType.KING),
            )
        val after = tap(position, "a7", "a8", "e1")

        assertNull(after.pendingPromotion)
        assertTrue(after.game.moves.isEmpty())
    }

    @Test
    fun aMateEndsTheGameAndStopsFurtherPlay() {
        val position =
            uiState(
                "a1" to white(PieceType.KING),
                "b7" to white(PieceType.ROOK),
                "c6" to white(PieceType.ROOK),
                "h8" to black(PieceType.KING),
            )
        val finished = tap(position, "c6", "c8")

        assertTrue(finished.game.isOver)

        val afterFurtherTaps = tap(finished, "b7", "b8")

        assertEquals(finished.game, afterFurtherTaps.game)
    }

    @Test
    fun theMoveIsPlayedThroughGameCoreSoTheHistoryIsUndoable() {
        val after = tap(BoardUiState.newGame(), "e2", "e4")

        assertEquals(1, after.game.history.size)
        assertEquals(
            BoardUiState.newGame().game.state,
            after.game.history
                .single()
                .positionBefore,
        )
    }
}
