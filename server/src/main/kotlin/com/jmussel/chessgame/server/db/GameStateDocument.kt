package com.jmussel.chessgame.server.db

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PositionKey
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import kotlinx.serialization.Serializable

/**
 * How a chess position is stored in a `jsonb` column.
 *
 * This is a persistence DTO and lives in the server, not in `game-core`: the domain types
 * stay free of serialization concerns, and the stored shape can change without changing
 * the rules.
 *
 * The board is eight rows of eight characters, rank 8 first, using FEN-style piece letters
 * and `.` for an empty square — the same rendering `Board.toString()` produces.
 */
@Serializable
data class GameStateDocument(
    val board: List<String>,
    val sideToMove: String,
    val castling: String,
    val enPassant: String? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
    val repetitions: Map<String, Int> = emptyMap(),
    val result: ResultDocument? = null,
) {
    /** The stored result of a finished game. */
    @Serializable
    data class ResultDocument(
        val outcome: String,
        val reason: String,
    )

    /** Rebuilds the domain state this document was written from. */
    fun toGameState(): GameState =
        GameState(
            board = boardFrom(board),
            sideToMove = Side.valueOf(sideToMove),
            castlingRights = castlingRightsFrom(castling),
            enPassantTarget = enPassant?.let(Square::parse),
            drawRuleState =
                DrawRuleState(
                    halfmoveClock = halfmoveClock,
                    positionCounts = repetitions.mapKeys { (key, _) -> PositionKey(key) },
                ),
            fullmoveNumber = fullmoveNumber,
            result =
                result?.let {
                    GameResult(GameOutcome.valueOf(it.outcome), TerminationReason.valueOf(it.reason))
                },
        )

    companion object {
        /** The character standing for an empty square. */
        const val EMPTY_SQUARE: Char = '.'

        /** Captures [state] for storage. */
        fun of(state: GameState): GameStateDocument =
            GameStateDocument(
                board = state.board.toString().lines(),
                sideToMove = state.sideToMove.name,
                castling = state.castlingRights.toString(),
                enPassant = state.enPassantTarget?.name,
                halfmoveClock = state.halfmoveClock,
                fullmoveNumber = state.fullmoveNumber,
                repetitions = state.drawRuleState.positionCounts.mapKeys { (key, _) -> key.value },
                result =
                    state.result?.let {
                        ResultDocument(outcome = it.outcome.name, reason = it.reason.name)
                    },
            )

        private fun boardFrom(rows: List<String>): Board {
            require(rows.size == Square.RANKS) { "A board has ${Square.RANKS} rows, not ${rows.size}" }

            val placement = mutableMapOf<Square, Piece>()
            rows.forEachIndexed { rowIndex, row ->
                require(row.length == Square.FILES) { "Row ${rowIndex + 1} is not ${Square.FILES} squares wide" }
                val rank = Square.RANKS - 1 - rowIndex
                row.forEachIndexed { file, symbol ->
                    if (symbol != EMPTY_SQUARE) {
                        placement[Square.of(file, rank)] = Piece.fromSymbol(symbol)
                    }
                }
            }
            return Board.of(placement)
        }

        private fun castlingRightsFrom(text: String): CastlingRights =
            CastlingRights(
                whiteKingSide = text.contains('K'),
                whiteQueenSide = text.contains('Q'),
                blackKingSide = text.contains('k'),
                blackQueenSide = text.contains('q'),
            )
    }
}
