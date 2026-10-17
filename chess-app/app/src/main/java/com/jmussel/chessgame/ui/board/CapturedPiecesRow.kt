package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.ui.theme.ChessGameTheme

/** The test tag on the captured-piece row for the side drawn at the top of the board. */
const val CAPTURED_TOP_TAG = "capturedTop"

/** The test tag on the captured-piece row for the side drawn at the bottom of the board. */
const val CAPTURED_BOTTOM_TAG = "capturedBottom"

/**
 * One captured-piece row (`D104`, `M23.1`): who it is for, the pieces they captured, and `+N`
 * when they lead in material.
 *
 * Display only: it has no click action, and a screen reader hears [CapturedRow.summary] once
 * instead of a glyph at a time. It is [CapturedPieces.ROW_HEIGHT] tall whatever it holds, on a
 * light tint of the theme's surface, with no border. An empty row shows its label alone; no
 * "No captures" is drawn. The pieces never clip or scroll: they repeat
 * where they fit, are counted where they do not, and are drawn smaller only if even counting does
 * not fit ([CapturedPieces.fit]).
 */
@Composable
fun CapturedPiecesRow(
    row: CapturedRow,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .height(CapturedPieces.ROW_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = TINT_ALPHA), ROW_SHAPE)
                .clearAndSetSemantics { contentDescription = row.summary },
    ) {
        val labelLimit = maxWidth * LABEL_SHARE
        Row(
            modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = ROW_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ROW_PADDING),
        ) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = labelLimit),
            )
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                CapturedPieceGlyphs(row = row)
            }
            row.advantageText?.let { advantage ->
                Text(
                    text = advantage,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Both rows stacked, top first, for a two-pane panel, where they sit together above the controls. */
@Composable
fun CapturedPiecesPanel(
    rows: CapturedRows,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CapturedPieces.ROW_GAP)) {
        CapturedPiecesRow(row = rows.top, modifier = Modifier.fillMaxWidth().testTag(CAPTURED_TOP_TAG))
        CapturedPiecesRow(row = rows.bottom, modifier = Modifier.fillMaxWidth().testTag(CAPTURED_BOTTOM_TAG))
    }
}

/**
 * The pieces, fitted to the width the row leaves them. A player who has captured nothing gets
 * nothing here: the row keeps its label and height, and only [CapturedRow.summary] says so.
 */
@Composable
private fun CapturedPieceGlyphs(row: CapturedRow) {
    if (row.captured.isEmpty()) return

    BoxWithConstraints(contentAlignment = Alignment.CenterStart) {
        val fit = CapturedPieces.fit(row, maxWidth)
        val glyph = CapturedPieces.GLYPH * fit.scale

        if (!fit.compact) {
            // One glyph each, a little overlapped, like pieces set down beside a board.
            Box {
                row.captured.forEachIndexed { index, piece ->
                    Box(
                        modifier = Modifier.padding(start = CapturedPieces.GLYPH_STEP * index),
                    ) { CapturedGlyph(piece = piece, size = glyph) }
                }
            }
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CapturedPieces.GROUP_GAP * fit.scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.groups.forEach { group ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CapturedGlyph(piece = group.piece, size = glyph)
                        group.countText?.let { count ->
                            Text(
                                text = count,
                                fontSize = with(LocalDensity.current) { (COUNT_SIZE * fit.scale).toSp() },
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(CapturedPieces.COUNT_WIDTH * fit.scale),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * One captured piece, in the board's glyph and fill ([PieceStyle]), outlined in the other side's
 * colour so it reads on the row's light or dark tint. Sized in dp, like the board's pieces.
 */
@Composable
private fun CapturedGlyph(
    piece: Piece,
    size: Dp,
) {
    val density = LocalDensity.current
    val fontSize = with(density) { size.toSp() }
    val glyph = BoardRendering.glyphFor(piece.type).toString()
    val outline = with(density) { (size * OUTLINE_SHARE).toPx() }

    Box(modifier = Modifier.width(size), contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = PieceStyle.outline(piece.side),
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            softWrap = false,
            style = TextStyle(drawStyle = Stroke(width = outline, join = StrokeJoin.Round)),
        )
        Text(
            text = glyph,
            color = PieceStyle.fill(piece.side),
            fontSize = fontSize,
            lineHeight = fontSize,
            textAlign = TextAlign.Center,
            softWrap = false,
        )
    }
}

/** How strong the theme tint behind a row is: enough to group it, not enough to look like a control. */
private const val TINT_ALPHA = 0.6f

/** The most of a row its label may take, so the pieces always keep most of it. */
private const val LABEL_SHARE = 0.42f

/** A glyph's outline width, as a share of its size. */
private const val OUTLINE_SHARE = 0.1f

private val ROW_PADDING = 8.dp
private val ROW_SHAPE = RoundedCornerShape(6.dp)
private val COUNT_SIZE = 13.dp

@Preview(showBackground = true)
@Composable
private fun CapturedPiecesRowPreview() {
    ChessGameTheme {
        CapturedPiecesPanel(
            rows =
                CapturedPieces.rows(
                    captures =
                        listOf(
                            Piece(Side.BLACK, PieceType.QUEEN),
                            Piece(Side.BLACK, PieceType.PAWN),
                            Piece(Side.BLACK, PieceType.PAWN),
                            Piece(Side.WHITE, PieceType.KNIGHT),
                        ),
                    whiteBalanceQuarterPoints = 37,
                    topSide = Side.BLACK,
                    nameOf = { if (it == Side.WHITE) "You" else "Alex" },
                ),
        )
    }
}
