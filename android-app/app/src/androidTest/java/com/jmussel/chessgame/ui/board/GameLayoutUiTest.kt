package com.jmussel.chessgame.ui.board

import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jmussel.chessgame.core.chess.DrawClaim
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The game screen fitting any window (`D073`), drawn on a device.
 *
 * Every window is forced with `DeviceConfigurationOverride`, so one device covers phones,
 * tablets and split screens in both orientations. Not run by CI: see `docs/DEVELOPMENT.md`
 * for running it on a device.
 */
class GameLayoutUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(ALL_VIEWPORTS.first())
    private var fontScale by mutableFloatStateOf(1f)
    private var state by mutableStateOf(LocalGameUiState())

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    @Test
    fun theBoardIsSquareInsideTheWindowAndItsSquaresMeetTheFloor() {
        showLocalGame(LocalGameUiState(boardState = claimablePosition()))

        ALL_VIEWPORTS.forEach { window ->
            FONT_SCALES.forEach { scale ->
                show(window, scale)
                val where = "$window at font scale $scale"
                val size = composeRule.sizeOf(CHESS_BOARD_TAG)
                val board = composeRule.boundsOf(CHESS_BOARD_TAG)
                val sideDp = size.width / composeRule.pixelsPerDp(window)
                val spec = window.spec

                assertTrue("$where: square, $size", abs(size.width - size.height) <= 1)
                assertEquals("$where: the side the rule gives", spec.boardSide.value, sideDp, 1f)
                if (!spec.scrolls) assertInside(where, board, composeRule.boundsOf(VIEWPORT_TAG))
                if (!spec.narrow) {
                    val floor = if (spec.twoPane) GameLayoutSpec.MIN_TWO_PANE_BOARD else GameLayoutSpec.MIN_BOARD
                    assertTrue("$where: ${sideDp / 8} dp squares", sideDp >= floor.value - 1f)
                }
            }
        }
    }

    @Test
    fun everySquareCentreTapsThatSquareWithEitherSideAtTheBottom() {
        val tapped = mutableStateListOf<Square>()
        var orientation by mutableStateOf(Side.WHITE)
        composeRule.setContent {
            ChessGameTheme {
                InViewport(viewport, fontScale) {
                    GameLayout(
                        onBack = {},
                        board = { side ->
                            ChessBoard(
                                board = StandardPosition.BOARD,
                                side = side,
                                orientation = orientation,
                                onSquareClick = { tapped += it },
                            )
                        },
                        controls = { Text(text = "Status") },
                        moveList = {},
                    )
                }
            }
        }

        ALL_VIEWPORTS.forEach { window ->
            Side.entries.forEach { bottom ->
                composeRule.runOnIdle {
                    viewport = window
                    orientation = bottom
                    tapped.clear()
                }
                Square.ALL.forEach { square ->
                    composeRule.tapSquareIn(window, square, bottom)
                }
                composeRule.runOnIdle { assertEquals("$window, $bottom at the bottom", Square.ALL, tapped.toList()) }
            }
        }
    }

    @Test
    fun theLocalBoardTurnsAfterAMoveAndTapsFollowIt() {
        showLocalGame(LocalGameUiState())

        listOf(TWO_PANE_VIEWPORTS.first(), ONE_COLUMN_VIEWPORTS.first()).forEach { window ->
            composeRule.runOnIdle { state = LocalGameUiState() }
            show(window, 1f)

            composeRule.tapSquareAt(Square.parse("e2"), Side.WHITE)
            composeRule.onNodeWithTag(squareTag(Square.parse("e2"))).assertIsSelected()
            composeRule.tapSquareAt(Square.parse("e4"), Side.WHITE)
            composeRule.onNodeWithText("1. e2e4").assertExists()

            // Black now plays from the bottom: e7 is where Black's own second rank is drawn.
            composeRule.tapSquareAt(Square.parse("e7"), Side.BLACK)
            composeRule.onNodeWithTag(squareTag(Square.parse("e7"))).assertIsSelected()
        }
    }

    @Test
    fun withTwoPanesEveryControlIsInViewWithoutScrolling() {
        val position = claimablePosition()
        val controls =
            listOf(
                "Back",
                GameControls.statusFor(position.game),
                "Undo",
                GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION),
                GameControls.resignLabelFor(Side.WHITE),
                GameControls.resignLabelFor(Side.BLACK),
            )
        showLocalGame(LocalGameUiState(boardState = position))

        TWO_PANE_VIEWPORTS.forEach { window ->
            show(window, 1f)
            controls.forEach { label ->
                composeRule.onNodeWithText(label).assertIsDisplayed().assertInsideViewport(composeRule, "$window: $label")
            }
        }
    }

    @Test
    fun thePromotionPromptIsWhollyInViewWithTwoPanes() {
        val pending = promotionPending()
        assertTrue(pending.pendingPromotion != null)
        showLocalGame(LocalGameUiState(boardState = pending))

        TWO_PANE_VIEWPORTS.forEach { window ->
            show(window, 1f)
            composeRule.onNodeWithText("Promote to").assertIsDisplayed().assertInsideViewport(composeRule, "$window: prompt")
            listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT).forEach { type ->
                composeRule
                    .onNode(hasText(BoardRendering.glyphFor(type).toString()) and hasClickAction() and isNotASquare)
                    .assertIsDisplayed()
                    .assertInsideViewport(composeRule, "$window: $type")
            }
        }
    }

    @Test
    fun aLongHistoryScrollsOnItsOwnWhileTheBoardStaysPut() {
        val game = longGame(plies = 170)
        val lines = GameControls.moveListLines(game.game)
        assertTrue("${lines.size} lines", lines.size >= 80)
        showLocalGame(LocalGameUiState(boardState = game))

        listOf(ONE_COLUMN_VIEWPORTS[1], TWO_PANE_VIEWPORTS[2]).forEach { window ->
            show(window, 1f)
            val board = composeRule.boundsOf(CHESS_BOARD_TAG)
            composeRule.onNodeWithText(lines.last()).assertIsNotDisplayed()

            composeRule.onNodeWithText(lines.last()).performScrollTo().assertIsDisplayed()

            assertEquals("$window: the board did not move", board, composeRule.boundsOf(CHESS_BOARD_TAG))
            // In one column the controls stay put too; with two panes they scroll with the panel.
            if (!window.spec.twoPane) composeRule.onNodeWithText(GameControls.statusFor(game.game)).assertIsDisplayed()
        }
    }

    @Test
    fun aShortWindowScrollsToEverySquareAndEveryControl() {
        val position = claimablePosition()
        showLocalGame(LocalGameUiState(boardState = position))

        SHORT_VIEWPORTS.forEach { window ->
            show(window, 1f)
            Square.ALL.forEach { square ->
                composeRule.onNodeWithTag(squareTag(square)).performScrollTo().assertIsDisplayed()
            }
            listOf(
                "Back",
                "Undo",
                GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION),
                GameControls.resignLabelFor(Side.WHITE),
                GameControls.resignLabelFor(Side.BLACK),
            ).forEach { label -> composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed() }

            // A square scrolled back into view can still be played.
            composeRule.onNodeWithTag(squareTag(Square.parse("g1"))).performScrollTo().performClick()
            composeRule.onNodeWithTag(squareTag(Square.parse("g1"))).assertIsSelected()
            composeRule.runOnIdle { state = LocalGameUiState(boardState = position) }
        }
    }

    @Test
    fun atLargeFontScalesThePanelScrollsToEveryControlAndTheBoardStaysPut() {
        val position = claimablePosition()
        showLocalGame(LocalGameUiState(boardState = position))

        TWO_PANE_VIEWPORTS.forEach { window ->
            listOf(1.3f, 2.0f).forEach { scale ->
                show(window, scale)
                val board = composeRule.boundsOf(CHESS_BOARD_TAG)
                listOf(
                    "Back",
                    GameControls.statusFor(position.game),
                    "Undo",
                    GameControls.labelFor(DrawClaim.THREEFOLD_REPETITION),
                    GameControls.resignLabelFor(Side.WHITE),
                    GameControls.resignLabelFor(Side.BLACK),
                    "4. f3g1 f6g8",
                ).forEach { label -> composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed() }
                assertEquals("$window at $scale: the board did not move", board, composeRule.boundsOf(CHESS_BOARD_TAG))
            }
        }
    }

    @Test
    fun piecesKeepTheirSizeWhateverTheFontScale() {
        showLocalGame(LocalGameUiState())
        val king = hasText(BoardRendering.glyphFor(PieceType.KING).toString()) and hasParent(hasTestTag(squareTag(Square.parse("e1"))))

        val heights =
            FONT_SCALES.map { scale ->
                show(ONE_COLUMN_VIEWPORTS[1], scale)
                composeRule
                    .onNode(king, useUnmergedTree = true)
                    .fetchSemanticsNode()
                    .boundsInRoot.height
            }

        heights.forEach { height -> assertEquals("glyph heights $heights", heights.first(), height, 1f) }
    }

    private fun showLocalGame(initial: LocalGameUiState) {
        state = initial
        composeRule.setContent {
            ChessGameTheme {
                InViewport(viewport, fontScale) {
                    LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {})
                }
            }
        }
    }

    private fun show(
        window: Viewport,
        scale: Float,
    ) {
        composeRule.runOnIdle {
            viewport = window
            fontScale = scale
        }
        composeRule.waitForIdle()
    }
}
