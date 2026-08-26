package com.jmussel.chessgame.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.jmussel.chessgame.core.chess.StandardPosition
import com.jmussel.chessgame.ui.theme.ChessGameTheme

private val LightSquare = Color(0xFFF0D9B5)
private val DarkSquare = Color(0xFFB58863)
private val WhitePiece = Color(0xFFFFFFFF)
private val BlackPiece = Color(0xFF2B2B2B)

/** How much of a square's width a piece glyph fills. */
private const val GLYPH_SCALE = 0.72f

/**
 * Draws [board] as a square eight-by-eight grid, White at the bottom.
 *
 * Everything shown comes from `game-core` through [BoardRendering]; this composable holds
 * no chess rules of its own.
 */
@Composable
fun ChessBoard(
    board: Board,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(1f),
    ) {
        BoardRendering.rows(board).forEach { row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                row.forEach { square ->
                    SquareCell(
                        square = square,
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
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.background(if (square.isLight) LightSquare else DarkSquare),
        contentAlignment = Alignment.Center,
    ) {
        val glyphSize = (maxWidth.value * GLYPH_SCALE).sp
        square.piece?.let { piece ->
            Text(
                text = BoardRendering.glyphFor(piece.type).toString(),
                color = if (piece.side == Side.WHITE) WhitePiece else BlackPiece,
                fontSize = glyphSize,
                textAlign = TextAlign.Center,
            )
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
