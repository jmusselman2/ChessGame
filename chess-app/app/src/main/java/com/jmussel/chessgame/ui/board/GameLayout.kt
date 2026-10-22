package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min

/**
 * How a game screen divides the space it has between the board and everything else
 * (`D073`).
 *
 * Decided from the window the game screen is given, after insets, and never from the
 * device's orientation: a split-screen window on a tablet held sideways is as tall as it is
 * wide, and a phone in landscape at a large display size can be shorter than the board.
 *
 * - [twoPane]: the board on the left, everything else in a panel on the right that scrolls
 *   on its own. Otherwise one column: the board, then the controls, then the move list.
 * - [scrolls]: the window is too short for a full-size board and the controls, so the
 *   whole screen scrolls instead (one column only). This is the only case in which the
 *   board may be taller than the window.
 * - [narrow]: the window is too narrow for a full-size board, so the board is as wide as
 *   the window and its squares are smaller than 48 dp. Nothing ever scrolls sideways.
 *
 * A live game's captured-piece rows (`D104`, `M23.1`) sit above and below a one-column board,
 * so that board leaves room for them; with two panes they go in the panel, and the board is
 * the size it would be without them.
 */
data class GameLayoutSpec(
    val twoPane: Boolean,
    val boardSide: Dp,
    val scrolls: Boolean = false,
    val narrow: Boolean = false,
) {
    companion object {
        /** The smallest one-column board: eight 48 dp squares, the touch-target standard. */
        val MIN_BOARD: Dp = 384.dp

        /**
         * The smallest two-pane board: eight 44 dp squares.
         *
         * Smaller than [MIN_BOARD] because a phone in landscape is not tall enough for eight
         * 48 dp squares: a stock Pixel 7 has at most 383.4 dp once the status bar is taken
         * out. The project owner chose smaller squares over scrolling the board (`D073`).
         */
        val MIN_TWO_PANE_BOARD: Dp = 352.dp

        /** The narrowest panel beside a two-pane board: room for a line of status text and two buttons. */
        val MIN_PANEL_WIDTH: Dp = 280.dp

        /** The largest two-pane board, so a tablet's spare width goes to the panel instead. */
        val MAX_BOARD: Dp = 640.dp

        /**
         * The height a one-column screen keeps for things other than the board: Back, the
         * status line, and one row of buttons, at font scale 1.0, with the spacing between.
         */
        val MIN_CONTROLS_HEIGHT: Dp = 160.dp

        /** The margin either side of a one-column board, dropped when the width cannot spare it. */
        val SIDE_MARGIN: Dp = 16.dp

        /**
         * How a game screen given [width] by [height] is laid out, with [captureRows] when it
         * shows a live game's captured-piece rows.
         */
        fun forWindow(
            width: Dp,
            height: Dp,
            captureRows: Boolean = false,
        ): GameLayoutSpec {
            val oneColumn = oneColumn(width, if (captureRows) height - CapturedPieces.ROWS_HEIGHT else height)

            // Two panes need a landscape-shaped window with room for the panel beside the
            // board. Where they would give a board under 48 dp squares, one column is
            // preferred when it can show a full-size board without scrolling.
            val twoPaneBoard = min(min(height, width - MIN_PANEL_WIDTH), MAX_BOARD)
            val twoPaneFits = width >= height && twoPaneBoard >= MIN_TWO_PANE_BOARD
            val oneColumnIsFull = !oneColumn.scrolls && !oneColumn.narrow
            if (twoPaneFits && (twoPaneBoard >= MIN_BOARD || !oneColumnIsFull)) {
                return GameLayoutSpec(twoPane = true, boardSide = twoPaneBoard)
            }

            return oneColumn
        }

        private fun oneColumn(
            width: Dp,
            height: Dp,
        ): GameLayoutSpec {
            val usableWidth = if (width - SIDE_MARGIN * 2 >= MIN_BOARD) width - SIDE_MARGIN * 2 else width
            val usableHeight = max(height - MIN_CONTROLS_HEIGHT, 0.dp)
            val narrow = usableWidth < MIN_BOARD

            return when {
                min(usableWidth, usableHeight) >= MIN_BOARD ->
                    GameLayoutSpec(twoPane = false, boardSide = min(usableWidth, usableHeight))

                narrow && usableHeight >= usableWidth ->
                    GameLayoutSpec(twoPane = false, boardSide = usableWidth, narrow = true)

                else ->
                    GameLayoutSpec(twoPane = false, boardSide = min(MIN_BOARD, usableWidth), scrolls = true, narrow = narrow)
            }
        }
    }
}

/**
 * A game screen: the [board], and beside or below it Back, the [header], the [controls] and
 * the [moveHistory], arranged by [GameLayoutSpec].
 *
 * Shared by the local, the online and the review screens so all fit a window the same way. The
 * board is given its side and never moves while the rest scrolls. Back is drawn only when
 * [onBack] is given.
 *
 * A live game passes its [captures] (`D104`, `M23.1`): in one column the top row sits just
 * above the board and the bottom row just below it, as wide as the board; with two panes both
 * sit in the panel after the [header], which says whose game it is, and before the controls.
 * The [header] otherwise leads the controls.
 *
 * The move list has its own bounded scroll below everything else (`D105`, `M23.2`), so
 * scrolling it never moves the board, Back, the header, the captured pieces or a control. When
 * a window is too short for all of those at once, they scroll in a region of their own above the
 * list ([FixedThenHistory]); the two never share a scroll.
 */
@Composable
fun GameLayout(
    onBack: (() -> Unit)?,
    board: @Composable (side: Dp) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    moveHistory: MoveHistory = MoveHistory.EMPTY,
    moveHistoryStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    captures: CapturedRows? = null,
    header: @Composable ColumnScope.() -> Unit = {},
) {
    // An empty list is not drawn at all, so it takes no room and no touches: a swipe that ends
    // up nearest an empty region at the window's edge still scrolls a short screen (`M22.12`).
    val history: @Composable () -> Unit = {
        if (moveHistory.rows.isNotEmpty()) {
            MoveHistoryList(history = moveHistory, style = moveHistoryStyle, modifier = Modifier.fillMaxSize())
        }
    }
    // A nonempty list always keeps one row, so its current move is in view; the controls get
    // everything else first.
    val reserve = if (moveHistory.rows.isEmpty()) 0.dp else oneMoveRowHeight(moveHistoryStyle)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spec = GameLayoutSpec.forWindow(maxWidth, maxHeight, captureRows = captures != null)

        when {
            spec.twoPane ->
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.width(spec.boardSide).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        board(spec.boardSide)
                    }

                    // Controls first, then the move list in what is left, each scrolling apart.
                    FixedThenHistory(
                        reserve = reserve,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = PANEL_PADDING, vertical = SPACING),
                        fixed = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(SPACING),
                            ) {
                                onBack?.let { GameBackButton(onClick = it) }
                                header()
                                captures?.let { CapturedPiecesPanel(rows = it, modifier = Modifier.fillMaxWidth()) }
                                controls()
                            }
                        },
                        history = history,
                    )
                }

            spec.scrolls ->
                // Too short for the board and the controls at once: they scroll together, as
                // `D073` allows, and the move list keeps its own region below them (`D105`).
                FixedThenHistory(
                    reserve = reserve,
                    modifier = Modifier.fillMaxSize(),
                    fixed = {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(SPACING),
                        ) {
                            onBack?.let { GameBackButton(onClick = it, modifier = Modifier.padding(horizontal = SPACING)) }
                            CentredBoard(side = spec.boardSide, board = board, captures = captures)
                            Column(
                                modifier = Modifier.padding(horizontal = PANEL_PADDING),
                                verticalArrangement = Arrangement.spacedBy(SPACING),
                            ) {
                                header()
                                controls()
                            }
                        }
                    },
                    history = { Box(modifier = Modifier.padding(horizontal = PANEL_PADDING)) { history() } },
                )

            else ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SPACING),
                ) {
                    onBack?.let { GameBackButton(onClick = it, modifier = Modifier.padding(horizontal = SPACING)) }
                    CentredBoard(side = spec.boardSide, board = board, captures = captures)

                    // The controls take the height they need, scrolling on their own only when
                    // even that is not there; the move list gets what is left.
                    FixedThenHistory(
                        reserve = reserve,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = PANEL_PADDING),
                        fixed = {
                            Column(
                                modifier = Modifier.verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(SPACING),
                            ) {
                                header()
                                controls()
                            }
                        },
                        history = history,
                    )
                }
        }
    }
}

/**
 * The [fixed] content above the [history], in the height this is given (`D105`): the fixed
 * content takes what it needs, up to all but [reserve], and scrolls within that if it needs
 * more; the history gets the rest, at least [reserve], and scrolls on its own. Neither moves
 * when the other scrolls.
 *
 * The [reserve] is one row of a nonempty list, so its current move stays in view: the controls
 * come first, and only a window too short for all of them and that one row scrolls them.
 */
@Composable
private fun FixedThenHistory(
    reserve: Dp,
    fixed: @Composable () -> Unit,
    history: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(contents = listOf(fixed, history), modifier = modifier) { (fixedParts, historyParts), constraints ->
        val gap = HISTORY_GAP.roundToPx()
        val height = constraints.maxHeight
        val kept = if (reserve > 0.dp) minOf(reserve.roundToPx() + gap, height) else 0
        val loose = constraints.copy(minHeight = 0)

        val top = fixedParts.first().measure(loose.copy(maxHeight = (height - kept).coerceAtLeast(0)))
        val rest = (height - top.height - gap).coerceAtLeast(0)
        val bottom = historyParts.firstOrNull()?.measure(loose.copy(minHeight = rest, maxHeight = rest))

        layout(constraints.maxWidth, height) {
            top.place(0, 0)
            bottom?.place(0, height - rest)
        }
    }
}

/** The way out of a game screen, with the 48 dp touch target a Material button has. */
@Composable
fun GameBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text = BACK) }
}

/** The board, centred, with the captured-piece rows just above and below it when there are any. */
@Composable
private fun CentredBoard(
    side: Dp,
    board: @Composable (side: Dp) -> Unit,
    captures: CapturedRows?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CapturedPieces.ROW_GAP),
    ) {
        captures?.let { CapturedPiecesRow(row = it.top, modifier = Modifier.width(side).testTag(CAPTURED_TOP_TAG)) }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            board(side)
        }
        captures?.let { CapturedPiecesRow(row = it.bottom, modifier = Modifier.width(side).testTag(CAPTURED_BOTTOM_TAG)) }
    }
}

private val SPACING = 8.dp
private val PANEL_PADDING = 16.dp

/**
 * Between the controls and the move list: less than [SPACING] because the list pads its own top
 * for the highlight, and every dp here is one the controls above can use (`D105`).
 */
private val HISTORY_GAP = 4.dp

private const val BACK = "Back"
