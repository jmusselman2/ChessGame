package com.jmussel.chessgame.ui.board

import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.theme.ChessGameTheme
import kotlin.math.floor
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path as ComposePath

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val SelectedSquare = Color(0x8046A5FF)
private val SelectedOutline = Color(0xFF1565C0)
private val LastMoveSquare = Color(0x66FFD54F)
private val LastMoveCorner = Color(0xCC8D6E00)
private val CheckGlow = Color(0xE6D32F2F)
private val DestinationMarker = Color(0x9925691E)

/** How much of a square's width a piece glyph fills. */
private const val GLYPH_SCALE = 0.72f

/** The turn, in degrees, that stands a piece on its head. */
private const val UPSIDE_DOWN = 180f

/** Marker sizes, as fractions of a square. */
private const val DOT_SCALE = 0.28f
private const val CAPTURE_RING_SCALE = 0.86f
private const val CAPTURE_RING_WIDTH = 0.07f
private const val LAST_MOVE_CORNER = 0.24f
private const val LABEL_SCALE = 0.2f
private const val LABEL_INSET = 0.04f
private const val SELECTED_OUTLINE_WIDTH = 0.08f

/**
 * How a piece of each side is coloured, on the board and wherever a piece is shown beside it,
 * as in the captured-piece rows (`M23.1`). Every piece is the same solid glyph
 * ([BoardRendering.glyphFor]); its [fill] tells the sides apart.
 */
object PieceStyle {
    val WHITE: Color = Color(0xFFFFFFFF)
    val BLACK: Color = Color(0xFF2B2B2B)

    /** The colour a piece of [side] is filled with. */
    fun fill(side: Side): Color = if (side == Side.WHITE) WHITE else BLACK

    /**
     * The outline a piece of [side] gets off the board, where no square sits behind it: the other
     * side's colour, so white pieces read on a light background and black ones on a dark one.
     */
    fun outline(side: Side): Color = fill(side.opposite)
}

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
 * player sitting across from the device (`D087`). Tapping any square calls [onSquareClick] —
 * deciding what a tap means belongs to [BoardInteraction].
 *
 * Every playable board marks the same things the same way (`M21.17`): [lastMove], the
 * [checkedKing], the [selectedSquare], and the [legalDestinations] as dots and capture
 * rings above them. [BoardRendering.feedbackFor] decides which apply and in what order;
 * each has its own shape as well as its colour, and each square says what it shows to a
 * screen reader.
 *
 * A piece may also be dragged (`M22.12`): held for a long press, then moved and dropped.
 * [dragDestinations] says where the piece on a square may go, or `null` where no drag may
 * start, and [onPieceDrop] is told where it was dropped; both belong to [BoardInteraction]
 * or `OnlineGame`, as a tap's meaning does. The long press keeps an ordinary swipe across the
 * board scrolling the screen, the Android convention for dragging inside scrolling content.
 * While a piece is held the board shows it under the finger, its square selected and its
 * destinations marked; none of that reaches the game until it is dropped.
 */
@Composable
fun ChessBoard(
    board: Board,
    side: Dp,
    modifier: Modifier = Modifier,
    selectedSquare: Square? = null,
    legalDestinations: Set<Square> = emptySet(),
    lastMove: Set<Square> = emptySet(),
    /** The king in check, from `chess-core` or the server's `inCheck`, never worked out here. */
    checkedKing: Square? = null,
    orientation: Side = Side.WHITE,
    faceToFace: Boolean = false,
    onSquareClick: (Square) -> Unit = {},
    dragDestinations: (Square) -> Set<Square>? = { null },
    onPieceDrop: (from: Square, to: Square) -> Unit = { _, _ -> },
) {
    val cellSize = side / Square.FILES
    var drag by remember { mutableStateOf<PieceDrag?>(null) }
    val currentBoard by rememberUpdatedState(board)
    val currentOrientation by rememberUpdatedState(orientation)
    val currentDestinations by rememberUpdatedState(dragDestinations)
    val currentDrop by rememberUpdatedState(onPieceDrop)
    val shownSelection = drag?.from ?: selectedSquare
    val shownDestinations = drag?.destinations ?: legalDestinations

    // A piece is a picture of a piece, so it is sized with the square rather than with the
    // system font size: `toSp` undoes the font scale, non-linear scaling included.
    val glyphSize = with(LocalDensity.current) { (cellSize * GLYPH_SCALE).toSp() }
    // The coordinates are small text inside the edge squares, sized with the square too.
    val labelSize = with(LocalDensity.current) { (cellSize * LABEL_SCALE).toSp() }
    val labels = rememberTextMeasurer()

    Box(
        modifier =
            modifier
                .size(side)
                .clipToBounds()
                .testTag(CHESS_BOARD_TAG)
                .pointerInput(Unit) {
                    fun squareUnder(at: Offset): Square? {
                        val cell = size.width.toFloat() / Square.FILES
                        return BoardRendering.squareDrawnAt(floor(at.y / cell).toInt(), floor(at.x / cell).toInt(), currentOrientation)
                    }

                    awaitEachGesture {
                        // Watched before the squares see it, so a drag once started can be kept
                        // from them; until then nothing is consumed and a tap or a scroll goes on.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val from = squareUnder(down.position) ?: return@awaitEachGesture
                        val piece = currentBoard.pieceAt(from) ?: return@awaitEachGesture
                        val destinations = currentDestinations(from) ?: return@awaitEachGesture

                        // Lifted, moved away (a scroll, or a swipe) or taken by something else
                        // before the long press: not a drag. Only a press held still picks it up.
                        val endedEarly =
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                do {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                                } while (
                                    change != null &&
                                    change.pressed &&
                                    !change.isConsumed &&
                                    (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
                                )
                            }
                        if (endedEarly != null) return@awaitEachGesture

                        var dropped: Square? = null
                        drag = PieceDrag(from, piece, destinations, down.position)
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                // A pointer the system cancels arrives already consumed: no drop.
                                val cancelled = change.isConsumed
                                event.changes.forEach { it.consume() }
                                if (!change.pressed) {
                                    if (!cancelled) dropped = squareUnder(change.position)
                                    break
                                }
                                drag = drag?.copy(at = change.position)
                            }
                        } finally {
                            drag = null
                        }
                        dropped?.takeIf { it != from }?.let { currentDrop(from, it) }
                    }
                },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoardRendering.rows(board, orientation).forEachIndexed { rowIndex, row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                ) {
                    row.forEachIndexed { columnIndex, square ->
                        SquareCell(
                            square = square,
                            cellSize = cellSize,
                            glyphSize = glyphSize,
                            edgeLabels = BoardRendering.edgeLabels(rowIndex, columnIndex, orientation),
                            labelStyle = TextStyle(fontSize = labelSize, color = if (square.isLight) DarkSquare else LightSquare),
                            labels = labels,
                            feedback = BoardRendering.feedbackFor(square, shownSelection, shownDestinations, lastMove, checkedKing),
                            isUpsideDown = square.piece?.let { BoardRendering.isUpsideDown(it, orientation, faceToFace) } == true,
                            // The held piece is drawn under the finger instead.
                            isLifted = drag?.from == square.square,
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

        drag?.let { held ->
            Box(
                modifier =
                    Modifier
                        .offset {
                            val half = (cellSize / 2).roundToPx()
                            IntOffset(held.at.x.roundToInt() - half, held.at.y.roundToInt() - half)
                        }.size(cellSize),
                contentAlignment = Alignment.Center,
            ) {
                PieceGlyph(
                    piece = held.piece,
                    glyphSize = glyphSize,
                    isUpsideDown = BoardRendering.isUpsideDown(held.piece, orientation, faceToFace),
                )
            }
        }
    }
}

/** A piece being dragged: where it came from, where it may go, and where the finger is. */
private data class PieceDrag(
    val from: Square,
    val piece: Piece,
    val destinations: Set<Square>,
    val at: Offset,
)

@Composable
private fun SquareCell(
    square: BoardSquare,
    cellSize: Dp,
    glyphSize: TextUnit,
    edgeLabels: EdgeLabels,
    labelStyle: TextStyle,
    labels: TextMeasurer,
    feedback: SquareFeedback,
    isUpsideDown: Boolean,
    isLifted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .testTag(squareTag(square.square))
                .semantics {
                    selected = SquareHighlight.SELECTED in feedback.highlights
                    feedback.description?.let { stateDescription = it }
                    // For tests only: a custom key no screen reader reads (board accessibility is F40).
                    if (!edgeLabels.isEmpty) this[EdgeLabelsKey] = edgeLabels
                }.background(if (square.isLight) LightSquare else DarkSquare)
                .clickable(onClick = onClick)
                .drawBehind {
                    // The coordinates first, so every highlight is drawn over them and the
                    // destination marks after them; none of those is ever hidden (`M22.11`).
                    coordinates(edgeLabels, labels, labelStyle)
                    feedback.highlights.forEach { highlight(it) }
                },
        contentAlignment = Alignment.Center,
    ) {
        square.piece?.takeUnless { isLifted }?.let { piece -> PieceGlyph(piece, glyphSize, isUpsideDown) }

        // Drawn after the piece and the square treatments, so neither hides it: a dot marks an
        // empty destination, and a ring around the piece marks a capture.
        when (feedback.destination) {
            DestinationMark.MOVE ->
                Box(
                    modifier =
                        Modifier
                            .size(cellSize * DOT_SCALE)
                            .background(DestinationMarker, CircleShape),
                )

            DestinationMark.CAPTURE ->
                Box(
                    modifier =
                        Modifier
                            .size(cellSize * CAPTURE_RING_SCALE)
                            .border(cellSize * CAPTURE_RING_WIDTH, DestinationMarker, CircleShape),
                )

            null -> Unit
        }
    }
}

/** One piece's glyph, upside down for the player across a face-to-face board (`D087`). */
@Composable
private fun PieceGlyph(
    piece: Piece,
    glyphSize: TextUnit,
    isUpsideDown: Boolean,
) {
    // Kept for every piece, not only upside-down ones: a capture can turn the same glyph
    // over without laying the text out again.
    var turnAbout by remember { mutableStateOf(TransformOrigin.Center) }
    Text(
        text = BoardRendering.glyphFor(piece.type).toString(),
        color = PieceStyle.fill(piece.side),
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

/** The rank number in the top-left corner and the file letter in the bottom-right, never turned. */
private fun DrawScope.coordinates(
    edgeLabels: EdgeLabels,
    labels: TextMeasurer,
    style: TextStyle,
) {
    val inset = size.minDimension * LABEL_INSET
    edgeLabels.rank?.let { rank -> drawText(labels.measure(rank, style), topLeft = Offset(inset, inset)) }
    edgeLabels.file?.let { file ->
        val layout = labels.measure(file, style)
        drawText(layout, topLeft = Offset(size.width - layout.size.width - inset, size.height - layout.size.height - inset))
    }
}

/** The coordinates an edge square carries, exposed to tests and to nothing that speaks. */
val EdgeLabelsKey = SemanticsPropertyKey<EdgeLabels>("EdgeLabels")

/**
 * Draws one square treatment (`M21.17`). Each has a shape of its own, so they can be told
 * apart without their colours: corner triangles for the last move, a glow for a check, an
 * outline for the selected square.
 */
private fun DrawScope.highlight(highlight: SquareHighlight) {
    when (highlight) {
        SquareHighlight.LAST_MOVE -> {
            drawRect(LastMoveSquare)
            val corner = size.minDimension * LAST_MOVE_CORNER
            listOf(
                Offset(0f, 0f) to Offset(1f, 1f),
                Offset(size.width, 0f) to Offset(-1f, 1f),
                Offset(0f, size.height) to Offset(1f, -1f),
                Offset(size.width, size.height) to Offset(-1f, -1f),
            ).forEach { (at, inwards) ->
                val triangle =
                    ComposePath().apply {
                        moveTo(at.x, at.y)
                        lineTo(at.x + inwards.x * corner, at.y)
                        lineTo(at.x, at.y + inwards.y * corner)
                        close()
                    }
                drawPath(triangle, LastMoveCorner)
            }
        }

        SquareHighlight.CHECK ->
            drawCircle(
                brush = Brush.radialGradient(listOf(CheckGlow, Color.Transparent), center = center, radius = size.minDimension / 2),
                radius = size.minDimension / 2,
            )

        SquareHighlight.SELECTED -> {
            drawRect(SelectedSquare)
            val width = size.minDimension * SELECTED_OUTLINE_WIDTH
            drawRect(
                color = SelectedOutline,
                topLeft = Offset(width / 2, width / 2),
                size = Size(size.width - width, size.height - width),
                style = Stroke(width = width),
            )
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

    // Named for a screen reader, which would otherwise have only a chess glyph to read (`M21.17`).
    val name = BoardRendering.nameFor(type)
    Button(
        onClick = onClick,
        modifier = modifier.size(PROMOTION_BUTTON).semantics { contentDescription = name },
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
