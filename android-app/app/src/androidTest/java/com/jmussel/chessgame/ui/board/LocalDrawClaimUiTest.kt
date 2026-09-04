package com.jmussel.chessgame.ui.board

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * The declared-move draw prompt as the player actually sees it (`D041`).
 *
 * What the declaration means is settled host-side in `DeclaredDrawClaimTest`; what this
 * covers is that `LocalGameScreen` renders the prompt and that its buttons are wired to
 * the claim, to playing the move, and to backing out.
 */
class LocalDrawClaimUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theProspectiveClaimIsOfferedAndEndsTheGameWithoutPlayingTheMove() {
        val declared = declaredThirdOccurrence()
        assertEquals(setOf(DrawClaim.THREEFOLD_REPETITION), GameControls.declaredDrawClaims(declared))

        composeRule.setContent { ChessGameTheme { LocalGameScreen(initialState = declared) } }

        composeRule.onNodeWithText("Playing f6g8", substring = true).assertExists()
        composeRule.onNodeWithText("Claim draw (threefold repetition)").performClick()

        composeRule.onNodeWithText("Draw — THREEFOLD_REPETITION_CLAIM").assertExists()
        composeRule.onNodeWithText("Claim draw (threefold repetition)").assertDoesNotExist()
        composeRule.onNodeWithText("Playing f6g8", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("4. f3g1 f6g8").assertDoesNotExist()
    }

    @Test
    fun theDeclaredMoveCanBePlayedInsteadOfClaimed() {
        composeRule.setContent { ChessGameTheme { LocalGameScreen(initialState = declaredThirdOccurrence()) } }

        composeRule.onNodeWithText("Play f6g8").performClick()

        composeRule.onNodeWithText("WHITE to move").assertExists()
        composeRule.onNodeWithText("4. f3g1 f6g8").assertExists()
        composeRule.onNodeWithText("Playing f6g8", substring = true).assertDoesNotExist()
    }

    @Test
    fun backingOutLeavesTheGameWhereItWas() {
        composeRule.setContent { ChessGameTheme { LocalGameScreen(initialState = declaredThirdOccurrence()) } }

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithText("BLACK to move").assertExists()
        composeRule.onNodeWithText("Playing f6g8", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Claim draw (threefold repetition)").assertDoesNotExist()
        composeRule.onNodeWithText("4. f3g1").assertExists()
    }

    /** `1. Nf3 Nf6 2. Ng1 Ng8 3. Nf3 Nf6 4. Ng1`, with Black's `f6g8` tapped out and declared. */
    private fun declaredThirdOccurrence(): BoardUiState =
        listOf(
            "g1" to "f3",
            "g8" to "f6",
            "f3" to "g1",
            "f6" to "g8",
            "g1" to "f3",
            "g8" to "f6",
            "f3" to "g1",
            "f6" to "g8",
        ).fold(BoardUiState.newGame()) { state, (from, to) -> tap(state, from, to) }

    private fun tap(
        state: BoardUiState,
        vararg squares: String,
    ): BoardUiState = squares.fold(state) { current, square -> BoardInteraction.onSquareTapped(current, Square.parse(square)) }
}
