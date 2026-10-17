package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Board coordinates (`M22.11`), in portrait and wide windows: the true file letters along the
 * bottom and rank numbers along the left, inside the edge squares, and nothing a screen reader
 * says. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class BoardCoordinatesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(ONE_COLUMN_VIEWPORTS.first())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    private fun labelsOf(square: String): EdgeLabels? =
        composeRule
            .onNodeWithTag(squareTag(Square.parse(square)))
            .fetchSemanticsNode()
            .config
            .getOrNull(EdgeLabelsKey)

    @Test
    fun passAndPlayIsLabelledFromWhitesSideInEveryLayout() {
        composeRule.setContent {
            ChessGameTheme { InViewport(viewport, 1f) { LocalGameScreen() } }
        }

        listOf(ONE_COLUMN_VIEWPORTS.first(), TWO_PANE_VIEWPORTS.first()).forEach { window ->
            composeRule.runOnIdle { viewport = window }
            composeRule.waitForIdle()

            assertEquals("$window", EdgeLabels(file = "a", rank = "1"), labelsOf("a1"))
            assertEquals(EdgeLabels(file = "h"), labelsOf("h1"))
            assertEquals(EdgeLabels(rank = "8"), labelsOf("a8"))
            assertNull(labelsOf("e4"))
        }
    }

    @Test
    fun aBoardTurnedForBlackNamesTheTrueSquares() {
        composeRule.setContent {
            ChessGameTheme {
                InViewport(viewport, 1f) { ChessBoard(board = ChessGame.newGame().state.board, side = boardSide, orientation = Side.BLACK) }
            }
        }

        assertEquals(EdgeLabels(file = "h", rank = "8"), labelsOf("h8"))
        assertEquals(EdgeLabels(file = "a"), labelsOf("a8"))
        assertEquals(EdgeLabels(rank = "1"), labelsOf("h1"))
    }

    @Test
    fun theLabelsAreNotSpoken() {
        composeRule.setContent { ChessGameTheme { ChessBoard(board = ChessGame.newGame().state.board, side = boardSide) } }

        // a3 is empty and carries the rank label "3": nothing about it is text or a description.
        val labelled = composeRule.onNodeWithTag(squareTag(Square.parse("a3"))).fetchSemanticsNode()
        assertEquals(EdgeLabels(rank = "3"), labelled.config.getOrNull(EdgeLabelsKey))
        assertNull(labelled.config.getOrNull(SemanticsProperties.ContentDescription))
        assertNull(labelled.config.getOrNull(SemanticsProperties.Text))
        assertEquals(emptyList<Any>(), labelled.children)
    }

    private val boardSide = 320.dp
}
