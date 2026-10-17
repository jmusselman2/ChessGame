package com.jmussel.chessgame.ui.review

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.CompletedGameReviewDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.ReviewPlyDto
import com.jmussel.chessgame.api.ReviewPositionDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Online review (`D100`, `M22.10`): offered on a finished game and never on one in play, and
 * stepped through with the same Start, Previous, Next and End as a local review. Not run by CI:
 * see `docs/DEVELOPMENT.md`.
 */
class OnlineReviewUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    private val start = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR")
    private val afterE4 = listOf("rnbqkbnr", "pppppppp", "........", "........", "....P...", "........", "PPPP.PPP", "RNBQKBNR")

    private fun game(result: String? = null) =
        GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 2,
            yourSide = "WHITE",
            sideToMove = "BLACK",
            board = afterE4,
            moves = listOf(MoveDto(from = "e2", to = "e4", san = "e4")),
            result = result,
            terminationReason = result?.let { "RESIGNATION" },
        )

    @Test
    fun aFinishedGameOffersReviewAndAGameInPlayDoesNot() {
        var reviewed = 0
        var finished by mutableStateOf(false)
        composeRule.setContent {
            ChessGameTheme {
                OnlineGameScreen(
                    state = OnlineGameState.Ready(game(result = if (finished) "WHITE_WINS" else null)),
                    onBack = {},
                    onReview = { reviewed++ },
                )
            }
        }

        composeRule.onNodeWithText("Review").assertDoesNotExist()

        composeRule.runOnIdle { finished = true }
        composeRule.onNodeWithText("Review").performClick()
        composeRule.runOnIdle { assertEquals(1, reviewed) }
    }

    @Test
    fun theOnlineReviewStepsWithTheLocalReviewsControls() {
        val projection =
            CompletedGameReviewDto(
                gameId = "game-1",
                version = 2,
                plies = listOf(ReviewPlyDto(MoveDto(from = "e2", to = "e4", san = "e4"), ReviewPositionDto(start, "WHITE", false))),
                finalPosition = ReviewPositionDto(afterE4, "BLACK", false),
                result = "WHITE_WINS",
                terminationReason = "RESIGNATION",
            )
        composeRule.setContent {
            ChessGameTheme {
                var review by remember { mutableStateOf(GameReviews.ofOnline(game(result = "WHITE_WINS"), projection)) }
                GameReviewScreen(review = review, onStep = { review = review.at(it) })
            }
        }

        composeRule.onNodeWithText("Against Alex").assertExists()
        composeRule.onNodeWithText("You won by resignation").assertExists()
        composeRule.onNodeWithText("After e4 (1 of 1)").assertExists()
        composeRule.onNodeWithText("1. e4").assertExists()
        composeRule.onNodeWithText("Next").assertIsNotEnabled()

        composeRule.onNodeWithText("Start").performClick()
        composeRule.onNodeWithText("Start of the game").assertExists()
        composeRule.onNodeWithText("Previous").assertIsNotEnabled()
        composeRule.onNodeWithText("End").performClick()
        composeRule.onNodeWithText("After e4 (1 of 1)").assertExists()
    }

    @Test
    fun aReviewThatCannotBeShownSaysSo() {
        composeRule.setContent {
            ChessGameTheme { GameReviewScreen(review = null, onStep = {}, unavailable = OnlineReviewState.UNAVAILABLE) }
        }

        composeRule.onNodeWithText(OnlineReviewState.UNAVAILABLE).assertExists()
    }
}
