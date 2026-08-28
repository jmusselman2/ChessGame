package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.jmussel.chessgame.core.chess.Board
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

/** Marker sizes, as fractions of a square. */
private const val DOT_SCALE = 0.28f
private const val CAPTURE_RING_SCALE = 0.86f
private const val CAPTURE_RING_WIDTH = 0.07f

/**
 * Draws [board] as a square eight-by-eight grid.
 *
 * Everything shown comes from `game-core` through [BoardRendering]; this composable holds
 * no chess rules of its own. The board is drawn with [orientation]'s own side at the
 * bottom. [selectedSquare] is highlighted, [lastMove] marks the move just played, and
 * tapping any square calls [onSquareClick] — deciding what a tap means belongs to
 * [BoardInteraction].
 */
@Composable
fun ChessBoard(
    board: Board,
    modifier: Modifier = Modifier,
    selectedSquare: Square? = null,
    legalDestinations: Set<Square> = emptySet(),
    lastMove: Set<Square> = emptySet(),
    orientation: Side = Side.WHITE,
    onSquareClick: (Square) -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f),
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
                        isSelected = square.square == selectedSquare,
                        isLegalDestination = square.square in legalDestinations,
                        isLastMove = square.square in lastMove,
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
    isSelected: Boolean,
    isLegalDestination: Boolean,
    isLastMove: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .background(if (square.isLight) LightSquare else DarkSquare)
                .clickable(onClick = onClick)
                .then(if (isLastMove) Modifier.background(LastMoveSquare) else Modifier)
                .then(if (isSelected) Modifier.background(SelectedSquare) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        val cellSize = maxWidth

        square.piece?.let { piece ->
            Text(
                text = BoardRendering.glyphFor(piece.type).toString(),
                color = if (piece.side == Side.WHITE) WhitePiece else BlackPiece,
                fontSize = (cellSize.value * GLYPH_SCALE).sp,
                textAlign = TextAlign.Center,
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

@Preview(showBackground = true)
@Composable
private fun ChessBoardPreview() {
    ChessGameTheme {
        Box(modifier = Modifier.fillMaxWidth()) {
            ChessBoard(board = StandardPosition.BOARD)
        }
    }
}
