package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UndoNoteDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.playedMoves
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The Redo control on every playable board (`D092`, `M21.21`), from fixed states: no store
 * or server is involved. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class RedoUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(TWO_PANE_VIEWPORTS.first())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)

    @Test
    fun passAndPlayRedoAppearsAfterUndoAndRestoresTheMoveInOneTap() {
        var state by mutableStateOf(LocalGameUiState(boardState = localMoves("e2", "e4", "e7", "e5")))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }
        composeRule.onNodeWithText(GameControls.REDO_LABEL).assertDoesNotExist()

        composeRule.onNodeWithText("Undo Black's move").performClick()
        composeRule.onNodeWithText("1. e4").assertIsDisplayed()

        composeRule
            .onNodeWithText(GameControls.REDO_LABEL)
            .assertIsDisplayed()
            .assert(isButton)
            .performClick()

        composeRule.onNodeWithText("1. e4 e5").assertIsDisplayed()
        composeRule.onNodeWithText("WHITE to move").assertIsDisplayed()
        composeRule.onNodeWithText(GameControls.REDO_LABEL).assertDoesNotExist()
        assertSquare("e5", "last move")
        assertSquare("e7", "last move")
    }

    @Test
    fun afterRedoTheCheckAndTheLastMoveAreTheRestoredOnes() {
        // 1. e4 f6 2. Qh5+, taken back and redone.
        val undone = GameControls.undo(localMoves("e2", "e4", "f7", "f6", "d1", "h5"))
        var state by mutableStateOf(LocalGameUiState(boardState = undone))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        composeRule.onNodeWithText(GameControls.REDO_LABEL).performClick()

        assertSquare("e8", "king in check")
        assertSquare("h5", "last move")
        assertSquare("d1", "last move")
        composeRule.onNodeWithText("BLACK to move — Check").assertIsDisplayed()
    }

    @Test
    fun aFinishedLocalGameOffersNoRedo() {
        val undone = GameControls.undo(localMoves("e2", "e4"))
        show {
            LocalGameScreen(state = LocalGameUiState(boardState = GameControls.resign(undone, Side.WHITE)), onStateChange = {}, onBack = {})
        }

        composeRule.onNodeWithText(GameControls.REDO_LABEL).assertDoesNotExist()
    }

    @Test
    fun theFullestLocalPanelFitsWithTwoPanesAndScrollsToRedoElsewhere() {
        // The third repetition, then a move taken back: Undo, Redo, a claim and its line, and
        // both resignations at once.
        val withEverything = GameControls.undo(tap(claimablePosition(), "e2", "e4"))
        val controls =
            listOf(
                "Back",
                GameControls.undoLabelFor(withEverything)!!,
                GameControls.REDO_LABEL,
                GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION),
                GameControls.CLAIM_EXPLANATION,
                GameControls.resignLabelFor(Side.WHITE),
                GameControls.resignLabelFor(Side.BLACK),
            )
        show { LocalGameScreen(state = LocalGameUiState(boardState = withEverything), onStateChange = {}, onBack = {}) }

        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            composeRule.assertPanelShows(window, controls)
        }
        (ONE_COLUMN_VIEWPORTS + SHORT_VIEWPORTS).forEach { window ->
            showIn(window)
            composeRule.onNodeWithText(GameControls.REDO_LABEL).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun theComputerRedoIsOneTapAndHiddenWhileItThinks() {
        var redone = 0
        val takenBack = ChessRules.undoLastMoves(localMoves("e2", "e4", "e7", "e5").game, 2)
        var state by mutableStateOf(computerGame(BoardUiState(takenBack)))
        show {
            ComputerGameScreen(
                state = state,
                setup = null,
                loading = false,
                actions = ComputerGameActions(onRedo = { redone++ }),
                onBack = {},
            )
        }

        composeRule
            .onNodeWithText(GameControls.REDO_LABEL)
            .assertIsDisplayed()
            .assert(isButton)
            .performClick()
        composeRule.runOnIdle { assertEquals("one tap, no confirmation", 1, redone) }

        composeRule.runOnIdle { state = computerGame(localMoves("e2", "e4"), thinking = true) }
        composeRule.onNodeWithText(GameControls.REDO_LABEL).assertDoesNotExist()
    }

    @Test
    fun theComputerRedoFitsWithTwoPanes() {
        val takenBack = ChessRules.undoLastMoves(localMoves("e2", "e4", "e7", "e5", "g1", "f3", "b8", "c6").game, 2)
        show {
            ComputerGameScreen(
                state =
                    computerGame(
                        BoardUiState(takenBack),
                    ),
                setup = null,
                loading = false,
                actions = ComputerGameActions(),
                onBack = {
                },
            )
        }

        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            listOf(ComputerGameUiState.TAKE_BACK_LABEL, GameControls.REDO_LABEL, "Resign").forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed().assertInsideViewport(composeRule, "$window: $label")
            }
        }
    }

    @Test
    fun onlineRedoIsOfferedWhenTheServerSaysAndWaitsWhileACommandIsOnItsWay() {
        var redone = 0
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(onlineGame(canRedo = true)))
        show { OnlineGameScreen(state = state, onBack = {}, onRedo = { redone++ }) }

        composeRule
            .onNodeWithText(OnlineGame.REDO_LABEL)
            .assertIsDisplayed()
            .assert(isButton)
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertEquals(1, redone) }

        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(canRedo = true), submitting = true) }
        composeRule.onNodeWithText(OnlineGame.REDO_LABEL).assertIsNotEnabled()

        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(canRedo = false)) }
        composeRule.onNodeWithText(OnlineGame.REDO_LABEL).assertDoesNotExist()
    }

    @Test
    fun theFullestOnlinePanelWithRedoFitsWithTwoPanes() {
        // Jordan after a declined request, with a draw to claim and a Redo waiting.
        val game =
            onlineGame(
                canRedo = true,
                canRequestUndo = true,
                note =
                    UndoNoteDto(
                        kind = "DECLINED",
                        requesterMove = MoveDto(from = "g1", to = "f3", san = "Nf3"),
                        replyMove = MoveDto(from = "b8", to = "c6", san = "Nc6"),
                    ),
                claims = listOf("THREEFOLD_REPETITION"),
            )
        show { OnlineGameScreen(state = OnlineGameState.Ready(game), onBack = {}) }

        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            listOf(
                "Back",
                OnlineGame.REDO_LABEL,
                OnlineGame.ASK_TO_UNDO_LABEL,
                "Alex declined your undo request",
                OnlineGame.claimLabel("THREEFOLD_REPETITION"),
                OnlineGame.CLAIM_EXPLANATION,
                "Resign",
            ).let { labels -> composeRule.assertPanelShows(window, labels) }
        }
        (ONE_COLUMN_VIEWPORTS + SHORT_VIEWPORTS).forEach { window ->
            showIn(window)
            composeRule.onNodeWithText(OnlineGame.REDO_LABEL).performScrollTo().assertIsDisplayed()
        }
    }

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState = squares.fold(state) { current, square -> BoardInteraction.onSquareTapped(current, Square.parse(square)) }

    private fun assertSquare(
        square: String,
        shows: String,
    ) {
        composeRule
            .onNodeWithTag(squareTag(Square.parse(square)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, shows))
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun computerGame(
        board: BoardUiState,
        thinking: Boolean = false,
    ) = ComputerGameUiState(
        id = 1,
        opponent = ComputerOpponent(Side.WHITE, 2),
        boardState = board.copy(orientation = Side.WHITE),
        thinking = thinking,
    )

    private fun onlineGame(
        canRedo: Boolean,
        canRequestUndo: Boolean = false,
        note: UndoNoteDto? = null,
        claims: List<String> = emptyList(),
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = 4,
        yourSide = "WHITE",
        sideToMove = "WHITE",
        yourTurn = true,
        board = listOf("r.bqkbnr", "pppppppp", "..n.....", "........", "........", ".....N..", "PPPPPPPP", "RNBQKB.R"),
        moves = playedMoves("g1f3", "b8c6"),
        moveNumber = 2,
        canRedo = canRedo,
        canRequestUndo = canRequestUndo,
        undoNote = note,
        availableDrawClaims = claims,
    )
}
