package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.DrawRuleState
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

class M5AdversarialTest {
    @Test
    fun enPassantCanBeSelectedAndPlayedThroughTheLocalInteractionPath() {
        val beforeCapture =
            tap(
                BoardUiState.newGame(),
                "e2",
                "e4",
                "a7",
                "a6",
                "e4",
                "e5",
                "d7",
                "d5",
            )
        val selected = tap(beforeCapture, "e5")

        assertTrue(BoardInteraction.isLegalDestination(selected, Square.parse("d6")))

        val captured = tap(selected, "d6")

        assertEquals(Piece(Side.WHITE, PieceType.PAWN), captured.board.pieceAt(Square.parse("d6")))
        assertTrue(captured.board.isEmpty(Square.parse("d5")))
        assertEquals(Move.of("e5", "d6"), captured.game.moves.last())
        assertEquals(Side.BLACK, captured.orientation)
        assertNull(captured.selectedSquare)
    }

    @Test
    fun aProspectiveThreefoldClaimHasNoLocalUiActionBeforeItsDeclaredMove() {
        val beforeDeclaration =
            tap(
                BoardUiState.newGame(),
                "g1",
                "f3",
                "g8",
                "f6",
                "f3",
                "g1",
                "f6",
                "g8",
                "g1",
                "f3",
                "g8",
                "f6",
                "f3",
                "g1",
            )
        val declaredMove = Move.of("f6", "g8")

        assertEquals(
            setOf(DrawClaim.THREEFOLD_REPETITION),
            ChessRules.availableDrawClaims(beforeDeclaration.game, declaredMove),
        )
        assertFalse("the local UI exposes only current-position claims", GameControls.canClaimDraw(beforeDeclaration))

        val afterOrdinaryTaps = tap(beforeDeclaration, "f6", "g8")

        assertEquals(declaredMove, afterOrdinaryTaps.game.moves.last())
        assertEquals(Side.WHITE, afterOrdinaryTaps.game.sideToMove)
        assertEquals(
            setOf(DrawClaim.THREEFOLD_REPETITION),
            GameControls.availableDrawClaims(afterOrdinaryTaps),
        )
    }

    @Test
    fun aProspectiveFiftyMoveClaimHasNoLocalUiActionBeforeItsDeclaredMove() {
        val beforeDeclaration = quietPosition(halfmoveClock = 99)
        val declaredMove = Move.of("d1", "d2")

        assertEquals(
            setOf(DrawClaim.FIFTY_MOVE_RULE),
            ChessRules.availableDrawClaims(beforeDeclaration.game, declaredMove),
        )
        assertFalse("the local UI exposes only current-position claims", GameControls.canClaimDraw(beforeDeclaration))

        val afterOrdinaryTaps = tap(beforeDeclaration, "d1", "d2")

        assertEquals(declaredMove, afterOrdinaryTaps.game.moves.last())
        assertEquals(100, afterOrdinaryTaps.game.state.halfmoveClock)
        assertEquals(Side.BLACK, afterOrdinaryTaps.game.sideToMove)
        assertEquals(
            setOf(DrawClaim.FIFTY_MOVE_RULE),
            GameControls.availableDrawClaims(afterOrdinaryTaps),
        )
    }

    private fun quietPosition(halfmoveClock: Int): BoardUiState =
        BoardUiState(
            ChessGame(
                GameState(
                    board =
                        Board.of(
                            mapOf(
                                Square.parse("a1") to Piece(Side.WHITE, PieceType.KING),
                                Square.parse("d1") to Piece(Side.WHITE, PieceType.ROOK),
                                Square.parse("h8") to Piece(Side.BLACK, PieceType.KING),
                                Square.parse("e8") to Piece(Side.BLACK, PieceType.ROOK),
                            ),
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                    drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
                ),
            ),
        )

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState =
        squares.fold(state) { current, square ->
            BoardInteraction.onSquareTapped(current, Square.parse(square))
        }
}
