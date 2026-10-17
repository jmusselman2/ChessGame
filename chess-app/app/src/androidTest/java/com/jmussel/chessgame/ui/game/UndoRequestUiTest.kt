package com.jmussel.chessgame.ui.game

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.DashboardEntryDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.PendingRequestDto
import com.jmussel.chessgame.api.UndoNoteDto
import com.jmussel.chessgame.api.UndoRequestDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.api.playedMoves
import com.jmussel.chessgame.ui.board.ALL_VIEWPORTS
import com.jmussel.chessgame.ui.board.InViewport
import com.jmussel.chessgame.ui.board.TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.Viewport
import com.jmussel.chessgame.ui.board.assertInsideViewport
import com.jmussel.chessgame.ui.board.assertPanelShows
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.dashboard.DashboardScreen
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Undo requests on the online game screen and the dashboard (`D091`, `M21.19`), from fixed
 * server answers: no server is involved. Jordan plays White and asks; Alex answers. Not run
 * by CI: see `docs/DEVELOPMENT.md`.
 */
class UndoRequestUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(ALL_VIEWPORTS.first())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun jordanMayAskOnceTheMoveIsAnsweredAndIsNeverOfferedUndoAsWell() {
        var asked = 0
        show {
            OnlineGameScreen(
                state = OnlineGameState.Ready(forJordan(canRequestUndo = true)),
                onBack = {},
                undoRequests = UndoRequestActions(onAsk = { asked++ }),
            )
        }

        composeRule.onNodeWithText(OnlineGame.ASK_TO_UNDO_LABEL).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(OnlineGame.UNDO_LABEL).assertDoesNotExist()
        composeRule.runOnIdle { assertEquals("one tap, no confirmation", 1, asked) }
    }

    @Test
    fun jordanWaitsWithNoCancelAndCanStillPlay() {
        show { OnlineGameScreen(state = OnlineGameState.Ready(forJordan(request = request(fromYou = true))), onBack = {}) }

        composeRule.onNodeWithText("Undo requested. Waiting for Alex.").assertIsDisplayed()
        composeRule.onNodeWithText("Your move").assertIsDisplayed()
        listOf(OnlineGame.ASK_TO_UNDO_LABEL, OnlineGame.ACCEPT_LABEL, OnlineGame.DECLINE_LABEL, "Cancel").forEach {
            composeRule.onNodeWithText(it).assertDoesNotExist()
        }
    }

    @Test
    fun alexSeesTheRequestWithAcceptDeclineAndUndoEachInOneTap() {
        var accepted = 0
        var declined = 0
        show {
            OnlineGameScreen(
                state = OnlineGameState.Ready(forAlex(request = request(fromYou = false))),
                onBack = {},
                undoRequests = UndoRequestActions(onAccept = { accepted++ }, onDecline = { declined++ }),
            )
        }

        composeRule.onNodeWithText("Jordan asks to undo Nf3 and your Nc6").assertIsDisplayed()
        composeRule.onNodeWithText(OnlineGame.UNDO_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText(OnlineGame.ACCEPT_LABEL).performClick()
        composeRule.onNodeWithText(OnlineGame.DECLINE_LABEL).performClick()
        composeRule.runOnIdle {
            assertEquals(1, accepted)
            assertEquals(1, declined)
        }
    }

    @Test
    fun everyNoteIsShown() {
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(forJordan(note = note("ACCEPTED"))))
        show { OnlineGameScreen(state = state, onBack = {}) }

        listOf(
            forJordan(note = note("ACCEPTED")) to "Alex accepted your undo request: Nf3 and Nc6 undone",
            forJordan(note = note("DECLINED"), canRequestUndo = true) to "Alex declined your undo request",
            forJordan(note = note("UNDONE_TOO")) to "Alex had undone Nc6, so Nf3 was undone too",
            forAlex(note = note("BOTH_UNDONE")) to "Jordan had asked to undo Nf3, so both moves were undone",
        ).forEach { (game, words) ->
            composeRule.runOnIdle { state = OnlineGameState.Ready(game) }
            composeRule.onNodeWithText(words).assertIsDisplayed()
        }
    }

    @Test
    fun alexsPanelFitsWithTwoPanesWithoutScrolling() {
        show { OnlineGameScreen(state = OnlineGameState.Ready(forAlex(request = request(fromYou = false))), onBack = {}) }

        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            listOf(
                "Back",
                OnlineGame.UNDO_LABEL,
                "Jordan asks to undo Nf3 and your Nc6",
                OnlineGame.ACCEPT_LABEL,
                OnlineGame.DECLINE_LABEL,
                "Resign",
            ).forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed().assertInsideViewport(composeRule, "$window: $label")
            }
        }
    }

    @Test
    fun jordansPanelAfterAnAnswerFitsWithTwoPanesWithoutScrollingWhereThereIsRoom() {
        val answered = forJordan(note = note("DECLINED"), canRequestUndo = true, claims = listOf("THREEFOLD_REPETITION"))
        show { OnlineGameScreen(state = OnlineGameState.Ready(answered), onBack = {}) }

        // In view without scrolling where there is room beside the captured-piece rows (`D104`).
        TWO_PANE_VIEWPORTS.forEach { window ->
            showIn(window)
            listOf(
                "Back",
                OnlineGame.ASK_TO_UNDO_LABEL,
                "Alex declined your undo request",
                OnlineGame.claimLabel("THREEFOLD_REPETITION"),
                OnlineGame.CLAIM_EXPLANATION,
                "Resign",
            ).let { labels -> composeRule.assertPanelShows(window, labels) }
        }
    }

    @Test
    fun bothDashboardRowsSayWhatWaits() {
        val forAlex =
            entry(opponent = "Jordan", yourTurn = false, pending = PendingRequestDto(kind = "UNDO", waitingOnYou = true))
        val forJordan =
            entry(opponent = "Alex", yourTurn = true, pending = PendingRequestDto(kind = "UNDO", waitingOnYou = false))
        var entries by mutableStateOf(listOf(forAlex))
        show { DashboardScreen(entries = entries, versionName = "test") }

        composeRule.onNodeWithText("YOUR TURN").assertIsDisplayed()
        composeRule.onNodeWithText("White • Move 3 • Jordan asks to undo").assertIsDisplayed()

        composeRule.runOnIdle { entries = listOf(forJordan) }
        composeRule.onNodeWithText("White • Move 3 • Undo requested").assertIsDisplayed()
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun request(fromYou: Boolean) =
        UndoRequestDto(
            fromYou = fromYou,
            requesterMove = MoveDto(from = "g1", to = "f3", san = "Nf3"),
            replyMove = MoveDto(from = "b8", to = "c6", san = "Nc6"),
        )

    private fun note(kind: String) =
        UndoNoteDto(
            kind = kind,
            requesterMove = MoveDto(from = "g1", to = "f3", san = "Nf3"),
            replyMove = MoveDto(from = "b8", to = "c6", san = "Nc6"),
        )

    /** Jordan's view: White, to move, with Alex's b8c6 the latest move. */
    private fun forJordan(
        canRequestUndo: Boolean = false,
        request: UndoRequestDto? = null,
        note: UndoNoteDto? = null,
        claims: List<String> = emptyList(),
    ) = game(
        opponent = "Alex",
        yourSide = "WHITE",
        yourTurn = true,
        canUndo = false,
        canRequestUndo = canRequestUndo,
        request = request,
        note = note,
        claims = claims,
    )

    /** Alex's view: Black, waiting, with their own b8c6 the latest move. */
    private fun forAlex(
        request: UndoRequestDto? = null,
        note: UndoNoteDto? = null,
    ) = game(
        opponent = "Jordan",
        yourSide = "BLACK",
        yourTurn = false,
        canUndo = request != null,
        canRequestUndo = false,
        request = request,
        note = note,
    )

    private fun game(
        opponent: String,
        yourSide: String,
        yourTurn: Boolean,
        canUndo: Boolean,
        canRequestUndo: Boolean,
        request: UndoRequestDto?,
        note: UndoNoteDto?,
        claims: List<String> = emptyList(),
    ) = GameViewDto(
        gameId = "game-1",
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        version = 3,
        yourSide = yourSide,
        sideToMove = "WHITE",
        yourTurn = yourTurn,
        board = listOf("r.bqkbnr", "pppppppp", "..n.....", "........", "........", ".....N..", "PPPPPPPP", "RNBQKB.R"),
        moves = playedMoves("g1f3", "b8c6"),
        moveNumber = 2,
        canUndo = canUndo,
        canRequestUndo = canRequestUndo,
        undoRequest = request,
        undoNote = note,
        availableDrawClaims = claims,
    )

    private fun entry(
        opponent: String,
        yourTurn: Boolean,
        pending: PendingRequestDto,
    ) = DashboardEntryDto(
        seriesId = "series-1",
        opponent = UserSummaryDto(userId = "user-$opponent", username = opponent),
        gameId = "game-1",
        version = 3,
        yourSide = "WHITE",
        sideToMove = "WHITE",
        moveNumber = 3,
        yourTurn = yourTurn,
        pendingRequests = listOf(pending),
    )
}
