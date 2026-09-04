package com.jmussel.chessgame.ui.board

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameControlsTest {
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

    /**
     * Taps [from] to [to] and plays it, including a move a prospective draw claim holds
     * back for the player to decide on first (`D041`).
     */
    private fun move(
        state: BoardUiState,
        from: String,
        to: String,
    ): BoardUiState {
        val tapped = tap(state, from, to)
        return if (tapped.declaredMove == null) tapped else BoardInteraction.playDeclaredMove(tapped)
    }

    private fun shuffled(rounds: Int): BoardUiState =
        (1..rounds).fold(BoardUiState.newGame()) { state, _ ->
            listOf("g1" to "f3", "g8" to "f6", "f3" to "g1", "f6" to "g8")
                .fold(state) { current, (from, to) -> move(current, from, to) }
        }

    private fun quietPosition(halfmoveClock: Int): BoardUiState =
        BoardUiState(
            ChessGame(
                GameState(
                    board =
                        Board.of(
                            mapOf(
                                "a1" to white(PieceType.KING),
                                "d1" to white(PieceType.ROOK),
                                "h8" to black(PieceType.KING),
                                "e8" to black(PieceType.ROOK),
                            ).mapKeys { (square, _) -> Square.parse(square) },
                        ),
                    sideToMove = Side.WHITE,
                    castlingRights = CastlingRights.NONE,
                    drawRuleState = DrawRuleState(halfmoveClock = halfmoveClock),
                ),
            ),
        )

    @Test
    fun aNewGameHasAnEmptyMoveList() {
        assertTrue(GameControls.moveList(ChessGame.newGame()).isEmpty())
        assertTrue(GameControls.moveListLines(ChessGame.newGame()).isEmpty())
    }

    @Test
    fun theMoveListPairsTheMovesByNumber() {
        val played = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5", "g1", "f3")
        val rows = GameControls.moveList(played.game)

        assertEquals(2, rows.size)
        assertEquals(MoveListRow(1, "e2e4", "e7e5"), rows[0])
        assertEquals(MoveListRow(2, "g1f3", null), rows[1])
    }

    @Test
    fun theMoveListRendersOneLinePerNumberedMove() {
        val played = tap(BoardUiState.newGame(), "e2", "e4", "e7", "e5", "g1", "f3")

        assertEquals(listOf("1. e2e4 e7e5", "2. g1f3"), GameControls.moveListLines(played.game))
    }

    @Test
    fun theMoveListShowsThePromotionChoice() {
        val position =
            BoardUiState(
                ChessGame(
                    GameState(
                        board =
                            Board.of(
                                mapOf(
                                    "e1" to white(PieceType.KING),
                                    "a7" to white(PieceType.PAWN),
                                    "h8" to black(PieceType.KING),
                                ).mapKeys { (square, _) -> Square.parse(square) },
                            ),
                        sideToMove = Side.WHITE,
                        castlingRights = CastlingRights.NONE,
                    ),
                ),
            )
        val promoted = BoardInteraction.choosePromotion(tap(position, "a7", "a8"), PieceType.KNIGHT)

        assertEquals(listOf("1. a7a8n"), GameControls.moveListLines(promoted.game))
    }

    @Test
    fun undoIsHiddenBeforeAnyMove() {
        assertFalse(GameControls.canUndo(BoardUiState.newGame()))
        assertNull(GameControls.undoableSide(BoardUiState.newGame()))
    }

    @Test
    fun undoIsOfferedForTheLatestMove() {
        val played = tap(BoardUiState.newGame(), "e2", "e4")

        assertTrue(GameControls.canUndo(played))
        assertEquals(Side.WHITE, GameControls.undoableSide(played))
    }

    @Test
    fun undoingTakesTheMoveBackAndTurnsTheBoard() {
        val played = tap(BoardUiState.newGame(), "e2", "e4")
        val restored = GameControls.undo(played)

        assertEquals(BoardUiState.newGame().game, restored.game)
        assertEquals(Side.WHITE, restored.orientation)
        assertNull(restored.selectedSquare)
    }

    @Test
    fun undoIsHiddenOnceTheGameIsOver() {
        val position =
            BoardUiState(
                ChessGame(
                    GameState(
                        board =
                            Board.of(
                                mapOf(
                                    "a1" to white(PieceType.KING),
                                    "b7" to white(PieceType.ROOK),
                                    "c6" to white(PieceType.ROOK),
                                    "h8" to black(PieceType.KING),
                                ).mapKeys { (square, _) -> Square.parse(square) },
                            ),
                        sideToMove = Side.WHITE,
                        castlingRights = CastlingRights.NONE,
                    ),
                ),
            )
        val finished = tap(position, "c6", "c8")

        assertTrue(finished.game.isOver)
        assertFalse(GameControls.canUndo(finished))
    }

    @Test
    fun claimDrawIsHiddenWhenNoClaimIsValid() {
        assertFalse(GameControls.canClaimDraw(BoardUiState.newGame()))
        assertTrue(GameControls.availableDrawClaims(BoardUiState.newGame()).isEmpty())
        assertFalse(GameControls.canClaimDraw(quietPosition(halfmoveClock = 99)))
    }

    @Test
    fun claimDrawAppearsOnAThreefoldRepetition() {
        val repeated = shuffled(rounds = 2)

        assertTrue(GameControls.canClaimDraw(repeated))
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), GameControls.availableDrawClaims(repeated))
    }

    @Test
    fun claimDrawAppearsOnTheFiftyMoveRule() {
        assertEquals(
            setOf(DrawClaim.FIFTY_MOVE_RULE),
            GameControls.availableDrawClaims(quietPosition(halfmoveClock = 100)),
        )
    }

    @Test
    fun claimingEndsTheGameAsADraw() {
        val claimed = GameControls.claimDraw(shuffled(rounds = 2), DrawClaim.THREEFOLD_REPETITION)

        assertTrue(claimed.game.isOver)
        assertEquals(TerminationReason.THREEFOLD_REPETITION_CLAIM, claimed.game.result?.reason)
        assertFalse(GameControls.canClaimDraw(claimed))
        assertFalse(GameControls.canUndo(claimed))
    }

    @Test
    fun theStatusNamesWhoIsToMove() {
        assertEquals("WHITE to move", GameControls.statusFor(ChessGame.newGame()))
        assertEquals("BLACK to move", GameControls.statusFor(tap(BoardUiState.newGame(), "e2", "e4").game))
    }

    @Test
    fun theStatusReportsALiveCheck() {
        // 1. e4 f6 2. Qh5+ — Black is in check and has to be told so.
        val checked = tap(BoardUiState.newGame(), "e2", "e4", "f7", "f6", "d1", "h5")

        assertFalse(checked.game.isOver)
        assertEquals("BLACK to move — Check", GameControls.statusFor(checked.game))
    }

    @Test
    fun aTerminalResultIsReportedInsteadOfTheCheck() {
        // Fool's mate: checkmate is a check too, and the result is what the player needs.
        val mated = tap(BoardUiState.newGame(), "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")

        assertTrue(mated.game.isOver)
        assertEquals("BLACK wins — CHECKMATE", GameControls.statusFor(mated.game))
        assertEquals("WHITE wins — RESIGNATION", GameControls.statusFor(GameControls.resign(BoardUiState.newGame(), Side.BLACK).game))
        assertEquals(
            "Draw — THREEFOLD_REPETITION_CLAIM",
            GameControls.statusFor(GameControls.claimDraw(shuffled(rounds = 2), DrawClaim.THREEFOLD_REPETITION).game),
        )
    }

    @Test
    fun eachClaimHasItsOwnLabel() {
        assertEquals(
            DrawClaim.entries.size,
            DrawClaim.entries
                .map { GameControls.labelFor(it) }
                .toSet()
                .size,
        )
    }

    @Test
    fun eitherPlayerMayResignWhileTheGameIsRunning() {
        val state = BoardUiState.newGame()

        assertTrue(GameControls.canResign(state))
    }

    @Test
    fun whiteResigningEndsTheGameForBlack() {
        val resigned = GameControls.resign(BoardUiState.newGame(), Side.WHITE)

        assertEquals(Side.BLACK, resigned.game.result?.winner)
        assertEquals(TerminationReason.RESIGNATION, resigned.game.result?.reason)
    }

    @Test
    fun blackResigningEndsTheGameForWhite() {
        val resigned = GameControls.resign(BoardUiState.newGame(), Side.BLACK)

        assertEquals(Side.WHITE, resigned.game.result?.winner)
    }

    @Test
    fun aPlayerMayResignOnTheOpponentsMove() {
        // White has moved, so it is Black's turn; White may still give up.
        val afterWhiteMove =
            BoardInteraction.onSquareTapped(
                BoardInteraction.onSquareTapped(BoardUiState.newGame(), Square.parse("e2")),
                Square.parse("e4"),
            )

        val resigned = GameControls.resign(afterWhiteMove, Side.WHITE)

        assertEquals(Side.BLACK, resigned.game.result?.winner)
    }

    @Test
    fun resigningClearsWhateverTheBoardWasShowing() {
        val selected = BoardInteraction.onSquareTapped(BoardUiState.newGame(), Square.parse("e2"))

        val resigned = GameControls.resign(selected, Side.WHITE)

        assertNull(resigned.selectedSquare)
        assertNull(resigned.pendingPromotion)
    }

    @Test
    fun aFinishedGameOffersNoResignation() {
        val resigned = GameControls.resign(BoardUiState.newGame(), Side.WHITE)

        assertFalse(GameControls.canResign(resigned))
    }

    @Test
    fun theResignLabelNamesTheSideGivingUp() {
        assertEquals("Resign as White", GameControls.resignLabelFor(Side.WHITE))
        assertEquals("Resign as Black", GameControls.resignLabelFor(Side.BLACK))
    }
}
