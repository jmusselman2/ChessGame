package com.jmussel.chessgame.ui.board

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class M5LocalUiAdversarialTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aNonTerminalCheckIsShownToTheLocalPlayer() {
        val checking = tap(BoardUiState.newGame(), "e2", "e4", "f7", "f6", "d1", "h5")
        assertTrue(ChessRules.isInCheck(checking.game.state))

        composeRule.setContent {
            ChessGameTheme {
                LocalGameScreen(initialState = checking)
            }
        }

        composeRule.onNodeWithText("Check", substring = true, ignoreCase = true).assertExists()
    }

    @Test
    fun checkmateIsShownAndTerminalControlsStayHidden() {
        val checkmate = tap(BoardUiState.newGame(), "f2", "f3", "e7", "e5", "g2", "g4", "d8", "h4")
        assertTrue(checkmate.game.isOver)

        composeRule.setContent {
            ChessGameTheme {
                LocalGameScreen(initialState = checkmate)
            }
        }

        composeRule.onNodeWithText("CHECKMATE", substring = true).assertExists()
        composeRule.onNodeWithText("Undo").assertDoesNotExist()
        composeRule.onNodeWithText("Resign as White").assertDoesNotExist()
        composeRule.onNodeWithText("Resign as Black").assertDoesNotExist()
    }

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState =
        squares.fold(state) { current, square ->
            BoardInteraction.onSquareTapped(current, Square.parse(square))
        }
}
