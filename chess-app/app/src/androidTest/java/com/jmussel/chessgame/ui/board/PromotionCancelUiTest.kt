package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Every promotion prompt offers Cancel (`D107`, `M23.15`), which backs out with nothing played.
 * Finding F4 of the 2026-10-22 adversarial UI test: only the online prompt had it. Not run by
 * CI: see `docs/DEVELOPMENT.md`.
 */
class PromotionCancelUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun passAndPlaysPromptCancelsWithNothingPlayed() {
        val pending = promotionPending()
        var state by mutableStateOf(LocalGameUiState(boardState = pending))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        composeRule
            .onNodeWithText(PROMOTION_CANCEL)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            assertNull("the prompt is gone", state.boardState.pendingPromotion)
            assertEquals("no move was played", pending.game, state.boardState.game)
        }
        composeRule.onNodeWithText("Promote to").assertDoesNotExist()
    }

    @Test
    fun theComputersPromptCancelsWithNothingPlayed() {
        val pending = promotionPending()
        val updates = mutableListOf<BoardUiState>()
        show {
            ComputerGameScreen(
                state = ComputerGameUiState(id = 1, opponent = ComputerOpponent(Side.WHITE, 2), boardState = pending),
                setup = null,
                loading = false,
                actions = ComputerGameActions(onUpdate = { updates += it }),
                onBack = {},
            )
        }

        composeRule
            .onNodeWithText(PROMOTION_CANCEL)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle {
            val update = updates.single()
            assertNull("the prompt is gone", update.pendingPromotion)
            assertEquals("no move was played", pending.game, update.game)
        }
    }

    @Test
    fun theOnlinePromptStillCancels() {
        val pending = promotionPending().pendingPromotion!!
        var cancelled = 0
        val game =
            GameViewDto(
                gameId = "game-1",
                seriesId = "series-1",
                opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                version = 9,
                yourSide = "WHITE",
                sideToMove = "WHITE",
                yourTurn = true,
                board = listOf("rnbqk.nr", "pppppp.P", "........", "........", "........", "........", "PPPPPPP.", "RNBQKBNR"),
                moveNumber = 5,
            )
        show {
            OnlineGameScreen(
                state = OnlineGameState.Ready(game, pendingPromotion = pending),
                onBack = {},
                onCancelPromotion = { cancelled++ },
            )
        }

        composeRule
            .onNodeWithText(PROMOTION_CANCEL)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, cancelled) }
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(ONE_COLUMN_VIEWPORTS.first(), 1f) { content() } } }
    }
}
