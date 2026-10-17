package com.jmussel.chessgame.ui.board

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.api.CapturedPieceDto
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.api.MoveDto
import com.jmussel.chessgame.api.UserSummaryDto
import com.jmussel.chessgame.computer.ComputerGameActions
import com.jmussel.chessgame.computer.ComputerGameScreen
import com.jmussel.chessgame.computer.ComputerGameUiState
import com.jmussel.chessgame.computer.computerLabel
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.local.ComputerOpponent
import com.jmussel.chessgame.ui.game.OnlineGame
import com.jmussel.chessgame.ui.game.OnlineGameScreen
import com.jmussel.chessgame.ui.game.OnlineGameState
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/**
 * The captured-piece rows on every live game screen (`D104`, `M23.1`), drawn on a device at the
 * Pixel 7's portrait and landscape windows and the Fire HD 8's portrait one: where they sit for
 * each orientation and layout, that the board keeps its size and its feedback, what a screen
 * reader hears, and that the pieces read in both themes. Not run by CI: see `docs/DEVELOPMENT.md`.
 */
class CapturedPiecesUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var viewport by mutableStateOf(PIXEL_7_PORTRAIT)
    private var fontScale by mutableFloatStateOf(1f)
    private var dark by mutableStateOf(false)

    @Before
    fun keepTheScreenOn() = keepScreenOn(composeRule.activity)

    /** `1. e4 d5 2. exd5`: White has taken a pawn and leads by one. */
    private val whiteTookAPawn = localMoves("e2", "e4", "d7", "d5", "e4", "d5")

    @Test
    fun passAndPlayKeepsBlackAboveTheBoardAndWhiteBelowItOnBothTurns() {
        var state by mutableStateOf(LocalGameUiState(boardState = whiteTookAPawn))
        show { LocalGameScreen(state = state, onStateChange = { state = it }, onBack = {}) }

        ONE_COLUMN_WINDOWS.forEach { window ->
            showIn(window)
            assertRowsAroundTheBoard(window)
            top().assertContentDescriptionEquals("Black. No captures.")
            bottom().assertContentDescriptionEquals("White. Captured pawn. Ahead by 1 point.")
        }

        // Black recaptures: the rows stay where they are, as the board does (`D087`).
        composeRule.runOnIdle { state = LocalGameUiState(boardState = localMoves("e2", "e4", "d7", "d5", "e4", "d5", "d8", "d5")) }
        ONE_COLUMN_WINDOWS.forEach { window ->
            showIn(window)
            assertRowsAroundTheBoard(window)
            top().assertContentDescriptionEquals("Black. Captured pawn.")
            bottom().assertContentDescriptionEquals("White. Captured pawn.")
        }
    }

    @Test
    fun playingBlackOnlineTheOpponentsRowIsAboveTheBoard() {
        show { OnlineGameScreen(state = OnlineGameState.Ready(onlineGame(yourSide = "BLACK")), onBack = {}) }

        ONE_COLUMN_WINDOWS.forEach { window ->
            showIn(window)
            assertRowsAroundTheBoard(window)
            top().assertContentDescriptionEquals("Alex, White. Captured pawn. Ahead by 1 point.")
            bottom().assertContentDescriptionEquals("You, Black. No captures.")
        }
    }

    @Test
    fun playingWhiteOnlineTheSameFactsArePlacedTheOtherWay() {
        show { OnlineGameScreen(state = OnlineGameState.Ready(onlineGame(yourSide = "WHITE")), onBack = {}) }

        showIn(PIXEL_7_PORTRAIT)
        top().assertContentDescriptionEquals("Alex, Black. No captures.")
        bottom().assertContentDescriptionEquals("You, White. Captured pawn. Ahead by 1 point.")
    }

    @Test
    fun theComputerGameNamesTheComputerAboveTheBoard() {
        val level = computerLabel(2)
        val state = ComputerGameUiState(id = 1, opponent = ComputerOpponent(Side.WHITE, 2), boardState = whiteTookAPawn)
        show { ComputerGameScreen(state = state, setup = null, loading = false, actions = ComputerGameActions(), onBack = {}) }

        (ONE_COLUMN_WINDOWS + PIXEL_7_LANDSCAPE).forEach { window ->
            showIn(window)
            top().assertContentDescriptionEquals("$level, Black. No captures.")
            bottom().assertContentDescriptionEquals("You, White. Captured pawn. Ahead by 1 point.")
        }
    }

    @Test
    fun withTwoPanesTheBoardKeepsItsSizeAndTheRowsLeadThePanel() {
        val game = onlineGame(yourSide = "BLACK", canUndo = true)
        show { OnlineGameScreen(state = OnlineGameState.Ready(game), onBack = {}) }

        TWO_PANE_WINDOWS.forEach { window ->
            showIn(window)
            val perDp = composeRule.pixelsPerDp(window)
            val board = composeRule.boundsOf(CHESS_BOARD_TAG)
            assertTrue("$window: two panes", window.liveSpec.twoPane)
            assertEquals("$window: the board a bare layout would have", window.spec.boardSide.value, board.width / perDp, 1f)

            val heading = composeRule.onNodeWithText(OnlineGame.headingFor(game)).fetchSemanticsNode().boundsInRoot
            val undo = composeRule.onNodeWithText(OnlineGame.UNDO_LABEL).fetchSemanticsNode().boundsInRoot
            listOf(composeRule.boundsOf(CAPTURED_TOP_TAG), composeRule.boundsOf(CAPTURED_BOTTOM_TAG)).forEach { row ->
                assertTrue("$window: in the panel, $row beside $board", row.left >= board.right - 1)
                assertTrue("$window: after the heading", row.top >= heading.bottom - 1)
                assertTrue("$window: before the controls", row.bottom <= undo.top + 1)
            }
            assertTrue(
                "$window: top row first",
                composeRule.boundsOf(CAPTURED_TOP_TAG).bottom <= composeRule.boundsOf(CAPTURED_BOTTOM_TAG).top,
            )
            top().assertIsDisplayed().assertInsideViewport(composeRule, "$window: top row")
            bottom().assertIsDisplayed().assertInsideViewport(composeRule, "$window: bottom row")
        }
    }

    @Test
    fun passAndPlayWithTwoPanesKeepsTheBoardsSize() {
        show { LocalGameScreen(state = LocalGameUiState(boardState = whiteTookAPawn), onStateChange = {}, onBack = {}) }

        TWO_PANE_WINDOWS.forEach { window ->
            showIn(window)
            assertEquals(
                "$window: board",
                window.spec.boardSide.value,
                composeRule.boundsOf(CHESS_BOARD_TAG).width / composeRule.pixelsPerDp(window),
                1f,
            )
            top().assertContentDescriptionEquals("Black. No captures.")
            assertTrue(
                "$window: Black's row first",
                composeRule.boundsOf(CAPTURED_TOP_TAG).bottom <= composeRule.boundsOf(CAPTURED_BOTTOM_TAG).top,
            )
        }
    }

    @Test
    fun theBoardsFeedbackIsUntouchedAndNothingOverlapsIt() {
        // 1. e4 d5, and White's pawn picked up: it may advance or take on d5.
        val selected = localMoves("e2", "e4", "d7", "d5", "e4")
        show { LocalGameScreen(state = LocalGameUiState(boardState = selected), onStateChange = {}, onBack = {}) }

        (ONE_COLUMN_WINDOWS + TWO_PANE_WINDOWS).forEach { window ->
            showIn(window)
            assertSquare("e4", "selected")
            assertSquare("e5", "legal move")
            assertSquare("d5", "last move, legal capture")
            assertSquare("d7", "last move")

            val board = composeRule.boundsOf(CHESS_BOARD_TAG)
            val status = composeRule.onNodeWithText(GameControls.statusFor(selected.game)).fetchSemanticsNode().boundsInRoot
            listOf(CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).forEach { tag ->
                val row = composeRule.boundsOf(tag)
                assertTrue("$window: $tag clear of the board", !row.overlaps(board.deflate(1f)))
                assertTrue("$window: $tag clear of the status", !row.overlaps(status.deflate(1f)))
            }
        }
    }

    @Test
    fun aRowIsDisplayOnlyAndOneSummaryWhateverItHolds() {
        show { LocalGameScreen(state = LocalGameUiState(boardState = whiteTookAPawn), onStateChange = {}, onBack = {}) }

        showIn(PIXEL_7_PORTRAIT)
        listOf(top(), bottom()).forEach { row -> row.assertHasNoClickAction() }
        // The glyphs and words inside are not read one by one.
        composeRule.onNodeWithText(CapturedRow.NO_CAPTURES).assertDoesNotExist()
        composeRule.onNodeWithText("+1").assertDoesNotExist()
    }

    @Test
    fun anEmptyRowKeepsItsLabelHeightAndSummaryButShowsNoText() {
        // `D104`, amended 2026-10-18: nothing is drawn where an empty row's pieces would be.
        var screen by mutableIntStateOf(0)
        val computer = ComputerGameUiState(id = 1, opponent = ComputerOpponent(Side.WHITE, 2), boardState = whiteTookAPawn)
        show {
            when (screen) {
                0 -> LocalGameScreen(state = LocalGameUiState(), onStateChange = {}, onBack = {})
                1 -> OnlineGameScreen(state = OnlineGameState.Ready(onlineGame(yourSide = "WHITE")), onBack = {})
                else -> ComputerGameScreen(state = computer, setup = null, loading = false, actions = ComputerGameActions(), onBack = {})
            }
        }

        // Each live screen with an empty top row: pass-and-play from the start, online and the computer.
        listOf("Black. No captures.", "Alex, Black. No captures.", "${computerLabel(2)}, Black. No captures.").forEachIndexed {
            index,
            summary,
            ->
            composeRule.runOnIdle { screen = index }
            (ONE_COLUMN_WINDOWS + TWO_PANE_WINDOWS).forEach { window ->
                FONT_SCALES.forEach { scale ->
                    showIn(window, scale)
                    val where = "screen $index, $window at $scale"
                    val perDp = composeRule.pixelsPerDp(window)

                    composeRule
                        .onAllNodes(hasText(CapturedRow.NO_CAPTURES, substring = true), useUnmergedTree = true)
                        .assertCountEquals(0)
                    top().assertContentDescriptionEquals(summary)
                    assertEquals(
                        "$where: row height",
                        CapturedPieces.ROW_HEIGHT.value,
                        composeRule.sizeOf(CAPTURED_TOP_TAG).height / perDp,
                        1f,
                    )
                    val spec = if (window.liveSpec.twoPane) window.spec else window.liveSpec
                    assertEquals("$where: board", spec.boardSide.value, composeRule.boundsOf(CHESS_BOARD_TAG).width / perDp, 1f)
                }
            }
        }
    }

    @Test
    fun anEmptyRowIsPlainTintBesideItsLabel() {
        // Reading pixels needs PixelCopy, from API 26: the Pixel 7 runs this, the Fire HD 8 skips it.
        assumeTrue("PixelCopy needs API 26", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        show { LocalGameScreen(state = LocalGameUiState(), onStateChange = {}, onBack = {}) }

        listOf(false, true).forEach { theme ->
            composeRule.runOnIdle { dark = theme }
            showIn(PIXEL_7_PORTRAIT)
            listOf(CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).forEach { tag ->
                val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
                // Past the short colour label ("Black", "White"), where "No captures" used to be
                // drawn, the whole middle of the row is the one tint.
                val tint = pixels[pixels.width / 2, pixels.height / 2]
                val from = pixels.width / 5
                val stray =
                    (from until pixels.width * 19 / 20).sumOf { x ->
                        (pixels.height / 5 until pixels.height * 4 / 5).count { y -> !near(pixels[x, y], tint) }
                    }
                assertEquals("${if (theme) "dark" else "light"} theme, $tag: drawn pixels beside the label", 0, stray)
            }
        }
    }

    @Test
    fun aRowIsTheSameHeightEmptyOrFullAtEveryFontScale() {
        show { LocalGameScreen(state = LocalGameUiState(boardState = whiteTookAPawn), onStateChange = {}, onBack = {}) }

        (ONE_COLUMN_WINDOWS + TWO_PANE_WINDOWS).forEach { window ->
            FONT_SCALES.forEach { scale ->
                showIn(window, scale)
                val perDp = composeRule.pixelsPerDp(window)
                listOf(CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).forEach { tag ->
                    val heightDp = composeRule.sizeOf(tag).height / perDp
                    assertEquals("$window at $scale: $tag", CapturedPieces.ROW_HEIGHT.value, heightDp, 1f)
                }
            }
        }
    }

    @Test
    fun thePiecesReadInTheLightAndTheDarkTheme() {
        // Capturing pixels needs PixelCopy, from API 26: the Pixel 7 runs this, the Fire HD 8 skips it.
        assumeTrue("PixelCopy needs API 26", Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        // Each side has taken a pawn: one row of white glyphs, one of black.
        val both = localMoves("e2", "e4", "d7", "d5", "e4", "d5", "d8", "d5")
        show { LocalGameScreen(state = LocalGameUiState(boardState = both), onStateChange = {}, onBack = {}) }

        listOf(false, true).forEach { theme ->
            composeRule.runOnIdle { dark = theme }
            showIn(PIXEL_7_PORTRAIT)
            listOf(CAPTURED_TOP_TAG, CAPTURED_BOTTOM_TAG).forEach { tag ->
                val pixels = composeRule.onNodeWithTag(tag).captureToImage().toPixelMap()
                var fills = 0
                var outlines = 0
                for (x in 0 until pixels.width) {
                    for (y in 0 until pixels.height) {
                        val pixel = pixels[x, y]
                        if (near(pixel, PieceStyle.WHITE)) fills++
                        if (near(pixel, PieceStyle.BLACK)) outlines++
                    }
                }
                // A white glyph has a dark outline and a black glyph a white one, so both colours
                // are on every row, apart from the tint behind them, in either theme.
                val where = "${if (theme) "dark" else "light"} theme, $tag"
                assertTrue("$where: $fills white pixels", fills >= MIN_PIXELS)
                assertTrue("$where: $outlines dark pixels", outlines >= MIN_PIXELS)
            }
        }
    }

    /** One-column rows sit just above and below the board, as wide as it, inside the window. */
    private fun assertRowsAroundTheBoard(window: Viewport) {
        val perDp = composeRule.pixelsPerDp(window)
        val board = composeRule.boundsOf(CHESS_BOARD_TAG)
        val upper = composeRule.boundsOf(CAPTURED_TOP_TAG)
        val lower = composeRule.boundsOf(CAPTURED_BOTTOM_TAG)
        val gap = CapturedPieces.ROW_GAP.value * perDp

        assertTrue("$window: one column", !window.liveSpec.twoPane)
        assertEquals("$window: the board the rule gives", window.liveSpec.boardSide.value, board.width / perDp, 1f)
        assertEquals("$window: top row just above", board.top - gap, upper.bottom, 2f)
        assertEquals("$window: bottom row just below", board.bottom + gap, lower.top, 2f)
        listOf(upper, lower).forEach { row ->
            assertEquals("$window: as wide as the board", board.width, row.width, 2f)
            assertTrue("$window: lined up with the board", abs(board.left - row.left) <= 2f)
        }
        listOf(top(), bottom()).forEach { row -> row.assertIsDisplayed().assertInsideViewport(composeRule, "$window: row") }
    }

    private fun top() = composeRule.onNodeWithTag(CAPTURED_TOP_TAG)

    private fun bottom() = composeRule.onNodeWithTag(CAPTURED_BOTTOM_TAG)

    private fun assertSquare(
        square: String,
        shows: String,
    ) {
        composeRule
            .onNodeWithTag(squareTag(Square.parse(square)))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, shows))
    }

    private fun near(
        pixel: Color,
        target: Color,
    ): Boolean =
        abs(pixel.red - target.red) <= CLOSE &&
            abs(pixel.green - target.green) <= CLOSE &&
            abs(pixel.blue - target.blue) <= CLOSE

    private fun Rect.deflate(by: Float): Rect = Rect(left + by, top + by, right - by, bottom - by)

    /** `1. e4 d5 2. exd5` as the server sends it, with its capture facts and balance. */
    private fun onlineGame(
        yourSide: String,
        canUndo: Boolean = false,
    ): GameViewDto {
        val moves =
            listOf(
                MoveDto(from = "e2", to = "e4", san = "e4"),
                MoveDto(from = "d7", to = "d5", san = "d5"),
                MoveDto(from = "e4", to = "d5", san = "exd5", captured = CapturedPieceDto("BLACK", "PAWN")),
            )
        return GameViewDto(
            gameId = "game-1",
            seriesId = "series-1",
            opponent = UserSummaryDto(userId = "user-1", username = "Alex"),
            version = 4,
            yourSide = yourSide,
            sideToMove = "BLACK",
            yourTurn = yourSide == "BLACK",
            board = listOf("rnbqkbnr", "ppp.pppp", "........", "...P....", "........", "........", "PPPP.PPP", "RNBQKBNR"),
            moves = moves,
            lastMove = moves.last(),
            moveNumber = 2,
            canUndo = canUndo,
            whiteMaterialBalanceQuarterPoints = 4,
        )
    }

    private fun show(content: @Composable () -> Unit) {
        // On the theme's background, as the app's Scaffold draws every screen.
        composeRule.setContent {
            ChessGameTheme(darkTheme = dark, dynamicColor = false) {
                InViewport(viewport, fontScale) { Surface(color = MaterialTheme.colorScheme.background) { content() } }
            }
        }
    }

    private fun showIn(
        window: Viewport,
        scale: Float = 1f,
    ) {
        composeRule.runOnIdle {
            viewport = window
            fontScale = scale
        }
        composeRule.waitForIdle()
    }

    private companion object {
        val PIXEL_7_PORTRAIT = Viewport("Pixel 7 stock portrait", 411.dp, 914.dp)
        val FIRE_HD_8_PORTRAIT = Viewport("Fire HD 8 portrait", 601.dp, 914.dp)
        val PIXEL_7_LANDSCAPE = Viewport("Pixel 7 stock landscape", 834.dp, 383.dp)
        val ONE_COLUMN_WINDOWS =
            listOf(PIXEL_7_PORTRAIT, FIRE_HD_8_PORTRAIT, Viewport("Pixel 7 at owner's size, portrait", 485.dp, 1078.dp))
        val TWO_PANE_WINDOWS = listOf(PIXEL_7_LANDSCAPE, Viewport("Pixel 7 at owner's size, landscape", 969.dp, 457.dp))

        /** How close a pixel must be to a piece colour, per channel, to count as it. */
        const val CLOSE = 0.03f

        /** How many pixels of a colour make a visible glyph or outline rather than a stray edge. */
        const val MIN_PIXELS = 20
    }
}
