package com.jmussel.chessgame.ui.board

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
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.StoredLocalGame
import com.jmussel.chessgame.ui.game.AfterGame
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.localhistory.LocalGameReview
import com.jmussel.chessgame.ui.localhistory.LocalGameReviewScreen
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * What a finished game shows on every board (`M21.16`): how it ended, above the final board
 * and move list, and what the player can do next. From fixed states: no server or store is
 * involved. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class GameEndUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(TWO_PANE_VIEWPORTS.first())
    private val pressed = mutableListOf<String>()

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun aFinishedPassAndPlayGameSaysHowItEndedAndOffersNewGameAndReview() {
        val mated = localMoves("f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
        show {
            LocalGameScreen(
                state = LocalGameUiState(boardState = mated),
                onStateChange = { pressed += "change" },
                onBack = {},
                onNewGame = { pressed += "new game" },
                onReview = { pressed += "review" },
            )
        }

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)
            composeRule.onNodeWithText("Black won by checkmate").assertIsDisplayed().assertInsideViewport(composeRule, "$window: ending")
            composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertIsDisplayed().assertInsideViewport(composeRule, "$window: board")
            composeRule.onNodeWithText("2. g2g4 d8h4").assertExists()
        }
        listOf("Undo", "Resign as White", "Resign as Black").forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }

        composeRule.onNodeWithText("Review").performClick()
        composeRule.onNodeWithText("New game").performClick()
        composeRule.runOnIdle { assertEquals("a finished game starts another without asking", listOf("review", "new game"), pressed) }
    }

    @Test
    fun aFinishedComputerGameOffersPlayAgainNewGameReviewAndLeave() {
        val resigned = ChessRules.resign(play("e2e4", "e7e5"), Side.WHITE)
        val state =
            ComputerGameUiState(
                id = 4,
                opponent = ComputerOpponent(Side.WHITE, 3),
                boardState = BoardUiState(resigned, orientation = Side.WHITE),
            )
        show {
            ComputerGameScreen(
                state = state,
                setup = null,
                loading = false,
                actions =
                    ComputerGameActions(
                        onPlayAgain = { pressed += "play again" },
                        onNewGame = { pressed += "new game" },
                        onReview = { pressed += "review" },
                    ),
                onBack = { pressed += "leave" },
            )
        }

        composeRule.onNodeWithText("The computer won by resignation").assertIsDisplayed()
        composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("1. e2e4 e7e5").assertExists()

        listOf("Play again", "New game", "Review", "Leave").forEach { composeRule.onNodeWithText(it).assertIsDisplayed().performClick() }
        composeRule.runOnIdle { assertEquals(listOf("play again", "new game", "review", "leave"), pressed) }
    }

    @Test
    fun aFinishedOnlineGameSaysItIsFindingTheNextGameAndOffersOnlyWhatTheServerDecided() {
        var state by mutableStateOf<OnlineGameState>(OnlineGameState.Ready(onlineGame(), after = AfterGame.Looking))
        show {
            OnlineGameScreen(
                state = state,
                onBack = {},
                onOpenNextGame = { pressed += "next game" },
                onFindNextGame = { pressed += "find" },
                onDone = { pressed += "dashboard" },
            )
        }

        composeRule.onNodeWithText("Alex won by resignation").assertIsDisplayed()
        composeRule.onNodeWithTag(CHESS_BOARD_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(OnlineGame.afterGameText(AfterGame.Looking, onlineGame())).assertIsDisplayed()
        composeRule.onNodeWithText("Rematch", substring = true, ignoreCase = true).assertDoesNotExist()

        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(), after = AfterGame.NextGame("game-2")) }
        composeRule.onNodeWithText("Play the next game").performClick()

        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(), after = AfterGame.SeriesOver) }
        composeRule.onNodeWithText("That was the last game with Alex.").assertIsDisplayed()
        composeRule.onNodeWithText("Back to your games").performClick()

        composeRule.runOnIdle { state = OnlineGameState.Ready(onlineGame(), after = AfterGame.NotFound) }
        composeRule.onNodeWithText("Try again").performClick()

        composeRule.runOnIdle { assertEquals(listOf("next game", "dashboard", "find"), pressed) }
    }

    @Test
    fun theReviewOfAFinishedGameSaysHowItEndedAtTheFinalMove() {
        val mated = play("f2f3", "e7e5", "g2g4", "d8h4")
        val stored = StoredLocalGame(id = 5, computer = ComputerOpponent(Side.BLACK, 2), createdAt = 1, completedAt = 2, game = mated)
        show { LocalGameReviewScreen(review = LocalGameReview(stored), onStep = {}) }

        composeRule.onNodeWithText("You won by checkmate").assertIsDisplayed()
        composeRule.onNodeWithText("After d8h4 (4 of 4)").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }

    private fun play(vararg moves: String): ChessGame =
        moves.fold(ChessGame.newGame()) { game, text -> ChessRules.applyMove(game, Move.of(text.substring(0, 2), text.substring(2, 4))) }

    private fun onlineGame() =
        GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 3,
            yourSide = "WHITE",
            sideToMove = "WHITE",
            yourTurn = false,
            board = listOf("rnbqkbnr", "pppp.ppp", "........", "....p...", "....P...", "........", "PPPP.PPP", "RNBQKBNR"),
            moves = listOf("e2e4", "e7e5"),
            lastMove = MoveDto(from = "e7", to = "e5"),
            moveNumber = 2,
            result = "BLACK_WINS",
            terminationReason = "RESIGNATION",
        )
}
