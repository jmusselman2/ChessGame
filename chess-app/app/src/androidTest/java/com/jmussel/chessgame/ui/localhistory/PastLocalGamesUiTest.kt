package com.jmussel.chessgame.ui.localhistory

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.LocalGameSummary
import com.jmussel.chessgame.local.StoredLocalGame
import com.jmussel.chessgame.ui.board.squareTag
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Past local games as the player sees them (`M21.3`): the list opens a game, and the review
 * steps through it while the board stays untouchable.
 *
 * What the review shows at each ply is settled host-side in `PastLocalGamesTest`; this
 * covers that the screens draw it and that their buttons are wired.
 */
class PastLocalGamesUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val mated: ChessGame =
        listOf("f2f3", "e7e5", "g2g4", "d8h4").fold(ChessGame.newGame()) { game, text ->
            ChessRules.applyMove(game, Move.of(text.substring(0, 2), text.substring(2, 4)))
        }

    private val stored = StoredLocalGame(id = 3, computer = null, createdAt = 1, completedAt = 2, game = mated)

    @Test
    fun theListOpensTheGameTapped() {
        val summary = LocalGameSummary(id = 3, computer = null, createdAt = 1, completedAt = 2, result = GameResult.checkmate(Side.WHITE))
        var opened: Long? = null

        val state = PastLocalGamesUiState(listOf(summary), loading = false)

        composeRule.setContent { ChessGameTheme { PastLocalGamesScreen(state = state, onOpenGame = { opened = it.id }) } }

        composeRule.onNodeWithText("Black won by checkmate", substring = true).performClick()
        assertEquals(3L, opened)
    }

    @Test
    fun theReviewStepsThroughTheGameAndTheBoardIgnoresTaps() {
        composeRule.setContent {
            ChessGameTheme {
                var review by remember { mutableStateOf(LocalGameReview(stored)) }
                LocalGameReviewScreen(review = review, onStep = { review = review.at(it) })
            }
        }

        composeRule.onNodeWithText("BLACK wins", substring = true).assertExists()
        composeRule.onNodeWithText("After d8h4 (4 of 4)").assertExists()
        composeRule.onNodeWithText("Next").assertIsNotEnabled()

        composeRule.onNodeWithText("Previous").performClick()
        composeRule.onNodeWithText("After g2g4 (3 of 4)").assertExists()
        composeRule.onNodeWithText("BLACK to move").assertExists()

        // A tap on the board does nothing: the game is finished and this is only a review.
        composeRule.onNodeWithTag(squareTag(Square.parse("d8"))).performClick()
        composeRule.onNodeWithText("After g2g4 (3 of 4)").assertExists()

        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithText("Start of the game").assertExists()
        composeRule.onNodeWithText("WHITE to move").assertExists()
        composeRule.onNodeWithText("Previous").assertIsNotEnabled()

        composeRule.onNodeWithText("End").performClick()
        composeRule.onNodeWithText("After d8h4 (4 of 4)").assertExists()
    }
}
