package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Dragging a piece to move it (`M22.12`): held for a long press, then dropped. A legal drop
 * plays the move a tap would, an illegal or cancelled one changes nothing, a promotion still
 * asks first, and a swipe across the board scrolls a short screen without touching the game.
 * Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class PieceDragUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(LocalGameUiState())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    private fun showLocalGame(
        initial: BoardUiState,
        viewport: Viewport = ONE_COLUMN_VIEWPORTS.first(),
    ) {
        state = LocalGameUiState(boardState = initial)
        composeRule.setContent {
            ChessGameTheme {
                InViewport(viewport, 1f) { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }
            }
        }
    }

    private fun TouchInjectionScope.centreOf(square: String): Offset = squareCentre(Square.parse(square), Side.WHITE, width.toFloat())

    /** Presses [from] past the long press, moves in steps to [to] and, unless [cancel], lets go there. */
    private fun dragOnBoard(
        from: String,
        to: String,
        cancel: Boolean = false,
    ) {
        composeRule.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput {
            val start = centreOf(from)
            val end = centreOf(to)
            down(start)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            (1..5).forEach { step -> moveTo(start + (end - start) * (step / 5f)) }
            if (cancel) cancel() else up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun aLegalDropPlaysTheMoveForEitherPlayerOfAFaceToFaceGame() {
        showLocalGame(BoardUiState.newGame())

        dragOnBoard("e2", "e4")
        composeRule.runOnIdle { assertEquals(Move.of("e2", "e4"), state.boardState.game.lastMove) }

        // Black's pieces are drawn upside down for the player opposite (`D087`); theirs drag too.
        dragOnBoard("e7", "e5")
        composeRule.runOnIdle {
            assertEquals(Move.of("e7", "e5"), state.boardState.game.lastMove)
            assertNull(state.boardState.selectedSquare)
        }
    }

    @Test
    fun anIllegalDropReturnsThePieceAndChangesNothing() {
        val initial = localMoves("g1")
        showLocalGame(initial)

        dragOnBoard("e2", "e5")
        composeRule.runOnIdle { assertEquals(initial, state.boardState) }

        dragOnBoard("e7", "e5")
        composeRule.runOnIdle { assertEquals("the other side's piece is not picked up", initial, state.boardState) }
    }

    @Test
    fun aCancelledDragOrOneLetGoWhereItStartedChangesNothing() {
        val initial = BoardUiState.newGame()
        showLocalGame(initial)

        dragOnBoard("e2", "e4", cancel = true)
        composeRule.runOnIdle { assertEquals(initial, state.boardState) }

        dragOnBoard("e2", "e2")
        composeRule.runOnIdle { assertEquals("not even a selection, as a tap would make", initial, state.boardState) }
    }

    @Test
    fun aDragOntoTheLastRankAsksForThePromotionFirst() {
        // `1. h4 g5 2. hxg5 h6 3. gxh6 Bg7 4. hxg7 a6`: White's pawn on g7 takes on h8.
        val ready = localMoves("h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "f8", "g7", "h6", "g7", "a7", "a6")
        showLocalGame(ready)

        dragOnBoard("g7", "h8")
        composeRule.runOnIdle {
            assertNotNull(state.boardState.pendingPromotion)
            assertEquals("nothing is played before the piece is chosen", ready.game, state.boardState.game)
        }

        composeRule.onNodeWithContentDescription("Knight").performClick()
        composeRule.runOnIdle { assertEquals(Move.of("g7", "h8", PieceType.KNIGHT), state.boardState.game.lastMove) }
    }

    @Test
    fun aSwipeStartingOnAPieceScrollsAShortScreenAndMakesNoMove() {
        val initial = BoardUiState.newGame()
        val short = SHORT_VIEWPORTS.first()
        assertTrue("$short scrolls", short.liveSpec.scrolls)
        showLocalGame(initial, short)
        val topBefore = composeRule.boundsOf(CHESS_BOARD_TAG).top

        composeRule.onNodeWithTag(CHESS_BOARD_TAG).performTouchInput {
            val start = centreOf("e2")
            swipe(start = start, end = start - Offset(0f, height * 0.6f), durationMillis = 200)
        }
        composeRule.waitForIdle()

        assertTrue("the screen scrolled", composeRule.boundsOf(CHESS_BOARD_TAG).top < topBefore)
        composeRule.runOnIdle { assertEquals("no move and no selection", initial, state.boardState) }
    }

    @Test
    fun anOnlineDropIsHandedOnForTheServerToDecide() {
        val dropped = mutableStateListOf<Pair<Square, Square>>()
        composeRule.setContent {
            ChessGameTheme {
                InViewport(ONE_COLUMN_VIEWPORTS.first(), 1f) {
                    OnlineGameScreen(
                        state = OnlineGameState.Ready(onlineGame(yourTurn = true)),
                        onBack = {},
                        onPieceDropped = { from, to -> dropped += from to to },
                    )
                }
            }
        }

        dragOnBoard("e2", "e4")
        dragOnBoard("e7", "e5")

        composeRule.runOnIdle {
            assertEquals("only the player's own piece is picked up", listOf(Square.parse("e2") to Square.parse("e4")), dropped.toList())
        }
    }

    @Test
    fun noOnlinePieceIsPickedUpWhenItIsNotThePlayersTurn() {
        val dropped = mutableStateListOf<Pair<Square, Square>>()
        composeRule.setContent {
            ChessGameTheme {
                InViewport(ONE_COLUMN_VIEWPORTS.first(), 1f) {
                    OnlineGameScreen(
                        state = OnlineGameState.Ready(onlineGame(yourTurn = false)),
                        onBack = {},
                        onPieceDropped = { from, to -> dropped += from to to },
                    )
                }
            }
        }

        dragOnBoard("e2", "e4")

        composeRule.runOnIdle { assertTrue(dropped.isEmpty()) }
    }

    private fun onlineGame(yourTurn: Boolean) =
        GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 1,
            yourSide = "WHITE",
            sideToMove = if (yourTurn) "WHITE" else "BLACK",
            yourTurn = yourTurn,
            board = listOf("rnbqkbnr", "pppppppp", "........", "........", "........", "........", "PPPPPPPP", "RNBQKBNR"),
        )
}
