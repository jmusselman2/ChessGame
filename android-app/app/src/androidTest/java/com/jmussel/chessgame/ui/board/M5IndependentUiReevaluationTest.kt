package com.jmussel.chessgame.ui.board

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Fresh rendered-screen coverage for the independent M5 re-evaluation. */
class M5IndependentUiReevaluationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aCompleteGameCanBePlayedThroughRenderedBoardTapsAndThenLocks() {
        composeRule.setContent { ChessGameTheme { LocalGameScreen() } }

        assertTrue(composeRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().size >= Square.COUNT)

        tap("f2", Side.WHITE)
        tap("f3", Side.WHITE)
        composeRule.onNodeWithText("BLACK to move").assertExists()
        composeRule.onNodeWithText("1. f2f3").assertExists()

        tap("e7", Side.BLACK)
        tap("e5", Side.BLACK)
        tap("g2", Side.WHITE)
        tap("g4", Side.WHITE)
        tap("d8", Side.BLACK)
        tap("h4", Side.BLACK)

        composeRule.onNodeWithText("BLACK wins — CHECKMATE").assertExists()
        composeRule.onNodeWithText("2. g2g4 d8h4").assertExists()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.onNodeWithText("Resign as White").assertDoesNotExist()
        composeRule.onNodeWithText("Resign as Black").assertDoesNotExist()

        // A terminal board remains clickable for presentation, but the interaction layer
        // must ignore the tap and leave the final position and history unchanged.
        tap("e2", Side.WHITE)
        composeRule.onNodeWithText("BLACK wins — CHECKMATE").assertExists()
        composeRule.onNodeWithText("2. g2g4 d8h4").assertExists()
    }

    private fun tap(
        name: String,
        orientation: Side,
    ) {
        val square = Square.parse(name)
        val row = if (orientation == Side.WHITE) 7 - square.rank else square.rank
        val column = if (orientation == Side.WHITE) square.file else 7 - square.file

        composeRule.onAllNodes(hasClickAction())[row * Square.FILES + column].performClick()
    }
}
