package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The same move feedback on every playable board (`M21.17`): each screen hands the board
 * the right last move, checked king, selection and destinations, and each square says what
 * it shows. From fixed states: no server or store is involved. Not run by CI: see
 * `docs/DEVELOPMENT.md`.
 */
class MoveFeedbackUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(TWO_PANE_VIEWPORTS.first())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun passAndPlayMarksTheSelectionItsMovesACaptureAndTheLastMoveInEveryLayout() {
        // 1. e4 d5, and White's pawn picked up: it may advance or take on d5.
        val selected = localMoves("e2", "e4", "d7", "d5", "e4")
        show { LocalGameScreen(state = LocalGameUiState(boardState = selected), onStateChange = {}, onBack = {}) }

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)
            assertSquare("e4", "selected")
            assertSquare("e5", "legal move")
            assertSquare("d5", "last move, legal capture")
            assertSquare("d7", "last move")
            listOf("e4", "e5", "d5").forEach { square ->
                composeRule
                    .onNodeWithTag(
                        squareTag(Square.parse(square)),
                    ).assertIsDisplayed()
                    .assertInsideViewport(composeRule, "$window: $square")
            }
        }
    }

    @Test
    fun passAndPlayMarksTheKingInCheckUnderTheSelection() {
        // 1. e4 f6 2. Qh5+, and Black picks up the king.
        val checked = localMoves("e2", "e4", "f7", "f6", "d1", "h5", "e8")
        show { LocalGameScreen(state = LocalGameUiState(boardState = checked), onStateChange = {}, onBack = {}) }

        assertSquare("e8", "king in check, selected")
        assertSquare("h5", "last move")
    }

    @Test
    fun theComputerGameMarksTheHumansKingInCheckAndTheComputersMove() {
        val state =
            ComputerGameUiState(
                id = 1,
                opponent = ComputerOpponent(Side.BLACK, 1),
                boardState = localMoves("e2", "e4", "f7", "f6", "d1", "h5").copy(orientation = Side.BLACK),
            )
        show { ComputerGameScreen(state = state, setup = null, loading = false, actions = ComputerGameActions(), onBack = {}) }

        assertSquare("e8", "king in check")
        assertSquare("d1", "last move")
        assertSquare("h5", "last move")
    }

    @Test
    fun theOnlineGameMarksTheServersCheckTheLastMoveAndTheSelection() {
        val checked =
            GameViewDto(
                gameId = "game-1",
                seriesId = "series-1",
                opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                version = 4,
                yourSide = "BLACK",
                sideToMove = "BLACK",
                yourTurn = true,
                inCheck = true,
                board = listOf("rnbqkbnr", "ppppp.pp", ".....p..", ".......Q", "....P...", "........", "PPPP.PPP", "RNB.KBNR"),
                moves = listOf("e2e4", "f7f6", "d1h5"),
                lastMove = MoveDto(from = "d1", to = "h5"),
                moveNumber = 3,
            )
        show { OnlineGameScreen(state = OnlineGameState.Ready(checked, selected = Square.parse("g7")), onBack = {}) }

        assertSquare("e8", "king in check")
        assertSquare("h5", "last move")
        assertSquare("g7", "selected")
        assertSquare("g6", "legal move")
    }

    @Test
    fun everyPromotionChoiceIsNamedAndInViewInEveryLayout() {
        var state by mutableStateOf(LocalGameUiState(boardState = promotionPending()))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            showIn(window)
            listOf("Queen", "Rook", "Bishop", "Knight").forEach { name ->
                composeRule.onNodeWithContentDescription(name).assertIsDisplayed().assertInsideViewport(composeRule, "$window: $name")
            }
        }

        composeRule.onNodeWithContentDescription("Knight").performClick()
        composeRule.runOnIdle {
            assertEquals(
                PieceType.KNIGHT,
                state.boardState.game.lastMove
                    ?.promotion,
            )
        }
    }

    @Test
    fun theOnlinePromotionChoicesAreNamedToo() {
        val pending = promotionPending().pendingPromotion!!
        val chosen = mutableListOf<PieceType>()
        val game =
            GameViewDto(
                gameId = "game-1",
                seriesId = "series-1",
                opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
                version = 9,
                yourSide = "WHITE",
                sideToMove = "WHITE",
                yourTurn = true,
                board = listOf("rnbqk.nr", "pppppp.P", "........", "........", "........", "........", "PPPPPPP.", "RNBQKBNR"),
                moveNumber = 5,
            )
        show {
            OnlineGameScreen(state = OnlineGameState.Ready(game, pendingPromotion = pending), onBack = {}, onChoosePromotion = {
                chosen +=
                    it
            })
        }

        listOf("Queen", "Rook", "Bishop", "Knight").forEach { name -> composeRule.onNodeWithContentDescription(name).assertIsDisplayed() }
        composeRule.onNodeWithContentDescription("Rook").performClick()
        composeRule.runOnIdle { assertEquals(listOf(PieceType.ROOK), chosen) }
    }

    private fun assertSquare(
        square: String,
        shows: String,
    ) {
        composeRule
            .onNodeWithTag(squareTag(Square.parse(square)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, shows))
    }

    private fun show(content: @Composable () -> Unit) {
        composeRule.setContent { ChessGameTheme { InViewport(viewport, 1f) { content() } } }
    }

    private fun showIn(window: Viewport) {
        composeRule.runOnIdle { viewport = window }
        composeRule.waitForIdle()
    }
}
