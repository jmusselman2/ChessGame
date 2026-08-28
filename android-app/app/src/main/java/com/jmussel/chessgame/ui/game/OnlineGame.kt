package com.jmussel.chessgame.ui.game

import com.jmussel.chessgame.api.ChessApiException
import com.jmussel.chessgame.api.GameViewDto
import com.jmussel.chessgame.core.chess.Board
import com.jmussel.chessgame.core.chess.Piece
import com.jmussel.chessgame.core.chess.Side
import com.jmussel.chessgame.core.chess.Square

/**
 * A game the server owns, as the screen showing it has got so far.
 *
 * There is no board here until the server has sent one: an online game is never invented
 * locally, only drawn from what came back (`D004`).
 */
sealed interface OnlineGameState {
    /** The game is being fetched; [gameId] is all that is known about it. */
    data class Loading(
        val gameId: String,
    ) : OnlineGameState

    /** The canonical state, as the server last described it. */
    data class Ready(
        val game: GameViewDto,
    ) : OnlineGameState

    /**
     * The game could not be shown.
     *
     * [canRetry] is false when trying again cannot help — a game that is not yours stays
     * not yours, and a game that does not exist will not appear.
     */
    data class Failed(
        val gameId: String,
        val message: String,
        val canRetry: Boolean,
    ) : OnlineGameState
}

/**
 * Turning what the server said about a game into what the screen draws.
 *
 * Every answer here comes from the server's own words — the position, whose move it is,
 * whether the mover is in check, and how it ended. Nothing is recomputed from the position,
 * because the app is not the authority on any of it (`D004`). `game-core` is used only to
 * hold the pieces that were sent, so the same board renderer draws an online game and a
 * local one.
 *
 * Pure, so the mapping and the wording are tested without a screen.
 */
object OnlineGame {
    /**
     * The position the server sent, as a `game-core` board.
     *
     * The rows are rank 8 first, FEN-style letters, `.` for an empty square — the shape
     * `Board.toString()` produces, read back.
     */
    fun boardFrom(rows: List<String>): Board {
        val placement = mutableMapOf<Square, Piece>()

        rows.take(Square.RANKS).forEachIndexed { index, row ->
            val rank = Square.RANKS - 1 - index

            row.take(Square.FILES).forEachIndexed { file, symbol ->
                if (symbol != EMPTY_SQUARE) placement[Square.of(file, rank)] = Piece.fromSymbol(symbol)
            }
        }

        return Board.of(placement)
    }

    /** The side the viewer is playing, which decides which way the board faces. */
    fun sideOf(game: GameViewDto): Side = sideNamed(game.yourSide)

    /** The two squares of the move just played, for the highlight, or empty before the first. */
    fun lastMoveSquares(game: GameViewDto): Set<Square> {
        val move = game.lastMove ?: return emptySet()
        val from = Square.parseOrNull(move.from)
        val to = Square.parseOrNull(move.to)

        return setOfNotNull(from, to)
    }

    /** `"Alex • You are White"`. */
    fun headingFor(game: GameViewDto): String = "${game.opponent.username} $SEPARATOR You are ${sideLabel(game.yourSide)}"

    /**
     * Where the game stands: whose move it is, or how it finished.
     *
     * A finished game says what the server said finished it; nothing here decides an
     * outcome.
     */
    fun statusFor(game: GameViewDto): String {
        val result = game.result ?: return turnFor(game)
        val reason = game.terminationReason?.let(::reasonLabel)
        val verdict =
            when {
                result == DRAW -> "Drawn"
                result == "${game.yourSide}_WINS" -> "You won"
                else -> "${game.opponent.username} won"
            }

        return listOfNotNull(verdict, reason).joinToString(separator = " by ")
    }

    /** `"Move 18 • version 34"`, which is what a client has to send back with a command. */
    fun positionFor(game: GameViewDto): String = "Move ${game.moveNumber} $SEPARATOR version ${game.version}"

    /** The moves played so far, numbered in pairs as a game is written down. */
    fun moveListLines(game: GameViewDto): List<String> =
        game.moves.chunked(2).mapIndexed { index, pair ->
            val white = pair.first()
            val black = pair.getOrNull(1)

            "${index + 1}. $white${black?.let { " $it" }.orEmpty()}"
        }

    /** What to show when the server refused to show the game. */
    fun messageFor(refusal: ChessApiException): String =
        when (refusal.status) {
            FORBIDDEN -> NOT_YOURS
            NOT_FOUND -> NO_SUCH_GAME
            else -> refusal.explanation.ifBlank { REFUSED }
        }

    /** Whether trying again could help; a game that is not yours will never be. */
    fun canRetry(refusal: ChessApiException): Boolean = refusal.status !in setOf(FORBIDDEN, NOT_FOUND)

    /** What to show when the request never reached the server. */
    fun unreachableMessage(): String = UNREACHABLE

    /** `"Your move"`, or `"Alex to move"`, and the check that goes with it. */
    private fun turnFor(game: GameViewDto): String {
        val turn = if (game.yourTurn) YOUR_MOVE else "${game.opponent.username} to move"

        return if (game.inCheck) "$turn $SEPARATOR $CHECK" else turn
    }

    private fun sideNamed(name: String): Side = if (name.equals(Side.BLACK.name, ignoreCase = true)) Side.BLACK else Side.WHITE

    private fun sideLabel(name: String): String = if (sideNamed(name) == Side.BLACK) "Black" else "White"

    /** `THREEFOLD_REPETITION_CLAIM` reads as `threefold repetition claim`. */
    private fun reasonLabel(reason: String): String = reason.lowercase().replace('_', ' ')

    private const val EMPTY_SQUARE = '.'
    private const val SEPARATOR = "•"
    private const val DRAW = "DRAW"
    private const val YOUR_MOVE = "Your move"
    private const val CHECK = "Check"
    private const val FORBIDDEN = 403
    private const val NOT_FOUND = 404
    private const val NOT_YOURS = "This game is not yours to look at."
    private const val NO_SUCH_GAME = "That game is gone."
    private const val REFUSED = "The server would not show that game. Try again."
    private const val UNREACHABLE = "Could not reach the server. Check your connection and try again."
}
