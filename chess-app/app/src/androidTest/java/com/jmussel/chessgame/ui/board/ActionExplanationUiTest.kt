package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What Undo and a draw claim say where they are offered (`M21.18`), on every board. From
 * fixed states: no server or store is involved. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class ActionExplanationUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun passAndPlayUndoNamesTheSideWhoseMoveItTakesBack() {
        var state by mutableStateOf(LocalGameUiState(boardState = localMoves("e2", "e4")))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        composeRule.onNodeWithText("Undo White's move").assertIsDisplayed()

        composeRule.runOnIdle { state = LocalGameUiState(boardState = localMoves("e2", "e4", "e7", "e5")) }
        composeRule.onNodeWithText("Undo Black's move").assertIsDisplayed()
        composeRule.onNodeWithText("Undo White's move").assertDoesNotExist()
    }

    @Test
    fun aLocalClaimSaysItEndsTheGameAtOnce() {
        show { LocalGameScreen(state = LocalGameUiState(boardState = claimablePosition()), onStateChange = {}, onBack = {}) }

        composeRule.onNodeWithText(GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION)).assertIsDisplayed()
        composeRule.onNodeWithText(GameControls.CLAIM_EXPLANATION).assertIsDisplayed()
    }

    @Test
    fun theComputerUndoSaysWhatItTakesBackBeforeAndAfterTheReply() {
        var state by mutableStateOf(computerGame(localMoves("e2", "e4"), thinking = true))
        show { ComputerGameScreen(state = state, setup = null, loading = false, actions = ComputerGameActions(), onBack = {}) }

        composeRule.onNodeWithText("Undo your move").assertIsDisplayed()

        composeRule.runOnIdle { state = computerGame(localMoves("e2", "e4", "e7", "e5")) }
        composeRule.onNodeWithText("Undo your move and the computer's reply").assertIsDisplayed()
    }

    @Test
    fun onlineUndoIsExplainedAndGoesOnceTheOpponentHasReplied() {
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(onlineGame(canUndo = true)))
        show { OnlineGameScreen(state = state, onBack = {}) }

        composeRule.onNodeWithText(OnlineGame.UNDO_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(OnlineGame.UNDO_EXPLANATION).assertIsDisplayed()

        // Alex has replied: the server no longer offers it, and neither does the screen (`D016`).
        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(canUndo = false)) }
        composeRule.onNodeWithText(OnlineGame.UNDO_LABEL).assertDoesNotExist()
        composeRule.onNodeWithText("No approval needed", substring = true).assertDoesNotExist()
    }

    @Test
    fun onlineClaimsNameTheirRuleSayTheyAreNotOffersAndAskNobodyAnything() {
        val claims = listOf("THREEFOLD_REPETITION", "FIFTY_MOVE_RULE")
        show { OnlineGameScreen(state = OnlineGameState.Ready(onlineGame(canUndo = true, claims = claims)), onBack = {}) }

        claims.forEach { composeRule.onNodeWithText(OnlineGame.claimLabel(it)).assertIsDisplayed() }
        composeRule.onNodeWithText(OnlineGame.CLAIM_EXPLANATION).assertIsDisplayed()

        // No control offers a draw, asks for approval, or sends a command again.
        listOf("offer", "agree", "approv", "accept", "again", "resend", "retry").forEach { word ->
            composeRule.onNode(hasClickAction() and hasText(word, substring = true, ignoreCase = true)).assertDoesNotExist()
        }
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(TWO_PANE_VIEWPORTS.first(), 1f) { content() } } }
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
        canUndo: Boolean,
        claims: List<String> = emptyList(),
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = 2,
        yourSide = "WHITE",
        sideToMove = "BLACK",
        yourTurn = false,
        board = listOf("rnbqkbnr", "pppppppp", "........", "........", "....P...", "........", "PPPP.PPP", "RNBQKBNR"),
        moves = listOf("e2e4"),
        moveNumber = 1,
        canUndo = canUndo,
        availableDrawClaims = claims,
    )
}
