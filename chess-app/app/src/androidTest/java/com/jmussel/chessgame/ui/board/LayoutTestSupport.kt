package com.jmussel.chessgame.ui.board

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.ChessGame
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import org.junit.Assert.assertTrue
import kotlin.random.Random

// What the layout tests share (`D073`): the windows, a way to draw into one, and ways to
// measure and tap what was drawn.

/** A window a game screen can be given, in dp, after insets. */
data class Viewport(
    val name: String,
    val width: Dp,
    val height: Dp,
) {
    val spec: GameLayoutSpec
        get() = GameLayoutSpec.forWindow(width, height)

    override fun toString(): String = "$name (${width.value.toInt()}×${height.value.toInt()})"
}

/** Representative windows from `GameLayoutSpecTest`, covering every mode and exception. */
val TWO_PANE_VIEWPORTS =
    listOf(
        Viewport("Pixel 7 stock landscape", 834.dp, 383.dp),
        Viewport("Pixel 7 stock landscape, gesture", 858.dp, 359.dp),
        Viewport("Pixel 7 at owner's size, landscape", 969.dp, 457.dp),
        Viewport("Fire HD 8 landscape", 962.dp, 553.dp),
        Viewport("small phone landscape", 640.dp, 360.dp),
        Viewport("tablet landscape", 1280.dp, 800.dp),
    )

val ONE_COLUMN_VIEWPORTS =
    listOf(
        Viewport("Pixel 7 stock portrait", 411.dp, 914.dp),
        Viewport("Pixel 7 at owner's size, portrait", 485.dp, 1078.dp),
        Viewport("Fire HD 8 portrait", 601.dp, 914.dp),
        Viewport("small phone portrait", 360.dp, 640.dp),
        Viewport("foldable inner", 673.dp, 841.dp),
    )

val SHORT_VIEWPORTS =
    listOf(
        Viewport("split screen", 485.dp, 400.dp),
        Viewport("small phone landscape after insets", 616.dp, 332.dp),
    )

val ALL_VIEWPORTS = TWO_PANE_VIEWPORTS + ONE_COLUMN_VIEWPORTS + SHORT_VIEWPORTS

val FONT_SCALES = listOf(1.0f, 1.3f, 2.0f)

const val VIEWPORT_TAG = "viewport"

/** [content] laid out in [viewport] at [fontScale], whatever the device's own window. */
@Composable
fun InViewport(
    viewport: Viewport,
    fontScale: Float,
    content: @Composable () -> Unit,
) {
    DeviceConfigurationOverride(
        DeviceConfigurationOverride.ForcedSize(DpSize(viewport.width, viewport.height)) then
            DeviceConfigurationOverride.FontScale(fontScale),
    ) {
        Box(modifier = Modifier.fillMaxSize().testTag(VIEWPORT_TAG)) { content() }
    }
}

/** Where [tag] is drawn, in pixels of the root, clipped to what is visible. */
fun ComposeContentTestRule.boundsOf(tag: String): Rect = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

/** How big [tag] is, in pixels, however much of it is scrolled out of view. */
fun ComposeContentTestRule.sizeOf(tag: String): IntSize = onNodeWithTag(tag).fetchSemanticsNode().size

/**
 * Keeps the screen on while [activity] is showing.
 *
 * The layout tests run for several minutes without touching the screen as a person would,
 * so without this the device dozes and the next test finds no window to draw in.
 */
fun keepScreenOn(activity: ComponentActivity) {
    activity.runOnUiThread { activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
}

/**
 * How many pixels a dp of [viewport] is.
 *
 * `ForcedSize` shrinks the density when the device's window is smaller than the viewport,
 * so dp are measured against the viewport as drawn rather than the device's density.
 */
fun ComposeContentTestRule.pixelsPerDp(viewport: Viewport): Float = boundsOf(VIEWPORT_TAG).width / viewport.width.value

/** Fails unless [inner] lies within [outer], allowing a pixel for rounding. */
fun assertInside(
    message: String,
    inner: Rect,
    outer: Rect,
) {
    val fits =
        inner.left >= outer.left - 1 &&
            inner.top >= outer.top - 1 &&
            inner.right <= outer.right + 1 &&
            inner.bottom <= outer.bottom + 1
    assertTrue("$message: $inner is not inside $outer", fits)
}

/** The centre of [square] on a board [boardSize] pixels wide, with [orientation] at the bottom. */
fun squareCentre(
    square: Square,
    orientation: Side,
    boardSize: Float,
): Offset {
    val row = if (orientation == Side.WHITE) Square.RANKS - 1 - square.rank else square.rank
    val column = if (orientation == Side.WHITE) square.file else Square.FILES - 1 - square.file
    val cell = boardSize / Square.FILES

    return Offset((column + 0.5f) * cell, (row + 0.5f) * cell)
}

/** Taps the centre of [square] by its position on the board, not by finding its node. */
fun ComposeContentTestRule.tapSquareAt(
    square: Square,
    orientation: Side,
) {
    onNodeWithTag(CHESS_BOARD_TAG).performTouchInput { click(squareCentre(square, orientation, width.toFloat())) }
}

/**
 * Taps [square] in [viewport] where it is drawn.
 *
 * Where the window does not scroll, by its position on the board. Where it does, the square
 * is scrolled into view and tapped at the centre of its own drawn bounds: on the Pixel 7,
 * taps placed from the board's position missed whichever rank a scroll had just left flush
 * with the window's edge, in the test harness only, while the square's own centre is hit.
 */
fun ComposeContentTestRule.tapSquareIn(
    viewport: Viewport,
    square: Square,
    orientation: Side,
) {
    if (!viewport.spec.scrolls) return tapSquareAt(square, orientation)

    onNodeWithTag(squareTag(square)).performScrollTo().performTouchInput { click() }
}

/** Any node except a board square, whose merged text is a piece glyph like a promotion button's. */
val isNotASquare: SemanticsMatcher =
    SemanticsMatcher("is not a board square") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("square-") != true
    }

/** Whether this node's bounds lie entirely within the viewport. */
fun SemanticsNodeInteraction.assertInsideViewport(
    rule: ComposeContentTestRule,
    message: String,
): SemanticsNodeInteraction {
    assertInside(message, fetchSemanticsNode().boundsInRoot, rule.boundsOf(VIEWPORT_TAG))
    return this
}

/** A new local game with [squares] tapped in turn. */
fun localMoves(vararg squares: String): BoardUiState =
    squares.fold(BoardUiState.newGame()) { state, square -> BoardInteraction.onSquareTapped(state, Square.parse(square)) }

/**
 * `1. Nf3 Nf6 2. Ng1 Ng8`, twice: the start position for the third time, so White may
 * claim a threefold repetition, may undo, and either side may resign.
 *
 * Black's last move would itself entitle the claim, so it is declared first and then
 * played rather than claimed (`D041`).
 */
fun claimablePosition(): BoardUiState {
    val shuffle = listOf("g1", "f3", "g8", "f6", "f3", "g1", "f6", "g8")

    return BoardInteraction.playDeclaredMove(localMoves(*(shuffle + shuffle).toTypedArray()))
}

/** `1. h4 g5 2. hxg5 h6 3. gxh6 Bg7 4. hxg7 a6`, then g7 to h8 tapped: White must choose a promotion. */
fun promotionPending(): BoardUiState =
    localMoves("h2", "h4", "g7", "g5", "h4", "g5", "h7", "h6", "g5", "h6", "f8", "g7", "h6", "g7", "a7", "a6", "g7", "h8")

/**
 * A legal game at least [plies] half-moves long that has not ended, found by playing random
 * legal moves from fixed seeds so every run gets the same one.
 */
fun longGame(plies: Int): BoardUiState {
    for (seed in 0 until 1_000) {
        val random = Random(seed)
        var game = ChessGame.newGame()
        while (!game.isOver && game.history.size < plies) {
            val moves = ChessRules.legalMoves(game)
            game = ChessRules.applyMove(game, moves[random.nextInt(moves.size)])
        }
        if (!game.isOver) return BoardUiState(game = game, orientation = game.sideToMove)
    }
    error("No seed below 1000 gave an unfinished game of $plies plies")
}
