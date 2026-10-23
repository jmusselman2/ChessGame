package com.jmussel.chessgame.ui.review

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.local.StoredLocalGame
import com.jmussel.chessgame.ui.board.InViewport
import com.jmussel.chessgame.ui.board.ONE_COLUMN_VIEWPORTS
import com.jmussel.chessgame.ui.board.SHORT_VIEWPORTS
import com.jmussel.chessgame.ui.board.TWO_PANE_VIEWPORTS
import com.jmussel.chessgame.ui.board.Viewport
import com.jmussel.chessgame.ui.board.keepScreenOn
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Review's Start, Previous, Next and End stay exactly where they are while the player steps
 * (`M23.14`): finding F7 of the 2026-10-22 adversarial UI test was the row moving whenever the
 * review stepped onto or off the last ply, whose ending is a larger headline than a turn line.
 * Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class ReviewControlsUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(WINDOWS.first())
    private var fontScale by mutableFloatStateOf(1f)

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun theStepButtonsDoNotMoveBetweenTheStartAMiddlePlyAndTheEnd() {
        // 1. f3 e5 2. g4 Qh4#: a short game whose ending is a headline at the last ply.
        val mated =
            listOf("f2f3", "e7e5", "g2g4", "d8h4").fold(ChessGame.newGame()) { game, text ->
                ChessRules.applyMove(game, Move.of(text.substring(0, 2), text.substring(2, 4)))
            }
        val base =
            GameReviews.ofLocal(
                StoredLocalGame(id = 5, computer = ComputerOpponent(Side.BLACK, 2), createdAt = 1, completedAt = 2, game = mated),
            )
        var review by mutableStateOf(base)
        composeRule.setContent {
            ChessGameTheme {
                InViewport(viewport, fontScale) { key(viewport, fontScale) { GameReviewScreen(review = review, onStep = {}) } }
            }
        }

        WINDOWS.forEach { window ->
            listOf(1f, 2f).forEach { scale ->
                composeRule.runOnIdle {
                    viewport = window
                    fontScale = scale
                    review = base.at(base.plies)
                }
                composeRule.waitForIdle()
                val atEnd = buttons()

                listOf(0, 2, base.plies - 1, base.plies).forEach { ply ->
                    composeRule.runOnIdle { review = base.at(ply) }
                    composeRule.waitForIdle()
                    assertEquals("$window at $scale, ply $ply: the step buttons did not move", atEnd, buttons())
                }
            }
        }
    }

    /** Where each step button is drawn, unclipped, so a button scrolled away still counts. */
    private fun buttons(): List<Rect> =
        listOf("Start", "Previous", "Next", "End").map { label ->
            val node = composeRule.onNodeWithText(label).fetchSemanticsNode()
            Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
        }

    private companion object {
        val WINDOWS: List<Viewport> =
            listOf(
                ONE_COLUMN_VIEWPORTS.first(),
                ONE_COLUMN_VIEWPORTS.first { it.name == "Fire HD 8 portrait" },
                ONE_COLUMN_VIEWPORTS.first { it.name == "small phone portrait" },
                TWO_PANE_VIEWPORTS.first { it.name == "Pixel 7 at owner's size, landscape" },
                TWO_PANE_VIEWPORTS.first { it.name == "Fire HD 8 landscape" },
                SHORT_VIEWPORTS.first(),
            )
    }
}
