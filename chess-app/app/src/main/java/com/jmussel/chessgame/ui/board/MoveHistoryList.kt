package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The test tag on the move list's own scrolling region. */
const val MOVE_HISTORY_TAG = "moveHistory"

/** What a screen reader hears on the row holding the current move, before that move: `"Current move: e5"`. */
const val CURRENT_MOVE = "Current move"

/**
 * A move list (`D105`, `M23.2`): numbered White and Black pairs, one line each as `M22.7` wrote
 * them, in its own vertical scroll. Give it a bounded height; nothing in it scrolls sideways,
 * and a long line wraps.
 *
 * The current move is drawn bold on a rounded highlight in the theme's primary container colour,
 * so it does not rely on colour alone, and its row tells a screen reader which move is current.
 * It is display only: nothing here can be tapped.
 *
 * When the current move changes — a move, an opponent's move from a refresh, Undo, Redo, a
 * review step — its row is scrolled fully into view with its number. A recomposition that leaves
 * the same move current leaves the scroll where the player put it, and so does a change in the
 * list's height, except that a list showing the current move keeps showing it. An empty list
 * scrolls back to the top.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoveHistoryList(
    history: MoveHistory,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val scroll = rememberScrollState()
    val requester = remember { BringIntoViewRequester() }
    val current = history.current
    val currentRow = history.currentRow

    Column(
        // Padded inside the scroll so the highlight around a first or last move is not clipped.
        modifier = modifier.testTag(MOVE_HISTORY_TAG).verticalScroll(scroll).padding(vertical = HIGHLIGHT_PADDING),
        verticalArrangement = Arrangement.spacedBy(ROW_SPACING),
    ) {
        history.rows.forEachIndexed { index, row ->
            MoveHistoryRowText(
                row = row,
                style = style,
                modifier = if (index == currentRow) Modifier.bringIntoViewRequester(requester) else Modifier,
            )
        }
    }

    // The current move is always the list's last row, so a list showing its end shows it. When
    // the list's height changes, as when a request appears among the controls above it, a list
    // that was at its end stays there; one the player scrolled back stays where they put it.
    // Bringing a row into view may leave the padding below it unscrolled, which is still the end.
    val slack = with(LocalDensity.current) { HIGHLIGHT_PADDING.roundToPx() } + 1
    var showingEnd by remember { mutableStateOf(true) }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.value }.collect { showingEnd = it >= scroll.maxValue - slack }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.maxValue }.collect { end -> if (showingEnd && scroll.value < end) scroll.scrollTo(end) }
    }

    // Keyed on which move is current, never on the list itself, so a refresh that changes
    // nothing does not move a list the player has scrolled.
    LaunchedEffect(current?.ply, current?.san) {
        if (current == null) {
            scroll.scrollTo(0)
        } else {
            // After the row is laid out where it now belongs.
            withFrameNanos { }
            requester.bringIntoView()
        }
    }
}

/** One numbered row, its current move, if it holds it, drawn bold on the highlight. */
@Composable
private fun MoveHistoryRowText(
    row: MoveHistoryRow,
    style: TextStyle,
    modifier: Modifier,
) {
    val current = row.current
    val highlight = MaterialTheme.colorScheme.primaryContainer
    val onHighlight = MaterialTheme.colorScheme.onPrimaryContainer
    var range: Pair<Int, Int>? = null
    val text =
        buildAnnotatedString {
            append("${row.number}.")
            listOfNotNull(row.white, row.black).forEach { entry ->
                append(" ")
                if (entry.current) {
                    val start = length
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = onHighlight)) { append(entry.san) }
                    range = start to length
                } else {
                    append(entry.san)
                }
            }
        }
    val highlighted = range
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Text(
        text = text,
        style = style,
        onTextLayout = { layout = it },
        modifier =
            modifier
                .then(if (current != null) Modifier.semantics { stateDescription = "$CURRENT_MOVE: ${current.san}" } else Modifier)
                .drawBehind {
                    val shown = layout ?: return@drawBehind
                    val (start, end) = highlighted ?: return@drawBehind
                    // The move's own box, widened by a rounded stroke: a padded pill behind it alone.
                    val path = shown.getPathForRange(start, end)
                    drawPath(path, highlight)
                    drawPath(path, highlight, style = Stroke(width = HIGHLIGHT_PADDING.toPx() * 2, join = StrokeJoin.Round))
                },
    )
}

/**
 * The height of a move list showing a single row in [style], highlight included: the least a
 * nonempty list is given (`D105`), which keeps the current move in view.
 */
@Composable
fun oneMoveRowHeight(style: TextStyle): Dp = with(LocalDensity.current) { style.lineHeight.toDp() } + HIGHLIGHT_PADDING * 2

private val ROW_SPACING = 2.dp
private val HIGHLIGHT_PADDING = 3.dp
