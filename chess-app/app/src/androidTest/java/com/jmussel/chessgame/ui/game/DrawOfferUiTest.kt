package com.jmussel.chessgame.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.DrawOfferDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.PendingRequestDto
import com.jmussel.chessgame.api.UndoRequestDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.playedMoves
import com.jmussel.chessgame.ui.board.ALL_VIEWPORTS
import com.jmussel.chessgame.ui.board.InViewport
import com.jmussel.chessgame.ui.board.ROOMY_TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.Viewport
import com.jmussel.chessgame.ui.board.assertPanelShows
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Draw offers on the online game screen and the dashboard (`D097`, `M22.3`), from fixed server
 * answers: no server is involved. Jordan plays White; Alex plays Black. Not run by CI: see
 * `docs/DEVELOPMENT.md`.
 */
class DrawOfferUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(ALL_VIEWPORTS.first())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun offeringADrawTakesOneTapOnEitherTurn() {
        var offered = 0
        show {
            OnlineGameScreen(
                state = OnlineGameState.Ready(game(yourTurn = false, canOfferDraw = true)),
                onBack = {},
                drawOffers = DrawOfferActions(onOffer = { offered++ }),
            )
        }

        composeRule.onNodeWithText(OnlineGame.OFFER_DRAW_LABEL).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("one tap, no confirmation", 1, offered) }
    }

    @Test
    fun theOffererWaitsWithNoCancelAndNoSecondOffer() {
        show { OnlineGameScreen(state = OnlineGameState.Ready(game(drawOffer = DrawOfferDto(fromYou = true))), onBack = {}) }

        composeRule.onNodeWithText("Draw offered. Waiting for Alex.").assertIsDisplayed()
        listOf(OnlineGame.OFFER_DRAW_LABEL, OnlineGame.ACCEPT_LABEL, OnlineGame.DECLINE_LABEL, "Cancel").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun theOtherPlayerAcceptsOrDeclinesInOneTap() {
        var accepted = 0
        var declined = 0
        show {
            OnlineGameScreen(
                state = OnlineGameState.Ready(game(drawOffer = DrawOfferDto(fromYou = false))),
                onBack = {},
                drawOffers = DrawOfferActions(onAccept = { accepted++ }, onDecline = { declined++ }),
            )
        }

        composeRule.onNodeWithText("Alex offers a draw").assertIsDisplayed()
        composeRule.onNodeWithText(OnlineGame.ACCEPT_LABEL).performClick()
        composeRule.onNodeWithText(OnlineGame.DECLINE_LABEL).performClick()
        composeRule.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(1, declined)
        }
    }

    @Test
    fun anUndoRequestAndADrawOfferAreShownTogetherAndFitTwoPanesWhereThereIsRoom() {
        val both =
            game(
                yourSide = "BLACK",
                yourTurn = false,
                opponent = "Jordan",
                request =
                    UndoRequestDto(
                        fromYou = false,
                        requesterMove = MoveDto(from = "g1", to = "f3", san = "Nf3"),
                        replyMove = MoveDto(from = "b8", to = "c6", san = "Nc6"),
                    ),
                drawOffer = DrawOfferDto(fromYou = false),
                canUndo = true,
            )
        show { OnlineGameScreen(state = OnlineGameState.Ready(both), onBack = {}) }

        composeRule.onAllNodesWithText(OnlineGame.ACCEPT_LABEL).assertCountEquals(2)
        // In view without scrolling where there is room beside the captured-piece rows (`D104`).
        // Not on the owner's Pixel 7: with both requests waiting, Resign ends 487 dp down a 457 dp
        // window (measured on the Fire HD 8), so it was never wholly in view there, and the panel
        // scrolls its controls to it (`D105`).
        val roomy = ROOMY_TWO_PANE_VIEWPORTS.filterNot { it.name == "Pixel 7 at owner's size, landscape" }
        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            composeRule.assertPanelShows(
                window,
                listOf("Back", "Jordan asks to undo Nf3 and your Nc6", "Jordan offers a draw", "Resign"),
                roomy,
            )
        }
    }

    @Test
    fun theDashboardRowNamesBothRequestsUndoFirst() {
        val entry =
            DashboardEntryDto(
                seriesId = "series-1",
                opponent = UserSummaryDto(userId = "user-Jordan", username = "Jordan"),
                gameId = "game-1",
                version = 4,
                yourSide = "BLACK",
                sideToMove = "WHITE",
                moveNumber = 3,
                yourTurn = false,
                pendingRequests =
                    listOf(
                        PendingRequestDto(kind = PendingRequestDto.UNDO, waitingOnYou = false),
                        PendingRequestDto(kind = PendingRequestDto.DRAW, waitingOnYou = true),
                    ),
            )
        show { DashboardScreen(entries = listOf(entry), versionName = "test") }

        composeRule.onNodeWithText("YOUR TURN").assertIsDisplayed()
        composeRule.onNodeWithText("Black • Move 3 • Undo requested • Jordan offers a draw").assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun game(
        yourSide: String = "WHITE",
        yourTurn: Boolean = true,
        opponent: String = "Alex",
        canOfferDraw: Boolean = false,
        drawOffer: DrawOfferDto? = null,
        request: UndoRequestDto? = null,
        canUndo: Boolean = false,
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        version = 4,
        yourSide = yourSide,
        sideToMove = if (yourTurn) yourSide else opposite(yourSide),
        yourTurn = yourTurn,
        board = listOf("r.bqkbnr", "pppppppp", "..n.....", "........", "........", ".....N..", "PPPPPPPP", "RNBQKB.R"),
        moves = playedMoves("g1f3", "b8c6"),
        moveNumber = 2,
        canUndo = canUndo,
        canOfferDraw = canOfferDraw,
        drawOffer = drawOffer,
        undoRequest = request,
    )

    private fun opposite(side: String): String = if (side == "WHITE") "BLACK" else "WHITE"
}
