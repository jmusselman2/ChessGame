package com.jmussel.chessgame.ui.board

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.theme.ChessGameTheme

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val WhitePiece = Color(0xFFFFFFFF)
private val BlackPiece = Color(0xFF2B2B2B)
private val SelectedSquare = Color(0x8046A5FF)
private val LastMoveSquare = Color(0x66FFD54F)
private val DestinationMarker = Color(0x9925691E)

/** How much of a square's width a piece glyph fills. */
private const val GLYPH_SCALE = 0.72f

/** The turn, in degrees, that stands a piece on its head. */
private const val UPSIDE_DOWN = 180f

/** Marker sizes, as fractions of a square. */
private const val DOT_SCALE = 0.28f
private const val CAPTURE_RING_SCALE = 0.86f
private const val CAPTURE_RING_WIDTH = 0.07f

/** The test tag on the whole board. */
const val CHESS_BOARD_TAG = "chessBoard"

/** The test tag on one square of the board, e.g. `square-e4`. */
fun squareTag(square: Square): String = "square-$square"

/**
 * Draws [board] as a square eight-by-eight grid, [side] wide and tall.
 *
 * The caller decides the side (`GameLayoutSpec`, `D073`); the board never sizes itself from
 * the space around it, and nothing it draws reaches outside that square.
 *
 * Everything shown comes from `chess-core` through [BoardRendering]; this composable holds
 * no chess rules of its own. The board is drawn with [orientation]'s own side at the
 * bottom, and on a [faceToFace] board the other side's pieces are drawn upside down, for a
 * player sitting across from the device (`D087`). [selectedSquare] is highlighted,
 * [lastMove] marks the move just played, and tapping any square calls [onSquareClick] —
 * deciding what a tap means belongs to [BoardInteraction].
 */
@Composable
fun ChessBoard(
    board: Board,
    side: Dp,
    modifier: Modifier = Modifier,
    selectedSquare: Square? = null,
    legalDestinations: Set<Square> = emptySet(),
    lastMove: Set<Square> = emptySet(),
    orientation: Side = Side.WHITE,
    faceToFace: Boolean = false,
    onSquareClick: (Square) -> Unit = {},
) {
    val cellSize = side / Square.FILES

    // A piece is a picture of a piece, so it is sized with the square rather than with the
    // system font size: `toSp` undoes the font scale, non-linear scaling included.
    val glyphSize = with(LocalDensity.current) { (cellSize * GLYPH_SCALE).toSp() }

    Column(
        modifier =
            modifier
                .size(side)
                .clipToBounds()
                .testTag(CHESS_BOARD_TAG),
    ) {
        BoardRendering.rows(board, orientation).forEach { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                row.forEach { square ->
                    SquareCell(
                        square = square,
                        cellSize = cellSize,
                        glyphSize = glyphSize,
                        isSelected = square.square == selectedSquare,
                        isLegalDestination = square.square in legalDestinations,
                        isLastMove = square.square in lastMove,
                        isUpsideDown = square.piece?.let { BoardRendering.isUpsideDown(it, orientation, faceToFace) } == true,
                        onClick = { onSquareClick(square.square) },
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SquareCell(
    square: BoardSquare,
    cellSize: Dp,
    glyphSize: TextUnit,
    isSelected: Boolean,
    isLegalDestination: Boolean,
    isLastMove: Boolean,
    isUpsideDown: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .testTag(squareTag(square.square))
                .semantics { selected = isSelected }
                .background(if (square.isLight) LightSquare else DarkSquare)
                .clickable(onClick = onClick)
                .then(if (isLastMove) Modifier.background(LastMoveSquare) else Modifier)
                .then(if (isSelected) Modifier.background(SelectedSquare) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        square.piece?.let { piece ->
            // Kept for every piece, not only upside-down ones: a capture can turn the same glyph
            // over without laying the text out again.
            var turnAbout by remember { mutableStateOf(TransformOrigin.Center) }
            Text(
                text = BoardRendering.glyphFor(piece.type).toString(),
                color = if (piece.side == Side.WHITE) WhitePiece else BlackPiece,
                fontSize = glyphSize,
                textAlign = TextAlign.Center,
                onTextLayout = { layout -> turnAbout = inkCentre(layout) },
                modifier =
                    if (isUpsideDown) {
                        Modifier.graphicsLayer {
                            rotationZ = UPSIDE_DOWN
                            transformOrigin = turnAbout
                        }
                    } else {
                        Modifier
                    },
            )
        }

        if (isLegalDestination) {
            // A dot marks an empty destination; a ring around the piece marks a capture.
            if (square.piece == null) {
                Box(
                    modifier =
                        Modifier
                            .size(cellSize * DOT_SCALE)
                            .background(DestinationMarker, CircleShape),
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(cellSize * CAPTURE_RING_SCALE)
                            .border(cellSize * CAPTURE_RING_WIDTH, DestinationMarker, CircleShape),
                )
            }
        }
    }
}

/**
 * The point an upside-down glyph turns about: half-way across its text box, and level with
 * the middle of its drawn outline.
 *
 * A glyph's outline does not sit in the middle of its text box, so turning it about the box
 * put it up to 5% of a square higher or lower than the same glyph upright (measured on a
 * Pixel 7, `M21.8`). Turning it about the outline's middle leaves the outline at the height
 * an upright glyph's would have. Across, the outline was already within 1% of the middle.
 */
private fun inkCentre(layout: TextLayoutResult): TransformOrigin {
    val input = layout.layoutInput
    val text = input.text.text
    val height = layout.size.height
    if (text.isEmpty() || height == 0) return TransformOrigin.Center

    val paint = Paint()
    paint.textSize = with(input.density) { input.style.fontSize.toPx() }
    paint.typeface = Typeface.DEFAULT
    val outline = Path()
    paint.getTextPath(text, 0, text.length, 0f, 0f, outline)
    val ink = RectF()
    outline.computeBounds(ink, true)
    if (ink.isEmpty) return TransformOrigin.Center

    return TransformOrigin(0.5f, (layout.firstBaseline + ink.centerY()) / height)
}

/**
 * One piece a pawn may become, as a button showing that piece.
 *
 * The piece is drawn as large as the button allows and, like a piece on the board, in dp,
 * so it is recognisable at a glance and the font scale does not change it. Four of these
 * fit side by side in the narrowest two-pane panel (`D073`). The size is fixed rather than a
 * minimum because a Material button's own minimum is 58 dp, and four of those do not fit.
 */
@Composable
fun PromotionChoice(
    type: PieceType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glyphSize = with(LocalDensity.current) { PROMOTION_GLYPH.toSp() }

    Button(
        onClick = onClick,
        modifier = modifier.size(PROMOTION_BUTTON),
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(text = BoardRendering.glyphFor(type).toString(), fontSize = glyphSize, lineHeight = glyphSize)
    }
}

/** A promotion button's side, and the piece drawn on it. */
private val PROMOTION_BUTTON = 52.dp
private val PROMOTION_GLYPH = 32.dp

@Preview(showBackground = true)
@Composable
private fun ChessBoardPreview() {
    ChessGameTheme {
        ChessBoard(board = StandardPosition.BOARD, side = 384.dp)
    }
}
