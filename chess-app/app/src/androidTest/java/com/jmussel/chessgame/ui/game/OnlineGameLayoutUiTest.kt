package com.jmussel.chessgame.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.board.ALL_VIEWPORTS
import com.jmussel.chessgame.ui.board.CHESS_BOARD_TAG
import com.jmussel.chessgame.ui.board.InViewport
import com.jmussel.chessgame.ui.board.ONE_COLUMN_VIEWPORTS
import com.jmussel.chessgame.ui.board.TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.Viewport
import com.jmussel.chessgame.ui.board.assertInsideViewport
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.board.tapSquareIn
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The online game screen fitting any window (`D073`), from a fixed server answer: no
 * server is involved. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class OnlineGameLayoutUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(ALL_VIEWPORTS.first())
    private var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(game(yourSide = "BLACK")))

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun playingBlackEverySquareCentreTapsThatSquare() {
        val tapped = mutableStateListOf<Square>()
        show { OnlineGameScreen(state = state, onBack = {}, onSquareTapped = { tapped += it }) }

        ALL_VIEWPORTS.forEach { window ->
            composeRule.runOnIdle {
                viewport = window
                tapped.clear()
            }
            Square.ALL.forEach { square ->
                composeRule.tapSquareIn(window, square, Side.BLACK)
            }
            composeRule.runOnIdle { assertEquals("$window", Square.ALL, tapped.toList()) }
        }
    }

    @Test
    fun aFinishedGameIsReadOnlyWithTwoPanesAndInOneColumn() {
        val finished = game(yourSide = "WHITE", result = "BLACK_WINS", terminationReason = "RESIGNATION", seriesActive = false)
        state = OnlineGameState.Ready(finished)
        show { OnlineGameScreen(state = state, onBack = {}) }

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)
            composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertIsDisplayed().assertInsideViewport(composeRule, "$window: board")
            composeRule.onNodeWithText("Back").assertIsDisplayed()
            composeRule.onNodeWithText(OnlineGame.statusFor(finished)).assertIsDisplayed()
            listOf("Undo", "Resign", "Leave series").forEach { action ->
                composeRule.onNodeWithText(action).assertDoesNotExist()
            }
        }
    }

    @Test
    fun aGameInProgressShowsItsControlsWithTwoPanesWithoutScrolling() {
        state = OnlineGameState.Ready(game(yourSide = "WHITE", canUndo = true, claims = listOf("THREEFOLD_REPETITION")))
        show { OnlineGameScreen(state = state, onBack = {}) }

        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            listOf("Back", "Undo", OnlineGame.claimLabel("THREEFOLD_REPETITION"), "Resign").forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed().assertInsideViewport(composeRule, "$window: $label")
            }
        }
    }

    @Test
    fun aGameStillLoadingOrThatFailedToLoadCanBeLeft() {
        state = OnlineGameState.Loading("game-1")
        show { OnlineGameScreen(state = state, onBack = {}) }

        TWO_PANE_VIEWPORTS.first().let(::showIn)
        composeRule.onNodeWithText("Back").assertIsDisplayed()

        composeRule.runOnIdle { state = OnlineGameState.Failed(gameId = "game-1", message = "Could not load the game.", canRetry = true) }
        composeRule.onNodeWithText("Back").assertIsDisplayed()
        composeRule.onNodeWithText("Could not load the game.").assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun game(
        yourSide: String,
        result: String? = null,
        terminationReason: String? = null,
        canUndo: Boolean = false,
        claims: List<String> = emptyList(),
        seriesActive: Boolean = true,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
        version = 5,
        yourSide = yourSide,
        sideToMove = "WHITE",
        yourTurn = yourSide == "WHITE" && result == null,
        board = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"),
        result = result,
        terminationReason = terminationReason,
        canUndo = canUndo,
        availableDrawClaims = claims,
        seriesActive = seriesActive,
    )
}
