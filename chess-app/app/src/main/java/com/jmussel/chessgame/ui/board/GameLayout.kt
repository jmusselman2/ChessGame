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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

        /** How a game screen given [width] by [height] is laid out. */
        fun forWindow(
            width: Dp,
            height: Dp,
        ): GameLayoutSpec {
            val oneColumn = oneColumn(width, height)

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
 * A game screen: the [board], and beside or below it Back, the [controls] and the
 * [moveList], arranged by [GameLayoutSpec].
 *
 * Shared by the local and the online game so both fit a window the same way. The board is
 * given its side and never moves while the rest scrolls. Back is drawn only when [onBack]
 * is given.
 */
@Composable
fun GameLayout(
    onBack: (() -> Unit)?,
    board: @Composable (side: Dp) -> Unit,
    controls: @Composable ColumnScope.() -> Unit,
    moveList: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val spec = GameLayoutSpec.forWindow(maxWidth, maxHeight)

        when {
            spec.twoPane ->
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier.width(spec.boardSide).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        board(spec.boardSide)
                    }

                    // Controls first, so they are in view without scrolling however long the
                    // move list grows.
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = PANEL_PADDING, vertical = SPACING),
                        verticalArrangement = Arrangement.spacedBy(SPACING),
                    ) {
                        onBack?.let { GameBackButton(onClick = it) }
                        controls()
                        moveList()
                    }
                }

            spec.scrolls ->
                // Too short for anything to stay put: everything scrolls together, so no
                // second scrolling list is nested inside.
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(SPACING),
                ) {
                    onBack?.let { GameBackButton(onClick = it, modifier = Modifier.padding(horizontal = SPACING)) }
                    CentredBoard(side = spec.boardSide, board = board)
                    Column(
                        modifier = Modifier.padding(horizontal = PANEL_PADDING),
                        verticalArrangement = Arrangement.spacedBy(SPACING),
                    ) {
                        controls()
                        moveList()
                    }
                }

            else ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(SPACING),
                ) {
                    onBack?.let { GameBackButton(onClick = it, modifier = Modifier.padding(horizontal = SPACING)) }
                    CentredBoard(side = spec.boardSide, board = board)

                    // Measured before the move list, so the controls take the height they
                    // need and scroll only when even that is not there; the move list gets
                    // what is left and scrolls on its own.
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = PANEL_PADDING),
                        verticalArrangement = Arrangement.spacedBy(SPACING),
                    ) {
                        controls()
                    }
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = PANEL_PADDING),
                        verticalArrangement = Arrangement.spacedBy(MOVE_LIST_SPACING),
                    ) {
                        moveList()
                    }
                }
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

@Composable
private fun CentredBoard(
    side: Dp,
    board: @Composable (side: Dp) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        board(side)
    }
}

private val SPACING = 8.dp
private val PANEL_PADDING = 16.dp
private val MOVE_LIST_SPACING = 2.dp
private const val BACK = "Back"
