package com.jmussel.chessgame.ai

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.ChessRules
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.StandardPosition

/**
 * A position from the placement and side-to-move fields of FEN, with no castling and no
 * en passant: `"6k1/5ppp/8/8/8/8/5PPP/R5K1 w"`.
 */
internal fun position(fen: String): GameState {
    val (placement, side) = fen.split(" ")
    val pieces = mutableMapOf<Square, Piece>()
    placement.split("/").forEachIndexed { row, text ->
        var file = 0
        text.forEach { symbol ->
            if (symbol.isDigit()) {
                file += symbol.digitToInt()
            } else {
                pieces[Square.of(file, Square.RANKS - 1 - row)] = Piece.fromSymbol(symbol)
                file++
            }
        }
    }
    return GameState(
        board = Board.of(pieces),
        sideToMove = if (side == "w") Side.WHITE else Side.BLACK,
        castlingRights = CastlingRights.NONE,
    )
}

/** Moves in coordinate form, `"e2e4 e7e5"`. */
internal fun moves(line: String): List<Move> =
    line.split(" ").filter { it.isNotEmpty() }.map { Move.of(it.substring(0, 2), it.substring(2, 4)) }

/** The position after [line] from the start. */
internal fun after(line: String): GameState =
    moves(line).fold(StandardPosition.newGame()) { state, move -> ChessRules.applyMove(state, move) }
