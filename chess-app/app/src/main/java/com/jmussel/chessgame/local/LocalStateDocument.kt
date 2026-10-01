package com.jmussel.chessgame.local

import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.CastlingRights
import com.jmussel.chessgame.core.chess.DrawRuleState
import com.jmussel.chessgame.core.chess.GameOutcome
import com.jmussel.chessgame.core.chess.GameResult
import com.jmussel.chessgame.core.chess.GameState
import com.jmussel.chessgame.core.chess.Move
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.PieceType
import com.jmussel.chessgame.core.chess.PositionKey
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square
import com.jmussel.chessgame.core.chess.TerminationReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How a chess position is stored in the local-game database: the `state` of a
 * `local_games` row and the `position_before` of a `local_moves` row (`D084`).
 *
 * A persistence DTO, so it lives here and not in `chess-core`. It has the same shape as the
 * server's `GameStateDocument`, but the two are separate: the app cannot depend on the
 * server, and each store may change on its own.
 *
 * Every field the rules read is kept, including the repetition counts and the halfmove
 * clock, so a restored game offers exactly the draw claims it offered before. Changing
 * how `chess-core` writes a [PositionKey] would silently break the counts already stored
 * here, as it would on the server.
 *
 * The board is eight rows of eight characters, rank 8 first, using FEN-style piece letters
 * and `.` for an empty square: what `Board.toString()` produces.
 */
@Serializable
internal data class LocalStateDocument(
    val board: List<String>,
    val sideToMove: String,
    val castling: String,
    val enPassant: String? = null,
    val halfmoveClock: Int = 0,
    val fullmoveNumber: Int = 1,
    val repetitions: Map<String, Int> = emptyMap(),
    val result: ResultDocument? = null,
) {
    /** The result of a finished game. */
    @Serializable
    data class ResultDocument(
        val outcome: String,
        val reason: String,
    )

    /** Rebuilds the position this document was written from. */
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
            result = result?.let { GameResult(GameOutcome.valueOf(it.outcome), TerminationReason.valueOf(it.reason)) },
        )

    companion object {
        private const val EMPTY_SQUARE: Char = '.'

        private val json = Json { ignoreUnknownKeys = true }

        /** [state] as the JSON text stored in the database. */
        fun encode(state: GameState): String = json.encodeToString(serializer(), of(state))

        /** The position stored as [text]. */
        fun decode(text: String): GameState = json.decodeFromString(serializer(), text).toGameState()

        fun of(state: GameState): LocalStateDocument =
            LocalStateDocument(
                board = state.board.toString().lines(),
                sideToMove = state.sideToMove.name,
                castling = state.castlingRights.toString(),
                enPassant = state.enPassantTarget?.name,
                halfmoveClock = state.halfmoveClock,
                fullmoveNumber = state.fullmoveNumber,
                repetitions = state.drawRuleState.positionCounts.mapKeys { (key, _) -> key.value },
                result = state.result?.let { ResultDocument(outcome = it.outcome.name, reason = it.reason.name) },
            )

        private fun boardFrom(rows: List<String>): Board {
            require(rows.size == Square.RANKS) { "A board has ${Square.RANKS} rows, not ${rows.size}" }

            val placement = mutableMapOf<Square, Piece>()
            rows.forEachIndexed { rowIndex, row ->
                require(row.length == Square.FILES) { "Row ${rowIndex + 1} is not ${Square.FILES} squares wide" }
                val rank = Square.RANKS - 1 - rowIndex
                row.forEachIndexed { file, symbol ->
                    if (symbol != EMPTY_SQUARE) placement[Square.of(file, rank)] = Piece.fromSymbol(symbol)
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

/**
 * A move as stored in `local_moves.move`: coordinate notation, as `Move.toString()` writes
 * it (`e2e4`, `e7e8q`).
 */
internal fun moveFromCoordinates(text: String): Move {
    require(text.length == 4 || text.length == 5) { "Not a move in coordinate notation: $text" }
    return Move(
        from = Square.parse(text.substring(0, 2)),
        to = Square.parse(text.substring(2, 4)),
        promotion = text.getOrNull(4)?.let(PieceType::fromLetter),
    )
}
