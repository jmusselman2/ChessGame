package com.jmussel.chessgame.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.app.ChessApp
import com.jmussel.chessgame.app.StartupState
import com.jmussel.chessgame.navigation.AppNavigation
import com.jmussel.chessgame.ui.ServerWaiting
import com.jmussel.chessgame.ui.board.CHESS_BOARD_TAG
import com.jmussel.chessgame.ui.board.InViewport
import com.jmussel.chessgame.ui.board.ONE_COLUMN_VIEWPORTS
import com.jmussel.chessgame.ui.board.TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.Viewport
import com.jmussel.chessgame.ui.board.assertInsideViewport
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What the online game screen says while it catches up with the server (`M21.15`), from
 * fixed states: no server is involved. Not run by CI: see `docs/DEVELOPMENT.md`.
 *
 * Once a board has been drawn it stays drawn while the game is refreshed, while the server
 * wakes and while live updates reconnect, and nothing offered sends a command again.
 */
class OnlineGameConnectionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(TWO_PANE_VIEWPORTS.first())
    private var state by mutableStateOf<OnlineGameState>(OnlineGameState.Loading(GAME_ID))
    private var liveUpdates by mutableStateOf<LiveUpdates>(LiveUpdates.Live)

    private var retries = 0
    private var reconnects = 0
    private var commands = 0

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun aGameNotYetDrawnSaysItIsLoadingAndThenThatTheServerIsWaking() {
        show()

        composeRule.onNodeWithText("Loading the game…").assertIsDisplayed()
        composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertDoesNotExist()

        composeRule.runOnIdle { state = OnlineGameState.Loading(GAME_ID, waking = true) }
        composeRule.onNodeWithText(ServerWaiting.TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ServerWaiting.DETAIL).assertIsDisplayed()
    }

    @Test
    fun startupWaitsForTheServerInTheSameWords() {
        composeRule.setContent {
            ChessGameTheme { ChessApp(navigation = AppNavigation(), startup = StartupState.Waking(failures = 1, elapsedMillis = 1_000)) }
        }

        composeRule.onNodeWithText(ServerWaiting.TITLE).assertIsDisplayed()
        composeRule.onNodeWithText(ServerWaiting.DETAIL).assertIsDisplayed()
    }

    @Test
    fun aRefreshAndAWakingRefreshKeepTheBoardInEveryLayout() {
        show()

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)

            composeRule.runOnIdle { state = OnlineGameState.Ready(game(), sync = GameSync.Refreshing()) }
            assertBoardStays("$window: refreshing")
            composeRule.onNodeWithText("Refreshing the game…").assertIsDisplayed().assertInsideViewport(composeRule, "$window: refreshing")

            composeRule.runOnIdle { state = OnlineGameState.Ready(game(), sync = GameSync.Refreshing(waking = true)) }
            assertBoardStays("$window: waking")
            composeRule.onNodeWithText(ServerWaiting.TITLE).assertIsDisplayed().assertInsideViewport(composeRule, "$window: waking")
        }
    }

    @Test
    fun aDroppedConnectionKeepsTheBoardAndOffersToReconnectBetweenAttempts() {
        state = OnlineGameState.Ready(game())
        liveUpdates = LiveUpdates.Reconnecting(failedAttempts = 2, waiting = true)
        show()

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)
            assertBoardStays("$window: reconnecting")
            composeRule.onNodeWithText("Reconnecting live updates…").assertIsDisplayed().assertInsideViewport(composeRule, "$window")
        }

        composeRule.onNodeWithText(SyncAction.RECONNECT_NOW.label).performClick()
        composeRule.runOnIdle { assertEquals(1, reconnects) }

        // An attempt is under way: there is nothing to hurry.
        composeRule.runOnIdle { liveUpdates = LiveUpdates.Reconnecting(failedAttempts = 3, waiting = false) }
        composeRule.onNodeWithText("Reconnecting live updates…").assertIsDisplayed()
        composeRule.onNodeWithText(SyncAction.RECONNECT_NOW.label).assertDoesNotExist()

        // Back, and refreshed: nothing left to say.
        composeRule.runOnIdle { liveUpdates = LiveUpdates.Live }
        composeRule.onNodeWithText("Reconnecting live updates…").assertDoesNotExist()
        assertBoardStays("live again")
    }

    @Test
    fun aFailedRefreshKeepsTheBoardAndOffersTryAgain() {
        state = OnlineGameState.Ready(game(), sync = GameSync.RefreshFailed(OnlineGame.refreshUnreachableMessage(), canRetry = true))
        show()

        assertBoardStays("failed refresh")
        composeRule.onNodeWithText(OnlineGame.refreshUnreachableMessage()).assertIsDisplayed()
        composeRule.onNodeWithText(SyncAction.TRY_AGAIN.label).performClick()

        composeRule.runOnIdle {
            assertEquals("trying again reads the game", 1, retries)
            assertEquals("and sends nothing else", 0, commands)
        }
    }

    @Test
    fun aFailedFirstLoadOffersTryAgain() {
        state = OnlineGameState.Failed(GAME_ID, OnlineGame.unreachableMessage(), canRetry = true)
        show()

        composeRule.onNodeWithText(SyncAction.TRY_AGAIN.label).performClick()
        composeRule.runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun aLostCommandAnswerOffersRefreshGameAndNothingThatSendsItAgain() {
        state = OnlineGameState.Ready(game(), sync = GameSync.CommandOutcomeUnknown)
        show()

        assertBoardStays("outcome unknown")
        composeRule.onNodeWithText("No answer from the server").assertIsDisplayed()
        composeRule.onNodeWithText(SyncAction.TRY_AGAIN.label).assertDoesNotExist()
        composeRule.onNodeWithText(SyncAction.REFRESH_GAME.label).performClick()

        composeRule.runOnIdle {
            assertEquals("Refresh game reads the game", 1, retries)
            assertEquals("and never repeats the command", 0, commands)
        }
    }

    private fun assertBoardStays(what: String) {
        composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertIsDisplayed().assertInsideViewport(composeRule, "$what: board")
        // The move list is still the canonical one.
        composeRule.onNodeWithText("1. e2e4").assertExists()
    }

    private fun show(content: @Composable () -> Unit = { Screen() }) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    @Composable
    private fun Screen() {
        OnlineGameScreen(
            state = state,
            onBack = {},
            onRetry = { retries++ },
            liveUpdates = liveUpdates,
            onReconnectNow = { reconnects++ },
            onSquareTapped = { commands++ },
            onChoosePromotion = { commands++ },
            onUndo = { commands++ },
            onClaimDraw = { commands++ },
            onResign = { commands++ },
            onLeaveSeries = { commands++ },
        )
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun game() =
        GameViewDto(
            gameId = GAME_ID,
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 2,
            yourSide = "BLACK",
            sideToMove = "BLACK",
            yourTurn = true,
            board = listOf("rnbqkbnr", "pppppppp", "........", "........", "....P...", "........", "PPPP.PPP", "RNBQKBNR"),
            moves = listOf("e2e4"),
            lastMove = MoveDto(from = "e2", to = "e4"),
            moveNumber = 1,
        )

    private companion object {
        const val GAME_ID = "game-1"
    }
}
